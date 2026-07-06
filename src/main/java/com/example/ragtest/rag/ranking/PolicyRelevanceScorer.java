package com.example.ragtest.rag.ranking;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.example.ragtest.policy.match.PolicyConditionMatcher;
import com.example.ragtest.policy.match.PolicyMatchResult;
import com.example.ragtest.rag.condition.ExtractedUserCondition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PolicyRelevanceScorer {

    private final PolicyConditionMatcher conditionMatcher;

    public PolicyRelevanceScorer(PolicyConditionMatcher conditionMatcher) {
        this.conditionMatcher = conditionMatcher;
    }

    public PolicySearchCandidate score(PolicySearchCandidate candidate, ExtractedUserCondition condition) {
        Policy policy = candidate.getPolicy();
        PolicyMatchResult match = conditionMatcher.match(policy, condition);
        candidate.applyMatch(match);

        double conditionScore = match.matchedReasons().size() * 7.0 - match.cautionReasons().size() * 1.5;
        double keywordScore = detailedKeywordScore(policy, candidate.getMatchedKeywords());
        double finalScore = candidate.getVectorScore() * 35.0
                + candidate.getKeywordScore() * 2.0
                + keywordScore
                + conditionScore;
        if (candidate.isFromVector() && candidate.isFromKeyword()) finalScore += 8.0;
        if (policy.getSourceType() == PolicySourceType.YOUTH_CENTER) finalScore += 5.0;
        finalScore += intentPenalty(policy, condition);

        candidate.setConditionScore(conditionScore);
        candidate.setFinalScore(finalScore);
        if (!match.eligible()) return candidate;
        if (finalScore < 3.0) candidate.addExcludedReason("LOW_SCORE");
        return candidate;
    }

    private double detailedKeywordScore(Policy policy, List<String> keywords) {
        double score = 0;
        String title = safe(policy.getTitle());
        String body = String.join(" ", safe(policy.getSummary()), safe(policy.getSupportTarget()),
                safe(policy.getSelectionCriteria()), safe(policy.getApplicationMethod()), safe(policy.getCategoryName()));
        for (String keyword : keywords) {
            if (keyword == null || keyword.length() < 2) continue;
            if (title.contains(keyword)) score += 5.0;
            else if (body.contains(keyword)) score += 2.0;
        }
        return Math.min(score, 35.0);
    }

    private double intentPenalty(Policy policy, ExtractedUserCondition condition) {
        String text = String.join(" ", safe(policy.getTitle()), safe(policy.getSupportTarget()), safe(policy.getSelectionCriteria()));
        double penalty = 0;
        if (condition.employmentStatus() == null || !condition.employmentStatus().contains("창업")) {
            if (containsAny(text, "창업", "예비창업", "사업화", "창업자금", "청년상인")) penalty -= 15;
        }
        if (!condition.keywords().contains("농업인") && containsAny(text, "농업인 전용", "청년농업인 대상")) penalty -= 10;
        if (!condition.keywords().contains("어업인") && containsAny(text, "어업인 전용", "청년어업인 대상")) penalty -= 10;
        return penalty;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private String safe(String value) { return value == null ? "" : value; }
}
