package com.example.ragtest.rag.ranking;

import com.example.ragtest.rag.condition.ExtractedUserCondition;

import java.util.List;

public record HybridSearchResult(
        ExtractedUserCondition extractedCondition,
        List<PolicySearchCandidate> vectorCandidates,
        List<PolicySearchCandidate> keywordCandidates,
        List<PolicySearchCandidate> mergedCandidates,
        List<PolicySearchCandidate> finalCandidates,
        List<PolicySearchCandidate> excludedCandidates
) {
}
