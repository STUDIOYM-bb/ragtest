package com.example.ragtest.policy.filter;

import com.example.ragtest.policy.domain.Policy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class YouthPolicyFilter {

    private static final List<String> INCLUDE_KEYWORDS = List.of(
            "청년", "대학생", "취업준비생", "취준생", "사회초년생", "자립준비청년",
            "청년도약", "청년내일", "청년창업", "청년농업인", "청년어업인",
            "만 19세", "만 20세", "만 24세", "만 29세", "만 34세", "만 39세",
            "19세 이상", "18세 이상", "34세 이하", "39세 이하"
    );

    private static final List<String> EXCLUDE_KEYWORDS = List.of(
            "노숙인", "노인", "어르신", "영유아", "아동", "임산부", "장애인", "국가유공자",
            "보훈", "농업인", "어업인", "축산", "한부모가족", "다문화가족"
    );

    public boolean isYouthRelated(Policy policy) {
        String text = String.join(" ",
                safe(policy.getTitle()),
                safe(policy.getSummary()),
                safe(policy.getSupportTarget()),
                safe(policy.getSelectionCriteria()),
                safe(policy.getApplicationMethod()),
                safe(policy.getCategoryName()));
        if (INCLUDE_KEYWORDS.stream().anyMatch(text::contains)) {
            return true;
        }
        if (EXCLUDE_KEYWORDS.stream().anyMatch(text::contains)) {
            return false;
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
