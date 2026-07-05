package com.example.ragtest.ingest.controller;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.common.response.ApiResponse;
import com.example.ragtest.ingest.service.ExternalPolicyIngestService;
import com.example.ragtest.ingest.service.IngestResult;
import com.example.ragtest.ingest.service.PolicyIndexingService;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.example.ragtest.policy.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class PolicyIngestController {

    private final ExternalPolicyIngestService externalPolicyIngestService;
    private final PolicyIndexingService policyIndexingService;
    private final PolicyRepository policyRepository;
    private final String publicServiceKey;
    private final String localWelfareKey;
    private final String centralWelfareKey;

    public PolicyIngestController(
            ExternalPolicyIngestService externalPolicyIngestService,
            PolicyIndexingService policyIndexingService,
            PolicyRepository policyRepository,
            @Value("${external-api.data-go-kr.public-service-key:}") String publicServiceKey,
            @Value("${external-api.data-go-kr.local-welfare-key:}") String localWelfareKey,
            @Value("${external-api.data-go-kr.central-welfare-key:}") String centralWelfareKey
    ) {
        this.externalPolicyIngestService = externalPolicyIngestService;
        this.policyIndexingService = policyIndexingService;
        this.policyRepository = policyRepository;
        this.publicServiceKey = publicServiceKey;
        this.localWelfareKey = localWelfareKey;
        this.centralWelfareKey = centralWelfareKey;
    }

    @GetMapping("/rag/status")
    public ApiResponse<Map<String, Object>> ragStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalPolicies", policyRepository.count());
        data.put("realPolicies", policyRepository.countBySourceTypeNot(PolicySourceType.SAMPLE));
        data.put("samplePolicies", policyRepository.countBySourceType(PolicySourceType.SAMPLE));
        data.put("youthRelatedPolicies", policyRepository.countBySourceTypeNotAndYouthRelatedTrue(PolicySourceType.SAMPLE));
        data.put("indexedYouthPolicies", policyRepository.countBySourceTypeNotAndYouthRelatedTrueAndIndexedTrue(PolicySourceType.SAMPLE));

        Map<String, Object> bySourceType = new LinkedHashMap<>();
        for (PolicySourceType sourceType : PolicySourceType.values()) {
            bySourceType.put(sourceType.name(), Map.of(
                    "total", policyRepository.countBySourceType(sourceType),
                    "youthRelated", policyRepository.countBySourceTypeAndYouthRelatedTrue(sourceType),
                    "indexed", policyRepository.countBySourceTypeAndYouthRelatedTrueAndIndexedTrue(sourceType)
            ));
        }
        data.put("bySourceType", bySourceType);
        return ApiResponse.ok(data, "RAG 데이터 상태 조회 완료");
    }

    @PostMapping("/rag/index")
    public ApiResponse<IngestResult> index() {
        int indexedCount = policyIndexingService.indexUnindexedRealPolicies();
        return ApiResponse.ok(new IngestResult(0, 0, indexedCount, 0), "실제 정책 데이터 인덱싱 완료");
    }

    @PostMapping("/rag/reindex-real")
    public ApiResponse<IngestResult> reindexReal() {
        int indexedCount = policyIndexingService.reindexRealYouthPolicies();
        return ApiResponse.ok(new IngestResult(0, 0, indexedCount, 0), "실제 정책 데이터 재인덱싱 완료");
    }

    @PostMapping("/ingest/public-service")
    public ApiResponse<IngestResult> ingestPublicService() {
        requireKey(publicServiceKey, "DATA_GO_KR_PUBLIC_SERVICE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        IngestResult ingestResult = externalPolicyIngestService.ingestPublicYouthServices();
        int indexedCount = policyIndexingService.indexUnindexedPolicies(PolicySourceType.PUBLIC_SERVICE);
        return ApiResponse.ok(ingestResult.withIndexedCount(indexedCount), "행정안전부 공공서비스 API 수집 및 인덱싱 완료");
    }

    @PostMapping("/ingest/local-welfare")
    public ApiResponse<IngestResult> ingestLocalWelfare() {
        requireKey(localWelfareKey, "DATA_GO_KR_LOCAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        IngestResult ingestResult = externalPolicyIngestService.ingestLocalWelfareServices();
        int indexedCount = policyIndexingService.indexUnindexedPolicies(PolicySourceType.LOCAL_WELFARE);
        return ApiResponse.ok(ingestResult.withIndexedCount(indexedCount), "지자체복지서비스 API 수집 및 인덱싱 완료");
    }

    @PostMapping("/ingest/central-welfare")
    public ApiResponse<IngestResult> ingestCentralWelfare() {
        requireKey(centralWelfareKey, "DATA_GO_KR_CENTRAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        IngestResult ingestResult = externalPolicyIngestService.ingestCentralWelfareServices();
        int indexedCount = policyIndexingService.indexUnindexedPolicies(PolicySourceType.CENTRAL_WELFARE);
        return ApiResponse.ok(ingestResult.withIndexedCount(indexedCount), "중앙부처복지서비스 API 수집 및 인덱싱 완료");
    }

    @PostMapping("/ingest/all")
    public ApiResponse<Map<String, Object>> ingestAll() {
        requireKey(publicServiceKey, "DATA_GO_KR_PUBLIC_SERVICE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        requireKey(localWelfareKey, "DATA_GO_KR_LOCAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        requireKey(centralWelfareKey, "DATA_GO_KR_CENTRAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");

        Map<String, Object> result = new LinkedHashMap<>();
        IngestResult publicService = externalPolicyIngestService.ingestPublicYouthServices();
        result.put("publicService", publicService.withIndexedCount(policyIndexingService.indexUnindexedPolicies(PolicySourceType.PUBLIC_SERVICE)));

        IngestResult localWelfare = externalPolicyIngestService.ingestLocalWelfareServices();
        result.put("localWelfare", localWelfare.withIndexedCount(policyIndexingService.indexUnindexedPolicies(PolicySourceType.LOCAL_WELFARE)));

        IngestResult centralWelfare = externalPolicyIngestService.ingestCentralWelfareServices();
        result.put("centralWelfare", centralWelfare.withIndexedCount(policyIndexingService.indexUnindexedPolicies(PolicySourceType.CENTRAL_WELFARE)));

        return ApiResponse.ok(result, "공공데이터 API 전체 수집 및 인덱싱 완료");
    }

    private void requireKey(String key, String message) {
        if (!hasText(key)) {
            throw new BusinessException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
