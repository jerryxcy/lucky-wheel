"use strict";

/**
 * Walking-skeleton UI: roster -> spin -> draw order.
 * No wheel animation yet — the draw order is rendered as a plain ordered
 * list by renderDrawOrder(), kept as a single replaceable function so a
 * future reveal animation can swap it out without touching the rest.
 */

const STORAGE_KEY = "luckyWheel.roster";

/** @typedef {{ name: string, eligible: boolean }} Member */

/** @type {Member[]} */
let roster = loadRoster();

// ---- DOM references ----
const addMemberForm = document.getElementById("add-member-form");
const memberNameInput = document.getElementById("member-name-input");
const addMemberNotice = document.getElementById("add-member-notice");
const memberList = document.getElementById("member-list");
const emptyRosterNotice = document.getElementById("empty-roster-notice");

const countSelect = document.getElementById("count-select");
const orderEveryoneButton = document.getElementById("order-everyone-button");
const spinButton = document.getElementById("spin-button");
const spinDisabledReason = document.getElementById("spin-disabled-reason");
const spinError = document.getElementById("spin-error");

const drawOrderList = document.getElementById("draw-order-list");
const noResultNotice = document.getElementById("no-result-notice");

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

function normalizeName(name) {
    return name.trim();
}

function isDuplicateName(name) {
    return roster.some((member) => member.name === name);
}

function addMember(rawName) {
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
    roster = roster.filter((member) => member.name !== name);
    saveRoster();
    render();
}

function setEligible(name, eligible) {
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

// ---- Rendering ----

function render() {
    renderMemberList();
    renderCountOptions();
    renderSpinAvailability();
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
        checkbox.setAttribute("aria-label", `${member.name} 是否可抽選 (Eligible)`);
        checkbox.addEventListener("change", () => setEligible(member.name, checkbox.checked));

        const nameSpan = document.createElement("span");
        nameSpan.className = "member-name";
        nameSpan.textContent = member.name;

        const removeButton = document.createElement("button");
        removeButton.type = "button";
        removeButton.textContent = "移除";
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

    countSelect.disabled = eligibleCount === 0;
    orderEveryoneButton.disabled = eligibleCount === 0;

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

    spinButton.disabled = reason !== null;
    if (reason) {
        spinDisabledReason.textContent = reason;
        spinDisabledReason.hidden = false;
    } else {
        spinDisabledReason.hidden = true;
        spinDisabledReason.textContent = "";
    }
}

/**
 * Renders the draw order returned by the server as a plain ordered list.
 * Kept as a single, replaceable function: a wheel reveal animation can
 * swap this out without touching the rest of the app.
 * @param {string[]} drawOrder
 */
function renderDrawOrder(drawOrder) {
    drawOrderList.innerHTML = "";
    for (const name of drawOrder) {
        const item = document.createElement("li");
        item.textContent = name;
        drawOrderList.appendChild(item);
    }
    noResultNotice.hidden = drawOrder.length !== 0;
}

// ---- Spin ----

async function spin() {
    hideSpinError();
    const members = eligibleMembers().map((member) => member.name);
    const count = Number(countSelect.value);

    spinButton.disabled = true;
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
            return;
        }
        renderDrawOrder((await response.json()).drawOrder);
    } catch (error) {
        console.error("Spin request failed:", error);
        showSpinError("無法連線到伺服器，請確認伺服器是否啟動後再試一次。");
    } finally {
        renderSpinAvailability();
    }
}

// ---- Event wiring ----

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

// ---- Initial render ----
render();
