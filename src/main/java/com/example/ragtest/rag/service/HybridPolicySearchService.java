package com.example.ragtest.rag.service;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.example.ragtest.policy.repository.PolicyRepository;
import com.example.ragtest.rag.condition.ExtractedUserCondition;
import com.example.ragtest.rag.condition.UserConditionExtractor;
import com.example.ragtest.rag.ranking.HybridSearchResult;
import com.example.ragtest.rag.ranking.PolicyRelevanceScorer;
import com.example.ragtest.rag.ranking.PolicySearchCandidate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HybridPolicySearchService {

    private final JdbcTemplate jdbcTemplate;
    private final PolicyRepository policyRepository;
    private final UserConditionExtractor conditionExtractor;
    private final PolicyRelevanceScorer relevanceScorer;
    private final String openAiApiKey;
    private final String embeddingModel;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public HybridPolicySearchService(
            JdbcTemplate jdbcTemplate,
            PolicyRepository policyRepository,
            UserConditionExtractor conditionExtractor,
            PolicyRelevanceScorer relevanceScorer,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String embeddingModel
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.policyRepository = policyRepository;
        this.conditionExtractor = conditionExtractor;
        this.relevanceScorer = relevanceScorer;
        this.openAiApiKey = openAiApiKey;
        this.embeddingModel = embeddingModel;
    }

    @Transactional(readOnly = true)
    public HybridSearchResult search(String question, int topK, String regionOverride, Integer ageOverride,
                                     String employmentOverride) {
        ExtractedUserCondition condition = conditionExtractor.extract(question)
                .withOverrides(regionOverride, ageOverride, employmentOverride);
        int effectiveTopK = Math.max(1, Math.min(topK, 100));
        int candidateTopK = Math.min(effectiveTopK * 20, 100);

        List<PolicySearchCandidate> vectorCandidates = vectorCandidates(condition, candidateTopK);
        List<PolicySearchCandidate> keywordCandidates = keywordCandidates(condition, candidateTopK);
        List<PolicySearchCandidate> merged = merge(vectorCandidates, keywordCandidates);
        merged.forEach(candidate -> relevanceScorer.score(candidate, condition));
        vectorCandidates.forEach(candidate -> relevanceScorer.score(candidate, condition));
        keywordCandidates.forEach(candidate -> relevanceScorer.score(candidate, condition));
        merged.sort((left, right) -> Double.compare(right.getFinalScore(), left.getFinalScore()));

        List<PolicySearchCandidate> excluded = merged.stream()
                .filter(candidate -> !candidate.getExcludedReasons().isEmpty())
                .toList();
        List<PolicySearchCandidate> finalCandidates = merged.stream()
                .filter(candidate -> candidate.getExcludedReasons().isEmpty())
                .limit(effectiveTopK)
                .toList();
        return new HybridSearchResult(condition, vectorCandidates, keywordCandidates,
                List.copyOf(merged), finalCandidates, excluded);
    }

    private List<PolicySearchCandidate> vectorCandidates(ExtractedUserCondition condition, int limit) {
        requireOpenAiApiKey();
        try {
            String vector = toVectorLiteral(createEmbedding(buildQuery(condition)));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    select policy_id, max(similarity) as similarity
                    from (
                        select metadata ->> 'policyId' as policy_id,
                               1 - (embedding <=> ?::vector) as similarity
                        from vector_store
                        where coalesce(metadata ->> 'sourceType', '') <> ?
                          and coalesce(metadata ->> 'youthRelated', 'false') = 'true'
                          and coalesce(metadata ->> 'indexed', 'false') = 'true'
                    ) ranked
                    where policy_id is not null
                    group by policy_id
                    order by similarity desc
                    limit ?
                    """, vector, PolicySourceType.SAMPLE.name(), limit);
            Map<Long, Policy> policies = policiesById(rows.stream()
                    .map(row -> parseId(row.get("policy_id"))).filter(id -> id != null).toList());
            List<PolicySearchCandidate> candidates = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Long policyId = parseId(row.get("policy_id"));
                Policy policy = policies.get(policyId);
                if (policy == null || policy.getSourceType() == PolicySourceType.SAMPLE
                        || !policy.isYouthRelated() || !policy.isIndexed()) continue;
                PolicySearchCandidate candidate = new PolicySearchCandidate(policy);
                candidate.markVector(number(row.get("similarity")));
                candidates.add(candidate);
            }
            return candidates;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("벡터 후보 검색 실패: " + exception.getMessage());
        }
    }

    private List<PolicySearchCandidate> keywordCandidates(ExtractedUserCondition condition, int limit) {
        Set<String> keywords = searchKeywords(condition);
        Map<Long, PolicySearchCandidate> candidates = new LinkedHashMap<>();
        for (String keyword : keywords.stream().limit(30).toList()) {
            if (keyword.length() < 2) continue;
            List<Policy> found = policyRepository.searchIndexedYouthPoliciesByKeyword(
                    PolicySourceType.SAMPLE, keyword, PageRequest.of(0, limit));
            for (Policy policy : found) {
                PolicySearchCandidate candidate = candidates.computeIfAbsent(policy.getId(), id -> new PolicySearchCandidate(policy));
                double score = safe(policy.getTitle()).contains(keyword) ? 3.0 : 1.0;
                candidate.markKeyword(keyword, score);
            }
        }
        return candidates.values().stream()
                .sorted((left, right) -> Double.compare(right.getKeywordScore(), left.getKeywordScore()))
                .limit(limit)
                .toList();
    }

    private List<PolicySearchCandidate> merge(List<PolicySearchCandidate> vector, List<PolicySearchCandidate> keyword) {
        Map<Long, PolicySearchCandidate> merged = new LinkedHashMap<>();
        for (PolicySearchCandidate candidate : vector) merged.put(candidate.getPolicy().getId(), candidate.copy());
        for (PolicySearchCandidate candidate : keyword) {
            merged.compute(candidate.getPolicy().getId(), (id, existing) -> {
                if (existing == null) return candidate.copy();
                existing.merge(candidate);
                return existing;
            });
        }
        return new ArrayList<>(merged.values());
    }

    private Map<Long, Policy> policiesById(List<Long> ids) {
        Map<Long, Policy> result = new HashMap<>();
        policyRepository.findAllById(ids).forEach(policy -> result.put(policy.getId(), policy));
        return result;
    }

    private Set<String> searchKeywords(ExtractedUserCondition condition) {
        Set<String> keywords = new LinkedHashSet<>(condition.keywords());
        add(keywords, condition.region()); add(keywords, condition.targetGroup());
        add(keywords, condition.educationStatus()); add(keywords, condition.employmentStatus());
        add(keywords, condition.lifeStage()); add(keywords, condition.economicStatus());
        keywords.addAll(condition.interestCategories());
        return keywords;
    }

    private String buildQuery(ExtractedUserCondition condition) {
        return String.join("\n",
                condition.originalQuestion(),
                "지역 " + safe(condition.region()),
                "나이 " + (condition.age() == null ? "" : condition.age()),
                "대상 " + safe(condition.targetGroup()),
                "학업 " + safe(condition.educationStatus()),
                "취업 " + safe(condition.employmentStatus()),
                "생애단계 " + safe(condition.lifeStage()),
                "경제상태 " + safe(condition.economicStatus()),
                "관심분야 " + String.join(" ", condition.interestCategories()),
                "키워드 " + String.join(" ", condition.keywords()));
    }

    private List<Double> createEmbedding(String text) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("model", embeddingModel, "input", text));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/embeddings"))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json; charset=utf-8").build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) throw new BusinessException("OpenAI Embedding API 호출 실패: HTTP " + response.statusCode());
        JsonNode values = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
        if (!values.isArray()) throw new BusinessException("OpenAI Embedding 응답에 embedding 배열이 없습니다.");
        List<Double> embedding = new ArrayList<>(values.size());
        values.forEach(value -> embedding.add(value.asDouble()));
        return embedding;
    }

    private String toVectorLiteral(List<Double> embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.size(); index++) {
            if (index > 0) builder.append(',');
            builder.append(embedding.get(index));
        }
        return builder.append(']').toString();
    }

    private Long parseId(Object value) {
        try { return value == null ? null : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private void add(Set<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private String safe(String value) { return value == null ? "" : value; }

    private void requireOpenAiApiKey() {
        if (openAiApiKey == null || openAiApiKey.isBlank()) throw new BusinessException("OPENAI_API_KEY가 설정되지 않았습니다.");
    }
}
