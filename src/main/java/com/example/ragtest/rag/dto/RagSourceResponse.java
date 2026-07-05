package com.example.ragtest.rag.dto;

import java.util.List;

public record RagSourceResponse(
        Long policyId,
        String title,
        String sourceType,
        String regionName,
        String categoryName,
        String officialUrl,
        String eligibilityStatus,
        double finalScore,
        List<String> matchedReasons,
        List<String> cautionReasons
) {
}
