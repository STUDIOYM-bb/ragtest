package com.example.ragtest.policy.filter;

import com.example.ragtest.policy.domain.Policy;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgePolicyMatcher {

    private static final Pattern RANGE_PATTERN = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*(?:이상|부터|~|-|–|에서)\\s*(?:만\\s*)?(\\d{1,2})\\s*세\\s*(?:이하|까지)?");
    private static final Pattern MIN_MAX_PATTERN = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*이상.*?(?:만\\s*)?(\\d{1,2})\\s*세\\s*이하");
    private static final Pattern MIN_PATTERN = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*이상");
    private static final Pattern MAX_PATTERN = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*이하");

    public boolean isApplicable(Integer age, Policy policy) {
        if (age == null) {
            return true;
        }
        String text = String.join(" ",
                safe(policy.getTitle()),
                safe(policy.getSummary()),
                safe(policy.getSupportTarget()),
                safe(policy.getSelectionCriteria()),
                safe(policy.getCategoryName()));

        Matcher minMaxMatcher = MIN_MAX_PATTERN.matcher(text);
        if (minMaxMatcher.find()) {
            return inRange(age, minMaxMatcher.group(1), minMaxMatcher.group(2));
        }

        Matcher rangeMatcher = RANGE_PATTERN.matcher(text);
        if (rangeMatcher.find()) {
            return inRange(age, rangeMatcher.group(1), rangeMatcher.group(2));
        }

        Matcher minMatcher = MIN_PATTERN.matcher(text);
        while (minMatcher.find()) {
            if (age < Integer.parseInt(minMatcher.group(1))) {
                return false;
            }
        }
        Matcher maxMatcher = MAX_PATTERN.matcher(text);
        while (maxMatcher.find()) {
            if (age > Integer.parseInt(maxMatcher.group(1))) {
                return false;
            }
        }
        return true;
    }

    private boolean inRange(Integer age, String min, String max) {
        int lower = Integer.parseInt(min);
        int upper = Integer.parseInt(max);
        return age >= Math.min(lower, upper) && age <= Math.max(lower, upper);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
