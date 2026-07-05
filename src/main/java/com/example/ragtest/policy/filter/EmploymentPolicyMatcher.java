package com.example.ragtest.policy.filter;

import com.example.ragtest.policy.domain.Policy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmploymentPolicyMatcher {

    private static final List<String> JOB_SEEKER_TERMS = List.of("취업준비생", "취준생", "미취업", "구직자", "구직 청년");
    private static final List<String> EMPLOYED_TERMS = List.of("재직자", "재직 청년", "근로자", "직장인");

    public boolean isApplicable(String employmentStatus, Policy policy) {
        if (employmentStatus == null || employmentStatus.isBlank()) {
            return true;
        }
        String text = String.join(" ",
                safe(policy.getTitle()),
                safe(policy.getSummary()),
                safe(policy.getSupportTarget()),
                safe(policy.getSelectionCriteria()));

        boolean jobSeekerOnly = containsAny(text, JOB_SEEKER_TERMS) && !containsAny(text, EMPLOYED_TERMS);
        boolean employedOnly = containsAny(text, EMPLOYED_TERMS) && !containsAny(text, JOB_SEEKER_TERMS);

        return switch (employmentStatus.strip()) {
            case "취업준비생" -> !employedOnly;
            case "재직자" -> !jobSeekerOnly;
            default -> true;
        };
    }

    private boolean containsAny(String text, List<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
