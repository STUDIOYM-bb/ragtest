package com.example.ragtest.rag.dto;

import com.example.ragtest.rag.condition.ExtractedUserCondition;

import java.util.List;

public record SearchCandidateDebugResponse(
        ExtractedUserCondition extractedCondition,
        List<SearchCandidateView> vectorCandidates,
        List<SearchCandidateView> keywordCandidates,
        List<SearchCandidateView> mergedCandidates,
        List<SearchCandidateView> finalCandidates,
        List<SearchCandidateView> excludedCandidates
) {
}
