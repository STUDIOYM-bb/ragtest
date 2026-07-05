package com.example.ragtest.rag.condition;

import java.util.List;

public record ExtractedUserCondition(
        String originalQuestion,
        String region,
        Integer age,
        String targetGroup,
        String educationStatus,
        String employmentStatus,
        String lifeStage,
        String economicStatus,
        List<String> interestCategories,
        List<String> keywords
) {
    public ExtractedUserCondition {
        originalQuestion = originalQuestion == null ? "" : originalQuestion;
        interestCategories = interestCategories == null ? List.of() : List.copyOf(interestCategories);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public ExtractedUserCondition withOverrides(String region, Integer age, String employmentStatus) {
        return new ExtractedUserCondition(
                originalQuestion,
                hasText(region) ? region.strip() : this.region,
                age != null ? age : this.age,
                targetGroup,
                educationStatus,
                hasText(employmentStatus) ? employmentStatus.strip() : this.employmentStatus,
                lifeStage,
                economicStatus,
                interestCategories,
                keywords
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
