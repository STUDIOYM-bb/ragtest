package com.example.ragtest.rag.service;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.repository.PolicyRepository;
import com.example.ragtest.rag.dto.RagAskRequest;
import com.example.ragtest.rag.dto.RagAskResponse;
import com.example.ragtest.rag.dto.RagSourceResponse;
import com.example.ragtest.common.exception.BusinessException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RagService {

    private static final String NOT_ENOUGH_CONTEXT = "현재 수집된 정책 데이터만으로는 정확히 확인하기 어렵습니다.";
    private static final String SYSTEM_PROMPT = """
            너는 대한민국 청년정책/복지정책 안내 도우미다.
            반드시 제공된 CONTEXT 안의 정책 정보만 근거로 답변한다.
            CONTEXT에 없는 내용은 추측하지 않는다.
            사용자의 조건과 정책의 지원대상/선정기준이 명확히 일치하지 않으면 "추가 확인 필요"라고 말한다.
            답변에는 가능한 한 다음 항목을 포함한다.
            - 추천 정책명
            - 왜 관련 있는지
            - 지원대상 요약
            - 신청방법
            - 신청기간
            - 공식 링크
            마지막에는 반드시 "정확한 신청 가능 여부는 공식 링크에서 재확인하세요."라고 안내한다.
            검색 결과가 부족하면 "현재 수집된 정책 데이터만으로는 정확히 확인하기 어렵습니다."라고 답한다.
            """;

    private final VectorStore vectorStore;
    private final PolicyRepository policyRepository;
    private final ChatClient chatClient;
    private final String openAiApiKey;

    public RagService(
            VectorStore vectorStore,
            PolicyRepository policyRepository,
            ChatClient.Builder chatClientBuilder,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey
    ) {
        this.vectorStore = vectorStore;
        this.policyRepository = policyRepository;
        this.chatClient = chatClientBuilder.build();
        this.openAiApiKey = openAiApiKey;
    }

    @Transactional(readOnly = true)
    public RagAskResponse ask(RagAskRequest request) {
        requireOpenAiApiKey();
        String query = buildQuery(request);
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(request.effectiveTopK())
                .build());

        if (documents == null || documents.isEmpty()) {
            return new RagAskResponse(NOT_ENOUGH_CONTEXT, List.of());
        }

        List<Policy> policies = findPolicies(documents);
        if (policies.isEmpty()) {
            return new RagAskResponse(NOT_ENOUGH_CONTEXT, List.of());
        }

        String context = buildContext(policies);
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("""
                        사용자 질문:
                        %s

                        사용자 조건:
                        지역: %s
                        나이: %s
                        취업상태: %s

                        CONTEXT:
                        %s
                        """.formatted(
                        request.question(),
                        blankToDefault(request.region(), "미입력"),
                        request.age() == null ? "미입력" : request.age(),
                        blankToDefault(request.employmentStatus(), "미입력"),
                        context
                ))
                .call()
                .content();

        return new RagAskResponse(answer == null || answer.isBlank() ? NOT_ENOUGH_CONTEXT : answer, toSources(policies));
    }

    private String buildQuery(RagAskRequest request) {
        List<String> parts = new ArrayList<>();
        parts.add("질문: " + request.question());
        if (hasText(request.region())) {
            parts.add("지역: " + request.region());
        }
        if (request.age() != null) {
            parts.add("나이: " + request.age());
        }
        if (hasText(request.employmentStatus())) {
            parts.add("취업상태: " + request.employmentStatus());
        }
        return String.join("\n", parts);
    }

    private List<Policy> findPolicies(List<Document> documents) {
        Map<Long, Policy> orderedPolicies = new LinkedHashMap<>();
        for (Document document : documents) {
            Long policyId = readPolicyId(document.getMetadata());
            if (policyId == null || orderedPolicies.containsKey(policyId)) {
                continue;
            }
            policyRepository.findById(policyId).ifPresent(policy -> orderedPolicies.put(policyId, policy));
        }
        return new ArrayList<>(orderedPolicies.values());
    }

    private Long readPolicyId(Map<String, Object> metadata) {
        Object value = metadata.get("policyId");
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer integerValue) {
            return integerValue.longValue();
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Long.parseLong(stringValue);
        }
        return null;
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
        if (openAiApiKey == null || openAiApiKey.isBlank() || "__OPENAI_API_KEY_NOT_SET__".equals(openAiApiKey)) {
            throw new BusinessException("OPENAI_API_KEY가 설정되지 않았습니다.");
        }
    }
}
