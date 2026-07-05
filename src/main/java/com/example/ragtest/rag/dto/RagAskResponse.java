package com.example.ragtest.rag.dto;

import java.util.List;

public record RagAskResponse(
        String answer,
        List<RagSourceResponse> sources
) {
}
