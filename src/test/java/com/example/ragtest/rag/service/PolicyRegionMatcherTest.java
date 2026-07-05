package com.example.ragtest.rag.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRegionMatcherTest {

    private final PolicyRegionMatcher matcher = new PolicyRegionMatcher();

    @Test
    void acceptsNationalProvinceAndMatchingCityPolicies() {
        assertThat(matcher.isApplicable("경기도 수원시", "전국")).isTrue();
        assertThat(matcher.isApplicable("경기도 수원시", "경기도")).isTrue();
        assertThat(matcher.isApplicable("경기도 수원시", "수원시")).isTrue();
        assertThat(matcher.isApplicable("경기도 수원시", "경기 수원")).isTrue();
    }

    @Test
    void rejectsAnotherCityInSameProvince() {
        assertThat(matcher.isApplicable("경기도 수원시", "경기도 성남시")).isFalse();
    }

    @Test
    void skipsRegionFilteringWhenQuestionHasNoRegion() {
        assertThat(matcher.isApplicable(null, "부산광역시")).isTrue();
    }
}
