package com.example.ragtest.rag.controller;

import com.example.ragtest.common.response.ApiResponse;
import com.example.ragtest.rag.dto.DebugSearchRequest;
import com.example.ragtest.rag.dto.SearchCandidateDebugResponse;
import com.example.ragtest.rag.dto.SearchCandidateView;
import com.example.ragtest.rag.ranking.HybridSearchResult;
import com.example.ragtest.rag.service.HybridPolicySearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/debug")
public class RagDebugController {

    private final HybridPolicySearchService hybridPolicySearchService;

    public RagDebugController(HybridPolicySearchService hybridPolicySearchService) {
        this.hybridPolicySearchService = hybridPolicySearchService;
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
}
