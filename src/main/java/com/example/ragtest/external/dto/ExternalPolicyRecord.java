package com.example.ragtest.external.dto;

import com.example.ragtest.policy.domain.PolicySourceType;

import java.time.LocalDate;

public record ExternalPolicyRecord(
        PolicySourceType sourceType,
        String externalId,
        String title,
        String summary,
        String supportTarget,
        String selectionCriteria,
        String applicationMethod,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        String regionName,
        String categoryName,
        String officialUrl,
        String rawPayload
) {
}
