package com.example.ragtest.ingest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalPolicyIngestServiceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void extractsOdcloudDataArray() throws Exception {
        JsonNode response = jsonMapper.readTree("""
                {"data":[{"서비스ID":"A1","서비스명":"청년 지원"}]}
                """);

        List<JsonNode> items = ExternalPolicyIngestService.items(response);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("서비스ID").asText()).isEqualTo("A1");
    }

    @Test
    void extractsWelfareItemFromNamedWrapper() throws Exception {
        JsonNode response = jsonMapper.readTree("""
                {"response":{"wantedList":{"servId":"W1","servNm":"청년 복지"}}}
                """);

        List<JsonNode> items = ExternalPolicyIngestService.items(response);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("servId").asText()).isEqualTo("W1");
    }

    @Test
    void extractsYouthCenterItemsFromPublicDataStyleWrapper() throws Exception {
        JsonNode response = jsonMapper.readTree("""
                {"response":{"body":{"items":{"item":[{"plcyNo":"Y1","plcyNm":"청년 월세 지원"}]}}}}
                """);

        List<JsonNode> items = ExternalPolicyIngestService.items(response);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("plcyNo").asText()).isEqualTo("Y1");
    }

    @Test
    void extractsYouthCenterItemsWhenLegacyTitleIsOnlyStableIdentifier() throws Exception {
        JsonNode response = jsonMapper.readTree("""
                {"youthPolicyList":[{"polyBizSjnm":"청년 취업 지원"}]}
                """);

        List<JsonNode> items = ExternalPolicyIngestService.items(response);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("polyBizSjnm").asText()).isEqualTo("청년 취업 지원");
    }
}
