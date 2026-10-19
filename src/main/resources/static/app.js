const API_BASE = window.location.pathname.startsWith("/assistant/") ? "/assistant" : "";

const state = {
    files: [],
    selectedFileId: null,
    statusTimers: new Map(),
    activeStream: null,
    conversationId: getConversationId()
};

const $ = (selector) => document.querySelector(selector);
const fileInput = $("#file-input");
const uploadZone = $("#upload-zone");

document.addEventListener("DOMContentLoaded", () => {
    loadFiles();
    $("#refresh-button").addEventListener("click", loadFiles);
    $("#new-session-button").addEventListener("click", startNewSession);
    $("#file-filter").addEventListener("input", renderFileList);
    $("#close-detail").addEventListener("click", clearDetail);
    $("#question-form").addEventListener("submit", submitQuestion);
    $("#question-input").addEventListener("input", resizeQuestionInput);
    $("#question-input").addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            $("#question-form").requestSubmit();
        }
    });
    uploadZone.addEventListener("click", () => fileInput.click());
    uploadZone.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") fileInput.click();
    });
    fileInput.addEventListener("change", () => uploadFiles([...fileInput.files]));
    ["dragenter", "dragover"].forEach(type => uploadZone.addEventListener(type, (event) => {
        event.preventDefault(); uploadZone.classList.add("dragging");
    }));
    ["dragleave", "drop"].forEach(type => uploadZone.addEventListener(type, (event) => {
        event.preventDefault(); uploadZone.classList.remove("dragging");
    }));
    uploadZone.addEventListener("drop", (event) => uploadFiles([...event.dataTransfer.files]));
});

async function loadFiles() {
    try {
        const response = await fetch(`${API_BASE}/files`);
        if (!response.ok) throw new Error(`文件列表加载失败 (${response.status})`);
        state.files = await response.json();
        $("#library-updated").textContent = `刚刚同步 · ${formatTime(new Date())}`;
        $("#file-count").textContent = state.files.length;
        renderFileList();
        if (state.selectedFileId && state.files.some(file => file.fileId === state.selectedFileId)) {
            selectFile(state.selectedFileId, false);
        }
    } catch (error) {
        $("#library-updated").textContent = "同步失败";
        showToast(error.message, "error");
    }
}

function renderFileList() {
    const filter = $("#file-filter").value.trim().toLowerCase();
    const files = state.files.filter(file => file.fileName.toLowerCase().includes(filter));
    const container = $("#file-list");
    if (!files.length) {
        container.innerHTML = `<div class="empty-state compact">${state.files.length ? "没有匹配的文件" : "还没有上传文档"}</div>`;
        return;
    }
    container.innerHTML = files.map(file => {
        const active = file.fileId === state.selectedFileId ? " active" : "";
        const type = extension(file.fileName).toUpperCase() || "DOC";
        return `<button class="file-row${active}" type="button" data-file-id="${escapeHtml(file.fileId)}">
            <span class="file-type">${escapeHtml(type.slice(0, 4))}</span>
            <span class="file-main">
                <span class="file-name"><span class="file-name-text">${escapeHtml(file.fileName)}</span></span>
                <span class="file-meta">${formatCount(file.characterCount)} 字符</span>
                <span class="file-state" data-state-for="${escapeHtml(file.fileId)}"><span class="state-dot"></span>等待查询状态</span>
            </span>
        </button>`;
    }).join("");
    container.querySelectorAll("[data-file-id]").forEach(button => {
        button.addEventListener("click", () => selectFile(button.dataset.fileId));
        refreshStatus(button.dataset.fileId, false);
    });
}

async function uploadFiles(files) {
    if (!files.length) return;
    for (const file of files) await uploadFile(file);
    fileInput.value = "";
    await loadFiles();
}

async function uploadFile(file) {
    const item = document.createElement("div");
    item.className = "upload-item";
    item.innerHTML = `<div class="upload-item-head"><span class="upload-item-name">${escapeHtml(file.name)}</span><span class="upload-item-status">上传中</span></div><div class="progress-track"><div class="progress-bar"></div></div>`;
    $("#upload-queue").prepend(item);
    const status = item.querySelector(".upload-item-status");
    const progress = item.querySelector(".progress-bar");
    try {
        const body = new FormData();
        body.append("file", file);
        const response = await fetch(`${API_BASE}/embedding/uploadDocument`, { method: "POST", body });
        if (!response.ok) throw new Error(await response.text() || `上传失败 (${response.status})`);
        const fileId = await response.text();
        progress.style.width = "100%";
        status.textContent = "已入队";
        selectFile(fileId);
        startStatusPolling(fileId);
        setTimeout(() => item.remove(), 3500);
    } catch (error) {
        status.className = "upload-item-status error";
        status.textContent = "失败";
        showToast(`${file.name}：${error.message}`, "error");
    }
}

async function selectFile(fileId, fetchDetail = true) {
    state.selectedFileId = fileId;
    renderFileList();
    if (!fetchDetail) return;
    try {
        const response = await fetch(`${API_BASE}/files/${encodeURIComponent(fileId)}`);
        if (!response.ok) throw new Error("文档详情加载失败");
        const file = await response.json();
        renderDetail(file);
        refreshStatus(fileId, true);
    } catch (error) { showToast(error.message, "error"); }
}

function renderDetail(file, status = null) {
    const detail = $("#detail-content");
    const currentStatus = status || { status: "QUEUED", totalSegments: 0, processedSegments: 0, failedSegments: 0 };
    detail.innerHTML = `<h3 class="detail-file-name">${escapeHtml(file.fileName)}</h3>
        <div class="detail-meta"><span>${formatCount(file.fileContext?.length || 0)} 字符</span><span>${escapeHtml(extension(file.fileName).toUpperCase() || "DOCUMENT")}</span><span>ID ${escapeHtml(file.fileId.slice(0, 8))}</span></div>
        <div class="detail-status"><div class="status-line"><span>处理状态</span><span class="status-value" data-detail-status>${statusLabel(currentStatus.status)}</span></div>
        <div class="detail-progress"><div data-detail-progress style="width: ${progressPercent(currentStatus)}%"></div></div>
        <div class="status-line"><span class="detail-status-note" data-detail-progress-label>${progressLabel(currentStatus)}</span><span class="detail-status-note">${currentStatus.failedSegments ? `${currentStatus.failedSegments} 个失败` : ""}</span></div></div>
        <p class="preview-label">原文预览</p><pre class="preview-text">${escapeHtml(file.fileContext || "暂无可预览内容")}</pre>`;
}

async function refreshStatus(fileId, showDetail) {
    try {
        const response = await fetch(`${API_BASE}/embedding/ingestion/${encodeURIComponent(fileId)}`);
        if (!response.ok) return;
        const status = await response.json();
        updateListStatus(fileId, status);
        if (showDetail && state.selectedFileId === fileId) {
            const fileResponse = await fetch(`${API_BASE}/files/${encodeURIComponent(fileId)}`);
            if (fileResponse.ok) renderDetail(await fileResponse.json(), status);
        }
        if (["QUEUED", "PROCESSING"].includes(status.status)) startStatusPolling(fileId);
        else stopStatusPolling(fileId);
    } catch (_) { /* 列表中的历史文件可能还没有对应的任务状态 */ }
}

function startStatusPolling(fileId) {
    stopStatusPolling(fileId);
    state.statusTimers.set(fileId, setInterval(() => refreshStatus(fileId, state.selectedFileId === fileId), 2500));
    refreshStatus(fileId, state.selectedFileId === fileId);
}

function stopStatusPolling(fileId) {
    const timer = state.statusTimers.get(fileId);
    if (timer) clearInterval(timer);
    state.statusTimers.delete(fileId);
}

function updateListStatus(fileId, status) {
    const element = document.querySelector(`[data-state-for="${cssEscape(fileId)}"]`);
    if (!element) return;
    const type = status.status.toLowerCase();
    element.innerHTML = `<span class="state-dot ${type}"></span>${statusLabel(status.status)}${status.totalSegments ? ` · ${status.processedSegments}/${status.totalSegments}` : ""}`;
}

async function submitQuestion(event) {
    event.preventDefault();
    const input = $("#question-input");
    const question = input.value.trim();
    if (!question || state.activeStream) return;
    const maxResults = Number($("#max-results").value);
    $("#welcome-block")?.remove();
    appendQuestion(question);
    const answer = appendAnswer();
    input.value = "";
    resizeQuestionInput();
    toggleQuestionControls(true);

    try {
        const params = new URLSearchParams({
            question,
            maxResults: String(maxResults),
            conversationId: state.conversationId,
            includeMemory: "true"
        });
        const url = `${API_BASE}/embedding/searchRelevantContext/stream?${params}`;
        const response = await fetch(url, { headers: { Accept: "text/event-stream" } });
        if (!response.ok || !response.body) throw new Error("问答连接失败");
        state.activeStream = response.body.getReader();
        await readSseStream(state.activeStream, answer);
    } catch (error) {
        answer.text.textContent = `${answer.text.textContent || ""}\n${error.message}`;
        answer.text.classList.remove("streaming");
    } finally {
        state.activeStream = null;
        toggleQuestionControls(false);
    }
}

async function readSseStream(reader, answer) {
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
        const { value, done } = await reader.read();
        buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
        const events = buffer.split("\n\n");
        buffer = events.pop() || "";
        for (const rawEvent of events) handleSseEvent(rawEvent, answer);
        if (done) break;
    }
    if (buffer.trim()) handleSseEvent(buffer, answer);
}

function handleSseEvent(rawEvent, answer) {
    const event = parseSseEvent(rawEvent);
    const name = event.name;
    const data = event.data;
    if (name === "conversation") {
        try {
            const payload = JSON.parse(data);
            if (payload.conversationId) {
                state.conversationId = payload.conversationId;
                sessionStorage.setItem("assistant.conversationId", state.conversationId);
            }
        } catch (_) { /* 忽略无效会话事件 */ }
    } else if (name === "sources") {
        try {
            const contexts = JSON.parse(data);
            answer.sourceNumbers = contexts.map(context => context.sourceNumber);
            renderSources(answer.sources, contexts);
        } catch (_) { /* 忽略无效引用事件 */ }
    } else if (name === "memories") {
        try {
            const memories = JSON.parse(data);
            answer.memoryNumbers = memories.map(memory => memory.memoryNumber);
            renderMemorySources(answer.memories, memories);
        } catch (_) { /* 忽略无效记忆事件 */ }
    } else if (name === "token" || name === "message") {
        answer.text.textContent += parseSseData(data);
        answer.text.scrollIntoView({ block: "nearest" });
    } else if (name === "failure") {
        answer.text.textContent += `\n${parseSseData(data) || "回答生成失败"}`;
    } else if (name === "done") {
        answer.text.textContent = normalizeGenericCitations(answer.text.textContent, answer.sourceNumbers);
        answer.text.classList.remove("streaming");
    }
}

function parseSseEvent(rawEvent) {
    let name = "message";
    const dataLines = [];
    for (const line of rawEvent.replaceAll("\r\n", "\n").split("\n")) {
        if (line.startsWith("event:")) {
            name = line.slice("event:".length).trim();
        } else if (line.startsWith("data:")) {
            const value = line.slice("data:".length);
            dataLines.push(value.startsWith(" ") ? value.slice(1) : value);
        }
    }
    return { name, data: dataLines.join("\n") };
}

function renderSources(container, contexts) {
    container.innerHTML = contexts.length ? contexts.map((context, index) => `<div class="source-card">
        <div class="source-heading"><span class="source-index">资料${context.sourceNumber || index + 1}</span><span class="source-title">${escapeHtml(context.headingPath || "未命名章节")}</span></div>
        <div class="source-body">${escapeHtml(context.text || context.matchedText || "")}</div>
        <div class="source-footer"><span>${context.pageNumber ? `第 ${context.pageNumber} 页 · ` : ""}${escapeHtml(context.fileId || "未知文件")}</span><button type="button" class="source-toggle">展开原文</button></div>
    </div>`).join("") : "";
    container.querySelectorAll(".source-card").forEach(card => card.querySelector(".source-toggle").addEventListener("click", () => {
        card.classList.toggle("open");
        card.querySelector(".source-toggle").textContent = card.classList.contains("open") ? "收起原文" : "展开原文";
    }));
}

function renderMemorySources(container, memories) {
    if (!memories.length) return;
    container.innerHTML = memories.map((memory, index) => `<div class="source-card memory-card">
        <div class="source-heading"><span class="source-index memory-index">记忆${memory.memoryNumber || index + 1}</span><span class="source-title">历史会话</span></div>
        <div class="source-body">${escapeHtml(memory.text || "")}</div>
        <div class="source-footer"><span>${memory.createdAt ? escapeHtml(formatDate(memory.createdAt)) : "历史问答"}</span><button type="button" class="source-toggle">展开记忆</button></div>
    </div>`).join("");
    container.querySelectorAll(".source-card").forEach(card => card.querySelector(".source-toggle").addEventListener("click", () => {
        card.classList.toggle("open");
        card.querySelector(".source-toggle").textContent = card.classList.contains("open") ? "收起记忆" : "展开记忆";
    }));
}

function appendQuestion(question) {
    const wrapper = document.createElement("div");
    wrapper.className = "message message-question";
    wrapper.innerHTML = `<div class="question-bubble">${escapeHtml(question)}</div>`;
    $("#conversation").append(wrapper);
}

function appendAnswer() {
    const wrapper = document.createElement("div");
    wrapper.className = "message answer-message";
    wrapper.innerHTML = `<div class="answer-label">KNOWLEDGE ANSWER</div><div class="answer-text streaming"></div><div class="sources"></div><div class="memories"></div>`;
    $("#conversation").append(wrapper);
    wrapper.scrollIntoView({ block: "nearest" });
    return {
        text: wrapper.querySelector(".answer-text"),
        sources: wrapper.querySelector(".sources"),
        memories: wrapper.querySelector(".memories"),
        sourceNumbers: [],
        memoryNumbers: []
    };
}

function toggleQuestionControls(streaming) {
    const button = $("#ask-button");
    button.disabled = streaming;
    button.innerHTML = streaming ? "<span>生成中</span><span aria-hidden='true'>…</span>" : "<span>发送问题</span><span aria-hidden='true'>↗</span>";
}

function resizeQuestionInput() {
    const input = $("#question-input");
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 150)}px`;
}

function parseSseData(data) {
    try {
        const value = JSON.parse(data);
        return typeof value === "string" ? value : value.content || value.message || "";
    } catch (_) {
        return data;
    }
}
function normalizeGenericCitations(text, sourceNumbers) {
    if (!text.includes("[资料]") || !sourceNumbers.length) return text;
    const citations = sourceNumbers.map(number => `[资料${number}]`).join("");
    return text.replaceAll("[资料]", citations);
}
function extension(name) { return (name.split(".").pop() || "").toLowerCase(); }
function formatCount(value) { return Number(value || 0).toLocaleString("zh-CN"); }
function formatTime(value) { return value.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" }); }
function formatDate(value) { return new Date(value).toLocaleString("zh-CN"); }
function progressPercent(status) { return status.totalSegments ? Math.round((status.processedSegments + status.failedSegments) / status.totalSegments * 100) : 0; }
function progressLabel(status) { return status.totalSegments ? `${status.processedSegments + status.failedSegments} / ${status.totalSegments} 个片段完成` : "任务状态尚未建立"; }
function statusLabel(status) { return ({ QUEUED: "排队中", PROCESSING: "处理中", COMPLETED: "已完成", FAILED: "处理失败", PARTIALLY_FAILED: "部分失败" })[status] || "未开始"; }
function clearDetail() { state.selectedFileId = null; $("#detail-content").innerHTML = `<div class="empty-state">选择一个文档查看原文与处理状态</div>`; renderFileList(); }
function showToast(message, type = "info") { console.warn(`[${type}] ${message}`); }
function getConversationId() {
    const key = "assistant.conversationId";
    let value = sessionStorage.getItem(key);
    if (!value) {
        value = crypto.randomUUID();
        sessionStorage.setItem(key, value);
    }
    return value;
}
function startNewSession() {
    state.conversationId = crypto.randomUUID();
    sessionStorage.setItem("assistant.conversationId", state.conversationId);
    $("#conversation").innerHTML = `<div class="welcome-block" id="welcome-block"><span class="welcome-line"></span><p class="section-kicker">NEW SESSION</p><h3>新的会话已经准备好。</h3><p>短期窗口已清空，长期记忆仍按会话隔离。</p></div>`;
}
function cssEscape(value) { return window.CSS?.escape ? CSS.escape(value) : value.replace(/[^a-zA-Z0-9_-]/g, "\\$&"); }
function escapeHtml(value) { return String(value ?? "").replace(/[&<>'"]/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]); }
