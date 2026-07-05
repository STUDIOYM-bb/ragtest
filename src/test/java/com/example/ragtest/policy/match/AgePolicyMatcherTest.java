package com.example.ragtest.policy.match;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgePolicyMatcherTest {
    private final AgePolicyMatcher matcher = new AgePolicyMatcher();

    @Test
    void excludesTwentyYearOldFromExactTwentyFourPolicy() {
        assertThat(matcher.match(20, policy("24세 청년에게 지급하는 청년기본소득")).excluded()).isTrue();
    }

    @Test
    void acceptsAgeInsideRangeAndDecade() {
        assertThat(matcher.match(27, policy("만 19세 이상 34세 이하 청년")).matched()).isTrue();
        assertThat(matcher.match(29, policy("20대 대상 지원")).matched()).isTrue();
    }

    private Policy policy(String target) {
        Policy policy = Policy.create(PolicySourceType.PUBLIC_SERVICE, target, "테스트 정책");
        policy.updateFrom("테스트 정책", "", target, "", "", null, null,
                "전국", "청년", "https://example.com", "hash");
        return policy;
    }
}
