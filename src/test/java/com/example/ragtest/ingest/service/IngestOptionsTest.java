package com.example.ragtest.ingest.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestOptionsTest {

    @Test
    void usesLightweightDefaults() {
        assertThat(IngestOptions.defaults()).isEqualTo(new IngestOptions(3, 50, 150));
    }

    @Test
    void clampsValuesToAllowedRange() {
        assertThat(new IngestOptions(99, 999, 999)).isEqualTo(new IngestOptions(5, 100, 300));
        assertThat(new IngestOptions(0, 0, 0)).isEqualTo(new IngestOptions(1, 1, 1));
    }
}
