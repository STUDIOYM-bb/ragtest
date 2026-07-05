package com.example.ragtest.external.normalizer;

import com.example.ragtest.external.dto.ExternalPolicyRecord;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class LocalWelfareNormalizer implements PolicyNormalizer<JsonNode> {
    @Override
    public Policy normalize(JsonNode source) {
        ExternalPolicyRecord record = normalizeToRecord(source);
        Policy policy = Policy.create(record.sourceType(), record.externalId(), record.title());
        policy.updateFrom(record.title(), record.summary(), record.supportTarget(), record.selectionCriteria(),
                record.applicationMethod(), null, null, record.regionName(), record.categoryName(),
                record.officialUrl(), Integer.toHexString(record.rawPayload().hashCode()));
        return policy;
    }

    public ExternalPolicyRecord normalizeToRecord(JsonNode source) {
        String id = text(source, "servId", "서비스ID", "id");
        String title = firstNonBlank(text(source, "servNm"), text(source, "서비스명"), id);
        return new ExternalPolicyRecord(
                PolicySourceType.LOCAL_WELFARE,
                id,
                title,
                firstNonBlank(text(source, "servDgst"), text(source, "jurMnofNm"), title),
                firstNonBlank(text(source, "lifeArray"), text(source, "trgterIndvdlArray"), ""),
                firstNonBlank(text(source, "slctCritCn"), text(source, "alwServCn"), ""),
                firstNonBlank(text(source, "aplyMtdCn"), text(source, "servSeCode"), ""),
                null,
                null,
                firstNonBlank(text(source, "ctpvNm"), text(source, "sggNm"), text(source, "jurMnofNm"), "지자체"),
                "지자체복지서비스",
                firstNonBlank(text(source, "servDtlLink"), "https://www.bokjiro.go.kr"),
                source.toString()
        );
    }

    private String text(JsonNode source, String... names) {
        for (String name : names) {
            JsonNode value = source.get(name);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText().strip();
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }
}
