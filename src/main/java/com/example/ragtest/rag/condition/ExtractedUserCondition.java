package com.example.ragtest.rag.condition;

import java.util.List;

public record ExtractedUserCondition(
        String originalQuestion,
        String region,
        Integer age,
        String employmentStatus,
        String targetGroup,
        List<String> keywords
) {
    public ExtractedUserCondition withOverrides(String region, Integer age, String employmentStatus) {
        return new ExtractedUserCondition(
                originalQuestion,
                hasText(region) ? region.strip() : this.region,
                age != null ? age : this.age,
                hasText(employmentStatus) ? employmentStatus.strip() : this.employmentStatus,
                targetGroup,
                keywords
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
