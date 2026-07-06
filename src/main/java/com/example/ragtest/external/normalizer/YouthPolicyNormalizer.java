package com.example.ragtest.external.normalizer;

import com.example.ragtest.external.dto.ExternalPolicyRecord;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class YouthPolicyNormalizer implements PolicyNormalizer<JsonNode> {
    private static final Map<String, String> REGION_ALIASES = new LinkedHashMap<>();

    static {
        REGION_ALIASES.put("서울특별시", "서울특별시"); REGION_ALIASES.put("서울시", "서울특별시"); REGION_ALIASES.put("서울", "서울특별시");
        REGION_ALIASES.put("부산광역시", "부산광역시"); REGION_ALIASES.put("부산", "부산광역시");
        REGION_ALIASES.put("대구광역시", "대구광역시"); REGION_ALIASES.put("대구", "대구광역시");
        REGION_ALIASES.put("인천광역시", "인천광역시"); REGION_ALIASES.put("인천", "인천광역시");
        REGION_ALIASES.put("광주광역시", "광주광역시"); REGION_ALIASES.put("광주", "광주광역시");
        REGION_ALIASES.put("대전광역시", "대전광역시"); REGION_ALIASES.put("대전", "대전광역시");
        REGION_ALIASES.put("울산광역시", "울산광역시"); REGION_ALIASES.put("울산", "울산광역시");
        REGION_ALIASES.put("세종특별자치시", "세종특별자치시"); REGION_ALIASES.put("세종", "세종특별자치시");
        REGION_ALIASES.put("경기도", "경기도"); REGION_ALIASES.put("경기", "경기도");
        REGION_ALIASES.put("강원특별자치도", "강원특별자치도"); REGION_ALIASES.put("강원도", "강원특별자치도"); REGION_ALIASES.put("강원", "강원특별자치도");
        REGION_ALIASES.put("충청북도", "충청북도"); REGION_ALIASES.put("충북", "충청북도");
        REGION_ALIASES.put("충청남도", "충청남도"); REGION_ALIASES.put("충남", "충청남도");
        REGION_ALIASES.put("전북특별자치도", "전북특별자치도"); REGION_ALIASES.put("전라북도", "전북특별자치도"); REGION_ALIASES.put("전북", "전북특별자치도");
        REGION_ALIASES.put("전라남도", "전라남도"); REGION_ALIASES.put("전남", "전라남도");
        REGION_ALIASES.put("경상북도", "경상북도"); REGION_ALIASES.put("경북", "경상북도");
        REGION_ALIASES.put("경상남도", "경상남도"); REGION_ALIASES.put("경남", "경상남도");
        REGION_ALIASES.put("제주특별자치도", "제주특별자치도"); REGION_ALIASES.put("제주도", "제주특별자치도"); REGION_ALIASES.put("제주", "제주특별자치도");
    }

    @Override
    public Policy normalize(JsonNode source) {
        ExternalPolicyRecord record = normalizeToRecord(source);
        Policy policy = Policy.create(record.sourceType(), record.externalId(), record.title());
        policy.updateFrom(record.title(), record.summary(), record.supportTarget(), record.selectionCriteria(),
                record.applicationMethod(), record.applicationStartDate(), record.applicationEndDate(),
                record.regionName(), record.categoryName(), record.officialUrl(),
                Integer.toHexString(record.rawPayload().hashCode()));
        return policy;
    }

    public ExternalPolicyRecord normalizeToRecord(JsonNode source) {
        String rawTitle = text(source, "plcyNm", "정책명", "polyBizSjnm", "policyName", "title");
        String region = normalizeRegion(source);
        String id = first(
                text(source, "plcyNo", "정책ID", "정책번호", "bizId", "policyId", "id"),
                joinedId(text(source, "plcyNm", "정책명", "polyBizSjnm"),
                        text(source, "sprvsnInstCd", "주관기관코드", "sprvsnInstCdNm", "주관기관명",
                                "rgtrInstCd", "rgtrInstCdNm"))
        );
        if (!hasText(id)) {
            id = temporaryId(rawTitle, PolicySourceType.YOUTH_CENTER.name(), region);
        }
        String title = first(rawTitle, id);
        String ageTarget = ageRangeText(source);
        String supportTarget = joinUnique(
                text(source, "sprtTrgtCn", "지원대상", "지원대상내용", "sporScvl", "supportTarget"),
                text(source, "ageInfo"),
                ageTarget,
                text(source, "schoolCd"),
                text(source, "jobCd"),
                text(source, "mrgSttsCd")
        );
        if (!hasText(supportTarget)) supportTarget = "청년";
        String applicationPeriod = text(source, "aplyYmd", "rqutPrdCn", "aplyPrdCn", "bizPrdCn");
        String category = first(joinUnique(text(source, "lclsfNm", "정책대분류명"), text(source, "mclsfNm", "정책중분류명"),
                text(source, "plcyKywdNm", "정책키워드"), text(source, "polyBizSecd")), "청년정책");
        return new ExternalPolicyRecord(PolicySourceType.YOUTH_CENTER, id, title,
                first(text(source, "plcyExplnCn", "정책설명", "polyItcnCn", "plcySprtCn", "sporCn", "policyDescription"), title),
                supportTarget,
                first(text(source, "srngMthdCn", "선정기준", "selectionCriteria", "sprtTrgtCn", "지원대상"), supportTarget),
                first(text(source, "plcyAplyMthdCn", "신청방법", "rqutProcCn", "applicationMethod"), ""),
                parseStartDate(first(text(source, "aplyBgngYmd", "bizPrdBgngYmd"), applicationPeriod)),
                parseEndDate(first(text(source, "aplyEndYmd", "bizPrdEndYmd"), applicationPeriod)),
                region, category,
                first(text(source, "aplyUrlAddr", "신청URL", "상세URL", "rqutUrla", "refUrlAddr1"), "https://www.youthcenter.go.kr"),
                source.toString());
    }

    private LocalDate parseStartDate(String value) {
        return parseDate(value, false);
    }

    private LocalDate parseEndDate(String value) {
        return parseDate(value, true);
    }

    private LocalDate parseDate(String value, boolean last) {
        if (!hasText(value)) return null;
        String digits = value.replaceAll("[^0-9]", " ");
        List<String> candidates = new ArrayList<>();
        for (String part : digits.split("\\s+")) {
            if (part.length() >= 8) candidates.add(part.substring(0, 8));
        }
        if (candidates.isEmpty()) {
            String compact = value.replaceAll("[^0-9]", "");
            if (compact.length() >= 8) candidates.add(compact.substring(0, 8));
            if (compact.length() >= 16) candidates.add(compact.substring(8, 16));
        }
        if (candidates.isEmpty()) return null;
        String date = last ? candidates.getLast() : candidates.getFirst();
        try {
            return LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode source, String... names) {
        for (String name : names) {
            JsonNode value = source.get(name);
            String direct = nodeText(value);
            if (hasText(direct)) return direct;
            for (JsonNode found : source.findValues(name)) {
                String foundText = nodeText(found);
                if (hasText(foundText)) return foundText;
            }
        }
        return "";
    }

    private String nodeText(JsonNode value) {
        if (value == null || value.isNull()) return "";
        if (value.isTextual() || value.isNumber() || value.isBoolean()) return value.asText().strip();
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(child -> {
                String childText = nodeText(child);
                if (hasText(childText)) values.add(childText);
            });
            return joinUnique(values.toArray(String[]::new));
        }
        return "";
    }

    private String ageRangeText(JsonNode source) {
        String minAge = text(source, "sprtTrgtMinAge", "minAge", "ageMin");
        String maxAge = text(source, "sprtTrgtMaxAge", "maxAge", "ageMax");
        if (hasText(minAge) && hasText(maxAge)) {
            return "만 " + onlyDigits(minAge) + "세 이상 " + onlyDigits(maxAge) + "세 이하";
        }
        return first(minAge, text(source, "ageInfo"));
    }

    private String onlyDigits(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        return hasText(digits) ? digits : value;
    }

    private String normalizeRegion(JsonNode source) {
        String direct = first(text(source, "법정시군구명", "법정시군구코드", "regionName", "ctpvNm", "sggNm", "zipCd"));
        String extracted = extractRegion(direct);
        if (hasText(extracted)) return extracted;

        String agency = first(text(source, "sprvsnInstCdNm", "주관기관명", "주관기관코드", "주관기관담당자명",
                "rgtrInstCdNm", "operInstNm", "instNm"));
        extracted = extractRegion(agency);
        if (hasText(extracted)) return extracted;
        if (hasText(agency)) return "지역 확인 필요";
        return "전국";
    }

    private String extractRegion(String value) {
        if (!hasText(value)) return "";
        String normalized = value.strip();
        if (normalized.contains("전국") || normalized.contains("공통") || normalized.equals("전체")) return "전국";
        for (Map.Entry<String, String> entry : REGION_ALIASES.entrySet()) {
            if (normalized.contains(entry.getKey())) return entry.getValue();
        }
        return "";
    }

    private String first(String... values) {
        for (String value : values) if (hasText(value)) return value.strip();
        return "";
    }

    private String joinUnique(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (!hasText(value)) continue;
            String stripped = value.strip();
            if (!result.contains(stripped)) result.add(stripped);
        }
        return String.join(" / ", result);
    }

    private String joinedId(String title, String agencyCode) {
        if (!hasText(title) || !hasText(agencyCode)) return "";
        return title.strip() + "::" + agencyCode.strip();
    }

    private String temporaryId(String title, String sourceType, String regionName) {
        String key = String.join("::", first(title, "제목 확인 필요"), sourceType, first(regionName, "지역 확인 필요"));
        int hash = key.hashCode();
        return "temp-" + HexFormat.of().toHexDigits(hash);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
