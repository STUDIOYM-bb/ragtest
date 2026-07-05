package com.example.ragtest.ingest.service;

public record IngestOptions(int maxPages, int pageSize, int maxItems) {

    public static final int DEFAULT_MAX_PAGES = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_MAX_ITEMS = 50;

    public IngestOptions {
        maxPages = clamp(maxPages, 1, 5);
        pageSize = clamp(pageSize, 1, 100);
        maxItems = clamp(maxItems, 1, 300);
    }

    public static IngestOptions defaults() {
        return new IngestOptions(DEFAULT_MAX_PAGES, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ITEMS);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
