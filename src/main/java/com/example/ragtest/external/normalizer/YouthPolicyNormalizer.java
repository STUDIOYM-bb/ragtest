package com.example.ragtest.external.normalizer;

import com.example.ragtest.external.dto.ExternalPolicyRecord;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class YouthPolicyNormalizer implements PolicyNormalizer<JsonNode> {
    @Override
    public Policy normalize(JsonNode source) {
        ExternalPolicyRecord record = normalizeToRecord(source);
        Policy policy = Policy.create(record.sourceType(), record.externalId(), record.title());
        policy.updateFrom(record.title(), record.summary(), record.supportTarget(), record.selectionCriteria(),
                record.applicationMethod(), record.applicationStartDate(), record.applicationEndDate(),
                record.regionName(), record.categoryName(), record.officialUrl(),
                Integer.toHexString(record.rawPayload().hashCode()));
        return policy;
    }

    public ExternalPolicyRecord normalizeToRecord(JsonNode source) {
        String id = text(source, "plcyNo", "bizId", "policyId", "id");
        String title = first(text(source, "plcyNm", "polyBizSjnm", "policyName", "title"), id);
        String minAge = text(source, "sprtTrgtMinAge", "ageInfo");
        String maxAge = text(source, "sprtTrgtMaxAge");
        String ageTarget = hasText(minAge) && hasText(maxAge)
                ? "만 " + minAge + "세 이상 " + maxAge + "세 이하" : minAge;
        String supportTarget = first(text(source, "sprtTrgtCn", "sporScvl", "supportTarget"), ageTarget,
                text(source, "schoolCd", "jobCd", "mrgSttsCd"));
        String region = first(text(source, "zipCd", "rgtrInstCdNm", "sprvsnInstCdNm", "regionName"), "전국");
        String category = first(text(source, "lclsfNm", "mclsfNm", "plcyKywdNm", "polyBizSecd"), "청년정책");
        return new ExternalPolicyRecord(PolicySourceType.YOUTH_CENTER, id, title,
                first(text(source, "plcyExplnCn", "polyItcnCn", "plcySprtCn", "sporCn"), title),
                supportTarget,
                first(text(source, "srngMthdCn", "selectionCriteria"), supportTarget),
                first(text(source, "plcyAplyMthdCn", "rqutProcCn", "applicationMethod"), ""),
                parseDate(text(source, "aplyBgngYmd", "rqutPrdCn", "bizPrdBgngYmd")),
                parseDate(text(source, "aplyEndYmd", "bizPrdEndYmd")),
                region, category,
                first(text(source, "aplyUrlAddr", "rqutUrla", "refUrlAddr1"), "https://www.youthcenter.go.kr"),
                source.toString());
    }

    private LocalDate parseDate(String value) {
        if (!hasText(value)) return null;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 8) return null;
        try {
            return LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode source, String... names) {
        for (String name : names) {
            JsonNode value = source.get(name);
            if (value != null && !value.isNull() && hasText(value.asText())) return value.asText().strip();
        }
        return "";
    }

    private String first(String... values) {
        for (String value : values) if (hasText(value)) return value.strip();
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
