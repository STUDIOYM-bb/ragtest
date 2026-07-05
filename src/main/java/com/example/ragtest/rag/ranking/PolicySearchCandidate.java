package com.example.ragtest.rag.ranking;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.match.PolicyMatchResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PolicySearchCandidate {

    private final Policy policy;
    private boolean fromVector;
    private boolean fromKeyword;
    private double vectorScore;
    private double keywordScore;
    private double conditionScore;
    private double finalScore;
    private final Set<String> matchedKeywords = new LinkedHashSet<>();
    private List<String> matchedReasons = List.of();
    private List<String> cautionReasons = List.of();
    private List<String> excludedReasons = List.of();

    public PolicySearchCandidate(Policy policy) {
        this.policy = policy;
    }

    public PolicySearchCandidate copy() {
        PolicySearchCandidate copy = new PolicySearchCandidate(policy);
        copy.fromVector = fromVector;
        copy.fromKeyword = fromKeyword;
        copy.vectorScore = vectorScore;
        copy.keywordScore = keywordScore;
        copy.matchedKeywords.addAll(matchedKeywords);
        return copy;
    }

    public void merge(PolicySearchCandidate other) {
        this.fromVector |= other.fromVector;
        this.fromKeyword |= other.fromKeyword;
        this.vectorScore = Math.max(this.vectorScore, other.vectorScore);
        this.keywordScore = Math.max(this.keywordScore, other.keywordScore);
        this.matchedKeywords.addAll(other.matchedKeywords);
    }

    public void applyMatch(PolicyMatchResult matchResult) {
        this.matchedReasons = matchResult.matchedReasons();
        this.cautionReasons = matchResult.cautionReasons();
        this.excludedReasons = matchResult.excludedReasons();
    }

    public Policy getPolicy() { return policy; }
    public boolean isFromVector() { return fromVector; }
    public boolean isFromKeyword() { return fromKeyword; }
    public double getVectorScore() { return vectorScore; }
    public double getKeywordScore() { return keywordScore; }
    public double getConditionScore() { return conditionScore; }
    public double getFinalScore() { return finalScore; }
    public List<String> getMatchedKeywords() { return List.copyOf(matchedKeywords); }
    public List<String> getMatchedReasons() { return matchedReasons; }
    public List<String> getCautionReasons() { return cautionReasons; }
    public List<String> getExcludedReasons() { return excludedReasons; }

    public void markVector(double similarity) {
        this.fromVector = true;
        this.vectorScore = Math.max(this.vectorScore, Math.max(0, similarity));
    }

    public void markKeyword(String keyword, double score) {
        this.fromKeyword = true;
        this.matchedKeywords.add(keyword);
        this.keywordScore += score;
    }

    public void setConditionScore(double conditionScore) { this.conditionScore = conditionScore; }
    public void setFinalScore(double finalScore) { this.finalScore = finalScore; }
    public void addExcludedReason(String reason) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>(excludedReasons);
        reasons.add(reason);
        excludedReasons = List.copyOf(reasons);
    }
}
