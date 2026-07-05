package com.example.ragtest.rag.service;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.example.ragtest.policy.filter.AgePolicyMatcher;
import com.example.ragtest.policy.filter.EmploymentPolicyMatcher;
import com.example.ragtest.policy.repository.PolicyRepository;
import com.example.ragtest.rag.condition.ExtractedUserCondition;
import com.example.ragtest.rag.condition.UserConditionExtractor;
import com.example.ragtest.rag.dto.RagAskRequest;
import com.example.ragtest.rag.dto.RagAskResponse;
import com.example.ragtest.rag.dto.RagSourceResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RagService {

    private static final String NO_COLLECTED_YOUTH_DATA =
            "아직 수집된 청년정책 데이터가 없습니다. 관리자 테스트에서 실제 공공데이터 API 수집 및 인덱싱을 먼저 실행해주세요.";
    private static final String NO_INDEXED_YOUTH_DATA =
            "청년정책 데이터는 저장되어 있지만 아직 인덱싱되지 않았습니다. 관리자 테스트에서 실제 공공데이터 API 인덱싱을 먼저 실행해주세요.";
    private static final String NO_VECTOR_RESULT =
            "현재 인덱싱된 실제 정책 데이터에서 질문과 관련된 청년 지원 정책을 찾지 못했습니다.";
    private static final String NO_FILTERED_RESULT =
            "현재 수집된 실제 정책 데이터 중 입력하신 조건에 맞는 청년 지원 정책을 찾지 못했습니다.";
    private static final String SYSTEM_PROMPT = """
            너는 대한민국 청년정책/복지정책 안내 도우미다.
            반드시 제공된 CONTEXT 안의 정책 정보만 근거로 답변한다.
            CONTEXT에 없는 정책은 추천하지 않는다.
            사용자의 지역, 나이, 취업상태 조건과 맞지 않는 정책은 추천하지 않는다.
            답변에는 정책명, 관련 이유, 지원대상 요약, 신청방법, 신청기간, 공식 링크를 포함한다.
            정확한 신청 가능 여부는 공식 링크에서 재확인하라고 안내한다.
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PolicyRepository policyRepository;
    private final PolicyRegionMatcher policyRegionMatcher;
    private final UserConditionExtractor userConditionExtractor;
    private final AgePolicyMatcher agePolicyMatcher;
    private final EmploymentPolicyMatcher employmentPolicyMatcher;
    private final String openAiApiKey;
    private final String chatModel;
    private final String embeddingModel;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public RagService(
            JdbcTemplate jdbcTemplate,
            PolicyRepository policyRepository,
            PolicyRegionMatcher policyRegionMatcher,
            UserConditionExtractor userConditionExtractor,
            AgePolicyMatcher agePolicyMatcher,
            EmploymentPolicyMatcher employmentPolicyMatcher,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4.1-mini}") String chatModel,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String embeddingModel
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.policyRepository = policyRepository;
        this.policyRegionMatcher = policyRegionMatcher;
        this.userConditionExtractor = userConditionExtractor;
        this.agePolicyMatcher = agePolicyMatcher;
        this.employmentPolicyMatcher = employmentPolicyMatcher;
        this.openAiApiKey = openAiApiKey;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Transactional(readOnly = true)
    public RagAskResponse ask(RagAskRequest request) {
        ExtractedUserCondition condition = userConditionExtractor.extract(request.question())
                .withOverrides(request.region(), request.age(), request.employmentStatus());

        long realYouthPolicies = policyRepository.countBySourceTypeNotAndYouthRelatedTrue(PolicySourceType.SAMPLE);
        if (realYouthPolicies == 0) {
            return new RagAskResponse(NO_COLLECTED_YOUTH_DATA, condition, List.of());
        }
        long indexedRealYouthPolicies = policyRepository.countBySourceTypeNotAndYouthRelatedTrueAndIndexedTrue(PolicySourceType.SAMPLE);
        if (indexedRealYouthPolicies == 0) {
            return new RagAskResponse(NO_INDEXED_YOUTH_DATA, condition, List.of());
        }

        requireOpenAiApiKey();
        String query = buildQuery(condition);
        int searchTopK = Math.min(request.effectiveTopK() * 5, 30);
        List<Map<String, Object>> vectorRows = similaritySearch(query, searchTopK);

        if (vectorRows.isEmpty()) {
            return new RagAskResponse(NO_VECTOR_RESULT, condition, List.of());
        }

        List<Policy> policies = findPolicies(vectorRows).stream()
                .filter(policy -> policyRegionMatcher.isApplicable(condition.region(), policy.getRegionName()))
                .filter(policy -> agePolicyMatcher.isApplicable(condition.age(), policy))
                .filter(policy -> employmentPolicyMatcher.isApplicable(condition.employmentStatus(), policy))
                .limit(request.effectiveTopK())
                .toList();
        if (policies.isEmpty()) {
            return new RagAskResponse(filteredNoResultAnswer(condition), condition, List.of());
        }

        String context = buildContext(policies);
        String answer = createChatCompletion(condition, context);
        return new RagAskResponse(answer == null || answer.isBlank() ? NO_FILTERED_RESULT : answer, condition, toSources(policies));
    }

    private List<Map<String, Object>> similaritySearch(String query, int topK) {
        try {
            String vectorLiteral = toVectorLiteral(createEmbedding(query));
            return jdbcTemplate.queryForList("""
                    select content, metadata::text as metadata
                    from vector_store
                    where coalesce(metadata ->> 'sourceType', '') <> ?
                      and coalesce(metadata ->> 'youthRelated', 'false') = 'true'
                      and coalesce(metadata ->> 'indexed', 'false') = 'true'
                    order by embedding <=> ?::vector
                    limit ?
                    """, PolicySourceType.SAMPLE.name(), vectorLiteral, topK);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("벡터 검색 중 오류가 발생했습니다: " + exception.getMessage());
        }
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

    private String createChatCompletion(ExtractedUserCondition condition, String context) {
        try {
            String userPrompt = """
                    사용자 질문:
                    %s

                    추출 조건:
                    지역: %s
                    나이: %s
                    취업상태: %s
                    대상 그룹: %s
                    키워드: %s

                    CONTEXT:
                    %s
                    """.formatted(
                    condition.originalQuestion(),
                    blankToDefault(condition.region(), "미추출"),
                    condition.age() == null ? "미추출" : condition.age(),
                    blankToDefault(condition.employmentStatus(), "미추출"),
                    blankToDefault(condition.targetGroup(), "미추출"),
                    condition.keywords(),
                    context
            );
            Map<String, Object> body = Map.of(
                    "model", chatModel,
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new BusinessException("OpenAI Chat API 호출 실패: HTTP " + response.statusCode() + " - " + response.body());
            }
            return objectMapper.readTree(response.body())
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("OpenAI Chat API 호출 중 오류가 발생했습니다: " + exception.getMessage());
        }
    }

    private String filteredNoResultAnswer(ExtractedUserCondition condition) {
        if (hasText(condition.region())) {
            return "현재 수집된 실제 정책 데이터 중 '" + condition.region().strip() + "'에 적용되는 청년 지원 정책을 찾지 못했습니다.";
        }
        return NO_FILTERED_RESULT;
    }

    private String buildQuery(ExtractedUserCondition condition) {
        List<String> parts = new ArrayList<>();
        parts.add("질문: " + condition.originalQuestion());
        if (hasText(condition.region())) {
            parts.add("추출 지역: " + condition.region());
        }
        if (condition.age() != null) {
            parts.add("추출 나이: " + condition.age());
        }
        if (hasText(condition.employmentStatus())) {
            parts.add("추출 취업상태: " + condition.employmentStatus());
        }
        if (hasText(condition.targetGroup())) {
            parts.add("대상 그룹: " + condition.targetGroup());
        }
        if (condition.keywords() != null && !condition.keywords().isEmpty()) {
            parts.add("키워드: " + String.join(", ", condition.keywords()));
        }
        return String.join("\n", parts);
    }

    private List<Policy> findPolicies(List<Map<String, Object>> vectorRows) {
        Map<Long, Policy> orderedPolicies = new LinkedHashMap<>();
        for (Map<String, Object> row : vectorRows) {
            Long policyId = readPolicyId(row.get("metadata"));
            if (policyId == null || orderedPolicies.containsKey(policyId)) {
                continue;
            }
            policyRepository.findByIdAndSourceTypeNotAndYouthRelatedTrueAndIndexedTrue(policyId, PolicySourceType.SAMPLE)
                    .ifPresent(policy -> orderedPolicies.put(policyId, policy));
        }
        return new ArrayList<>(orderedPolicies.values());
    }

    private Long readPolicyId(Object metadataValue) {
        try {
            JsonNode metadata = metadataValue instanceof String stringValue
                    ? objectMapper.readTree(stringValue)
                    : objectMapper.valueToTree(metadataValue);
            JsonNode value = metadata.get("policyId");
            if (value == null || value.isNull()) {
                return null;
            }
            if (value.isNumber()) {
                return value.longValue();
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return Long.parseLong(value.asText());
            }
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private String buildContext(List<Policy> policies) {
        return policies.stream()
                .map(this::policyToContext)
                .collect(java.util.stream.Collectors.joining("\n---\n"));
    }

    private String policyToContext(Policy policy) {
        return """
                정책ID: %s
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
                policy.getId(),
                safe(policy.getTitle()),
                policy.getSourceType().name(),
                safe(policy.getSummary()),
                safe(policy.getSupportTarget()),
                safe(policy.getSelectionCriteria()),
                safe(policy.getApplicationMethod()),
                policy.getApplicationStartDate() == null ? "" : policy.getApplicationStartDate().format(DateTimeFormatter.ISO_DATE),
                policy.getApplicationEndDate() == null ? "" : policy.getApplicationEndDate().format(DateTimeFormatter.ISO_DATE),
                safe(policy.getRegionName()),
                safe(policy.getCategoryName()),
                safe(policy.getOfficialUrl())
        );
    }

    private List<RagSourceResponse> toSources(List<Policy> policies) {
        return policies.stream()
                .filter(Objects::nonNull)
                .map(policy -> new RagSourceResponse(
                        policy.getId(),
                        policy.getTitle(),
                        policy.getSourceType().name(),
                        policy.getRegionName(),
                        policy.getCategoryName(),
                        policy.getOfficialUrl()
                ))
                .toList();
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToDefault(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void requireOpenAiApiKey() {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new BusinessException("OPENAI_API_KEY가 설정되지 않았습니다.");
        }
    }
}
