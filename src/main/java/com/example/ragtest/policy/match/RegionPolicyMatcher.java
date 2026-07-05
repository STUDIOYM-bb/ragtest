package com.example.ragtest.policy.match;

import com.example.ragtest.policy.domain.Policy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class RegionPolicyMatcher {

    private static final Set<String> NATIONAL = Set.of("전국", "전국공통", "전체", "공통");
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("수원", "경기도 수원시"); ALIASES.put("수원시", "경기도 수원시");
        ALIASES.put("성남", "경기도 성남시"); ALIASES.put("성남시", "경기도 성남시");
        ALIASES.put("고양", "경기도 고양시"); ALIASES.put("고양시", "경기도 고양시");
        ALIASES.put("용인", "경기도 용인시"); ALIASES.put("용인시", "경기도 용인시");
        ALIASES.put("서울", "서울특별시"); ALIASES.put("서울시", "서울특별시");
        ALIASES.put("부산", "부산광역시"); ALIASES.put("인천", "인천광역시");
        ALIASES.put("대구", "대구광역시"); ALIASES.put("대전", "대전광역시");
        ALIASES.put("광주", "광주광역시"); ALIASES.put("울산", "울산광역시");
        ALIASES.put("경기", "경기도");
    }

    public MatchSignal match(String requestRegion, Policy policy) {
        if (!hasText(requestRegion)) return MatchSignal.neutral();
        if (!hasText(policy.getRegionName())) return MatchSignal.uncertain("지역 조건 추가 확인 필요");
        String request = canonical(requestRegion);
        String policyRegion = canonical(policy.getRegionName());
        if (NATIONAL.contains(policyRegion.replace(" ", ""))) return MatchSignal.matched("REGION_NATIONWIDE");
        if (request.equals(policyRegion)) return MatchSignal.matched("REGION_MATCH");

        String requestProvince = province(request);
        String policyProvince = province(policyRegion);
        if (requestProvince != null && policyProvince != null && !requestProvince.equals(policyProvince)) {
            return MatchSignal.excluded("REGION_MISMATCH");
        }
        boolean requestIsProvince = requestProvince != null && request.equals(requestProvince);
        boolean policyIsProvince = policyProvince != null && policyRegion.equals(policyProvince);
        if (requestIsProvince && request.equals(policyProvince)) return MatchSignal.matched("REGION_SUBREGION_MATCH");
        if (policyIsProvince && policyRegion.equals(requestProvince)) return MatchSignal.matched("REGION_PARENT_MATCH");
        if (requestProvince != null && requestProvince.equals(policyProvince)) {
            return request.equals(policyRegion)
                    ? MatchSignal.matched("REGION_MATCH") : MatchSignal.excluded("REGION_MISMATCH");
        }
        return MatchSignal.uncertain("지역 조건 추가 확인 필요");
    }

    private String canonical(String value) {
        String stripped = value.strip().replaceAll("\\s+", " ");
        return ALIASES.getOrDefault(stripped, stripped);
    }

    private String province(String value) {
        for (String province : Set.of("경기도", "서울특별시", "부산광역시", "인천광역시", "대구광역시",
                "대전광역시", "광주광역시", "울산광역시", "세종특별자치시", "강원특별자치도",
                "충청북도", "충청남도", "전북특별자치도", "전라남도", "경상북도", "경상남도", "제주특별자치도")) {
            if (value.startsWith(province)) return province;
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
