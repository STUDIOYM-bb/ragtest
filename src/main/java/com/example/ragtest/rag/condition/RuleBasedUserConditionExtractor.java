package com.example.ragtest.rag.condition;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedUserConditionExtractor implements UserConditionExtractor {

    private static final Pattern AGE_PATTERN = Pattern.compile("(만\\s*)?(\\d{1,2})\\s*(살|세)");

    @Override
    public ExtractedUserCondition extract(String question) {
        String source = question == null ? "" : question;
        String region = extractRegion(source);
        Integer age = extractAge(source);
        String employmentStatus = extractEmploymentStatus(source);
        String targetGroup = extractTargetGroup(source);
        List<String> keywords = extractKeywords(source, employmentStatus, targetGroup);
        return new ExtractedUserCondition(source, region, age, employmentStatus, targetGroup, keywords);
    }

    private Integer extractAge(String question) {
        Matcher matcher = AGE_PATTERN.matcher(question);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }
        return null;
    }

    private String extractRegion(String question) {
        if (containsAny(question, "경기도 수원시", "경기 수원시", "경기도 수원", "경기 수원")) {
            return "경기도 수원시";
        }
        if (containsAny(question, "수원시")) {
            return "수원시";
        }
        if (containsAny(question, "수원")) {
            return "수원";
        }
        if (containsAny(question, "서울특별시", "서울시", "서울")) {
            return "서울특별시";
        }
        if (containsAny(question, "경기도", "경기")) {
            return "경기도";
        }
        if (containsAny(question, "부산광역시", "부산시", "부산")) {
            return "부산광역시";
        }
        if (containsAny(question, "대구광역시", "대구")) {
            return "대구광역시";
        }
        if (containsAny(question, "인천광역시", "인천")) {
            return "인천광역시";
        }
        if (containsAny(question, "광주광역시", "광주")) {
            return "광주광역시";
        }
        if (containsAny(question, "대전광역시", "대전")) {
            return "대전광역시";
        }
        if (containsAny(question, "울산광역시", "울산")) {
            return "울산광역시";
        }
        if (containsAny(question, "세종특별자치시", "세종시", "세종")) {
            return "세종특별자치시";
        }
        if (containsAny(question, "강원특별자치도", "강원도", "강원")) {
            return "강원특별자치도";
        }
        if (containsAny(question, "충청북도", "충북")) {
            return "충청북도";
        }
        if (containsAny(question, "충청남도", "충남")) {
            return "충청남도";
        }
        if (containsAny(question, "전북특별자치도", "전라북도", "전북")) {
            return "전북특별자치도";
        }
        if (containsAny(question, "전라남도", "전남")) {
            return "전라남도";
        }
        if (containsAny(question, "경상북도", "경북")) {
            return "경상북도";
        }
        if (containsAny(question, "경상남도", "경남")) {
            return "경상남도";
        }
        if (containsAny(question, "제주특별자치도", "제주도", "제주")) {
            return "제주특별자치도";
        }
        return null;
    }

    private String extractEmploymentStatus(String question) {
        if (containsAny(question, "취업준비생", "취준생", "구직자", "미취업", "무직")) {
            return "취업준비생";
        }
        if (containsAny(question, "재직자", "직장인", "근로자")) {
            return "재직자";
        }
        if (containsAny(question, "대학생", "재학생")) {
            return "대학생";
        }
        if (containsAny(question, "예비창업자", "창업")) {
            return "예비창업자";
        }
        return null;
    }

    private String extractTargetGroup(String question) {
        if (containsAny(question, "청년", "20대", "사회초년생", "대학생", "취준생", "취업준비생")) {
            return "청년";
        }
        return null;
    }

    private List<String> extractKeywords(String question, String employmentStatus, String targetGroup) {
        Set<String> keywords = new LinkedHashSet<>();
        if ("청년".equals(targetGroup)) {
            keywords.add("청년");
        }
        if (employmentStatus != null) {
            keywords.add(employmentStatus);
        }
        for (String keyword : List.of("취업", "구직", "주거", "월세", "지원", "정책", "창업", "농업인", "대학생")) {
            if (question.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        if (keywords.isEmpty()) {
            keywords.add("청년");
            keywords.add("지원");
            keywords.add("정책");
        }
        return new ArrayList<>(keywords);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
