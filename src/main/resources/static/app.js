"use strict";

/**
 * Lucky Wheel UI: roster -> spin -> wheel reveal.
 *
 * The reveal is playback, not decision: POST /api/spins returns the whole
 * draw order in one call, then playReveal() replays it one pick at a time
 * (wheel spins, lands on the pick, banner announces it, pick is removed
 * from the wheel) before the final draw order is shown in an overlay.
 * Skip jumps straight to the overlay — it never calls the API again.
 */

const STORAGE_KEY = "luckyWheel.roster";
// P0 "Original" palette from the prototype verdict on issue #1: six segment
// colours, with per-segment light/dark labels picked for contrast.
const WHEEL_PALETTE = ["#ffd166", "#ef476f", "#06d6a0", "#118ab2", "#f78c6b", "#7b61a8"];
const WHEEL_LABEL_LIGHT = "#ffffff";
const WHEEL_LABEL_DARK = "#14172b";

/** @typedef {{ name: string, eligible: boolean }} Member */

/** @type {Member[]} */
let roster = loadRoster();

/** True while a reveal (per-pick playback sequence) is in progress. */
let spinning = false;

/** Set to the in-flight reveal's skip token while spinning, else null. */
let activeSkipToken = null;

// ---- DOM references ----
const drawerToggle = document.getElementById("drawer-toggle");
const drawerClose = document.getElementById("drawer-close");
const drawerBackdrop = document.getElementById("drawer-backdrop");
const rosterDrawer = document.getElementById("roster-drawer");

const addMemberForm = document.getElementById("add-member-form");
const memberNameInput = document.getElementById("member-name-input");
const addMemberNotice = document.getElementById("add-member-notice");
const memberList = document.getElementById("member-list");
const emptyRosterNotice = document.getElementById("empty-roster-notice");

const countSelect = document.getElementById("count-select");
const orderEveryoneButton = document.getElementById("order-everyone-button");
const spinButton = document.getElementById("spin-button");
const skipButton = document.getElementById("skip-button");
const spinDisabledReason = document.getElementById("spin-disabled-reason");
const spinError = document.getElementById("spin-error");

const pickBanner = document.getElementById("pick-banner");
const wheelCanvas = document.getElementById("wheel-canvas");

const resultOverlay = document.getElementById("result-overlay");
const drawOrderList = document.getElementById("draw-order-list");
const closeOverlayButton = document.getElementById("close-overlay-button");

// ---- Persistence (Roster lives in the browser) ----

function loadRoster() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) return [];
        const parsed = JSON.parse(raw);
        if (!Array.isArray(parsed)) return [];
        return parsed
            .filter((entry) => entry && typeof entry.name === "string")
            .map((entry) => ({ name: entry.name, eligible: entry.eligible !== false }));
    } catch (error) {
        console.error("Failed to load roster from localStorage:", error);
        return [];
    }
}

function saveRoster() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(roster));
}

// ---- Roster mutations ----
// Guarded with `if (spinning) return;` — the reveal plays back a snapshot
// of the eligible members taken at spin time; mutating the roster
// mid-reveal would desync that snapshot from what's on screen.

function normalizeName(name) {
    return name.trim();
}

function isDuplicateName(name) {
    return roster.some((member) => member.name === name);
}

function addMember(rawName) {
    if (spinning) return;
    const name = normalizeName(rawName);
    if (name === "") {
        showAddMemberNotice("請輸入姓名。");
        return;
    }
    if (isDuplicateName(name)) {
        showAddMemberNotice(`「${name}」已經在名單中，姓名不可重複。`);
        return;
    }
    hideAddMemberNotice();
    roster.push({ name, eligible: true });
    saveRoster();
    render();
}

function removeMember(name) {
    if (spinning) return;
    roster = roster.filter((member) => member.name !== name);
    saveRoster();
    render();
}

function setEligible(name, eligible) {
    if (spinning) return;
    const member = roster.find((m) => m.name === name);
    if (!member) return;
    member.eligible = eligible;
    saveRoster();
    render();
}

function eligibleMembers() {
    return roster.filter((member) => member.eligible);
}

// ---- Notices ----

function showAddMemberNotice(message) {
    addMemberNotice.textContent = message;
    addMemberNotice.hidden = false;
}

function hideAddMemberNotice() {
    addMemberNotice.hidden = true;
    addMemberNotice.textContent = "";
}

function showSpinError(message) {
    spinError.textContent = message;
    spinError.hidden = false;
}

function hideSpinError() {
    spinError.hidden = true;
    spinError.textContent = "";
}

// ---- Drawer ----

function openDrawer() {
    rosterDrawer.classList.add("open");
    drawerBackdrop.hidden = false;
    drawerToggle.setAttribute("aria-expanded", "true");
}

function closeDrawer() {
    rosterDrawer.classList.remove("open");
    drawerBackdrop.hidden = true;
    drawerToggle.setAttribute("aria-expanded", "false");
}

// ---- Wheel (canvas) ----

/** WCAG relative luminance of a "#rrggbb" colour. */
function luminance(hex) {
    const channels = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
        .map((v) => (v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)));
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrastRatio(a, b) {
    const l1 = luminance(a);
    const l2 = luminance(b);
    return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
}

/** Light or dark label, whichever reads better on the given segment fill. */
function segmentLabelColor(fill) {
    return contrastRatio(fill, WHEEL_LABEL_LIGHT) >= contrastRatio(fill, WHEEL_LABEL_DARK)
        ? WHEEL_LABEL_LIGHT
        : WHEEL_LABEL_DARK;
}

class Wheel {
    constructor(canvas, palette) {
        this.ctx = canvas.getContext("2d");
        this.size = canvas.width;
        this.palette = palette;
        this.names = [];
        this.rotation = 0;
    }

    setNames(names) {
        this.names = names;
        this.draw();
    }

    draw() {
        const { ctx, size } = this;
        const r = size / 2 - 4;
        const cx = size / 2;
        const cy = size / 2;
        ctx.clearRect(0, 0, size, size);

        const n = this.names.length;
        if (n === 0) {
            ctx.beginPath();
            ctx.arc(cx, cy, r, 0, Math.PI * 2);
            ctx.fillStyle = "rgba(136,136,136,0.2)";
            ctx.fill();
            ctx.fillStyle = WHEEL_LABEL_LIGHT;
            ctx.font = "15px sans-serif";
            ctx.textAlign = "center";
            ctx.fillText("名單是空的", cx, cy);
            return;
        }

        const seg = (Math.PI * 2) / n;
        for (let i = 0; i < n; i++) {
            const a0 = this.rotation + i * seg;
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.arc(cx, cy, r, a0, a0 + seg);
            ctx.closePath();
            let fill = this.palette[i % this.palette.length];
            // When the count wraps to exactly one leftover segment, the last
            // segment would repeat the first's colour right next to it.
            if (n % this.palette.length === 1 && i === n - 1) fill = this.palette[1];
            ctx.fillStyle = fill;
            ctx.fill();
            ctx.strokeStyle = "rgba(255,255,255,.35)";
            ctx.stroke();

            ctx.save();
            ctx.translate(cx, cy);
            ctx.rotate(a0 + seg / 2);
            ctx.textAlign = "right";
            ctx.fillStyle = segmentLabelColor(fill);
            ctx.font = `${Math.min(16, Math.max(11, r / 9))}px sans-serif`;
            let label = this.names[i];
            if (label.length > 8) label = label.slice(0, 7) + "…";
            ctx.fillText(label, r - 12, 5);
            ctx.restore();
        }

        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.12, 0, Math.PI * 2);
        ctx.fillStyle = "#fff";
        ctx.fill();
        ctx.strokeStyle = "rgba(0,0,0,0.2)";
        ctx.stroke();
    }

    /**
     * Animates the wheel spinning to land on `this.names[index]` under the
     * top pointer, with a cubic ease-out. Resolves early (jumping straight
     * to the final rotation) if `skipToken.skipped` becomes true mid-spin.
     */
    spinTo(index, duration, skipToken) {
        if (window.__instant) {
            this.rotation = this._targetRotationFor(index);
            this.draw();
            return Promise.resolve();
        }
        return new Promise((resolve) => {
            const n = this.names.length;
            const seg = (Math.PI * 2) / n;
            const pointer = -Math.PI / 2;
            const centre = (index + 0.5) * seg;
            let target = pointer - centre;
            const turns = 4 * Math.PI * 2;
            target =
                this.rotation +
                turns +
                (((target - this.rotation - turns) % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);

            const from = this.rotation;
            const delta = target - from;
            const t0 = performance.now();
            const ease = (t) => 1 - Math.pow(1 - t, 3);

            const step = () => {
                if (skipToken.skipped) {
                    this.rotation = target;
                    this.draw();
                    resolve();
                    return;
                }
                const t = Math.min(1, (performance.now() - t0) / duration);
                this.rotation = from + delta * ease(t);
                this.draw();
                if (t < 1) {
                    setTimeout(step, 16);
                } else {
                    resolve();
                }
            };
            step();
        });
    }

    _targetRotationFor(index) {
        const n = this.names.length;
        const seg = (Math.PI * 2) / n;
        const pointer = -Math.PI / 2;
        return pointer - (index + 0.5) * seg;
    }
}

const wheel = new Wheel(wheelCanvas, WHEEL_PALETTE);

/**
 * Resolves after `ms`, unless `skipToken.skipped` becomes true first (in
 * which case it resolves immediately). Polling keeps skip responsive
 * without needing to track/clear a pending timer.
 */
function sleep(ms, skipToken) {
    if (window.__instant) return Promise.resolve();
    return new Promise((resolve) => {
        const start = performance.now();
        const check = () => {
            if (skipToken.skipped || performance.now() - start >= ms) {
                resolve();
                return;
            }
            setTimeout(check, 40);
        };
        check();
    });
}

// ---- Rendering ----

function render() {
    renderMemberList();
    renderCountOptions();
    renderSpinAvailability();
    if (!spinning) {
        wheel.setNames(eligibleMembers().map((member) => member.name));
    }
}

function renderMemberList() {
    memberList.innerHTML = "";
    emptyRosterNotice.hidden = roster.length !== 0;

    for (const member of roster) {
        const item = document.createElement("li");
        item.className = member.eligible ? "" : "not-eligible";

        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.checked = member.eligible;
        checkbox.disabled = spinning;
        checkbox.setAttribute("aria-label", `${member.name} 是否可抽選 (Eligible)`);
        checkbox.addEventListener("change", () => setEligible(member.name, checkbox.checked));

        const nameSpan = document.createElement("span");
        nameSpan.className = "member-name";
        nameSpan.textContent = member.name;

        const removeButton = document.createElement("button");
        removeButton.type = "button";
        removeButton.textContent = "移除";
        removeButton.disabled = spinning;
        removeButton.setAttribute("aria-label", `移除 ${member.name}`);
        removeButton.addEventListener("click", () => removeMember(member.name));

        item.append(checkbox, nameSpan, removeButton);
        memberList.appendChild(item);
    }
}

function renderCountOptions() {
    const eligibleCount = eligibleMembers().length;
    const previousValue = countSelect.value;

    countSelect.innerHTML = "";
    for (let count = 1; count <= eligibleCount; count++) {
        const option = document.createElement("option");
        option.value = String(count);
        option.textContent = String(count);
        countSelect.appendChild(option);
    }

    countSelect.disabled = spinning || eligibleCount === 0;
    orderEveryoneButton.disabled = spinning || eligibleCount === 0;

    if (eligibleCount > 0) {
        const restoredValue = Number(previousValue);
        countSelect.value = restoredValue >= 1 && restoredValue <= eligibleCount
            ? String(restoredValue)
            : String(eligibleCount);
    }
}

function renderSpinAvailability() {
    const eligibleCount = eligibleMembers().length;
    let reason = null;
    if (roster.length === 0) {
        reason = "名單是空的，請先新增成員才能抽籤。";
    } else if (eligibleCount === 0) {
        reason = "目前沒有可抽選 (Eligible) 的成員，請至少勾選一位。";
    }

    spinButton.disabled = spinning || reason !== null;
    if (reason) {
        spinDisabledReason.textContent = reason;
        spinDisabledReason.hidden = false;
    } else {
        spinDisabledReason.hidden = true;
        spinDisabledReason.textContent = "";
    }
}

/**
 * Renders the final draw order in the result overlay.
 * @param {string[]} drawOrder
 */
function showResultOverlay(drawOrder) {
    drawOrderList.innerHTML = "";
    for (const name of drawOrder) {
        const item = document.createElement("li");
        item.textContent = name;
        drawOrderList.appendChild(item);
    }
    resultOverlay.hidden = false;
}

function hideResultOverlay() {
    resultOverlay.hidden = true;
}

// ---- Reveal (wheel playback of an already-decided draw order) ----

/**
 * Spins the wheel once per pick in `drawOrder`, removing each picked
 * member from the wheel and announcing it in the banner. Stops early if
 * `skipToken.skipped` is set (by the skip button) — the caller is
 * responsible for showing the final result overlay either way.
 * @param {string[]} drawOrder
 * @param {string[]} poolNames the eligible members sent to the API, i.e.
 *   the full candidate pool the wheel starts from.
 * @param {{ skipped: boolean }} skipToken
 */
async function playReveal(drawOrder, poolNames, skipToken) {
    let remaining = [...poolNames];
    for (let i = 0; i < drawOrder.length; i++) {
        if (skipToken.skipped) break;
        const name = drawOrder[i];

        wheel.setNames(remaining);
        pickBanner.textContent = "";
        await sleep(i === 0 ? 150 : 400, skipToken);
        if (skipToken.skipped) break;

        await wheel.spinTo(remaining.indexOf(name), i === 0 ? 2200 : 1400, skipToken);
        if (skipToken.skipped) break;

        pickBanner.textContent = `第 ${i + 1} 位：${name}`;
        remaining = remaining.filter((candidate) => candidate !== name);
        await sleep(650, skipToken);
    }
    pickBanner.textContent = "";
}

function setSpinning(isSpinning) {
    spinning = isSpinning;
    skipButton.hidden = !isSpinning;
    if (isSpinning) {
        spinButton.disabled = true;
        countSelect.disabled = true;
        orderEveryoneButton.disabled = true;
    } else {
        render();
    }
}

// ---- Spin ----

async function spin() {
    hideSpinError();
    const members = eligibleMembers().map((member) => member.name);
    const count = Number(countSelect.value);

    setSpinning(true);

    let drawOrder;
    try {
        const response = await fetch("/api/spins", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ members, count }),
        });

        if (!response.ok) {
            // The documented 400 contract carries {"message": ...}, but a
            // non-JSON body (unexpected 5xx, proxy error page) must not be
            // misreported as a connectivity failure.
            const message = await response
                .json()
                .then((body) => body.message)
                .catch(() => null);
            showSpinError(message || "抽籤失敗，請再試一次。");
            setSpinning(false);
            return;
        }
        drawOrder = (await response.json()).drawOrder;
    } catch (error) {
        console.error("Spin request failed:", error);
        showSpinError("無法連線到伺服器，請確認伺服器是否啟動後再試一次。");
        setSpinning(false);
        return;
    }

    // The API call is done — the reveal below is pure client-side playback
    // of the already-decided drawOrder. Skip never triggers another call.
    const skipToken = { skipped: false };
    activeSkipToken = skipToken;
    await playReveal(drawOrder, members, skipToken);
    activeSkipToken = null;
    skipButton.hidden = true;

    showResultOverlay(drawOrder);
    // Spin controls stay disabled (spinning === true) until the overlay is
    // closed, so a new spin can start right after — not while it's open.
}

// ---- Event wiring ----

drawerToggle.addEventListener("click", openDrawer);
drawerClose.addEventListener("click", closeDrawer);
drawerBackdrop.addEventListener("click", closeDrawer);

function handleAddMemberSubmit() {
    addMember(memberNameInput.value);
    memberNameInput.value = "";
    memberNameInput.focus();
}

addMemberForm.addEventListener("submit", (event) => {
    event.preventDefault();
    handleAddMemberSubmit();
});

// Handle Enter explicitly rather than relying solely on native implicit
// form submission, which some browser/automation contexts don't trigger
// for a bare text input + submit button.
memberNameInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        event.preventDefault();
        handleAddMemberSubmit();
    }
});

memberNameInput.addEventListener("input", hideAddMemberNotice);

orderEveryoneButton.addEventListener("click", () => {
    const eligibleCount = eligibleMembers().length;
    if (eligibleCount === 0) return;
    countSelect.value = String(eligibleCount);
});

spinButton.addEventListener("click", spin);

skipButton.addEventListener("click", () => {
    if (activeSkipToken) activeSkipToken.skipped = true;
});

closeOverlayButton.addEventListener("click", () => {
    hideResultOverlay();
    setSpinning(false);
});

// ---- Initial render ----
render();
