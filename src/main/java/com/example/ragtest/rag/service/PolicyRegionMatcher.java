package com.example.ragtest.rag.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PolicyRegionMatcher {

    private static final Set<String> NATIONAL_NAMES = Set.of("전국", "전국공통", "전체", "공통");

    public boolean isApplicable(String requestRegion, String policyRegion) {
        if (!hasText(requestRegion)) {
            return true;
        }
        if (!hasText(policyRegion)) {
            return false;
        }

        String request = normalizeToken(requestRegion);
        String policy = normalizeToken(policyRegion);
        if (NATIONAL_NAMES.contains(policy)) {
            return true;
        }
        if (request.equals(policy)) {
            return true;
        }

        List<String> requestTokens = tokens(requestRegion);
        List<String> policyTokens = tokens(policyRegion);
        if (policyTokens.stream().anyMatch(NATIONAL_NAMES::contains)) {
            return true;
        }

        // A broader policy region (경기도) applies to a narrower request region
        // (경기도 수원시). A sibling region (경기도 성남시) must not match.
        if (policyTokens.size() == 1) {
            return requestTokens.stream()
                    .anyMatch(requestToken -> sameAdministrativeName(requestToken, policyTokens.get(0)));
        }
        return requestTokens.size() >= policyTokens.size() && tokensMatchByHierarchy(requestTokens, policyTokens);
    }

    private boolean tokensMatchByHierarchy(List<String> requestTokens, List<String> policyTokens) {
        for (int i = 0; i < policyTokens.size(); i++) {
            if (!sameAdministrativeName(requestTokens.get(i), policyTokens.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean sameAdministrativeName(String left, String right) {
        return left.equals(right) || removeAdministrativeSuffix(left).equals(removeAdministrativeSuffix(right));
    }

    private List<String> tokens(String value) {
        List<String> tokens = new ArrayList<>();
        for (String token : value.strip().split("\\s+")) {
            String normalized = normalizeToken(token);
            if (!normalized.isBlank()) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").strip();
    }

    private String removeAdministrativeSuffix(String value) {
        return value.replaceAll("(특별시|광역시|특별자치시|특별자치도|자치시|자치도|도|시|군|구)$", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
