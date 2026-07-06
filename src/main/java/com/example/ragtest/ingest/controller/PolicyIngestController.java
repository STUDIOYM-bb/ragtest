package com.example.ragtest.ingest.controller;

import com.example.ragtest.admin.job.AdminJob;
import com.example.ragtest.admin.job.AdminJobManager;
import com.example.ragtest.admin.job.AdminJobType;
import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.common.response.ApiResponse;
import com.example.ragtest.ingest.service.ExternalPolicyIngestService;
import com.example.ragtest.ingest.service.IngestOptions;
import com.example.ragtest.ingest.service.IngestResult;
import com.example.ragtest.ingest.service.PolicyIndexingService;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.example.ragtest.policy.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class PolicyIngestController {

    private static final Set<PolicySourceType> INDEXABLE_SOURCE_TYPES = Set.of(
            PolicySourceType.PUBLIC_SERVICE, PolicySourceType.LOCAL_WELFARE,
            PolicySourceType.CENTRAL_WELFARE, PolicySourceType.YOUTH_CENTER
    );

    private final ExternalPolicyIngestService ingestService;
    private final PolicyIndexingService indexingService;
    private final PolicyRepository policyRepository;
    private final AdminJobManager jobManager;
    private final String publicServiceKey;
    private final String localWelfareKey;
    private final String centralWelfareKey;
    private final String youthCenterOfficialKey;
    private final String dataGoKrYouthPolicyKey;
    private final String dataGoKrYouthPolicyBaseUrl;

    public PolicyIngestController(
            ExternalPolicyIngestService ingestService,
            PolicyIndexingService indexingService,
            PolicyRepository policyRepository,
            AdminJobManager jobManager,
            @Value("${external-api.data-go-kr.public-service-key:}") String publicServiceKey,
            @Value("${external-api.data-go-kr.local-welfare-key:}") String localWelfareKey,
            @Value("${external-api.data-go-kr.central-welfare-key:}") String centralWelfareKey,
            @Value("${external-api.youth-center.official-api-key:}") String youthCenterOfficialKey,
            @Value("${external-api.data-go-kr.youth-policy-key:}") String dataGoKrYouthPolicyKey,
            @Value("${external-api.data-go-kr.youth-policy-base-url:}") String dataGoKrYouthPolicyBaseUrl
    ) {
        this.ingestService = ingestService;
        this.indexingService = indexingService;
        this.policyRepository = policyRepository;
        this.jobManager = jobManager;
        this.publicServiceKey = publicServiceKey;
        this.localWelfareKey = localWelfareKey;
        this.centralWelfareKey = centralWelfareKey;
        this.youthCenterOfficialKey = youthCenterOfficialKey;
        this.dataGoKrYouthPolicyKey = dataGoKrYouthPolicyKey;
        this.dataGoKrYouthPolicyBaseUrl = dataGoKrYouthPolicyBaseUrl;
    }

    @GetMapping("/rag/status")
    public ApiResponse<Map<String, Object>> ragStatus() {
        long youth = policyRepository.countBySourceTypeNotAndYouthRelatedTrue(PolicySourceType.SAMPLE);
        long indexed = policyRepository.countBySourceTypeNotAndYouthRelatedTrueAndIndexedTrue(PolicySourceType.SAMPLE);
        boolean youthCenterConfigured = youthIngestConfigured();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalPolicies", policyRepository.count());
        data.put("realPolicies", policyRepository.countBySourceTypeNot(PolicySourceType.SAMPLE));
        data.put("samplePolicies", policyRepository.countBySourceType(PolicySourceType.SAMPLE));
        data.put("youthRelatedPolicies", youth);
        data.put("indexedYouthPolicies", indexed);
        data.put("unindexedYouthPolicies", Math.max(0, youth - indexed));
        data.put("youthCenterConfigured", youthCenterConfigured);
        data.put("youthCenterCollectedCount", policyRepository.countBySourceType(PolicySourceType.YOUTH_CENTER));
        if (!youthCenterConfigured) {
            data.put("youthCenterNotice", "온통청년 API 키가 없어 청년정책 전용 데이터는 아직 수집되지 않았습니다. "
                    + "현재는 행정안전부 공공서비스/복지서비스 API 데이터만 사용합니다.");
        }

        Map<String, Object> bySourceType = new LinkedHashMap<>();
        for (PolicySourceType sourceType : PolicySourceType.values()) {
            long sourceYouth = policyRepository.countBySourceTypeAndYouthRelatedTrue(sourceType);
            long sourceIndexed = policyRepository.countBySourceTypeAndYouthRelatedTrueAndIndexedTrue(sourceType);
            bySourceType.put(sourceType.name(), Map.of(
                    "total", policyRepository.countBySourceType(sourceType),
                    "youthRelated", sourceYouth,
                    "indexed", sourceIndexed,
                    "unindexed", Math.max(0, sourceYouth - sourceIndexed)
            ));
        }
        data.put("bySourceType", bySourceType);
        return ApiResponse.ok(data, "RAG 데이터 상태 조회 완료");
    }

    @PostMapping("/rag/index")
    public ApiResponse<AdminJob> index(@RequestParam(defaultValue = "30") int limit) {
        int normalized = normalizeIndexLimit(limit);
        AdminJob job = jobManager.start(AdminJobType.INDEX_REAL,
                "실제 정책 데이터 인덱싱 작업을 시작했습니다.", "실제 정책 데이터 인덱싱 완료",
                progress -> {
                    progress.update(5, "인덱싱할 실제 청년정책 조회 중...");
                    int count = indexingService.indexUnindexedRealPolicies(normalized,
                            (done, total) -> progress.update(indexProgress(done, total),
                                    "실제 정책 데이터 인덱싱 중... " + done + "/" + total));
                    return new IngestResult(0, 0, count, 0);
                });
        return ApiResponse.ok(job, "작업 시작");
    }

    @PostMapping("/rag/reindex-real")
    public ApiResponse<AdminJob> reindexReal(@RequestParam(defaultValue = "30") int limit) {
        int normalized = normalizeIndexLimit(limit);
        AdminJob job = jobManager.start(AdminJobType.REINDEX_REAL,
                "실제 정책 데이터 재인덱싱 작업을 시작했습니다.", "실제 정책 데이터 재인덱싱 완료",
                progress -> {
                    progress.update(5, "기존 벡터를 정리하고 재인덱싱 준비 중...");
                    int count = indexingService.reindexRealYouthPolicies(normalized,
                            (done, total) -> progress.update(indexProgress(done, total),
                                    "실제 정책 데이터 재인덱싱 중... " + done + "/" + total));
                    return new IngestResult(0, 0, count, 0);
                });
        return ApiResponse.ok(job, "작업 시작");
    }

    @PostMapping("/rag/index-source/{sourceType}")
    public ApiResponse<AdminJob> indexSource(@PathVariable PolicySourceType sourceType,
                                              @RequestParam(defaultValue = "30") int limit) {
        if (!INDEXABLE_SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException("인덱싱 가능한 sourceType은 PUBLIC_SERVICE, LOCAL_WELFARE, "
                    + "CENTRAL_WELFARE, YOUTH_CENTER입니다.");
        }
        int normalized = normalizeIndexLimit(limit);
        AdminJob job = jobManager.start(AdminJobType.INDEX_REAL,
                sourceType + " 정책 인덱싱 작업을 시작했습니다.", sourceType + " 정책 인덱싱 완료",
                progress -> {
                    int count = indexingService.indexUnindexedPolicies(sourceType, normalized,
                            (done, total) -> progress.update(indexProgress(done, total),
                                    sourceType + " 인덱싱 중... " + done + "/" + total));
                    return new IngestResult(0, 0, count, 0);
                });
        return ApiResponse.ok(job, "작업 시작");
    }

    @PostMapping("/ingest/public-service")
    public ApiResponse<AdminJob> ingestPublicService(@RequestParam(defaultValue = "3") int maxPages,
                                                      @RequestParam(defaultValue = "50") int pageSize,
                                                      @RequestParam(defaultValue = "150") int maxItems) {
        requireKey(publicServiceKey, "DATA_GO_KR_PUBLIC_SERVICE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 필요합니다.");
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);
        return startIngest(AdminJobType.INGEST_PUBLIC_SERVICE, "행정안전부 공공서비스",
                progress -> ingestService.ingestPublicYouthServices(options));
    }

    @PostMapping("/ingest/local-welfare")
    public ApiResponse<AdminJob> ingestLocalWelfare(@RequestParam(defaultValue = "3") int maxPages,
                                                    @RequestParam(defaultValue = "50") int pageSize,
                                                    @RequestParam(defaultValue = "150") int maxItems) {
        requireKey(localWelfareKey, "DATA_GO_KR_LOCAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 필요합니다.");
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);
        return startIngest(AdminJobType.INGEST_LOCAL_WELFARE, "지자체복지서비스",
                progress -> ingestService.ingestLocalWelfareServices(options));
    }

    @PostMapping("/ingest/central-welfare")
    public ApiResponse<AdminJob> ingestCentralWelfare(@RequestParam(defaultValue = "3") int maxPages,
                                                      @RequestParam(defaultValue = "50") int pageSize,
                                                      @RequestParam(defaultValue = "150") int maxItems) {
        requireKey(centralWelfareKey, "DATA_GO_KR_CENTRAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 필요합니다.");
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);
        return startIngest(AdminJobType.INGEST_CENTRAL_WELFARE, "중앙부처복지서비스",
                progress -> ingestService.ingestCentralWelfareServices(options));
    }

    @PostMapping("/ingest/youth-center")
    public ApiResponse<AdminJob> ingestYouthCenter(@RequestParam(defaultValue = "3") int maxPages,
                                                   @RequestParam(defaultValue = "50") int pageSize,
                                                   @RequestParam(defaultValue = "150") int maxItems) {
        if (!youthIngestConfigured()) {
            throw new BusinessException("온통청년 수집 설정이 없습니다. DATA_GO_KR_YOUTH_POLICY_BASE_URL과 "
                    + "DATA_GO_KR_YOUTH_POLICY_KEY 또는 YOUTH_CENTER_API_KEY를 설정하세요.");
        }
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);
        return startIngest(AdminJobType.INGEST_YOUTH_CENTER, "온통청년 청년정책 자동",
                progress -> ingestService.ingestYouthPolicies(options));
    }

    @PostMapping("/ingest/youth-center-official")
    public ApiResponse<AdminJob> ingestYouthCenterOfficial(@RequestParam(defaultValue = "3") int maxPages,
                                                           @RequestParam(defaultValue = "10") int pageSize,
                                                           @RequestParam(defaultValue = "150") int maxItems) {
        requireKey(youthCenterOfficialKey, "YOUTH_CENTER_API_KEY가 필요합니다. 이 값은 온통청년 공식 openApiVlak 키여야 합니다.");
        IngestOptions options = new IngestOptions(maxPages, Math.min(pageSize, 10), maxItems);
        return startIngest(AdminJobType.INGEST_YOUTH_CENTER, "온통청년 공식 API",
                progress -> ingestService.ingestYouthCenterOfficial(options));
    }

    @PostMapping("/ingest/youth-policy-data-go-kr")
    public ApiResponse<AdminJob> ingestYouthPolicyDataGoKr(@RequestParam(defaultValue = "3") int maxPages,
                                                           @RequestParam(defaultValue = "50") int pageSize,
                                                           @RequestParam(defaultValue = "150") int maxItems) {
        requireKey(dataGoKrYouthPolicyKey, "DATA_GO_KR_YOUTH_POLICY_KEY 또는 DATA_GO_KR_SERVICE_KEY가 필요합니다.");
        requireKey(dataGoKrYouthPolicyBaseUrl, "DATA_GO_KR_YOUTH_POLICY_BASE_URL이 필요합니다.");
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);
        return startIngest(AdminJobType.INGEST_YOUTH_CENTER, "공공데이터포털 온통청년 API",
                progress -> ingestService.ingestDataGoKrYouthPolicy(options));
    }

    @PostMapping("/ingest/all")
    public ApiResponse<AdminJob> ingestAll(@RequestParam(defaultValue = "3") int maxPages,
                                           @RequestParam(defaultValue = "50") int pageSize,
                                           @RequestParam(defaultValue = "150") int maxItems) {
        requireKey(publicServiceKey, "DATA_GO_KR_PUBLIC_SERVICE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 필요합니다.");
        requireKey(localWelfareKey, "DATA_GO_KR_LOCAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 필요합니다.");
        requireKey(centralWelfareKey, "DATA_GO_KR_CENTRAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 필요합니다.");
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);
        AdminJob job = jobManager.start(AdminJobType.INGEST_ALL,
                "공공데이터 API 전체 수집 작업을 시작했습니다.", "공공데이터 API 전체 수집 완료",
                progress -> {
                    Map<String, IngestResult> result = new LinkedHashMap<>();
                    progress.update(5, "행정안전부 공공서비스 수집 중...");
                    result.put("publicService", ingestService.ingestPublicYouthServices(options));
                    progress.update(30, "지자체복지서비스 수집 중...");
                    result.put("localWelfare", ingestService.ingestLocalWelfareServices(options));
                    progress.update(55, "중앙부처복지서비스 수집 중...");
                    result.put("centralWelfare", ingestService.ingestCentralWelfareServices(options));
                    if (youthIngestConfigured()) {
                        progress.update(80, "온통청년 청년정책 수집 중...");
                        result.put("youthCenter", ingestService.ingestYouthPolicies(options));
                    }
                    progress.update(95, "수집 결과 정리 중...");
                    return result;
                });
        return ApiResponse.ok(job, "작업 시작");
    }

    private ApiResponse<AdminJob> startIngest(AdminJobType type, String label, IngestTask task) {
        AdminJob job = jobManager.start(type, label + " API 수집 작업을 시작했습니다.", label + " API 수집 완료",
                progress -> {
                    progress.update(10, label + " 목록 수집 중...");
                    return task.run(progress);
                });
        return ApiResponse.ok(job, "작업 시작");
    }

    private int indexProgress(int completed, int total) {
        return total <= 0 ? 95 : Math.min(95, 10 + (completed * 85 / total));
    }

    private int normalizeIndexLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private void requireKey(String key, String message) {
        if (!hasText(key)) throw new BusinessException(message);
    }

    private boolean youthIngestConfigured() {
        return (hasText(dataGoKrYouthPolicyKey) && hasText(dataGoKrYouthPolicyBaseUrl))
                || hasText(youthCenterOfficialKey);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface IngestTask {
        IngestResult run(AdminJobManager.AdminJobProgress progress);
    }
}
