package com.example.ragtest.rag.controller;

import com.example.ragtest.common.response.ApiResponse;
import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.external.client.DataGoKrYouthPolicyApiClient;
import com.example.ragtest.external.client.ExternalApiRawResponse;
import com.example.ragtest.external.client.YouthCenterOfficialApiClient;
import com.example.ragtest.rag.dto.DebugSearchRequest;
import com.example.ragtest.rag.dto.SearchCandidateDebugResponse;
import com.example.ragtest.rag.dto.SearchCandidateView;
import com.example.ragtest.rag.ranking.HybridSearchResult;
import com.example.ragtest.rag.service.HybridPolicySearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/debug")
public class RagDebugController {

    private final HybridPolicySearchService hybridPolicySearchService;
    private final YouthCenterOfficialApiClient youthCenterOfficialApiClient;
    private final DataGoKrYouthPolicyApiClient dataGoKrYouthPolicyApiClient;

    public RagDebugController(HybridPolicySearchService hybridPolicySearchService,
                              YouthCenterOfficialApiClient youthCenterOfficialApiClient,
                              DataGoKrYouthPolicyApiClient dataGoKrYouthPolicyApiClient) {
        this.hybridPolicySearchService = hybridPolicySearchService;
        this.youthCenterOfficialApiClient = youthCenterOfficialApiClient;
        this.dataGoKrYouthPolicyApiClient = dataGoKrYouthPolicyApiClient;
    }

    @PostMapping("/search-candidates")
    public ApiResponse<SearchCandidateDebugResponse> search(@Valid @RequestBody DebugSearchRequest request) {
        HybridSearchResult result = hybridPolicySearchService.search(request.question(), request.effectiveTopK(), null, null, null);
        SearchCandidateDebugResponse response = new SearchCandidateDebugResponse(
                result.extractedCondition(),
                result.vectorCandidates().stream().map(SearchCandidateView::from).toList(),
                result.keywordCandidates().stream().map(SearchCandidateView::from).toList(),
                result.mergedCandidates().stream().map(SearchCandidateView::from).toList(),
                result.finalCandidates().stream().map(SearchCandidateView::from).toList(),
                result.excludedCandidates().stream().map(SearchCandidateView::from).toList()
        );
        return ApiResponse.ok(response, "검색 후보 디버그 완료");
    }

    @GetMapping("/youth-center/raw")
    public ApiResponse<ExternalApiRawResponse> youthCenterRaw(
            @RequestParam(defaultValue = "청년") String query,
            @RequestParam(defaultValue = "1") int pageIndex,
            @RequestParam(defaultValue = "10") int display
    ) {
        ExternalApiRawResponse raw;
        if (dataGoKrYouthPolicyApiClient.isConfigured()) {
            raw = dataGoKrYouthPolicyApiClient.fetchRaw(pageIndex, display, query);
        } else if (youthCenterOfficialApiClient.isConfigured()) {
            raw = youthCenterOfficialApiClient.fetchRaw(pageIndex, display, query);
        } else {
            throw new BusinessException("온통청년 raw 확인 설정이 없습니다. DATA_GO_KR_YOUTH_POLICY_BASE_URL과 "
                    + "DATA_GO_KR_YOUTH_POLICY_KEY 또는 YOUTH_CENTER_API_KEY를 설정하세요.");
        }
        return rawResponse(raw, "온통청년 자동 원본 응답 조회 완료");
    }

    @GetMapping("/youth-center-official/raw")
    public ApiResponse<ExternalApiRawResponse> youthCenterOfficialRaw(
            @RequestParam(defaultValue = "청년") String query,
            @RequestParam(defaultValue = "1") int pageIndex,
            @RequestParam(defaultValue = "10") int display
    ) {
        ExternalApiRawResponse raw = youthCenterOfficialApiClient.fetchRaw(pageIndex, display, query);
        return rawResponse(raw, "온통청년 공식 원본 응답 조회 완료");
    }

    @GetMapping("/youth-policy-data-go-kr/raw")
    public ApiResponse<ExternalApiRawResponse> youthPolicyDataGoKrRaw(
            @RequestParam(defaultValue = "청년") String query,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int numOfRows
    ) {
        ExternalApiRawResponse raw = dataGoKrYouthPolicyApiClient.fetchRaw(pageNo, numOfRows, query);
        return rawResponse(raw, "공공데이터포털 온통청년 원본 응답 조회 완료");
    }

    private ApiResponse<ExternalApiRawResponse> rawResponse(ExternalApiRawResponse raw, String successMessage) {
        if (raw.looksLikeHtml()) {
            return new ApiResponse<>(false, raw, raw.apiType().equals("OFFICIAL")
                    ? "공식 온통청년 API가 XML이 아닌 HTML을 반환했습니다."
                    : "공공데이터포털 온통청년 API가 JSON/XML이 아닌 HTML 응답을 반환했습니다.");
        }
        if (raw.statusCode() < 200 || raw.statusCode() >= 300) {
            return new ApiResponse<>(false, raw, "온통청년 API가 성공 상태가 아닌 HTTP 응답을 반환했습니다.");
        }
        return ApiResponse.ok(raw, successMessage);
    }
}
