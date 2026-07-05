package com.example.ragtest.policy.match;

import java.util.List;

public record PolicyMatchResult(
        boolean eligible,
        boolean uncertain,
        List<String> matchedReasons,
        List<String> excludedReasons,
        List<String> cautionReasons
) {
    public PolicyMatchResult {
        matchedReasons = List.copyOf(matchedReasons);
        excludedReasons = List.copyOf(excludedReasons);
        cautionReasons = List.copyOf(cautionReasons);
    }
}
