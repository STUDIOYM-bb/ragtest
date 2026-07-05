package com.example.ragtest.ingest.service;

public record IngestResult(
        int fetchedCount,
        int savedCount,
        int indexedCount,
        int skippedCount
) {
    public IngestResult(int savedCount, int indexedCount) {
        this(savedCount, savedCount, indexedCount, 0);
    }

    public IngestResult withIndexedCount(int indexedCount) {
        return new IngestResult(fetchedCount, savedCount, indexedCount, skippedCount);
    }
}
