package com.example.ragtest.policy.match;

import com.example.ragtest.policy.domain.Policy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class InterestCategoryMatcher {

    public static final Map<String, List<String>> CATEGORY_TERMS = new LinkedHashMap<>();

    static {
        CATEGORY_TERMS.put("교육", List.of("교육", "학자금", "장학금", "등록금", "대출이자", "국가장학금", "대학생", "학생"));
        CATEGORY_TERMS.put("주거", List.of("주거", "월세", "전세", "임대", "보증금", "행복주택", "청년주택"));
        CATEGORY_TERMS.put("취업", List.of("취업", "구직", "일자리", "일경험", "면접수당", "직무훈련", "인턴"));
        CATEGORY_TERMS.put("금융", List.of("금융", "자산", "저축", "적금", "도약계좌", "내일저축", "대출", "이자", "햇살론"));
        CATEGORY_TERMS.put("창업", List.of("창업", "예비창업", "스타트업", "사업화", "창업자금"));
        CATEGORY_TERMS.put("교통", List.of("교통비", "교통", "대중교통", "통학"));
        CATEGORY_TERMS.put("문화", List.of("문화", "바우처", "여가", "활동지원"));
        CATEGORY_TERMS.put("복지", List.of("복지", "건강", "상담"));
    }

    public MatchSignal match(List<String> categories, Policy policy) {
        if (categories == null || categories.isEmpty()) return MatchSignal.neutral();
        String text = String.join(" ", safe(policy.getTitle()), safe(policy.getSummary()), safe(policy.getSupportTarget()),
                safe(policy.getSelectionCriteria()), safe(policy.getApplicationMethod()), safe(policy.getCategoryName()));
        for (String category : categories) {
            if (CATEGORY_TERMS.getOrDefault(category, List.of()).stream().anyMatch(text::contains)) {
                return MatchSignal.matched("INTEREST_CATEGORY_MATCH:" + category);
            }
        }
        return MatchSignal.uncertain("관심분야 직접 일치 정보 없음");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
