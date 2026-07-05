package com.example.ragtest.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RagAskRequest(
        @NotBlank String question,
        String region,
        Integer age,
        String employmentStatus,
        @Min(1) @Max(10) Integer topK
) {
    public int effectiveTopK() {
        if (topK == null) {
            return 5;
        }
        return Math.min(topK, 10);
    }
}
