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
// Sibling key: the auto-remove toggle is a roster-wide setting, not a
// per-member one, so it gets its own localStorage entry.
const AUTO_REMOVE_STORAGE_KEY = "luckyWheel.autoRemove";
// Sibling key: the chosen UI language. An explicit choice here always beats
// navigator.language detection (see detectInitialLanguage).
const LANGUAGE_STORAGE_KEY = "luckyWheel.language";
// P0 "Original" palette from the prototype verdict on issue #1: six segment
// colours, with per-segment light/dark labels picked for contrast.
const WHEEL_PALETTE = ["#ffd166", "#ef476f", "#06d6a0", "#118ab2", "#f78c6b", "#7b61a8"];
const WHEEL_LABEL_LIGHT = "#ffffff";
const WHEEL_LABEL_DARK = "#14172b";

// ---- i18n ----
//
// Client-side only: a JS string table keyed by id, a t(key, params) lookup,
// and data-i18n[-attr] slots in the HTML filled on render. See ADR-0002 for
// why this replaces Spring MessageSource / a fetched JSON file. Member
// names are user data and never pass through this table.

/**
 * String table. Values are either a plain string or a function taking a
 * params object and returning the interpolated string (used wherever the
 * message embeds a runtime value, e.g. a member name or a count).
 */
const STRINGS = {
    en: {
        appTitle: "Lucky Wheel",
        localModeTitle: "Local Wheel",
        localModeScope: "Only in this browser",
        sharedModeTitle: "Shared Wheel",
        sharedModeScope: "Saved on server, accessible by link",
        sharedBadgeScope: "Shared Wheel · saved on server",
        sharedUnavailableBadgeScope: "This Shared Wheel is unavailable",
        createSharedWheelHeading: "Create Shared Wheel",
        createSharedWheelExplanation: "Save this roster on the server and open a shareable link.",
        wheelModeHeading: "Wheel mode",
        closeWheelModeAria: "Close Wheel mode",
        currentSharedWheel: "Current Shared Wheel",
        sharedWheelCreatedHeading: "Shared Wheel created",
        sharedWheelCreatedReminder: "There is no account or Shared Wheel list. Save this link now; the service cannot recover it for you if it is lost.",
        sharedWheelBookmarkHint: "Tip: press Command+D on Mac or Ctrl+D on Windows and Linux to bookmark it.",
        sharedWheelNameLabel: "Wheel name",
        cancel: "Cancel",
        create: "Create",
        sharedWheelLabel: "Shared Wheel",
        sharedWheelLinkExplanation: "Anyone with this link can open this wheel.",
        copySharedWheelLink: "Copy link",
        sharedLinkCopied: "Link copied ✓",
        sharedUnavailableHeading: "Shared Wheel unavailable",
        sharedUnavailableBody: "This link is invalid, expired, or no longer available.",
        problemSharedWheelNotFound: "This Shared Wheel is no longer available.",
        problemSharedWheelValidation: "Check the Wheel name and roster, then try again.",
        problemSharedWheelInvalidRequest: "The Shared Wheel request is invalid.",
        problemSharedApiError: "The Shared Wheel request could not be completed — please try again.",
        sharedReadOnlyNotice: "Shared editing and spinning arrive in the next implementation tickets.",
        createSharedWheelGenericError: "Could not create the Shared Wheel — please try again.",
        sharedWheelExpiry: (p) => `Expires ${p.date}`,
        drawerToggle: "⚙ Roster",
        countLabel: "Number to pick",
        orderEveryone: "Order everyone",
        autoRemoveLabel: "Skip winners in future spins",
        spinButton: "SPIN",
        skipButton: "Skip",
        rosterHeading: "Roster",
        close: "Close",
        drawerCloseAria: "Close roster",
        memberNameLabel: "Member name",
        memberNamePlaceholder: "Type a name, then press Enter or “Add”",
        addMemberButton: "Add",
        emptyRosterNotice: "The roster is empty — add a member first.",
        recheckAllButton: "Re-check all",
        recheckAllAria: "Re-check all members as eligible",
        copyRosterButton: "Copy roster",
        bulkImportHeading: "Bulk import",
        bulkImportLabel: "Paste names in bulk",
        bulkImportPlaceholder: "Paste multiple lines, or names separated by commas or semicolons",
        bulkImportButton: "Import roster",
        drawOrderHeading: "Draw order",
        pleaseEnterName: "Please enter a name.",
        duplicateName: (p) => `"${p.name}" is already on the roster — names must be unique.`,
        copiedConfirmation: "Copied ✓",
        copyPromptLabel: "Copy the roster below:",
        bulkImportResult: (p) => `Imported ${p.added}, skipped ${p.skipped} duplicate(s).`,
        bulkImportEmpty: "Paste some names first.",
        spinDisabledEmptyRoster: "The roster is empty — add a member before spinning.",
        spinDisabledNoEligible: "No eligible members right now — check at least one.",
        spinErrorGeneric: "Spin failed — please try again.",
        spinErrorConnection: "Can't reach the server — check that it's running, then try again.",
        pickBanner: (p) => `Pick ${p.index}: ${p.name}`,
        canvasEmpty: "The roster is empty",
        eligibleAria: (p) => `${p.name} eligible`,
        removeAria: (p) => `Remove ${p.name}`,
        removeButtonText: "Remove",
    },
    "zh-Hant": {
        appTitle: "Lucky Wheel 抽籤轉盤",
        localModeTitle: "Local Wheel",
        localModeScope: "僅儲存在這個瀏覽器",
        sharedModeTitle: "Shared Wheel 共享轉盤",
        sharedModeScope: "儲存在伺服器，可透過連結存取",
        sharedBadgeScope: "Shared Wheel · 儲存在伺服器",
        sharedUnavailableBadgeScope: "這個 Shared Wheel 無法使用",
        createSharedWheelHeading: "建立 Shared Wheel",
        createSharedWheelExplanation: "將目前名單儲存在伺服器，並開啟可分享的連結。",
        wheelModeHeading: "轉盤模式",
        closeWheelModeAria: "關閉轉盤模式",
        currentSharedWheel: "目前的 Shared Wheel",
        sharedWheelCreatedHeading: "Shared Wheel 已建立",
        sharedWheelCreatedReminder: "這個服務沒有帳號或 Shared Wheel 清單。請立即保存此連結；遺失後服務無法替你找回。",
        sharedWheelBookmarkHint: "提示：Mac 按 Command+D，Windows 或 Linux 按 Ctrl+D，即可加入書籤。",
        sharedWheelNameLabel: "轉盤名稱",
        cancel: "取消",
        create: "建立",
        sharedWheelLabel: "Shared Wheel 共享轉盤",
        sharedWheelLinkExplanation: "任何取得這個連結的人都可以開啟此轉盤。",
        copySharedWheelLink: "複製連結",
        sharedLinkCopied: "已複製連結 ✓",
        sharedUnavailableHeading: "Shared Wheel 無法使用",
        sharedUnavailableBody: "這個連結無效、已過期，或已經無法使用。",
        problemSharedWheelNotFound: "這個 Shared Wheel 已經無法使用。",
        problemSharedWheelValidation: "請檢查轉盤名稱與名單後再試一次。",
        problemSharedWheelInvalidRequest: "Shared Wheel 的請求內容無效。",
        problemSharedApiError: "無法完成 Shared Wheel 請求，請再試一次。",
        sharedReadOnlyNotice: "Shared Wheel 的修改與抽選會由接下來的 implementation tickets 完成。",
        createSharedWheelGenericError: "無法建立 Shared Wheel，請再試一次。",
        sharedWheelExpiry: (p) => `到期時間：${p.date}`,
        drawerToggle: "⚙ 名單",
        countLabel: "抽出人數",
        orderEveryone: "全員排序",
        autoRemoveLabel: "中籤者不參加之後抽選",
        spinButton: "SPIN",
        skipButton: "跳過",
        rosterHeading: "名單 Roster",
        close: "關閉",
        drawerCloseAria: "關閉名單",
        memberNameLabel: "成員姓名",
        memberNamePlaceholder: "輸入姓名後按 Enter 或「新增」",
        addMemberButton: "新增",
        emptyRosterNotice: "名單目前是空的，請先新增成員。",
        recheckAllButton: "全部重新勾選",
        recheckAllAria: "重新勾選所有成員為可抽選 (Re-check all as eligible)",
        copyRosterButton: "複製名單",
        bulkImportHeading: "批次匯入",
        bulkImportLabel: "批次貼上姓名",
        bulkImportPlaceholder: "貼上多行，或以逗號、頓號、分號分隔的姓名",
        bulkImportButton: "匯入名單",
        drawOrderHeading: "抽籤結果 Draw order",
        pleaseEnterName: "請輸入姓名。",
        duplicateName: (p) => `「${p.name}」已經在名單中，姓名不可重複。`,
        copiedConfirmation: "已複製 ✓",
        copyPromptLabel: "複製以下名單：",
        bulkImportResult: (p) => `已匯入 ${p.added} 人，略過重複 ${p.skipped} 人。`,
        bulkImportEmpty: "請先貼上姓名。",
        spinDisabledEmptyRoster: "名單是空的，請先新增成員才能抽籤。",
        spinDisabledNoEligible: "目前沒有可抽選 (Eligible) 的成員，請至少勾選一位。",
        spinErrorGeneric: "抽籤失敗，請再試一次。",
        spinErrorConnection: "無法連線到伺服器，請確認伺服器是否啟動後再試一次。",
        pickBanner: (p) => `第 ${p.index} 位：${p.name}`,
        canvasEmpty: "名單是空的",
        eligibleAria: (p) => `${p.name} 是否可抽選 (Eligible)`,
        removeAria: (p) => `移除 ${p.name}`,
        removeButtonText: "移除",
    },
};

const DEFAULT_LANGUAGE = "en";

/** Each language's own name (endonym), shown on the toggle button. */
const LANGUAGE_LABEL = { en: "English", "zh-Hant": "中文" };

/** Current UI language ("en" | "zh-Hant"), set by applyLanguage(). */
let currentLanguage = DEFAULT_LANGUAGE;

/**
 * Looks up `key` in the current language's string table. `params` is passed
 * through to function-valued entries for interpolation (e.g. a member name
 * or a count) — those values are never translated themselves.
 * @param {string} key
 * @param {Record<string, unknown>} [params]
 */
function t(key, params) {
    const table = STRINGS[currentLanguage] || STRINGS[DEFAULT_LANGUAGE];
    const entry = table[key];
    if (typeof entry === "function") return entry(params || {});
    return entry;
}

function loadLanguage() {
    try {
        const stored = localStorage.getItem(LANGUAGE_STORAGE_KEY);
        return stored === "en" || stored === "zh-Hant" ? stored : null;
    } catch (error) {
        console.error("Failed to load language from localStorage:", error);
        return null;
    }
}

function saveLanguage() {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, currentLanguage);
}

/**
 * First visit picks the language from navigator.language (any zh* locale ->
 * zh-Hant, otherwise English). An explicit stored choice always wins over
 * that detection, on every later visit including a new tab.
 */
function detectInitialLanguage() {
    const stored = loadLanguage();
    if (stored) return stored;
    const nav = (navigator.language || navigator.userLanguage || "").toLowerCase();
    return nav.startsWith("zh") ? "zh-Hant" : "en";
}

/**
 * Fills every data-i18n / data-i18n-placeholder / data-i18n-aria-label slot
 * in the static HTML from the current language's string table. Runs
 * regardless of an element's hidden state, so a notice picks up the right
 * text whenever it's next shown.
 */
function translateStaticDom() {
    document.querySelectorAll("[data-i18n]").forEach((el) => {
        el.textContent = t(el.dataset.i18n);
    });
    document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
        el.setAttribute("placeholder", t(el.dataset.i18nPlaceholder));
    });
    document.querySelectorAll("[data-i18n-aria-label]").forEach((el) => {
        el.setAttribute("aria-label", t(el.dataset.i18nAriaLabel));
    });
}

/**
 * Switches the UI language: persists the choice, updates <html lang> and
 * the document title, re-fills every static slot, highlights the active
 * toggle option, and re-renders every dynamic bit of UI (roster rows, spin
 * availability, canvas "empty" text, and whichever transient notice is
 * currently on screen) so every visible string flips immediately.
 * @param {string} lang
 */
function applyLanguage(lang) {
    currentLanguage = lang;
    saveLanguage();
    document.documentElement.lang = lang;
    translateStaticDom();
    document.title = t("appTitle");

    languageCurrentLabel.textContent = LANGUAGE_LABEL[lang];
    document.querySelectorAll(".lang-option").forEach((btn) => {
        const isActive = btn.dataset.lang === lang;
        btn.classList.toggle("active", isActive);
        btn.setAttribute("aria-checked", String(isActive));
    });

    if (copyRosterFlashActive) {
        copyRosterButton.textContent = t("copiedConfirmation");
    }
    if (addMemberNoticeState) {
        addMemberNotice.textContent = t(addMemberNoticeState.key, addMemberNoticeState.params);
    }
    if (bulkImportNoticeState) {
        bulkImportNotice.textContent = t(bulkImportNoticeState.key, bulkImportNoticeState.params);
    }
    if (spinErrorState) {
        spinError.textContent = t(spinErrorState.key, spinErrorState.params);
    }

    renderWheelMode();
    render();
}

/** @typedef {{ name: string, eligible: boolean }} Member */

/** @type {Member[]} */
let roster = loadRoster();

/**
 * Declared before spinning: when true, every member in a spin's finalised
 * draw order is set not-eligible once the draw completes (see
 * applyAutoRemove). When false, a spin never touches eligibility.
 */
let autoRemove = loadAutoRemove();

/** "local", "shared", or "shared-unavailable". */
let wheelMode = "local";

/** The authoritative server snapshot while in Shared Wheel mode. */
let sharedWheelSnapshot = null;

/** Capability flag controls whether Shared creation appears in the Local mode sheet. */
let sharedWheelsEnabled = false;

/** Show the save-link reminder only on the first page reached after creation. */
let showSharedWheelCreatedReminder = false;

const CREATED_SHARED_WHEEL_KEY = "luckyWheel.createdSharedWheelId";

/** True while a reveal (per-pick playback sequence) is in progress. */
let spinning = false;

/** Set to the in-flight reveal's skip token while spinning, else null. */
let activeSkipToken = null;

// ---- DOM references ----
const languageMenuButton = document.getElementById("language-menu-button");
const languageMenu = document.getElementById("language-menu");
const languageCurrentLabel = document.getElementById("language-current");

const modeBadge = document.getElementById("mode-badge");
const modeIcon = document.getElementById("mode-icon");
const modeLabel = document.getElementById("mode-label");
const modeSubtitle = document.getElementById("mode-subtitle");
const sharedWheelUnavailable = document.getElementById("shared-wheel-unavailable");

const drawerToggle = document.getElementById("drawer-toggle");
const drawerClose = document.getElementById("drawer-close");
const drawerBackdrop = document.getElementById("drawer-backdrop");
const rosterDrawer = document.getElementById("roster-drawer");

const addMemberForm = document.getElementById("add-member-form");
const memberNameInput = document.getElementById("member-name-input");
const addMemberNotice = document.getElementById("add-member-notice");
const memberList = document.getElementById("member-list");
const emptyRosterNotice = document.getElementById("empty-roster-notice");

const recheckAllButton = document.getElementById("recheck-all-button");
const copyRosterButton = document.getElementById("copy-roster-button");
/** True while "Copied ✓" is showing in place of the button's default label. */
let copyRosterFlashActive = false;

const bulkImportTextarea = document.getElementById("bulk-import-textarea");
const bulkImportButton = document.getElementById("bulk-import-button");
const bulkImportNotice = document.getElementById("bulk-import-notice");

const countSelect = document.getElementById("count-select");
const orderEveryoneButton = document.getElementById("order-everyone-button");
const autoRemoveToggle = document.getElementById("auto-remove-toggle");
const spinButton = document.getElementById("spin-button");
const skipButton = document.getElementById("skip-button");
const spinDisabledReason = document.getElementById("spin-disabled-reason");
const spinError = document.getElementById("spin-error");

const pickBanner = document.getElementById("pick-banner");
const wheelCanvas = document.getElementById("wheel-canvas");

const resultOverlay = document.getElementById("result-overlay");
const drawOrderList = document.getElementById("draw-order-list");
const closeOverlayButton = document.getElementById("close-overlay-button");

const createSharedWheelDialog = document.getElementById("create-shared-wheel-dialog");
const createSharedWheelForm = document.getElementById("create-shared-wheel-form");
const sharedWheelNameInput = document.getElementById("shared-wheel-name-input");
const cancelCreateSharedWheel = document.getElementById("cancel-create-shared-wheel");
const confirmCreateSharedWheel = document.getElementById("confirm-create-shared-wheel");
const createSharedWheelError = document.getElementById("create-shared-wheel-error");
const sharedWheelCommandSheet = document.getElementById("shared-wheel-command-sheet");
const localWheelMode = document.getElementById("local-wheel-mode");
const sharedWheelMode = document.getElementById("shared-wheel-mode");
const sharedWheelCommandDetail = document.getElementById("shared-wheel-command-detail");
const sharedWheelCommandName = document.getElementById("shared-wheel-command-name");
const sharedWheelCreatedReminder = document.getElementById("shared-wheel-created-reminder");
const sharedWheelExpiry = document.getElementById("shared-wheel-expiry");
const closeSharedWheelCommand = document.getElementById("close-shared-wheel-command");
const copySharedWheelLink = document.getElementById("copy-shared-wheel-link");

function isSharedMode() {
    return wheelMode === "shared";
}

function localWheelMutationLocked() {
    return spinning || isSharedMode();
}

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

function loadAutoRemove() {
    try {
        return localStorage.getItem(AUTO_REMOVE_STORAGE_KEY) === "true";
    } catch (error) {
        console.error("Failed to load auto-remove setting from localStorage:", error);
        return false;
    }
}

function saveAutoRemove() {
    localStorage.setItem(AUTO_REMOVE_STORAGE_KEY, String(autoRemove));
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
    if (localWheelMutationLocked()) return;
    const name = normalizeName(rawName);
    if (name === "") {
        showAddMemberNotice("pleaseEnterName");
        return;
    }
    if (isDuplicateName(name)) {
        showAddMemberNotice("duplicateName", { name });
        return;
    }
    hideAddMemberNotice();
    roster.push({ name, eligible: true });
    saveRoster();
    render();
}

function removeMember(name) {
    if (localWheelMutationLocked()) return;
    roster = roster.filter((member) => member.name !== name);
    saveRoster();
    render();
}

function setEligible(name, eligible) {
    if (localWheelMutationLocked()) return;
    const member = roster.find((m) => m.name === name);
    if (!member) return;
    member.eligible = eligible;
    saveRoster();
    render();
}

function eligibleMembers() {
    return roster.filter((member) => member.eligible);
}

/**
 * The glossary's "one action re-checks everyone when a rotation
 * completes": sets every member back to eligible, regardless of how they
 * became ineligible (manual uncheck or auto-remove).
 */
function recheckAll() {
    if (localWheelMutationLocked()) return;
    for (const member of roster) {
        member.eligible = true;
    }
    saveRoster();
    render();
}

/**
 * Marks every member named in `drawOrder` as not eligible. Called once a
 * spin's draw order is finalised — after playReveal() resolves, whether it
 * played out fully or was skipped — never mid-reveal. Only takes effect
 * when the auto-remove toggle is on; the caller gates that.
 * @param {string[]} drawOrder
 */
function applyAutoRemove(drawOrder) {
    const picked = new Set(drawOrder);
    for (const member of roster) {
        if (picked.has(member.name)) member.eligible = false;
    }
    saveRoster();
}

/**
 * Splits pasted text into individual names. Names may be separated by
 * newlines, commas (ASCII or Chinese full-width "，"), the Chinese
 * enumeration comma "、", or semicolons (ASCII or Chinese "；"). Blank
 * entries (from consecutive separators or stray whitespace) are dropped.
 * @param {string} text
 * @returns {string[]}
 */
function splitPastedNames(text) {
    return text
        .split(/[\n,，、;；]+/)
        .map((name) => normalizeName(name))
        .filter((name) => name !== "");
}

/**
 * Imports every name parsed out of `rawText` into the roster (eligible by
 * default), skipping names already on the roster and duplicates within the
 * pasted text itself. Reports how many were added vs. skipped.
 */
function importBulk(rawText) {
    if (localWheelMutationLocked()) return;
    const names = splitPastedNames(rawText);
    if (names.length === 0) {
        showBulkImportNotice("bulkImportEmpty");
        return;
    }

    let added = 0;
    let skipped = 0;
    for (const name of names) {
        if (isDuplicateName(name)) {
            skipped++;
            continue;
        }
        roster.push({ name, eligible: true });
        added++;
    }

    saveRoster();
    bulkImportTextarea.value = "";
    render();
    showBulkImportNotice("bulkImportResult", { added, skipped });
}

/**
 * Copies the roster (member names, one per line) to the clipboard. Falls
 * back to window.prompt when the Clipboard API is unavailable or denied,
 * so the text is still reachable via manual copy.
 */
async function copyRoster() {
    const text = roster.map((member) => member.name).join("\n");
    if (navigator.clipboard && navigator.clipboard.writeText) {
        try {
            await navigator.clipboard.writeText(text);
            flashCopyRosterConfirmation();
            return;
        } catch (error) {
            console.error("Clipboard write failed, falling back to prompt:", error);
        }
    }
    window.prompt(t("copyPromptLabel"), text);
}

function flashCopyRosterConfirmation() {
    copyRosterFlashActive = true;
    copyRosterButton.textContent = t("copiedConfirmation");
    setTimeout(() => {
        copyRosterFlashActive = false;
        copyRosterButton.textContent = t("copyRosterButton");
    }, 1200);
}

// ---- Notices ----
//
// Each notice tracks the last { key, params } it was shown with (or null
// when hidden) so applyLanguage() can re-translate whichever notice is
// currently on screen instead of leaving it frozen in the old language.

let addMemberNoticeState = null;
let bulkImportNoticeState = null;
let spinErrorState = null;

function showAddMemberNotice(key, params) {
    addMemberNoticeState = { key, params };
    addMemberNotice.textContent = t(key, params);
    addMemberNotice.hidden = false;
}

function hideAddMemberNotice() {
    addMemberNoticeState = null;
    addMemberNotice.hidden = true;
    addMemberNotice.textContent = "";
}

function showBulkImportNotice(key, params) {
    bulkImportNoticeState = { key, params };
    bulkImportNotice.textContent = t(key, params);
    bulkImportNotice.hidden = false;
}

function hideBulkImportNotice() {
    bulkImportNoticeState = null;
    bulkImportNotice.hidden = true;
    bulkImportNotice.textContent = "";
}

function showSpinError(key, params) {
    spinErrorState = { key, params };
    spinError.textContent = t(key, params);
    spinError.hidden = false;
}

/**
 * Shows a server-supplied error message verbatim. Server error messages are
 * English-only by design (out of scope for this feature — see ADR-0002), so
 * unlike showSpinError() this does not track state for re-translation: the
 * text stays exactly as received even if the UI language is later switched.
 */
function showSpinErrorLiteral(message) {
    spinErrorState = null;
    spinError.textContent = message;
    spinError.hidden = false;
}

function hideSpinError() {
    spinErrorState = null;
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
            ctx.fillText(t("canvasEmpty"), cx, cy);
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
    renderRosterToolsAvailability();
    if (!spinning) {
        wheel.setNames(eligibleMembers().map((member) => member.name));
    }
}

function renderRosterToolsAvailability() {
    const locked = localWheelMutationLocked();
    memberNameInput.disabled = locked;
    addMemberForm.querySelector("button[type='submit']").disabled = locked;
    bulkImportTextarea.disabled = locked;
    bulkImportButton.disabled = locked;
    recheckAllButton.disabled = locked;
    autoRemoveToggle.disabled = locked;
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
        checkbox.disabled = localWheelMutationLocked();
        checkbox.setAttribute("aria-label", t("eligibleAria", { name: member.name }));
        checkbox.addEventListener("change", () => setEligible(member.name, checkbox.checked));

        const nameSpan = document.createElement("span");
        nameSpan.className = "member-name";
        nameSpan.textContent = member.name;

        const removeButton = document.createElement("button");
        removeButton.type = "button";
        removeButton.textContent = t("removeButtonText");
        removeButton.disabled = localWheelMutationLocked();
        removeButton.setAttribute("aria-label", t("removeAria", { name: member.name }));
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

    countSelect.disabled = localWheelMutationLocked() || eligibleCount === 0;
    orderEveryoneButton.disabled = localWheelMutationLocked() || eligibleCount === 0;

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
    if (isSharedMode()) {
        reason = t("sharedReadOnlyNotice");
    } else if (roster.length === 0) {
        reason = t("spinDisabledEmptyRoster");
    } else if (eligibleCount === 0) {
        reason = t("spinDisabledNoEligible");
    }

    spinButton.disabled = spinning || reason !== null;
    spinDisabledReason.classList.toggle("info-notice", isSharedMode());
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

        pickBanner.textContent = t("pickBanner", { index: i + 1, name });
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
        autoRemoveToggle.disabled = true;
        recheckAllButton.disabled = true;
    } else {
        render();
    }
}

// ---- Spin ----

async function spin() {
    if (isSharedMode()) return;
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
            if (message) {
                showSpinErrorLiteral(message);
            } else {
                showSpinError("spinErrorGeneric");
            }
            setSpinning(false);
            return;
        }
        drawOrder = (await response.json()).drawOrder;
    } catch (error) {
        console.error("Spin request failed:", error);
        showSpinError("spinErrorConnection");
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

    // The draw order is finalised the moment playReveal resolves, whether
    // it played out fully or was cut short by skip — auto-remove applies
    // either way, since it acts on the decided result, not the animation.
    if (autoRemove) {
        applyAutoRemove(drawOrder);
        render();
    }

    showResultOverlay(drawOrder);
    // Spin controls stay disabled (spinning === true) until the overlay is
    // closed, so a new spin can start right after — not while it's open.
}

// ---- Event wiring ----

function openLanguageMenu() {
    languageMenu.hidden = false;
    languageMenuButton.setAttribute("aria-expanded", "true");
}

function closeLanguageMenu() {
    languageMenu.hidden = true;
    languageMenuButton.setAttribute("aria-expanded", "false");
}

languageMenuButton.addEventListener("click", (event) => {
    event.stopPropagation();
    if (languageMenu.hidden) openLanguageMenu();
    else closeLanguageMenu();
});

document.querySelectorAll(".lang-option").forEach((btn) => {
    btn.addEventListener("click", () => {
        if (btn.dataset.lang !== currentLanguage) applyLanguage(btn.dataset.lang);
        closeLanguageMenu();
        languageMenuButton.focus();
    });
});

// Close the menu on an outside click or Escape.
document.addEventListener("click", () => closeLanguageMenu());
document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !languageMenu.hidden) {
        closeLanguageMenu();
        languageMenuButton.focus();
    }
});

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

copyRosterButton.addEventListener("click", copyRoster);

bulkImportButton.addEventListener("click", () => {
    importBulk(bulkImportTextarea.value);
});

bulkImportTextarea.addEventListener("input", hideBulkImportNotice);

orderEveryoneButton.addEventListener("click", () => {
    const eligibleCount = eligibleMembers().length;
    if (eligibleCount === 0) return;
    countSelect.value = String(eligibleCount);
});

autoRemoveToggle.checked = autoRemove;
autoRemoveToggle.addEventListener("change", () => {
    if (localWheelMutationLocked()) return;
    autoRemove = autoRemoveToggle.checked;
    saveAutoRemove();
});

recheckAllButton.addEventListener("click", recheckAll);

spinButton.addEventListener("click", spin);

skipButton.addEventListener("click", () => {
    if (activeSkipToken) activeSkipToken.skipped = true;
});

closeOverlayButton.addEventListener("click", () => {
    hideResultOverlay();
    setSpinning(false);
});

// ---- Local / Shared Wheel mode ----

function renderWheelMode() {
    const shared = wheelMode !== "local";
    modeIcon.textContent = shared ? "●" : "◉";
    modeLabel.textContent = isSharedMode()
        ? sharedWheelSnapshot.name
        : t(shared ? "sharedModeTitle" : "localModeTitle");
    modeSubtitle.textContent = t(
        wheelMode === "shared-unavailable"
            ? "sharedUnavailableBadgeScope"
            : (shared ? "sharedBadgeScope" : "localModeScope")
    );
    modeBadge.classList.toggle("shared", shared);
    modeBadge.setAttribute("aria-haspopup", "dialog");
    sharedWheelCommandName.textContent = sharedWheelSnapshot?.name || "";

    localWheelMode.classList.toggle("active", !shared);
    sharedWheelMode.classList.toggle("active", shared);
    sharedWheelMode.hidden = wheelMode === "local" && !sharedWheelsEnabled;
    sharedWheelCommandDetail.hidden = !isSharedMode();
    sharedWheelCreatedReminder.hidden = !showSharedWheelCreatedReminder;

    sharedWheelUnavailable.hidden = wheelMode !== "shared-unavailable";
    document.body.classList.toggle("shared-unavailable", wheelMode === "shared-unavailable");

    if (sharedWheelSnapshot?.expiresAt) {
        const localDate = new Date(sharedWheelSnapshot.expiresAt).toLocaleString(currentLanguage);
        sharedWheelExpiry.textContent = t("sharedWheelExpiry", { date: localDate });
        sharedWheelExpiry.hidden = false;
    } else {
        sharedWheelExpiry.hidden = true;
        sharedWheelExpiry.textContent = "";
    }
}

function openCreateSharedWheelDialog() {
    createSharedWheelError.hidden = true;
    createSharedWheelError.textContent = "";
    sharedWheelNameInput.value = "";
    createSharedWheelDialog.showModal();
    sharedWheelNameInput.focus();
}

function sharedProblemMessage(problem) {
    const knownTypes = {
        "https://github.com/jerryxcy/lucky-wheel/problems/shared-wheel-not-found":
            "problemSharedWheelNotFound",
        "https://github.com/jerryxcy/lucky-wheel/problems/shared-wheel-validation":
            "problemSharedWheelValidation",
        "https://github.com/jerryxcy/lucky-wheel/problems/shared-wheel-invalid-request":
            "problemSharedWheelInvalidRequest",
        "https://github.com/jerryxcy/lucky-wheel/problems/shared-api-error":
            "problemSharedApiError",
    };
    const translationKey = knownTypes[problem?.type];
    if (translationKey) return t(translationKey);
    return problem?.detail || t("createSharedWheelGenericError");
}

async function createSharedWheel() {
    createSharedWheelError.hidden = true;
    confirmCreateSharedWheel.disabled = true;
    const request = {
        name: sharedWheelNameInput.value,
        autoRemove,
        members: roster.map((member) => ({ ...member })),
    };

    try {
        const response = await fetch("/api/shared-wheels", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Accept: "application/json, application/problem+json",
            },
            body: JSON.stringify(request),
        });
        if (!response.ok) {
            const problem = await response.json().catch(() => null);
            throw new Error(sharedProblemMessage(problem));
        }
        const snapshot = await response.json();
        try {
            sessionStorage.setItem(CREATED_SHARED_WHEEL_KEY, snapshot.id);
        } catch (error) {
            console.warn("Could not remember newly created Shared Wheel:", error);
        }
        window.location.assign(`/shared-wheels/${snapshot.id}`);
    } catch (error) {
        createSharedWheelError.textContent = error.message || t("createSharedWheelGenericError");
        createSharedWheelError.hidden = false;
        confirmCreateSharedWheel.disabled = false;
    }
}

async function copySharedWheelUrl() {
    const url = window.location.href;
    try {
        await navigator.clipboard.writeText(url);
        copySharedWheelLink.textContent = t("sharedLinkCopied");
        setTimeout(() => {
            copySharedWheelLink.textContent = t("copySharedWheelLink");
        }, 1200);
    } catch (error) {
        window.prompt(t("sharedWheelLinkExplanation"), url);
    }
}

async function bootstrapWheel() {
    // Match the route shape before validating the ID. A malformed or stale
    // Shared URL must stay in the Shared unavailable state; it must never
    // silently turn into a Local Wheel just because its ID is invalid.
    const match = window.location.pathname.match(/^\/shared-wheels\/([^/]+)\/?$/);

    if (match) {
        wheelMode = "shared-unavailable";
        renderWheelMode();
        try {
            const response = await fetch(`/api/shared-wheels/${encodeURIComponent(match[1])}`, {
                headers: { Accept: "application/json, application/problem+json" },
            });
            if (!response.ok) throw new Error("Shared Wheel unavailable");
            sharedWheelSnapshot = await response.json();
            wheelMode = "shared";
            try {
                showSharedWheelCreatedReminder =
                    sessionStorage.getItem(CREATED_SHARED_WHEEL_KEY) === sharedWheelSnapshot.id;
                if (showSharedWheelCreatedReminder) {
                    sessionStorage.removeItem(CREATED_SHARED_WHEEL_KEY);
                }
            } catch (error) {
                console.warn("Could not read Shared Wheel creation state:", error);
            }
            roster = sharedWheelSnapshot.members.map((member) => ({ ...member }));
            autoRemove = sharedWheelSnapshot.autoRemove;
            autoRemoveToggle.checked = autoRemove;
        } catch (error) {
            console.error("Failed to open Shared Wheel:", error);
            wheelMode = "shared-unavailable";
        }
    } else {
        wheelMode = "local";
        try {
            const response = await fetch("/api/capabilities", {
                headers: { Accept: "application/json" },
            });
            if (response.ok) {
                sharedWheelsEnabled = (await response.json()).sharedWheels === true;
            }
        } catch (error) {
            console.error("Failed to load capabilities:", error);
        }
    }

    renderWheelMode();
    render();
    document.body.classList.remove("booting");
    if (showSharedWheelCreatedReminder) {
        sharedWheelCommandSheet.showModal();
    }
}

cancelCreateSharedWheel.addEventListener("click", () => createSharedWheelDialog.close());
createSharedWheelForm.addEventListener("submit", (event) => {
    event.preventDefault();
    createSharedWheel();
});
modeBadge.addEventListener("click", () => {
    sharedWheelCommandSheet.showModal();
});
closeSharedWheelCommand.addEventListener("click", () => sharedWheelCommandSheet.close());
localWheelMode.addEventListener("click", () => {
    if (wheelMode === "local") {
        sharedWheelCommandSheet.close();
        return;
    }
    window.location.assign("/");
});
sharedWheelMode.addEventListener("click", () => {
    if (isSharedMode()) {
        sharedWheelCommandSheet.close();
        return;
    }
    sharedWheelCommandSheet.close();
    openCreateSharedWheelDialog();
});
copySharedWheelLink.addEventListener("click", copySharedWheelUrl);

// ---- Initial render ----
applyLanguage(detectInitialLanguage());
bootstrapWheel();
