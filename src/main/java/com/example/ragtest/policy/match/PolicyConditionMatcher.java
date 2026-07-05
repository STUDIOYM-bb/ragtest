package com.example.ragtest.policy.match;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.example.ragtest.rag.condition.ExtractedUserCondition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PolicyConditionMatcher {

    private final AgePolicyMatcher agePolicyMatcher;
    private final RegionPolicyMatcher regionPolicyMatcher;
    private final TargetGroupPolicyMatcher targetGroupPolicyMatcher;
    private final InterestCategoryMatcher interestCategoryMatcher;

    public PolicyConditionMatcher(AgePolicyMatcher agePolicyMatcher, RegionPolicyMatcher regionPolicyMatcher,
                                  TargetGroupPolicyMatcher targetGroupPolicyMatcher,
                                  InterestCategoryMatcher interestCategoryMatcher) {
        this.agePolicyMatcher = agePolicyMatcher;
        this.regionPolicyMatcher = regionPolicyMatcher;
        this.targetGroupPolicyMatcher = targetGroupPolicyMatcher;
        this.interestCategoryMatcher = interestCategoryMatcher;
    }

    public PolicyMatchResult match(Policy policy, ExtractedUserCondition condition) {
        List<String> matched = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        List<String> cautions = new ArrayList<>();
        if (policy.getSourceType() == PolicySourceType.SAMPLE) excluded.add("SAMPLE_SOURCE");
        if (!policy.isYouthRelated()) excluded.add("NOT_YOUTH_RELATED");
        if (!policy.isIndexed()) excluded.add("NOT_INDEXED");

        apply(regionPolicyMatcher.match(condition.region(), policy), matched, excluded, cautions);
        apply(agePolicyMatcher.match(condition.age(), policy), matched, excluded, cautions);
        apply(targetGroupPolicyMatcher.match(condition, policy), matched, excluded, cautions);
        apply(interestCategoryMatcher.match(condition.interestCategories(), policy), matched, excluded, cautions);

        String text = policyText(policy);
        if (condition.lifeStage() != null) {
            if (text.contains(condition.lifeStage().replace("/", ""))) matched.add("LIFE_STAGE_MATCH");
            else cautions.add("생애단계 조건 추가 확인 필요");
        }
        if (condition.economicStatus() != null) {
            if (text.contains(condition.economicStatus())) matched.add("ECONOMIC_STATUS_MATCH");
            else cautions.add("경제상태 조건 추가 확인 필요");
        }
        return new PolicyMatchResult(excluded.isEmpty(), !cautions.isEmpty(), matched, excluded, cautions);
    }

    private void apply(MatchSignal signal, List<String> matched, List<String> excluded, List<String> cautions) {
        if (signal == null || signal.reason() == null) return;
        if (signal.excluded()) excluded.add(signal.reason());
        else if (signal.uncertain()) cautions.add(signal.reason());
        else if (signal.matched()) matched.add(signal.reason());
    }

    private String policyText(Policy policy) {
        return String.join(" ", safe(policy.getTitle()), safe(policy.getSummary()), safe(policy.getSupportTarget()),
                safe(policy.getSelectionCriteria()), safe(policy.getApplicationMethod()), safe(policy.getCategoryName()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
