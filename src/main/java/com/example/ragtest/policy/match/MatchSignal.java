package com.example.ragtest.policy.match;

public record MatchSignal(boolean matched, boolean excluded, boolean uncertain, String reason) {
    public static MatchSignal matched(String reason) {
        return new MatchSignal(true, false, false, reason);
    }

    public static MatchSignal excluded(String reason) {
        return new MatchSignal(false, true, false, reason);
    }

    public static MatchSignal uncertain(String reason) {
        return new MatchSignal(false, false, true, reason);
    }

    public static MatchSignal neutral() {
        return new MatchSignal(false, false, false, null);
    }
}
