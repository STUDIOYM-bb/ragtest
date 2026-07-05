package com.example.ragtest.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record RagAskRequest(
        @NotBlank String question,
        String region,
        Integer age,
        String employmentStatus,
        Integer topK
) {
    public int effectiveTopK() {
        if (topK == null || topK < 1) {
            return 5;
        }
        return Math.min(topK, 10);
    }
}
