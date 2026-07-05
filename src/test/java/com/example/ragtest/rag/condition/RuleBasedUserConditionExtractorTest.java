package com.example.ragtest.rag.condition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedUserConditionExtractorTest {

    private final RuleBasedUserConditionExtractor extractor = new RuleBasedUserConditionExtractor();

    @Test
    void extractsSuwonYouthJobSeekerCondition() {
        ExtractedUserCondition condition = extractor.extract("경기도 수원시에 사는 27살 취업준비생이 받을 수 있는 청년 정책 알려줘");

        assertThat(condition.region()).isEqualTo("경기도 수원시");
        assertThat(condition.age()).isEqualTo(27);
        assertThat(condition.employmentStatus()).isEqualTo("취업준비생");
        assertThat(condition.targetGroup()).isEqualTo("청년");
    }

    @Test
    void extractsSeoulStudentCondition() {
        ExtractedUserCondition condition = extractor.extract("서울에 사는 24살 대학생이 받을 수 있는 주거 지원 알려줘");

        assertThat(condition.region()).isEqualTo("서울특별시");
        assertThat(condition.age()).isEqualTo(24);
        assertThat(condition.employmentStatus()).isEqualTo("대학생");
    }
}
