package com.example.ragtest.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record DebugSearchRequest(@NotBlank String question, Integer topK) {
    public int effectiveTopK() { return topK == null ? 20 : Math.max(1, Math.min(topK, 100)); }
}
