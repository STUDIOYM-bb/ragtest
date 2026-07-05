package com.example.ragtest.rag.dto;

import com.example.ragtest.rag.condition.ExtractedUserCondition;

import java.util.List;

public record RagAskResponse(
        String answer,
        ExtractedUserCondition extractedCondition,
        List<RagSourceResponse> sources
) {
}
