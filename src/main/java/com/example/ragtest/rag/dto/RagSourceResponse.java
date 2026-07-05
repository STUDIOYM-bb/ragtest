package com.example.ragtest.rag.dto;

public record RagSourceResponse(
        Long policyId,
        String title,
        String sourceType,
        String regionName,
        String categoryName,
        String officialUrl
) {
}
