package com.example.ragtest.rag.condition;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedUserConditionExtractor implements UserConditionExtractor {

    private static final Pattern AGE_PATTERN = Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*(?:살|세)");
    private static final Pattern DECADE_PATTERN = Pattern.compile("(\\d{2})대");

    private static final Map<String, List<String>> INTEREST_TERMS = new LinkedHashMap<>();

    static {
        INTEREST_TERMS.put("주거", List.of("주거", "월세", "전세", "임대", "보증금", "행복주택", "청년주택"));
        INTEREST_TERMS.put("교육", List.of("교육", "대학", "대학생", "대학원", "학생", "학자금", "장학금", "등록금", "대출이자", "국가장학금", "교육비"));
        INTEREST_TERMS.put("취업", List.of("취업", "구직", "일자리", "일경험", "면접", "면접수당", "직무", "훈련", "인턴"));
        INTEREST_TERMS.put("금융", List.of("금융", "자산", "저축", "적금", "도약계좌", "내일저축", "대출", "이자", "신용", "햇살론"));
        INTEREST_TERMS.put("창업", List.of("창업", "예비창업", "스타트업", "사업화", "창업자금"));
        INTEREST_TERMS.put("교통", List.of("교통", "교통비", "대중교통", "통학"));
        INTEREST_TERMS.put("문화", List.of("문화", "바우처", "여가", "활동지원"));
        INTEREST_TERMS.put("복지", List.of("복지", "건강", "상담"));
    }

    @Override
    public ExtractedUserCondition extract(String question) {
        String source = question == null ? "" : question.strip();
        String region = extractRegion(source);
        Integer age = extractAge(source);
        String educationStatus = extractEducationStatus(source);
        String employmentStatus = extractEmploymentStatus(source);
        String lifeStage = extractLifeStage(source);
        String economicStatus = extractEconomicStatus(source);
        String targetGroup = extractTargetGroup(source, educationStatus, lifeStage);
        List<String> interests = extractInterests(source);
        List<String> keywords = extractKeywords(source, region, age, targetGroup, educationStatus,
                employmentStatus, lifeStage, economicStatus, interests);
        return new ExtractedUserCondition(source, region, age, targetGroup, educationStatus,
                employmentStatus, lifeStage, economicStatus, interests, keywords);
    }

    private Integer extractAge(String question) {
        Matcher matcher = AGE_PATTERN.matcher(question);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private String extractRegion(String question) {
        if (containsAny(question, "경기도 수원시", "경기 수원시", "경기도 수원", "경기 수원")) return "경기도 수원시";
        if (containsAny(question, "경기도 성남시", "경기 성남시", "성남시", "성남")) return "경기도 성남시";
        if (containsAny(question, "경기도 고양시", "경기 고양시", "고양시", "고양")) return "경기도 고양시";
        if (containsAny(question, "경기도 용인시", "경기 용인시", "용인시", "용인")) return "경기도 용인시";
        if (containsAny(question, "수원시", "수원")) return "경기도 수원시";
        if (containsAny(question, "서울특별시", "서울시", "서울")) return "서울특별시";
        if (containsAny(question, "경기도", "경기")) return "경기도";
        if (containsAny(question, "부산광역시", "부산시", "부산")) return "부산광역시";
        if (containsAny(question, "인천광역시", "인천")) return "인천광역시";
        if (containsAny(question, "대구광역시", "대구")) return "대구광역시";
        if (containsAny(question, "대전광역시", "대전")) return "대전광역시";
        if (containsAny(question, "광주광역시", "광주")) return "광주광역시";
        if (containsAny(question, "울산광역시", "울산")) return "울산광역시";
        if (containsAny(question, "세종특별자치시", "세종시", "세종")) return "세종특별자치시";
        if (containsAny(question, "강원특별자치도", "강원도", "강원")) return "강원특별자치도";
        if (containsAny(question, "충청북도", "충북")) return "충청북도";
        if (containsAny(question, "충청남도", "충남")) return "충청남도";
        if (containsAny(question, "전북특별자치도", "전라북도", "전북")) return "전북특별자치도";
        if (containsAny(question, "전라남도", "전남")) return "전라남도";
        if (containsAny(question, "경상북도", "경북")) return "경상북도";
        if (containsAny(question, "경상남도", "경남")) return "경상남도";
        if (containsAny(question, "제주특별자치도", "제주도", "제주")) return "제주특별자치도";
        return null;
    }

    private String extractEducationStatus(String question) {
        if (containsAny(question, "대학원생", "대학원")) return "대학원생";
        if (containsAny(question, "졸업예정자", "졸업생")) return "졸업생";
        if (question.contains("휴학생")) return "휴학생";
        if (question.contains("재학생")) return "재학생";
        if (containsAny(question, "대학생", "대학교")) return "대학생";
        if (question.contains("학생")) return "학생";
        return null;
    }

    private String extractEmploymentStatus(String question) {
        if (containsAny(question, "창업 준비", "예비창업자", "예비 창업")) return "예비창업자";
        if (containsAny(question, "취업준비생", "취준생", "구직자", "구직 중")) return "취업준비생";
        if (containsAny(question, "미취업", "무직")) return "미취업";
        if (containsAny(question, "재직자", "직장인", "근로자", "회사원")) return "재직자";
        if (containsAny(question, "창업자", "사업자", "자영업자")) return "창업자";
        return null;
    }

    private String extractLifeStage(String question) {
        if (question.contains("자립준비청년")) return "자립준비청년";
        if (question.contains("신혼부부")) return "신혼부부";
        if (question.contains("사회초년생")) return "사회초년생";
        if (containsAny(question, "전역", "군복무", "군인")) return "군복무/전역";
        if (question.contains("1인가구") || question.contains("1인 가구")) return "1인가구";
        return null;
    }

    private String extractEconomicStatus(String question) {
        if (question.contains("기초생활수급")) return "기초생활수급자";
        if (question.contains("차상위")) return "차상위";
        if (containsAny(question, "저소득", "소득 낮은")) return "저소득";
        if (question.contains("금융취약")) return "금융취약";
        if (question.contains("무소득")) return "무소득";
        if (question.contains("수급자")) return "수급자";
        return null;
    }

    private String extractTargetGroup(String question, String educationStatus, String lifeStage) {
        if ("자립준비청년".equals(lifeStage)) return "자립준비청년";
        if ("신혼부부".equals(lifeStage)) return "신혼부부";
        if (question.contains("청소년")) return "청소년";
        if (containsAny(question, "청년", "20대", "사회초년생") || educationStatus != null) return "청년";
        return null;
    }

    private List<String> extractInterests(String question) {
        List<String> interests = new ArrayList<>();
        INTEREST_TERMS.forEach((category, terms) -> {
            if (terms.stream().anyMatch(question::contains)) interests.add(category);
        });
        return interests;
    }

    private List<String> extractKeywords(String question, String region, Integer age, String targetGroup,
                                         String educationStatus, String employmentStatus, String lifeStage,
                                         String economicStatus, List<String> interests) {
        Set<String> keywords = new LinkedHashSet<>();
        addIfText(keywords, region);
        if (age != null) keywords.add(age + "살");
        Matcher decade = DECADE_PATTERN.matcher(question);
        if (decade.find()) keywords.add(decade.group());
        addIfText(keywords, targetGroup);
        addIfText(keywords, educationStatus);
        addIfText(keywords, employmentStatus);
        addIfText(keywords, lifeStage);
        addIfText(keywords, economicStatus);
        interests.forEach(category -> {
            keywords.add(category);
            keywords.addAll(INTEREST_TERMS.getOrDefault(category, List.of()));
        });
        for (String term : List.of("청년", "학생", "대학생", "대학원생", "학자금", "장학금", "등록금", "월세",
                "지원금", "기본소득", "정책", "취업", "구직", "면접수당", "일자리", "창업", "금융", "주거")) {
            if (question.contains(term)) keywords.add(term);
        }
        if (keywords.isEmpty()) keywords.addAll(List.of("청년", "지원", "정책"));
        return new ArrayList<>(keywords);
    }

    private void addIfText(Set<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }
}
