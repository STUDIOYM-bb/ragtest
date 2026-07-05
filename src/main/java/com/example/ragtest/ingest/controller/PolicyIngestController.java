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
            PolicySourceType.PUBLIC_SERVICE,
            PolicySourceType.LOCAL_WELFARE,
            PolicySourceType.CENTRAL_WELFARE
    );

    private final ExternalPolicyIngestService externalPolicyIngestService;
    private final PolicyIndexingService policyIndexingService;
    private final PolicyRepository policyRepository;
    private final AdminJobManager adminJobManager;
    private final String publicServiceKey;
    private final String localWelfareKey;
    private final String centralWelfareKey;

    public PolicyIngestController(
            ExternalPolicyIngestService externalPolicyIngestService,
            PolicyIndexingService policyIndexingService,
            PolicyRepository policyRepository,
            AdminJobManager adminJobManager,
            @Value("${external-api.data-go-kr.public-service-key:}") String publicServiceKey,
            @Value("${external-api.data-go-kr.local-welfare-key:}") String localWelfareKey,
            @Value("${external-api.data-go-kr.central-welfare-key:}") String centralWelfareKey
    ) {
        this.externalPolicyIngestService = externalPolicyIngestService;
        this.policyIndexingService = policyIndexingService;
        this.policyRepository = policyRepository;
        this.adminJobManager = adminJobManager;
        this.publicServiceKey = publicServiceKey;
        this.localWelfareKey = localWelfareKey;
        this.centralWelfareKey = centralWelfareKey;
    }

    @GetMapping("/rag/status")
    public ApiResponse<Map<String, Object>> ragStatus() {
        long youthRelatedPolicies = policyRepository.countBySourceTypeNotAndYouthRelatedTrue(PolicySourceType.SAMPLE);
        long indexedYouthPolicies = policyRepository.countBySourceTypeNotAndYouthRelatedTrueAndIndexedTrue(PolicySourceType.SAMPLE);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalPolicies", policyRepository.count());
        data.put("realPolicies", policyRepository.countBySourceTypeNot(PolicySourceType.SAMPLE));
        data.put("samplePolicies", policyRepository.countBySourceType(PolicySourceType.SAMPLE));
        data.put("youthRelatedPolicies", youthRelatedPolicies);
        data.put("indexedYouthPolicies", indexedYouthPolicies);
        data.put("unindexedYouthPolicies", Math.max(0, youthRelatedPolicies - indexedYouthPolicies));

        Map<String, Object> bySourceType = new LinkedHashMap<>();
        for (PolicySourceType sourceType : PolicySourceType.values()) {
            long youthRelated = policyRepository.countBySourceTypeAndYouthRelatedTrue(sourceType);
            long indexed = policyRepository.countBySourceTypeAndYouthRelatedTrueAndIndexedTrue(sourceType);
            bySourceType.put(sourceType.name(), Map.of(
                    "total", policyRepository.countBySourceType(sourceType),
                    "youthRelated", youthRelated,
                    "indexed", indexed,
                    "unindexed", Math.max(0, youthRelated - indexed)
            ));
        }
        data.put("bySourceType", bySourceType);
        return ApiResponse.ok(data, "RAG 데이터 상태 조회 완료");
    }

    @PostMapping("/rag/index")
    public ApiResponse<AdminJob> index(@RequestParam(defaultValue = "30") int limit) {
        int normalizedLimit = normalizeIndexLimit(limit);
        AdminJob job = adminJobManager.start(
                AdminJobType.INDEX_REAL,
                "실제 정책 데이터 인덱싱 작업을 시작했습니다.",
                "실제 정책 데이터 인덱싱 완료",
                progress -> {
                    progress.update(5, "인덱싱할 실제 청년정책을 조회 중...");
                    int indexedCount = policyIndexingService.indexUnindexedRealPolicies(
                            normalizedLimit,
                            (completed, total) -> progress.update(indexProgress(completed, total),
                                    "실제 정책 데이터 인덱싱 중... " + completed + "/" + total)
                    );
                    return new IngestResult(0, 0, indexedCount, 0);
                }
        );
        return ApiResponse.ok(job, "작업 시작");
    }

    @PostMapping("/rag/reindex-real")
    public ApiResponse<AdminJob> reindexReal(@RequestParam(defaultValue = "30") int limit) {
        int normalizedLimit = normalizeIndexLimit(limit);
        AdminJob job = adminJobManager.start(
                AdminJobType.REINDEX_REAL,
                "실제 정책 데이터 재인덱싱 작업을 시작했습니다.",
                "실제 정책 데이터 재인덱싱 완료",
                progress -> {
                    progress.update(5, "기존 벡터를 정리하고 재인덱싱을 준비 중...");
                    int indexedCount = policyIndexingService.reindexRealYouthPolicies(
                            normalizedLimit,
                            (completed, total) -> progress.update(indexProgress(completed, total),
                                    "실제 정책 데이터 재인덱싱 중... " + completed + "/" + total)
                    );
                    return new IngestResult(0, 0, indexedCount, 0);
                }
        );
        return ApiResponse.ok(job, "작업 시작");
    }

    @PostMapping("/rag/index-source/{sourceType}")
    public ApiResponse<AdminJob> indexSource(
            @PathVariable PolicySourceType sourceType,
            @RequestParam(defaultValue = "30") int limit
    ) {
        if (!INDEXABLE_SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException("인덱싱 가능한 sourceType은 PUBLIC_SERVICE, LOCAL_WELFARE, CENTRAL_WELFARE입니다.");
        }
        int normalizedLimit = normalizeIndexLimit(limit);
        AdminJob job = adminJobManager.start(
                AdminJobType.INDEX_REAL,
                sourceType + " 정책 인덱싱 작업을 시작했습니다.",
                sourceType + " 정책 인덱싱 완료",
                progress -> {
                    progress.update(5, sourceType + " 미인덱싱 정책 조회 중...");
                    int indexedCount = policyIndexingService.indexUnindexedPolicies(
                            sourceType,
                            normalizedLimit,
                            (completed, total) -> progress.update(indexProgress(completed, total),
                                    sourceType + " 정책 인덱싱 중... " + completed + "/" + total)
                    );
                    return new IngestResult(0, 0, indexedCount, 0);
                }
        );
        return ApiResponse.ok(job, "작업 시작");
    }

    @PostMapping("/ingest/public-service")
    public ApiResponse<AdminJob> ingestPublicService(
            @RequestParam(defaultValue = "1") int maxPages,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "50") int maxItems
    ) {
        requireKey(publicServiceKey, "DATA_GO_KR_PUBLIC_SERVICE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);
        AdminJob job = adminJobManager.start(
                AdminJobType.INGEST_PUBLIC_SERVICE,
                "행정안전부 공공서비스 API 수집 작업을 시작했습니다.",
                "행정안전부 공공서비스 API 수집 완료",
                progress -> {
                    progress.update(10, "행정안전부 공공서비스 목록 및 상세정보 수집 중...");
                    return externalPolicyIngestService.ingestPublicYouthServices(options);
                }
        );
        return ApiResponse.ok(job, "작업 시작");
    }

    @PostMapping("/ingest/local-welfare")
    public ApiResponse<AdminJob> ingestLocalWelfare(
            @RequestParam(defaultValue = "1") int maxPages,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "50") int maxItems
    ) {
        requireKey(localWelfareKey, "DATA_GO_KR_LOCAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);
        AdminJob job = adminJobManager.start(
                AdminJobType.INGEST_LOCAL_WELFARE,
                "지자체복지서비스 API 수집 작업을 시작했습니다.",
                "지자체복지서비스 API 수집 완료",
                progress -> {
                    progress.update(10, "지자체복지서비스 목록 및 상세정보 수집 중...");
                    return externalPolicyIngestService.ingestLocalWelfareServices(options);
                }
        );
        return ApiResponse.ok(job, "작업 시작");
    }

    @PostMapping("/ingest/central-welfare")
    public ApiResponse<AdminJob> ingestCentralWelfare(
            @RequestParam(defaultValue = "1") int maxPages,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "50") int maxItems
    ) {
        requireKey(centralWelfareKey, "DATA_GO_KR_CENTRAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);
        AdminJob job = adminJobManager.start(
                AdminJobType.INGEST_CENTRAL_WELFARE,
                "중앙부처복지서비스 API 수집 작업을 시작했습니다.",
                "중앙부처복지서비스 API 수집 완료",
                progress -> {
                    progress.update(10, "중앙부처복지서비스 목록 및 상세정보 수집 중...");
                    return externalPolicyIngestService.ingestCentralWelfareServices(options);
                }
        );
        return ApiResponse.ok(job, "작업 시작");
    }

    @PostMapping("/ingest/all")
    public ApiResponse<AdminJob> ingestAll(
            @RequestParam(defaultValue = "1") int maxPages,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "50") int maxItems
    ) {
        requireKey(publicServiceKey, "DATA_GO_KR_PUBLIC_SERVICE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        requireKey(localWelfareKey, "DATA_GO_KR_LOCAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        requireKey(centralWelfareKey, "DATA_GO_KR_CENTRAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        IngestOptions options = new IngestOptions(maxPages, pageSize, maxItems);

        AdminJob job = adminJobManager.start(
                AdminJobType.INGEST_ALL,
                "공공데이터 API 전체 수집 작업을 시작했습니다.",
                "공공데이터 API 전체 수집 완료",
                progress -> {
                    Map<String, IngestResult> result = new LinkedHashMap<>();
                    progress.update(5, "행정안전부 공공서비스 수집 중...");
                    result.put("publicService", externalPolicyIngestService.ingestPublicYouthServices(options));
                    progress.update(33, "행정안전부 공공서비스 수집 완료");

                    progress.update(36, "지자체복지서비스 수집 중...");
                    result.put("localWelfare", externalPolicyIngestService.ingestLocalWelfareServices(options));
                    progress.update(66, "지자체복지서비스 수집 완료");

                    progress.update(69, "중앙부처복지서비스 수집 중...");
                    result.put("centralWelfare", externalPolicyIngestService.ingestCentralWelfareServices(options));
                    progress.update(95, "수집 결과 정리 중...");
                    return result;
                }
        );
        return ApiResponse.ok(job, "작업 시작");
    }

    private int indexProgress(int completed, int total) {
        if (total <= 0) {
            return 95;
        }
        return Math.min(95, 10 + (completed * 85 / total));
    }

    private int normalizeIndexLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private void requireKey(String key, String message) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(message);
        }
    }
}
