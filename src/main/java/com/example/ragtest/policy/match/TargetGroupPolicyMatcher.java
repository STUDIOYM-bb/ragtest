package com.example.ragtest.policy.match;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.rag.condition.ExtractedUserCondition;
import org.springframework.stereotype.Component;

@Component
public class TargetGroupPolicyMatcher {

    public MatchSignal match(ExtractedUserCondition condition, Policy policy) {
        String text = text(policy);
        String education = condition.educationStatus();
        String employment = condition.employmentStatus();

        if (education != null && isExclusive(text, "재직자", "근로자", "직장인") && employment == null) {
            return MatchSignal.excluded("STATUS_MISMATCH");
        }
        if ("취업준비생".equals(employment) || "미취업".equals(employment)) {
            if (isExclusive(text, "재직자", "근로자", "직장인")) return MatchSignal.excluded("STATUS_MISMATCH");
            if (containsAny(text, "취업준비생", "미취업", "구직", "면접", "일자리")) return MatchSignal.matched("EMPLOYMENT_STATUS_MATCH");
        }
        if ("재직자".equals(employment)) {
            if (isExclusive(text, "미취업", "구직자")) return MatchSignal.excluded("STATUS_MISMATCH");
            if (containsAny(text, "재직자", "근로자", "직장인")) return MatchSignal.matched("EMPLOYMENT_STATUS_MATCH");
        }
        if ("예비창업자".equals(employment) && containsAny(text, "예비창업", "창업 준비", "사업화", "창업자금")) {
            return MatchSignal.matched("EMPLOYMENT_STATUS_MATCH");
        }
        if ("창업자".equals(employment) && containsAny(text, "창업자", "사업자", "자영업")) {
            return MatchSignal.matched("EMPLOYMENT_STATUS_MATCH");
        }
        if (education != null && containsAny(text, education, "대학생", "학생", "학자금", "장학금", "등록금")) {
            return MatchSignal.matched("EDUCATION_STATUS_MATCH");
        }
        if (condition.lifeStage() != null && text.contains(condition.lifeStage().replace("/", ""))) {
            return MatchSignal.matched("LIFE_STAGE_MATCH");
        }
        if (condition.targetGroup() != null && containsAny(text, condition.targetGroup(), "청년")) {
            return MatchSignal.matched("TARGET_GROUP_MATCH");
        }
        return condition.targetGroup() == null && education == null && employment == null
                ? MatchSignal.neutral() : MatchSignal.uncertain("대상 조건 추가 확인 필요");
    }

    private boolean isExclusive(String text, String... terms) {
        boolean contains = containsAny(text, terms);
        return contains && (text.contains("전용") || text.contains("대상") || text.contains("한함") || text.contains("만 지원"));
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private String text(Policy policy) {
        return String.join(" ", safe(policy.getTitle()), safe(policy.getSummary()), safe(policy.getSupportTarget()), safe(policy.getSelectionCriteria()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
