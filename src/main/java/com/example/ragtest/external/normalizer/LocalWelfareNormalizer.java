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
        String ctpvNm = text(source, "ctpvNm", "시도명");
        String sggNm = text(source, "sggNm", "시군구명");
        return new ExternalPolicyRecord(
                PolicySourceType.LOCAL_WELFARE,
                id,
                title,
                firstNonBlank(text(source, "servDgst"), text(source, "alwServCn"), text(source, "jurMnofNm"), title),
                firstNonBlank(text(source, "tgtrDtlCn"), text(source, "supportTarget"),
                        text(source, "lifeArray"), text(source, "trgterIndvdlArray"), ""),
                firstNonBlank(text(source, "slctCritCn"), text(source, "selectionCriteria"), ""),
                firstNonBlank(text(source, "aplyMtdCn"), text(source, "applicationMethod"), ""),
                null,
                null,
                normalizeRegion(ctpvNm, sggNm, text(source, "jurMnofNm")),
                "지자체복지서비스",
                firstNonBlank(text(source, "servDtlLink"), text(source, "servSeDetailLink"), "https://www.bokjiro.go.kr"),
                source.toString()
        );
    }

    private String normalizeRegion(String sido, String sigungu, String organizationName) {
        if (hasText(sido) && hasText(sigungu)) {
            return sido.strip() + " " + sigungu.strip();
        }
        if (hasText(sido)) {
            return sido.strip();
        }
        if (hasText(sigungu)) {
            return sigungu.strip();
        }
        return firstNonBlank(organizationName, "지자체");
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
            if (hasText(value)) {
                return value.strip();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
