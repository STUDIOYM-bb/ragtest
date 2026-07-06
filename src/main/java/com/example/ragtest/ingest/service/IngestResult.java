package com.example.ragtest.ingest.service;

public record IngestResult(
        int fetchedCount,
        int savedCount,
        int indexedCount,
        int skippedCount,
        String sourceType,
        String message
) {
    public IngestResult(int fetchedCount, int savedCount, int indexedCount, int skippedCount) {
        this(fetchedCount, savedCount, indexedCount, skippedCount, null, null);
    }

    public IngestResult(int savedCount, int indexedCount) {
        this(savedCount, savedCount, indexedCount, 0, null, null);
    }

    public IngestResult withIndexedCount(int indexedCount) {
        return new IngestResult(fetchedCount, savedCount, indexedCount, skippedCount, sourceType, message);
    }
}
