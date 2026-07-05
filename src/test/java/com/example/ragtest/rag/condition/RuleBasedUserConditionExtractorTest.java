package com.example.ragtest.rag.condition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedUserConditionExtractorTest {
    private final RuleBasedUserConditionExtractor extractor = new RuleBasedUserConditionExtractor();

    @Test
    void extractsGyeonggiCollegeStudent() {
        ExtractedUserCondition condition = extractor.extract("경기도에 사는 20살 대학생이 받을 수 있는 청년 정책 알려줘");
        assertThat(condition.region()).isEqualTo("경기도");
        assertThat(condition.age()).isEqualTo(20);
        assertThat(condition.targetGroup()).isEqualTo("청년");
        assertThat(condition.educationStatus()).isEqualTo("대학생");
        assertThat(condition.interestCategories()).contains("교육");
    }

    @Test
    void extractsSuwonJobSeeker() {
        ExtractedUserCondition condition = extractor.extract("수원에 사는 27살 취업준비생이 받을 수 있는 지원금 알려줘");
        assertThat(condition.region()).isEqualTo("경기도 수원시");
        assertThat(condition.age()).isEqualTo(27);
        assertThat(condition.employmentStatus()).isEqualTo("취업준비생");
        assertThat(condition.interestCategories()).contains("취업");
    }

    @Test
    void extractsBusanWorkerFinance() {
        ExtractedUserCondition condition = extractor.extract("부산에 사는 30살 직장인이 받을 수 있는 금융 정책 알려줘");
        assertThat(condition.region()).isEqualTo("부산광역시");
        assertThat(condition.age()).isEqualTo(30);
        assertThat(condition.employmentStatus()).isEqualTo("재직자");
        assertThat(condition.interestCategories()).contains("금융");
    }

    @Test
    void extractsProspectiveFounder() {
        ExtractedUserCondition condition = extractor.extract("창업 준비 중인 28살 청년이 받을 수 있는 지원사업 알려줘");
        assertThat(condition.age()).isEqualTo(28);
        assertThat(condition.employmentStatus()).isEqualTo("예비창업자");
        assertThat(condition.interestCategories()).contains("창업");
    }
}
