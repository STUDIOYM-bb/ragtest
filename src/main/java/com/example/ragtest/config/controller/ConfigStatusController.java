package com.example.ragtest.config.controller;

import com.example.ragtest.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class ConfigStatusController {

    private final String openAiApiKey;
    private final String dataGoKrServiceKey;
    private final String publicServiceKey;
    private final String localWelfareKey;
    private final String centralWelfareKey;
    private final String youthCenterOfficialApiKey;
    private final String dataGoKrYouthPolicyKey;
    private final String dataGoKrYouthPolicyBaseUrl;

    public ConfigStatusController(
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${external-api.data-go-kr.service-key:}") String dataGoKrServiceKey,
            @Value("${external-api.data-go-kr.public-service-key:}") String publicServiceKey,
            @Value("${external-api.data-go-kr.local-welfare-key:}") String localWelfareKey,
            @Value("${external-api.data-go-kr.central-welfare-key:}") String centralWelfareKey,
            @Value("${external-api.youth-center.official-api-key:}") String youthCenterOfficialApiKey,
            @Value("${external-api.data-go-kr.youth-policy-key:}") String dataGoKrYouthPolicyKey,
            @Value("${external-api.data-go-kr.youth-policy-base-url:}") String dataGoKrYouthPolicyBaseUrl
    ) {
        this.openAiApiKey = openAiApiKey;
        this.dataGoKrServiceKey = dataGoKrServiceKey;
        this.publicServiceKey = publicServiceKey;
        this.localWelfareKey = localWelfareKey;
        this.centralWelfareKey = centralWelfareKey;
        this.youthCenterOfficialApiKey = youthCenterOfficialApiKey;
        this.dataGoKrYouthPolicyKey = dataGoKrYouthPolicyKey;
        this.dataGoKrYouthPolicyBaseUrl = dataGoKrYouthPolicyBaseUrl;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("openAiConfigured", hasText(openAiApiKey));
        data.put("dataGoKrConfigured", hasText(dataGoKrServiceKey));
        data.put("publicServiceConfigured", hasText(publicServiceKey));
        data.put("localWelfareConfigured", hasText(localWelfareKey));
        data.put("centralWelfareConfigured", hasText(centralWelfareKey));
        data.put("youthCenterOfficialConfigured", hasText(youthCenterOfficialApiKey));
        data.put("youthCenterOfficialKeyPreview", hasText(youthCenterOfficialApiKey) ? mask(youthCenterOfficialApiKey.strip()) : "");
        data.put("dataGoKrYouthPolicyConfigured", hasText(dataGoKrYouthPolicyKey));
        data.put("dataGoKrYouthPolicyKeyPreview", hasText(dataGoKrYouthPolicyKey) ? mask(dataGoKrYouthPolicyKey.strip()) : "");
        data.put("dataGoKrYouthPolicyBaseUrlConfigured", hasText(dataGoKrYouthPolicyBaseUrl));
        data.put("dataGoKrYouthPolicyBaseUrlMasked", maskUrl(dataGoKrYouthPolicyBaseUrl));
        data.put("youthCenterConfigured", (hasText(dataGoKrYouthPolicyKey) && hasText(dataGoKrYouthPolicyBaseUrl))
                || hasText(youthCenterOfficialApiKey));
        data.put("youthCenterKeyPreview", hasText(youthCenterOfficialApiKey) ? mask(youthCenterOfficialApiKey.strip()) : "");
        return ApiResponse.ok(data, "환경변수 설정 상태 조회 완료");
    }

    private boolean hasText(String value) {
        return value != null && !value.strip().isBlank();
    }

    private String mask(String value) {
        if (value.length() <= 8) {
            return value.charAt(0) + "****" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private String maskUrl(String value) {
        if (!hasText(value)) return "";
        String result = value.strip();
        if (hasText(dataGoKrYouthPolicyKey)) {
            result = result.replace(dataGoKrYouthPolicyKey.strip(), "[REDACTED_SERVICE_KEY]");
        }
        if (hasText(dataGoKrServiceKey)) {
            result = result.replace(dataGoKrServiceKey.strip(), "[REDACTED_SERVICE_KEY]");
        }
        return result;
    }
}
