package com.example.ragtest.policy.match;

import com.example.ragtest.policy.domain.Policy;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("conditionAgePolicyMatcher")
public class AgePolicyMatcher {

    private static final Pattern MIN_MAX = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*이상\\s*(?:~|-|–|부터|에서)?\\s*(?:만\\s*)?(\\d{1,2})\\s*세\\s*이하");
    private static final Pattern RANGE = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*(?:~|-|–|부터|에서)\\s*(?:만\\s*)?(\\d{1,2})\\s*세(?:\\s*이하|\\s*까지)?");
    private static final Pattern DECADE = Pattern.compile("(\\d{2})대");
    private static final Pattern UNDER = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*미만");
    private static final Pattern MAX = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*이하");
    private static final Pattern MIN = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*이상");
    private static final Pattern SINGLE = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세(?:\\s*청년)?");

    public MatchSignal match(Integer age, Policy policy) {
        if (age == null) return MatchSignal.neutral();
        String text = policyText(policy);

        Matcher matcher = MIN_MAX.matcher(text);
        if (matcher.find()) return range(age, matcher.group(1), matcher.group(2));
        matcher = RANGE.matcher(text);
        if (matcher.find()) return range(age, matcher.group(1), matcher.group(2));
        matcher = DECADE.matcher(text);
        if (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            return age >= start && age <= start + 9
                    ? MatchSignal.matched("AGE_MATCH") : MatchSignal.excluded("AGE_MISMATCH");
        }
        matcher = UNDER.matcher(text);
        if (matcher.find()) return age < Integer.parseInt(matcher.group(1))
                ? MatchSignal.matched("AGE_MATCH") : MatchSignal.excluded("AGE_MISMATCH");
        matcher = MAX.matcher(text);
        if (matcher.find()) return age <= Integer.parseInt(matcher.group(1))
                ? MatchSignal.matched("AGE_MATCH") : MatchSignal.excluded("AGE_MISMATCH");
        matcher = MIN.matcher(text);
        if (matcher.find()) return age >= Integer.parseInt(matcher.group(1))
                ? MatchSignal.matched("AGE_MATCH") : MatchSignal.excluded("AGE_MISMATCH");
        matcher = SINGLE.matcher(text);
        if (matcher.find()) {
            int exact = Integer.parseInt(matcher.group(1));
            return age == exact ? MatchSignal.matched("AGE_MATCH") : MatchSignal.excluded("AGE_MISMATCH");
        }
        if (containsAny(text, "청년", "대학생", "학생")) return MatchSignal.matched("AGE_BROADLY_APPLICABLE");
        return MatchSignal.uncertain("나이 조건 추가 확인 필요");
    }

    private MatchSignal range(int age, String lowerValue, String upperValue) {
        int lower = Integer.parseInt(lowerValue);
        int upper = Integer.parseInt(upperValue);
        boolean applies = age >= Math.min(lower, upper) && age <= Math.max(lower, upper);
        return applies ? MatchSignal.matched("AGE_MATCH") : MatchSignal.excluded("AGE_MISMATCH");
    }

    private String policyText(Policy policy) {
        return String.join(" ", safe(policy.getTitle()), safe(policy.getSummary()), safe(policy.getSupportTarget()),
                safe(policy.getSelectionCriteria()), safe(policy.getCategoryName()));
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
