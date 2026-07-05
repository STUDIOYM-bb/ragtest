package com.example.ragtest.external.normalizer;

import com.example.ragtest.policy.domain.Policy;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class YouthPolicyNormalizer implements PolicyNormalizer<JsonNode> {
    @Override
    public Policy normalize(JsonNode source) {
        // TODO: 온통청년 API 응답 필드 확정 후 Policy 변환 구현.
        return null;
    }
}
