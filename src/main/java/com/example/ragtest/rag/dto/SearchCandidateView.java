package com.example.ragtest.rag.dto;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.rag.ranking.PolicySearchCandidate;

import java.util.List;

public record SearchCandidateView(
        Long policyId,
        String title,
        String sourceType,
        String regionName,
        String categoryName,
        boolean youthRelated,
        boolean indexed,
        boolean fromVector,
        boolean fromKeyword,
        double finalScore,
        List<String> matchedKeywords,
        List<String> matchedReasons,
        List<String> cautionReasons,
        List<String> excludedReasons
) {
    public static SearchCandidateView from(PolicySearchCandidate candidate) {
        Policy policy = candidate.getPolicy();
        return new SearchCandidateView(policy.getId(), policy.getTitle(), policy.getSourceType().name(),
                policy.getRegionName(), policy.getCategoryName(), policy.isYouthRelated(), policy.isIndexed(),
                candidate.isFromVector(), candidate.isFromKeyword(), candidate.getFinalScore(),
                candidate.getMatchedKeywords(), candidate.getMatchedReasons(), candidate.getCautionReasons(),
                candidate.getExcludedReasons());
    }
}
