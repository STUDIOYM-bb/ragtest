package com.example.ragtest.ingest.service;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.repository.PolicyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PolicyIndexingService {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 120;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    private final PolicyRepository policyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String openAiApiKey;
    private final String embeddingModel;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public PolicyIndexingService(
            PolicyRepository policyRepository,
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String embeddingModel
    ) {
        this.policyRepository = policyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.openAiApiKey = openAiApiKey;
        this.embeddingModel = embeddingModel;
    }

    @Transactional
    public int indexUnindexedPolicies() {
        requireOpenAiApiKey();
        List<Policy> policies = policyRepository.findAllByIndexedFalse();
        int indexedCount = 0;
        for (Policy policy : policies) {
            try {
                deleteExistingVectors(policy.getId());
                List<String> chunks = chunk(toContent(policy));
                for (int i = 0; i < chunks.size(); i++) {
                    String content = clean(chunks.get(i));
                    List<Double> embedding = createEmbedding(content);
                    insertVector(content, metadata(policy, i), embedding);
                }
                policy.markIndexed();
                indexedCount++;
            } catch (Exception exception) {
                throw new BusinessException("정책 인덱싱 실패 policyId=" + policy.getId()
                        + ", title=" + clean(policy.getTitle())
                        + ", cause=" + exception.getMessage());
            }
        }
        return indexedCount;
    }

    private void deleteExistingVectors(Long policyId) {
        jdbcTemplate.update("delete from vector_store where metadata ->> 'policyId' = ?", String.valueOf(policyId));
    }

    private void insertVector(String content, Map<String, Object> metadata, List<Double> embedding) throws Exception {
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String vectorLiteral = toVectorLiteral(embedding);
        jdbcTemplate.update(
                "insert into vector_store (content, metadata, embedding) values (?, ?::json, ?::vector)",
                content,
                metadataJson,
                vectorLiteral
        );
    }

    private List<Double> createEmbedding(String text) throws Exception {
        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", text
        );
        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/embeddings"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new BusinessException("OpenAI Embedding API 호출 실패: HTTP " + response.statusCode() + " - " + response.body());
        }
        JsonNode embeddingNode = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
        if (!embeddingNode.isArray()) {
            throw new BusinessException("OpenAI Embedding 응답에 embedding 배열이 없습니다.");
        }
        List<Double> embedding = new ArrayList<>(embeddingNode.size());
        embeddingNode.forEach(value -> embedding.add(value.asDouble()));
        return embedding;
    }

    private Map<String, Object> metadata(Policy policy, int chunkIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("policyId", policy.getId());
        metadata.put("sourceType", policy.getSourceType().name());
        metadata.put("externalId", clean(policy.getExternalId()));
        metadata.put("title", clean(policy.getTitle()));
        metadata.put("regionName", clean(policy.getRegionName()));
        metadata.put("categoryName", clean(policy.getCategoryName()));
        metadata.put("officialUrl", clean(policy.getOfficialUrl()));
        metadata.put("chunkIndex", chunkIndex);
        return metadata;
    }

    private String toContent(Policy policy) {
        return clean("""
                정책명: %s
                출처: %s
                요약: %s
                지원대상: %s
                선정기준: %s
                신청방법: %s
                신청기간: %s ~ %s
                지역: %s
                카테고리: %s
                공식 링크: %s
                """.formatted(
                safe(policy.getTitle()),
                policy.getSourceType().name(),
                safe(policy.getSummary()),
                safe(policy.getSupportTarget()),
                safe(policy.getSelectionCriteria()),
                safe(policy.getApplicationMethod()),
                policy.getApplicationStartDate() == null ? "" : policy.getApplicationStartDate().format(DATE_FORMATTER),
                policy.getApplicationEndDate() == null ? "" : policy.getApplicationEndDate().format(DATE_FORMATTER),
                safe(policy.getRegionName()),
                safe(policy.getCategoryName()),
                safe(policy.getOfficialUrl())
        ));
    }

    private List<String> chunk(String content) {
        if (content.length() <= CHUNK_SIZE) {
            return List.of(content);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + CHUNK_SIZE, content.length());
            chunks.add(content.substring(start, end));
            if (end == content.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private String toVectorLiteral(List<Double> embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding.get(i));
        }
        return builder.append(']').toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ")
                .replaceAll("[\\uD800-\\uDFFF]", " ")
                .replace('\u0000', ' ')
                .strip();
    }

    private void requireOpenAiApiKey() {
        if (openAiApiKey == null || openAiApiKey.isBlank() || "__OPENAI_API_KEY_NOT_SET__".equals(openAiApiKey)) {
            throw new BusinessException("OPENAI_API_KEY가 설정되지 않았습니다.");
        }
    }
}
