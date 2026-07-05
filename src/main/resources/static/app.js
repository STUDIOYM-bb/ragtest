const serverStatus = document.querySelector("#serverStatus");
const indexSampleButton = document.querySelector("#indexSampleButton");
const indexResult = document.querySelector("#indexResult");
const askForm = document.querySelector("#askForm");
const askButton = document.querySelector("#askButton");
const askResult = document.querySelector("#askResult");
const sourceList = document.querySelector("#sourceList");
const loadPoliciesButton = document.querySelector("#loadPoliciesButton");
const policyList = document.querySelector("#policyList");

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });
  const body = await response.json();
  if (!response.ok || body.success === false) {
    throw new Error(body.message || "요청 처리 중 오류가 발생했습니다.");
  }
  return body;
}

function setMessage(element, message, type) {
  element.textContent = message;
  element.className = type ? `message ${type}` : "message";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function checkServer() {
  try {
    await api("/api/policies");
    serverStatus.textContent = "서버 연결 정상";
    serverStatus.className = "status success";
  } catch (error) {
    serverStatus.textContent = `서버 연결 실패: ${error.message}`;
    serverStatus.className = "status error";
  }
}

indexSampleButton.addEventListener("click", async () => {
  indexSampleButton.disabled = true;
  indexSampleButton.textContent = "처리 중...";
  setMessage(indexResult, "", "");
  try {
    const body = await api("/api/admin/ingest/public-service", { method: "POST" });
    setMessage(indexResult, `${body.message} (저장 ${body.data.savedCount}건, 인덱싱 ${body.data.indexedCount}건)`, "success");
  } catch (error) {
    setMessage(indexResult, error.message, "error");
  } finally {
    indexSampleButton.disabled = false;
    indexSampleButton.textContent = "실제 공공서비스 API 수집 및 인덱싱";
  }
});

askForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  askButton.disabled = true;
  askButton.textContent = "질문 중...";
  askResult.textContent = "";
  sourceList.innerHTML = "";
  try {
    const payload = {
      question: document.querySelector("#question").value,
      region: document.querySelector("#region").value,
      age: numberOrNull(document.querySelector("#age").value),
      employmentStatus: document.querySelector("#employmentStatus").value,
      topK: numberOrNull(document.querySelector("#topK").value)
    };
    const body = await api("/api/rag/ask", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    askResult.textContent = body.data.answer;
    sourceList.innerHTML = body.data.sources.map(renderSourceCard).join("");
  } catch (error) {
    askResult.textContent = error.message;
  } finally {
    askButton.disabled = false;
    askButton.textContent = "질문하기";
  }
});

loadPoliciesButton.addEventListener("click", async () => {
  loadPoliciesButton.disabled = true;
  loadPoliciesButton.textContent = "불러오는 중...";
  policyList.innerHTML = "";
  try {
    const body = await api("/api/policies");
    policyList.innerHTML = body.data.map(renderPolicyCard).join("");
  } catch (error) {
    policyList.innerHTML = `<div class="message error">${escapeHtml(error.message)}</div>`;
  } finally {
    loadPoliciesButton.disabled = false;
    loadPoliciesButton.textContent = "저장된 정책 목록 불러오기";
  }
});

function numberOrNull(value) {
  return value === "" ? null : Number(value);
}

function renderSourceCard(source) {
  return `
    <article class="card">
      <h3>${escapeHtml(source.title)}</h3>
      <div class="meta">
        <span class="badge">${escapeHtml(source.sourceType)}</span>
        <span class="badge">${escapeHtml(source.regionName)}</span>
        <span class="badge">${escapeHtml(source.categoryName)}</span>
      </div>
      <a href="${escapeHtml(source.officialUrl)}" target="_blank" rel="noreferrer">${escapeHtml(source.officialUrl)}</a>
    </article>
  `;
}

function renderPolicyCard(policy) {
  return `
    <article class="card">
      <h3>${escapeHtml(policy.title)}</h3>
      <p>${escapeHtml(policy.summary)}</p>
      <div class="meta">
        <span class="badge">${escapeHtml(policy.regionName)}</span>
        <span class="badge">${escapeHtml(policy.categoryName)}</span>
        <span class="badge">${policy.indexed ? "indexed" : "not indexed"}</span>
      </div>
      <a href="${escapeHtml(policy.officialUrl)}" target="_blank" rel="noreferrer">${escapeHtml(policy.officialUrl)}</a>
    </article>
  `;
}

checkServer();
