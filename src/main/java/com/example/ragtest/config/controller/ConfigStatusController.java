package com.example.ragtest.config.controller;

import com.example.ragtest.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class ConfigStatusController {

    private final String openAiApiKey;
    private final String dataGoKrServiceKey;
    private final String publicServiceKey;
    private final String localWelfareKey;
    private final String centralWelfareKey;
    private final String youthCenterApiKey;
    private final String youthPolicyKey;

    public ConfigStatusController(
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${external-api.data-go-kr.service-key:}") String dataGoKrServiceKey,
            @Value("${external-api.data-go-kr.public-service-key:}") String publicServiceKey,
            @Value("${external-api.data-go-kr.local-welfare-key:}") String localWelfareKey,
            @Value("${external-api.data-go-kr.central-welfare-key:}") String centralWelfareKey,
            @Value("${external-api.youth-center.api-key:}") String youthCenterApiKey,
            @Value("${external-api.data-go-kr.youth-policy-key:}") String youthPolicyKey
    ) {
        this.openAiApiKey = openAiApiKey;
        this.dataGoKrServiceKey = dataGoKrServiceKey;
        this.publicServiceKey = publicServiceKey;
        this.localWelfareKey = localWelfareKey;
        this.centralWelfareKey = centralWelfareKey;
        this.youthCenterApiKey = youthCenterApiKey;
        this.youthPolicyKey = youthPolicyKey;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Boolean>> status() {
        return ApiResponse.ok(Map.of(
                "openAiConfigured", hasText(openAiApiKey),
                "dataGoKrConfigured", hasText(dataGoKrServiceKey),
                "publicServiceConfigured", hasText(publicServiceKey),
                "localWelfareConfigured", hasText(localWelfareKey),
                "centralWelfareConfigured", hasText(centralWelfareKey),
                "youthCenterConfigured", hasText(youthCenterApiKey) || hasText(youthPolicyKey)
        ), "환경변수 설정 상태 조회 완료");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
