package com.example.ragtest.policy.filter;

import com.example.ragtest.policy.domain.Policy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class YouthPolicyFilter {

    private static final List<String> INCLUDE_KEYWORDS = List.of(
            "청년", "청소년", "대학생", "학생", "대학원생", "재학생", "휴학생", "졸업생",
            "취업준비생", "취준생", "구직", "미취업", "사회초년생", "신혼부부", "자립준비청년",
            "청년도약", "청년내일", "면접수당", "월세", "주거", "창업", "예비창업",
            "학자금", "장학금", "등록금", "대출이자", "국가장학금",
            "만 19세", "만 20세", "만 24세", "만 29세", "만 34세", "만 39세",
            "19세 이상", "34세 이하", "39세 이하"
    );

    private static final List<String> EXCLUDE_KEYWORDS = List.of(
            "노숙인", "노인", "어르신", "영유아", "아동", "임산부", "국가유공자",
            "보훈", "농업인", "어업인", "축산"
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
