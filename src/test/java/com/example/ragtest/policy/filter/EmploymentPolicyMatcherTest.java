package com.example.ragtest.policy.filter;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmploymentPolicyMatcherTest {

    private final EmploymentPolicyMatcher matcher = new EmploymentPolicyMatcher();

    @Test
    void excludesEmployedOnlyPolicyForJobSeeker() {
        Policy policy = policy("재직 청년 근속 지원", "중소기업 재직자를 지원합니다.");

        assertThat(matcher.isApplicable("취업준비생", policy)).isFalse();
    }

    @Test
    void keepsUniversalPolicyAndSkipsMissingCondition() {
        Policy policy = policy("청년 월세 지원", "청년의 주거비를 지원합니다.");

        assertThat(matcher.isApplicable("취업준비생", policy)).isTrue();
        assertThat(matcher.isApplicable(null, policy)).isTrue();
    }

    private Policy policy(String title, String summary) {
        Policy policy = Policy.create(PolicySourceType.PUBLIC_SERVICE, title, title);
        policy.updateFrom(title, summary, "", "", "", null, null, "전국", "지원", "https://example.com", "hash");
        return policy;
    }
}
