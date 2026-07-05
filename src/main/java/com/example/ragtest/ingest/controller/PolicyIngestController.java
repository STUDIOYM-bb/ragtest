package com.example.ragtest.ingest.controller;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.common.response.ApiResponse;
import com.example.ragtest.ingest.service.ExternalPolicyIngestService;
import com.example.ragtest.ingest.service.IngestResult;
import com.example.ragtest.ingest.service.PolicyIndexingService;
import com.example.ragtest.ingest.service.SamplePolicyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class PolicyIngestController {

    private final SamplePolicyService samplePolicyService;
    private final ExternalPolicyIngestService externalPolicyIngestService;
    private final PolicyIndexingService policyIndexingService;
    private final String youthCenterApiKey;
    private final String dataGoKrServiceKey;

    public PolicyIngestController(
            SamplePolicyService samplePolicyService,
            ExternalPolicyIngestService externalPolicyIngestService,
            PolicyIndexingService policyIndexingService,
            @Value("${external-api.youth-center.api-key:}") String youthCenterApiKey,
            @Value("${external-api.data-go-kr.service-key:}") String dataGoKrServiceKey
    ) {
        this.samplePolicyService = samplePolicyService;
        this.externalPolicyIngestService = externalPolicyIngestService;
        this.policyIndexingService = policyIndexingService;
        this.youthCenterApiKey = youthCenterApiKey;
        this.dataGoKrServiceKey = dataGoKrServiceKey;
    }

    @PostMapping("/rag/index-sample")
    public ApiResponse<IngestResult> indexSample() {
        int savedCount = samplePolicyService.upsertSamplePolicies();
        int indexedCount = policyIndexingService.indexUnindexedPolicies();
        return ApiResponse.ok(new IngestResult(savedCount, indexedCount), "샘플 정책 데이터 생성 및 인덱싱 완료");
    }

    @PostMapping("/rag/index")
    public ApiResponse<IngestResult> index() {
        int indexedCount = policyIndexingService.indexUnindexedPolicies();
        return ApiResponse.ok(new IngestResult(0, indexedCount), "정책 인덱싱 완료");
    }

    @PostMapping("/ingest/youth-center")
    public ApiResponse<Map<String, Object>> ingestYouthCenter() {
        requireKey(youthCenterApiKey, "온통청년 API 키가 설정되지 않았습니다.");
        return ApiResponse.ok(Map.of("savedCount", 0), "온통청년 API는 키 설정 확인까지 완료되었습니다. 실제 저장 연동은 다음 단계에서 구현합니다.");
    }

    @PostMapping("/ingest/public-service")
    public ApiResponse<IngestResult> ingestPublicService() {
        requireDataGoKrKey();
        int savedCount = externalPolicyIngestService.ingestPublicYouthServices();
        int indexedCount = policyIndexingService.indexUnindexedPolicies();
        return ApiResponse.ok(new IngestResult(savedCount, indexedCount), "공공서비스 실제 API 수집 및 인덱싱 완료");
    }

    @PostMapping("/ingest/local-welfare")
    public ApiResponse<IngestResult> ingestLocalWelfare() {
        requireDataGoKrKey();
        int savedCount = externalPolicyIngestService.ingestLocalWelfareServices();
        int indexedCount = policyIndexingService.indexUnindexedPolicies();
        return ApiResponse.ok(new IngestResult(savedCount, indexedCount), "지자체복지서비스 실제 API 수집 및 인덱싱 완료");
    }

    @PostMapping("/ingest/central-welfare")
    public ApiResponse<IngestResult> ingestCentralWelfare() {
        requireDataGoKrKey();
        int savedCount = externalPolicyIngestService.ingestCentralWelfareServices();
        int indexedCount = policyIndexingService.indexUnindexedPolicies();
        return ApiResponse.ok(new IngestResult(savedCount, indexedCount), "중앙부처복지서비스 실제 API 수집 및 인덱싱 완료");
    }

    @PostMapping("/ingest/all")
    public ApiResponse<Map<String, Object>> ingestAll() {
        requireDataGoKrKey();
        Map<String, Object> result = new LinkedHashMap<>();

        int publicSavedCount = externalPolicyIngestService.ingestPublicYouthServices();
        result.put("publicServiceSavedCount", publicSavedCount);

        result.put("youthCenter", hasText(youthCenterApiKey)
                ? "키 설정됨: 실제 저장 연동은 다음 단계에서 구현"
                : "스킵: 온통청년 API 키가 설정되지 않았습니다.");

        try {
            result.put("localWelfareSavedCount", externalPolicyIngestService.ingestLocalWelfareServices());
        } catch (BusinessException exception) {
            result.put("localWelfare", exception.getMessage());
        }

        try {
            result.put("centralWelfareSavedCount", externalPolicyIngestService.ingestCentralWelfareServices());
        } catch (BusinessException exception) {
            result.put("centralWelfare", exception.getMessage());
        }

        int indexedCount = policyIndexingService.indexUnindexedPolicies();
        result.put("indexedCount", indexedCount);
        return ApiResponse.ok(result, "실제 정책 API 수집 및 인덱싱 완료");
    }

    private void requireDataGoKrKey() {
        requireKey(dataGoKrServiceKey, "공공데이터포털 API 키가 설정되지 않았습니다.");
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
