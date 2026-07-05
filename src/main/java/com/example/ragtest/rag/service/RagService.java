package com.example.ragtest.rag.service;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.example.ragtest.policy.repository.PolicyRepository;
import com.example.ragtest.rag.condition.ExtractedUserCondition;
import com.example.ragtest.rag.condition.UserConditionExtractor;
import com.example.ragtest.rag.dto.RagAskRequest;
import com.example.ragtest.rag.dto.RagAskResponse;
import com.example.ragtest.rag.dto.RagSourceResponse;
import com.example.ragtest.rag.ranking.HybridSearchResult;
import com.example.ragtest.rag.ranking.PolicySearchCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final String NO_COLLECTED_YOUTH_DATA =
            "아직 수집된 청년정책 데이터가 없습니다. 관리자 테스트에서 실제 공공데이터 API 수집을 먼저 실행해주세요.";
    private static final String NO_INDEXED_YOUTH_DATA =
            "청년정책 데이터는 저장되어 있지만 아직 인덱싱되지 않았습니다. 관리자 테스트에서 실제 정책 데이터 인덱싱을 먼저 실행해주세요.";
    private static final String NO_MATCHED_RESULT =
            "현재 수집·인덱싱된 실제 정책 데이터에서 입력하신 조건에 맞는 청년 지원 정책을 찾지 못했습니다.";
    private static final String SYSTEM_PROMPT = """
            너는 대한민국 청년정책/복지정책 안내 도우미다.
            반드시 CONTEXT 안의 정책만 근거로 답변하고 CONTEXT에 없는 정책은 절대 언급하지 않는다.
            sources에 포함된 정책만 답변에 언급한다.
            사용자의 지역, 나이, 학업상태, 취업상태, 생애단계, 경제상태, 관심분야와 맞는 정책을 우선 안내한다.
            조건이 명확히 맞는 정책과 추가 확인이 필요한 정책을 구분한다.
            조건이 명확히 맞지 않는 정책은 추천하지 않는다.
            답변 형식은 '1. 지원 가능성이 높은 정책', '2. 추가 확인이 필요한 정책'으로 구분한다.
            각 정책에는 정책명, 관련 이유, 지원대상, 신청방법, 신청기간, 공식 링크를 포함한다.
            마지막에는 반드시 '정확한 신청 가능 여부는 공식 링크에서 재확인하세요.'라고 안내한다.
            """;

    private final PolicyRepository policyRepository;
    private final HybridPolicySearchService hybridSearchService;
    private final UserConditionExtractor conditionExtractor;
    private final String openAiApiKey;
    private final String chatModel;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public RagService(PolicyRepository policyRepository, HybridPolicySearchService hybridSearchService,
                      UserConditionExtractor conditionExtractor,
                      @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
                      @Value("${spring.ai.openai.chat.options.model:gpt-4.1-mini}") String chatModel) {
        this.policyRepository = policyRepository;
        this.hybridSearchService = hybridSearchService;
        this.conditionExtractor = conditionExtractor;
        this.openAiApiKey = openAiApiKey;
        this.chatModel = chatModel;
    }

    @Transactional(readOnly = true)
    public RagAskResponse ask(RagAskRequest request) {
        ExtractedUserCondition extracted = conditionExtractor.extract(request.question())
                .withOverrides(request.region(), request.age(), request.employmentStatus());
        long realYouth = policyRepository.countBySourceTypeNotAndYouthRelatedTrue(PolicySourceType.SAMPLE);
        if (realYouth == 0) return empty(NO_COLLECTED_YOUTH_DATA, extracted);
        long indexedYouth = policyRepository.countBySourceTypeNotAndYouthRelatedTrueAndIndexedTrue(PolicySourceType.SAMPLE);
        if (indexedYouth == 0) return empty(NO_INDEXED_YOUTH_DATA, extracted);

        HybridSearchResult result = hybridSearchService.search(request.question(), request.effectiveTopK(),
                request.region(), request.age(), request.employmentStatus());
        if (result.finalCandidates().isEmpty()) {
            return empty(NO_MATCHED_RESULT, result.extractedCondition());
        }
        requireOpenAiApiKey();
        String context = result.finalCandidates().stream().map(this::candidateContext)
                .collect(Collectors.joining("\n---\n"));
        String answer = chat(result.extractedCondition(), context);
        return new RagAskResponse(answer, result.extractedCondition(), toSources(result.finalCandidates()), null);
    }

    private RagAskResponse empty(String message, ExtractedUserCondition condition) {
        return new RagAskResponse(message, condition, List.of(), message);
    }

    private String candidateContext(PolicySearchCandidate candidate) {
        Policy policy = candidate.getPolicy();
        return """
                정책ID: %s
                정책명: %s
                출처: %s
                적합도 구분: %s
                매칭 이유: %s
                추가 확인 사유: %s
                요약: %s
                지원대상: %s
                선정기준: %s
                신청방법: %s
                신청기간: %s ~ %s
                지역: %s
                카테고리: %s
                공식 링크: %s
                """.formatted(policy.getId(), safe(policy.getTitle()), policy.getSourceType(),
                candidate.getCautionReasons().isEmpty() ? "지원 가능성 높음" : "추가 확인 필요",
                candidate.getMatchedReasons(), candidate.getCautionReasons(), safe(policy.getSummary()),
                safe(policy.getSupportTarget()), safe(policy.getSelectionCriteria()), safe(policy.getApplicationMethod()),
                date(policy.getApplicationStartDate()), date(policy.getApplicationEndDate()), safe(policy.getRegionName()),
                safe(policy.getCategoryName()), safe(policy.getOfficialUrl()));
    }

    private String chat(ExtractedUserCondition condition, String context) {
        try {
            String prompt = """
                    사용자 질문: %s
                    추출 조건: 지역=%s, 나이=%s, 대상=%s, 학업=%s, 취업=%s, 생애단계=%s, 경제상태=%s, 관심분야=%s

                    CONTEXT:
                    %s
                    """.formatted(condition.originalQuestion(), safe(condition.region()), condition.age(),
                    safe(condition.targetGroup()), safe(condition.educationStatus()), safe(condition.employmentStatus()),
                    safe(condition.lifeStage()), safe(condition.economicStatus()), condition.interestCategories(), context);
            Map<String, Object> body = Map.of("model", chatModel, "temperature", 0.15,
                    "messages", List.of(Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", prompt)));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json; charset=utf-8").build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) throw new BusinessException("OpenAI Chat API 호출 실패: HTTP " + response.statusCode());
            return objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText(NO_MATCHED_RESULT);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("OpenAI Chat API 호출 중 오류가 발생했습니다: " + exception.getMessage());
        }
    }

    private List<RagSourceResponse> toSources(List<PolicySearchCandidate> candidates) {
        return candidates.stream().map(candidate -> {
            Policy policy = candidate.getPolicy();
            return new RagSourceResponse(policy.getId(), policy.getTitle(), policy.getSourceType().name(),
                    policy.getRegionName(), policy.getCategoryName(), policy.getOfficialUrl(),
                    candidate.getCautionReasons().isEmpty() ? "MATCHED" : "CHECK_REQUIRED",
                    candidate.getFinalScore(), candidate.getMatchedReasons(), candidate.getCautionReasons());
        }).toList();
    }

    private String date(java.time.LocalDate value) { return value == null ? "" : value.format(DateTimeFormatter.ISO_DATE); }
    private String safe(String value) { return value == null ? "" : value; }
    private void requireOpenAiApiKey() {
        if (openAiApiKey == null || openAiApiKey.isBlank()) throw new BusinessException("OPENAI_API_KEY가 설정되지 않았습니다.");
    }
}
