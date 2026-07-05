package com.example.ragtest.external.normalizer;

import com.example.ragtest.external.dto.ExternalPolicyRecord;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class PublicServiceNormalizer implements PolicyNormalizer<JsonNode> {

    @Override
    public Policy normalize(JsonNode source) {
        ExternalPolicyRecord record = normalizeToRecord(source);
        Policy policy = Policy.create(record.sourceType(), record.externalId(), record.title());
        policy.updateFrom(
                record.title(),
                record.summary(),
                record.supportTarget(),
                record.selectionCriteria(),
                record.applicationMethod(),
                record.applicationStartDate(),
                record.applicationEndDate(),
                record.regionName(),
                record.categoryName(),
                record.officialUrl(),
                Integer.toHexString(record.rawPayload().hashCode())
        );
        return policy;
    }

    public ExternalPolicyRecord normalizeToRecord(JsonNode source) {
        String serviceId = text(source, "서비스ID", "id", "serviceId");
        String title = text(source, "서비스명", "title", "serviceName");
        String summary = firstNonBlank(
                text(source, "서비스목적요약"),
                text(source, "서비스목적"),
                text(source, "지원내용"),
                title
        );
        String supportTarget = firstNonBlank(text(source, "지원대상"), text(source, "지원내용"), "");
        String selectionCriteria = firstNonBlank(text(source, "선정기준"), text(source, "지원대상"), "");
        String applicationMethod = firstNonBlank(text(source, "신청방법"), text(source, "신청기한"), "");
        String categoryName = firstNonBlank(text(source, "서비스분야"), text(source, "소관기관유형"), "공공서비스");
        String regionName = normalizeRegion(
                text(source, "시도명"),
                text(source, "시군구명"),
                text(source, "소관기관명"),
                text(source, "소관기관유형")
        );
        String officialUrl = firstNonBlank(text(source, "상세조회URL"), "https://www.gov.kr");
        return new ExternalPolicyRecord(
                PolicySourceType.PUBLIC_SERVICE,
                serviceId,
                title,
                summary,
                supportTarget,
                selectionCriteria,
                applicationMethod,
                null,
                null,
                regionName,
                categoryName,
                officialUrl,
                source.toString()
        );
    }

    private String normalizeRegion(String sido, String sigungu, String organizationName, String organizationType) {
        if (hasText(organizationType) && organizationType.contains("중앙")) {
            return "전국";
        }
        if (hasText(sido) && hasText(sigungu)) {
            return sido.strip() + " " + sigungu.strip();
        }
        if (hasText(sido)) {
            return sido.strip();
        }
        if (hasText(sigungu)) {
            return sigungu.strip();
        }
        if (hasText(organizationName) && isCentralOrganization(organizationName)) {
            return "전국";
        }
        return firstNonBlank(organizationName, "전국");
    }

    private boolean isCentralOrganization(String organizationName) {
        return organizationName.endsWith("부")
                || organizationName.endsWith("처")
                || organizationName.endsWith("청")
                || organizationName.contains("위원회");
    }

    private String text(JsonNode source, String... names) {
        for (String name : names) {
            JsonNode value = source.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (hasText(text)) {
                    return text.strip();
                }
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
