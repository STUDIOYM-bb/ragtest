package com.example.ragtest.policy.match;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionPolicyMatcherTest {
    private final RegionPolicyMatcher matcher = new RegionPolicyMatcher();

    @Test
    void provinceRequestIncludesItsCities() {
        assertThat(matcher.match("경기도", policy("경기도 수원시")).matched()).isTrue();
    }

    @Test
    void cityRequestIncludesNationAndParentButExcludesSibling() {
        assertThat(matcher.match("수원", policy("전국")).matched()).isTrue();
        assertThat(matcher.match("수원", policy("경기도")).matched()).isTrue();
        assertThat(matcher.match("수원", policy("경기도 성남시")).excluded()).isTrue();
    }

    private Policy policy(String region) {
        Policy policy = Policy.create(PolicySourceType.PUBLIC_SERVICE, region, "테스트 정책");
        policy.updateFrom("테스트 정책", "청년 지원", "청년", "", "", null, null,
                region, "청년", "https://example.com", "hash");
        return policy;
    }
}
