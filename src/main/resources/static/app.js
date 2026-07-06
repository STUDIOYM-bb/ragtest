const $ = (selector) => document.querySelector(selector);
const serverStatus = $("#serverStatus");
const adminResult = $("#adminResult");
const activePolling = new Set();

async function api(path, options = {}) {
  const response = await fetch(path, {headers: {"Content-Type": "application/json"}, ...options});
  const body = await response.json().catch(() => ({message: `HTTP ${response.status}`}));
  if (!response.ok || body.success === false) throw new Error(body.message || "요청 처리 중 오류가 발생했습니다.");
  return body;
}

const escapeHtml = (value) => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
const setMessage = (element, message, type = "") => {
  element.textContent = message || "";
  element.className = `message ${type}`.trim();
};
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const numberValue = (id, fallback) => Number($(id).value) || fallback;
const ingestQuery = () => new URLSearchParams({
  maxPages: numberValue("#maxPages", 3), pageSize: numberValue("#pageSize", 50), maxItems: numberValue("#maxItems", 150)
}).toString();

const actions = {
  configStatus: () => requestSimple("/api/admin/config/status", renderConfig),
  ragStatus: () => requestSimple("/api/admin/rag/status", renderRagStatus),
  ingestAll: (button, original) => startJob(`/api/admin/ingest/all?${ingestQuery()}`, button, original),
  ingestPublicService: (button, original) => startJob(`/api/admin/ingest/public-service?${ingestQuery()}`, button, original),
  ingestLocalWelfare: (button, original) => startJob(`/api/admin/ingest/local-welfare?${ingestQuery()}`, button, original),
  ingestCentralWelfare: (button, original) => startJob(`/api/admin/ingest/central-welfare?${ingestQuery()}`, button, original),
  ingestYouthCenter: (button, original) => startJob(`/api/admin/ingest/youth-center?${ingestQuery()}`, button, original),
  ingestYouthCenterOfficial: (button, original) => startJob(`/api/admin/ingest/youth-center-official?${ingestQuery()}`, button, original),
  ingestYouthPolicyDataGoKr: (button, original) => startJob(`/api/admin/ingest/youth-policy-data-go-kr?${ingestQuery()}`, button, original),
  indexReal: (button, original) => startJob(`/api/admin/rag/index?limit=${numberValue("#indexLimit", 30)}`, button, original),
  indexYouthCenter: (button, original) => startJob(`/api/admin/rag/index-source/YOUTH_CENTER?limit=${numberValue("#indexLimit", 30)}`, button, original),
  reindexReal: (button, original) => startJob(`/api/admin/rag/reindex-real?limit=${numberValue("#indexLimit", 30)}`, button, original),
  loadPolicies: loadPolicies,
  debugSearch: debugSearch,
  debugYouthCenterOfficialRaw: () => debugYouthRaw("/api/admin/debug/youth-center-official/raw?query=%EC%B2%AD%EB%85%84&pageIndex=1&display=10"),
  debugYouthPolicyDataGoKrRaw: () => debugYouthRaw("/api/admin/debug/youth-policy-data-go-kr/raw?query=%EC%B2%AD%EB%85%84&pageNo=1&numOfRows=10")
};

document.querySelectorAll("[data-action]").forEach((button) => button.addEventListener("click", async () => {
  const original = button.textContent;
  button.disabled = true;
  button.textContent = "처리 중...";
  try {
    await actions[button.dataset.action](button, original);
  } catch (error) {
    setMessage(adminResult, `${original} 실패: ${error.message}`, "error");
    button.disabled = false;
    button.textContent = original;
  }
  if (!button.dataset.jobType) {
    button.disabled = false;
    button.textContent = original;
  }
}));

async function requestSimple(path, renderer) {
  const body = await api(path);
  renderer(body);
  setMessage(adminResult, body.message || "조회 완료", "success");
}

async function startJob(path, button, original) {
  const body = await api(path, {method: "POST"});
  renderJob(body.data);
  setMessage(adminResult, `${body.message}: ${body.data.jobId}`, "success");
  button.textContent = "백그라운드 실행 중...";
  pollJob(body.data.jobId, button, original);
}

async function pollJob(jobId, button, original) {
  if (activePolling.has(jobId)) return;
  activePolling.add(jobId);
  try {
    while (true) {
      await delay(1500);
      const job = (await api(`/api/admin/jobs/${encodeURIComponent(jobId)}`)).data;
      renderJob(job);
      if (job.status === "SUCCESS") {
        setMessage(adminResult, `${job.message}${job.type.includes("INDEX") ? " 남은 정책이 있으면 인덱싱을 다시 실행하세요." : ""}`, "success");
        renderRagStatus(await api("/api/admin/rag/status"));
        break;
      }
      if (job.status === "FAILED") {
        const errorText = job.errorMessage || job.message || "";
        const redirectHint = errorText.includes("302")
          ? "\n공식 온통청년 API가 302 리다이렉트를 반환했습니다. 공공데이터포털 키를 공식 API에 넣은 경우일 수 있습니다. 공공데이터포털 온통청년 API 수집을 사용해보세요." : "";
        const hint = job.type === "INGEST_YOUTH_CENTER"
          ? `\n원본 응답 확인 버튼을 눌러 XML 또는 JSON이 내려오는지 먼저 확인해보세요.${redirectHint}` : "";
        setMessage(adminResult, `${job.type} 실패: ${job.errorMessage || job.message}${hint}`, "error");
        break;
      }
    }
  } catch (error) {
    setMessage(adminResult, `작업 상태 조회 실패: ${error.message}`, "error");
  } finally {
    activePolling.delete(jobId);
    if (button) { button.disabled = false; button.textContent = original; }
  }
}

function renderJob(job) {
  const id = `job-${job.jobId.replaceAll(/[^a-zA-Z0-9_-]/g, "")}`;
  let card = document.getElementById(id);
  if (!card) {
    $("#jobStatusBox").insertAdjacentHTML("afterbegin", `<article id="${id}" class="job-card"></article>`);
    card = document.getElementById(id);
  }
  card.className = `job-card ${String(job.status).toLowerCase()}`;
  card.innerHTML = `<div class="job-title"><strong>${escapeHtml(job.type)}</strong><span>${escapeHtml(job.status)}</span></div>
    <div class="progress-track"><div class="progress-value" style="width:${Number(job.progressPercent || 0)}%"></div></div>
    <dl class="job-details"><dt>진행률</dt><dd>${job.progressPercent || 0}%</dd><dt>메시지</dt><dd>${escapeHtml(job.message)}</dd>
    <dt>시작</dt><dd>${formatDate(job.startedAt)}</dd><dt>종료</dt><dd>${formatDate(job.finishedAt)}</dd></dl>
    ${renderJobResult(job)}`;
}

function renderJobResult(job) {
  if (!job.result) return job.errorMessage ? `<p class="job-error">${escapeHtml(job.errorMessage)}</p>` : "";
  if (job.type === "INGEST_ALL") return `<div class="job-result">${Object.entries(job.result)
    .map(([source, result]) => `<p>${escapeHtml(source)}: ${formatCounts(result)}</p>`).join("")}</div>`;
  if (job.type.startsWith("INGEST_")) return `<div class="job-result">${formatCounts(job.result)}</div>`;
  return `<div class="job-result">인덱싱 ${job.result.indexedCount || 0}건</div>`;
}
const formatCounts = (v) => `${v.sourceType ? `${v.sourceType}: ` : ""}수집 ${v.fetchedCount || 0}건, 청년 관련 ${v.savedCount || 0}건, 제외 ${v.skippedCount || 0}건${v.message ? ` (${escapeHtml(v.message)})` : ""}`;
const formatDate = (v) => v ? new Date(v).toLocaleString("ko-KR") : "-";

function renderConfig(body) {
  const labels = {openAiConfigured: "OPENAI_API_KEY", dataGoKrConfigured: "DATA_GO_KR_SERVICE_KEY",
    publicServiceConfigured: "공공서비스 키", localWelfareConfigured: "지자체복지 키",
    centralWelfareConfigured: "중앙부처복지 키", youthCenterConfigured: "온통청년 자동 수집",
    youthCenterOfficialConfigured: "온통청년 공식 키", dataGoKrYouthPolicyConfigured: "공공데이터 온통청년 키",
    dataGoKrYouthPolicyBaseUrlConfigured: "공공데이터 온통청년 URL"};
  $("#configStatusList").innerHTML = Object.entries(labels).map(([key, label]) =>
    `<div class="status-row ${body.data[key] ? "ok" : "missing"}"><span>${label}</span><strong>${body.data[key] ? "설정됨" : "미설정"}</strong></div>`).join("")
    + (body.data.youthCenterOfficialKeyPreview ? `<div class="status-row ok"><span>공식 키 preview</span><strong>${escapeHtml(body.data.youthCenterOfficialKeyPreview)}</strong></div>` : "")
    + (body.data.dataGoKrYouthPolicyKeyPreview ? `<div class="status-row ok"><span>공공데이터 키 preview</span><strong>${escapeHtml(body.data.dataGoKrYouthPolicyKeyPreview)}</strong></div>` : "")
    + (body.data.dataGoKrYouthPolicyBaseUrlMasked ? `<div class="status-row ok"><span>공공데이터 URL</span><strong>${escapeHtml(body.data.dataGoKrYouthPolicyBaseUrlMasked)}</strong></div>` : "");
}

function renderRagStatus(body) {
  const data = body.data || {};
  $("#ragStatusBox").innerHTML = `<div class="status-summary">
    ${tile("전체", data.totalPolicies)}${tile("실제", data.realPolicies)}${tile("샘플", data.samplePolicies)}
    ${tile("청년 관련", data.youthRelatedPolicies)}${tile("인덱싱 완료", data.indexedYouthPolicies)}${tile("미인덱싱", data.unindexedYouthPolicies)}
    </div>${data.youthCenterNotice ? `<p class="notice">${escapeHtml(data.youthCenterNotice)}</p>` : ""}
    <div class="source-status-grid">${Object.entries(data.bySourceType || {}).map(([type, v]) =>
      `<article class="source-status-card"><h3>${type}</h3><p>전체 ${v.total}건</p><p>청년 ${v.youthRelated}건</p><p>인덱싱 ${v.indexed}건</p><p>미인덱싱 ${v.unindexed}건</p></article>`).join("")}</div>`;
}
const tile = (label, value) => `<div class="summary-tile"><span>${label}</span><strong>${Number(value || 0).toLocaleString()}</strong></div>`;

$("#askForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = $("#askButton");
  button.disabled = true; button.textContent = "질문 중...";
  $("#askResult").textContent = ""; $("#sourceList").innerHTML = ""; setMessage($("#noResultReason"), "");
  try {
    const data = (await api("/api/rag/ask", {method: "POST", body: JSON.stringify({
      question: $("#question").value, topK: numberValue("#topK", 5)
    })})).data;
    $("#conditionResult").innerHTML = renderCondition(data.extractedCondition);
    $("#askResult").textContent = data.answer || "";
    setMessage($("#noResultReason"), data.noResultReason || "", data.noResultReason ? "warning" : "");
    $("#sourceList").innerHTML = (data.sources || []).map(renderSource).join("");
  } catch (error) {
    $("#askResult").textContent = error.message;
  } finally { button.disabled = false; button.textContent = "질문하기"; }
});

function renderCondition(condition) {
  if (!condition) return "";
  const values = [["지역", condition.region], ["나이", condition.age], ["대상", condition.targetGroup],
    ["학업상태", condition.educationStatus], ["취업상태", condition.employmentStatus], ["생애단계", condition.lifeStage],
    ["경제상태", condition.economicStatus], ["관심분야", (condition.interestCategories || []).join(", ")],
    ["키워드", (condition.keywords || []).join(", ")]];
  return `<div class="condition-title">추출 조건</div><div class="condition-items">${values.map(([label, value]) =>
    `<span>${label}: ${escapeHtml(value === null || value === "" ? "필터 미적용" : value)}</span>`).join("")}</div>`;
}

function renderSource(source) {
  return `<article class="card"><h3>${escapeHtml(source.title)}</h3><div class="meta"><span class="badge">${source.sourceType}</span>
    <span class="badge">${escapeHtml(source.regionName || "지역 확인 필요")}</span><span class="badge">${source.eligibilityStatus}</span>
    <span class="badge">점수 ${Number(source.finalScore).toFixed(2)}</span></div>
    ${(source.matchedReasons || []).length ? `<p>매칭: ${escapeHtml(source.matchedReasons.join(", "))}</p>` : ""}
    ${(source.cautionReasons || []).length ? `<p class="caution">확인 필요: ${escapeHtml(source.cautionReasons.join(", "))}</p>` : ""}
    ${source.officialUrl ? `<a href="${escapeHtml(source.officialUrl)}" target="_blank" rel="noreferrer">공식 링크</a>` : ""}</article>`;
}

$("#policyFilterForm").addEventListener("submit", (event) => { event.preventDefault(); loadPolicies(); });
async function loadPolicies() {
  const params = new URLSearchParams({youthOnly: $("#youthOnly").checked, indexedOnly: $("#indexedOnly").checked,
    sampleExcluded: $("#excludeSample").checked});
  if ($("#policyKeyword").value.trim()) params.set("keyword", $("#policyKeyword").value.trim());
  if ($("#policyRegion").value.trim()) params.set("region", $("#policyRegion").value.trim());
  if ($("#policySourceType").value) params.set("sourceType", $("#policySourceType").value);
  const body = await api(`/api/policies?${params}`);
  $("#policyList").innerHTML = body.data.length ? body.data.map(renderPolicy).join("") : `<div class="empty-state">조건에 맞는 정책이 없습니다.</div>`;
  setMessage(adminResult, `정책 목록 조회 완료 (${body.data.length}건)`, "success");
}

function renderPolicy(policy) {
  return `<article class="card"><h3>${escapeHtml(policy.title)}</h3><div class="meta"><span class="badge">${policy.sourceType}</span>
    <span class="badge">${escapeHtml(policy.regionName || "-")}</span><span class="badge">${escapeHtml(policy.categoryName || "-")}</span>
    <span class="badge">청년 ${policy.youthRelated ? "Y" : "N"}</span><span class="badge">인덱싱 ${policy.indexed ? "Y" : "N"}</span></div>
    <p>${escapeHtml(policy.summary || "")}</p>${policy.officialUrl ? `<a href="${escapeHtml(policy.officialUrl)}" target="_blank" rel="noreferrer">공식 링크</a>` : ""}</article>`;
}

async function debugSearch() {
  const body = await api("/api/admin/debug/search-candidates", {method: "POST", body: JSON.stringify({
    question: $("#question").value, topK: Math.max(1, Math.min(numberValue("#topK", 5) * 4, 100))
  })});
  $("#debugResult").textContent = JSON.stringify(body.data, null, 2);
  setMessage(adminResult, "검색 후보 디버그 완료", "success");
}

async function debugYouthRaw(path) {
  const response = await fetch(path);
  const body = await response.json().catch(() => ({success: false, message: `HTTP ${response.status}`}));
  if (!response.ok) throw new Error(body.message || "온통청년 원본 응답 조회 실패");
  $("#debugResult").textContent = JSON.stringify({
    apiType: body.data?.apiType,
    statusCode: body.data?.statusCode,
    contentType: body.data?.contentType,
    looksLikeXml: body.data?.looksLikeXml,
    looksLikeJson: body.data?.looksLikeJson,
    looksLikeHtml: body.data?.looksLikeHtml,
    requestUrlMasked: body.data?.requestUrlMasked,
    redirectLocation: body.data?.redirectLocation,
    bodyPreview: body.data?.bodyPreview
  }, null, 2);
  setMessage(adminResult, body.success
    ? (body.message || "온통청년 원본 응답 조회 완료")
    : `${body.message || "온통청년 원본 응답 확인 필요"} XML 또는 JSON이 내려오는지 확인하세요.`, body.success ? "success" : "error");
}

async function restoreRunningJobs() {
  try {
    const jobs = (await api("/api/admin/jobs/running")).data;
    jobs.forEach((job) => {
      renderJob(job);
      const button = document.querySelector(`[data-job-type="${job.type}"]`);
      const original = button?.textContent;
      if (button) { button.disabled = true; button.textContent = "백그라운드 실행 중..."; }
      pollJob(job.jobId, button, original);
    });
  } catch (error) { setMessage(adminResult, `실행 중 작업 조회 실패: ${error.message}`, "error"); }
}

(async () => {
  try {
    await api("/api/policies?youthOnly=true&sampleExcluded=true");
    serverStatus.textContent = "서버 연결 정상"; serverStatus.className = "status success";
  } catch (error) {
    serverStatus.textContent = `서버 연결 실패: ${error.message}`; serverStatus.className = "status error";
  }
  restoreRunningJobs();
})();
