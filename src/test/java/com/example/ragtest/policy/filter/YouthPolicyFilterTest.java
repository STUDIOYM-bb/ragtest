package com.example.ragtest.policy.filter;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YouthPolicyFilterTest {

    private final YouthPolicyFilter filter = new YouthPolicyFilter();

    @Test
    void keepsYouthFarmerPolicyEvenWithExcludedFarmerKeyword() {
        Policy policy = Policy.create(PolicySourceType.SAMPLE, "youth-farmer", "청년 농업인 지원");
        policy.updateFrom("청년 농업인 지원", "청년 농업인을 위한 창업 지원", "", "", "", null, null, "전국", "창업", "https://example.com", "hash");

        assertThat(filter.isYouthRelated(policy)).isTrue();
    }

    @Test
    void excludesHomelessPolicyWithoutYouthKeyword() {
        Policy policy = Policy.create(PolicySourceType.SAMPLE, "homeless", "노숙인 지원");
        policy.updateFrom("노숙인 지원", "노숙인을 위한 복지 서비스", "", "", "", null, null, "전국", "복지", "https://example.com", "hash");

        assertThat(filter.isYouthRelated(policy)).isFalse();
    }

    @Test
    void doesNotTreatEveryStartupPolicyAsYouthPolicy() {
        Policy policy = Policy.create(PolicySourceType.PUBLIC_SERVICE, "startup", "일반 창업 지원");
        policy.updateFrom("일반 창업 지원", "예비창업자에게 사업화를 지원", "전 연령", "", "", null, null,
                "전국", "창업", "https://example.com", "hash");

        assertThat(filter.isYouthRelated(policy)).isFalse();
    }
}
