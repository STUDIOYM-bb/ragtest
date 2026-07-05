const serverStatus = document.querySelector("#serverStatus");
const adminResult = document.querySelector("#adminResult");
const configStatusList = document.querySelector("#configStatusList");
const ragStatusBox = document.querySelector("#ragStatusBox");
const askForm = document.querySelector("#askForm");
const askButton = document.querySelector("#askButton");
const askResult = document.querySelector("#askResult");
const conditionResult = document.querySelector("#conditionResult");
const sourceList = document.querySelector("#sourceList");
const policyList = document.querySelector("#policyList");
const youthOnly = document.querySelector("#youthOnly");

let loadedPolicies = [];

const adminActions = {
  configStatus: {
    path: "/api/admin/config/status",
    method: "GET",
    busyText: "확인 중...",
    success: renderConfigStatus
  },
  ragStatus: {
    path: "/api/admin/rag/status",
    method: "GET",
    busyText: "확인 중...",
    success: renderRagStatus
  },
  ingestPublicService: {
    path: "/api/admin/ingest/public-service",
    method: "POST",
    busyText: "수집 및 인덱싱 중...",
    success: (body) => renderIngestResult(body, "행정안전부 공공서비스")
  },
  ingestLocalWelfare: {
    path: "/api/admin/ingest/local-welfare",
    method: "POST",
    busyText: "수집 및 인덱싱 중...",
    success: (body) => renderIngestResult(body, "지자체복지서비스")
  },
  ingestCentralWelfare: {
    path: "/api/admin/ingest/central-welfare",
    method: "POST",
    busyText: "수집 및 인덱싱 중...",
    success: (body) => renderIngestResult(body, "중앙부처복지서비스")
  },
  ingestAll: {
    path: "/api/admin/ingest/all",
    method: "POST",
    busyText: "수집 및 인덱싱 중...",
    success: renderAllIngestResult
  },
  reindexReal: {
    path: "/api/admin/rag/reindex-real",
    method: "POST",
    busyText: "재인덱싱 중...",
    success: (body) => renderIngestResult(body, "실제 정책 데이터")
  },
  loadPolicies: {
    path: "/api/policies",
    method: "GET",
    busyText: "불러오는 중...",
    success: renderPolicies
  }
};

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

document.querySelectorAll("[data-action]").forEach((button) => {
  button.addEventListener("click", () => runAdminAction(button));
});

youthOnly.addEventListener("change", () => renderPolicyCards());

async function runAdminAction(button) {
  const action = adminActions[button.dataset.action];
  const originalText = button.textContent;
  const ingestAction = button.dataset.action.startsWith("ingest");
  setBusyForAction(button, true, action.busyText || "처리 중...", ingestAction);
  setMessage(adminResult, "", "");
  if (button.dataset.action !== "configStatus") {
    configStatusList.innerHTML = "";
  }
  if (button.dataset.action !== "ragStatus") {
    ragStatusBox.innerHTML = "";
  }

  try {
    const body = await api(action.path, { method: action.method });
    action.success(body);
  } catch (error) {
    setMessage(adminResult, `${originalText} 실패: ${error.message}`, "error");
  } finally {
    setBusyForAction(button, false, originalText, ingestAction);
  }
}

function setBusyForAction(activeButton, busy, text, lockIngestButtons) {
  const buttons = lockIngestButtons
    ? document.querySelectorAll("[data-action^='ingest']")
    : [activeButton];
  buttons.forEach((button) => {
    button.disabled = busy;
    if (button === activeButton) {
      button.textContent = text;
    }
  });
}

function renderConfigStatus(body) {
  const labels = {
    openAiConfigured: "OPENAI_API_KEY",
    dataGoKrConfigured: "DATA_GO_KR_SERVICE_KEY",
    publicServiceConfigured: "DATA_GO_KR_PUBLIC_SERVICE_KEY 또는 fallback",
    localWelfareConfigured: "DATA_GO_KR_LOCAL_WELFARE_KEY 또는 fallback",
    centralWelfareConfigured: "DATA_GO_KR_CENTRAL_WELFARE_KEY 또는 fallback",
    youthCenterConfigured: "YOUTH_CENTER_API_KEY"
  };
  configStatusList.innerHTML = Object.entries(labels)
    .map(([key, label]) => {
      const configured = Boolean(body.data[key]);
      return `<div class="status-row ${configured ? "ok" : "missing"}">
        <span>${escapeHtml(label)}</span>
        <strong>${configured ? "설정됨" : "미설정"}</strong>
      </div>`;
    })
    .join("");
  setMessage(adminResult, body.message, "success");
}

function renderRagStatus(body) {
  const data = body.data || {};
  const sourceTypes = data.bySourceType || {};
  ragStatusBox.innerHTML = `
    <div class="status-summary">
      ${summaryTile("전체 정책", data.totalPolicies)}
      ${summaryTile("실제 정책", data.realPolicies)}
      ${summaryTile("샘플 정책", data.samplePolicies)}
      ${summaryTile("청년 관련", data.youthRelatedPolicies)}
      ${summaryTile("인덱싱 완료", data.indexedYouthPolicies)}
    </div>
    <div class="source-status-grid">
      ${Object.entries(sourceTypes).map(([sourceType, counts]) => `
        <article class="source-status-card">
          <h3>${escapeHtml(sourceType)}</h3>
          <p>전체 ${counts.total ?? 0}건</p>
          <p>청년 관련 ${counts.youthRelated ?? 0}건</p>
          <p>인덱싱 ${counts.indexed ?? 0}건</p>
        </article>
      `).join("")}
    </div>
  `;
  setMessage(adminResult, body.message, "success");
}

function summaryTile(label, value) {
  return `<div class="summary-tile"><span>${escapeHtml(label)}</span><strong>${Number(value ?? 0).toLocaleString()}</strong></div>`;
}

function renderIngestResult(body, label) {
  const data = body.data || {};
  setMessage(adminResult, `${label}: ${formatIngestCounts(data)}`, "success");
}

function renderAllIngestResult(body) {
  const labels = {
    publicService: "행정안전부 공공서비스",
    localWelfare: "지자체복지서비스",
    centralWelfare: "중앙부처복지서비스"
  };
  const lines = Object.entries(labels).map(([key, label]) => {
    const data = body.data?.[key] || {};
    return `${label}: ${formatIngestCounts(data)}`;
  });
  setMessage(adminResult, `${body.message}\n${lines.join("\n")}`, "success");
}

function formatIngestCounts(data) {
  return `수집 ${data.fetchedCount ?? 0}건, 청년 관련 ${data.savedCount ?? 0}건, 인덱싱 ${data.indexedCount ?? 0}건, 제외 ${data.skippedCount ?? 0}건`;
}

function renderPolicies(body) {
  loadedPolicies = body.data || [];
  renderPolicyCards();
  setMessage(adminResult, `${body.message || "정책 목록 조회 완료"} (${loadedPolicies.length}건)`, "success");
}

function renderPolicyCards() {
  const policies = youthOnly.checked
    ? loadedPolicies.filter((policy) => policy.youthRelated && policy.sourceType !== "SAMPLE")
    : loadedPolicies;
  policyList.innerHTML = policies.map(renderPolicyCard).join("");
}

askForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  askButton.disabled = true;
  askButton.textContent = "질문 중...";
  askResult.textContent = "";
  conditionResult.innerHTML = "";
  sourceList.innerHTML = "";
  try {
    const payload = {
      question: document.querySelector("#question").value,
      topK: numberOrNull(document.querySelector("#topK").value)
    };
    const body = await api("/api/rag/ask", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    conditionResult.innerHTML = renderCondition(body.data.extractedCondition);
    askResult.textContent = body.data.answer;
    sourceList.innerHTML = body.data.sources.map(renderSourceCard).join("");
  } catch (error) {
    askResult.textContent = error.message;
  } finally {
    askButton.disabled = false;
    askButton.textContent = "질문하기";
  }
});

function numberOrNull(value) {
  return value === "" ? null : Number(value);
}

function renderCondition(condition) {
  if (!condition) {
    return "";
  }
  const items = [
    ["지역", condition.region],
    ["나이", condition.age],
    ["취업상태", condition.employmentStatus],
    ["대상", condition.targetGroup],
    ["키워드", (condition.keywords || []).join(", ")]
  ];
  return `
    <div class="condition-title">추출 조건</div>
    <div class="condition-items">
      ${items.map(([label, value]) => `<span>${escapeHtml(label)}=${escapeHtml(value ?? "미추출")}</span>`).join("")}
    </div>
  `;
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
      ${source.officialUrl ? `<a href="${escapeHtml(source.officialUrl)}" target="_blank" rel="noreferrer">${escapeHtml(source.officialUrl)}</a>` : ""}
    </article>
  `;
}

function renderPolicyCard(policy) {
  return `
    <article class="card">
      <h3>${escapeHtml(policy.title)}</h3>
      <p>${escapeHtml(policy.summary)}</p>
      <div class="meta">
        <span class="badge">${escapeHtml(policy.sourceType)}</span>
        <span class="badge">${escapeHtml(policy.regionName)}</span>
        <span class="badge">${escapeHtml(policy.categoryName)}</span>
        <span class="badge">${policy.indexed ? "indexed" : "not indexed"}</span>
        <span class="badge">청년 관련: ${policy.youthRelated ? "Y" : "N"}</span>
      </div>
      ${policy.officialUrl ? `<a href="${escapeHtml(policy.officialUrl)}" target="_blank" rel="noreferrer">${escapeHtml(policy.officialUrl)}</a>` : ""}
    </article>
  `;
}

checkServer();
