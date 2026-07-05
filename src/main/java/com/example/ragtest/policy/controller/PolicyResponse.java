package com.example.ragtest.policy.controller;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;

import java.time.LocalDate;

public record PolicyResponse(
        Long id,
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
        boolean indexed
) {
    public static PolicyResponse from(Policy policy) {
        return new PolicyResponse(
                policy.getId(),
                policy.getSourceType(),
                policy.getExternalId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getSupportTarget(),
                policy.getSelectionCriteria(),
                policy.getApplicationMethod(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                policy.getRegionName(),
                policy.getCategoryName(),
                policy.getOfficialUrl(),
                policy.isIndexed()
        );
    }
}
