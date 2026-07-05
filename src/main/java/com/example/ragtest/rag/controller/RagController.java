package com.example.ragtest.rag.controller;

import com.example.ragtest.common.response.ApiResponse;
import com.example.ragtest.rag.dto.RagAskRequest;
import com.example.ragtest.rag.dto.RagAskResponse;
import com.example.ragtest.rag.service.RagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public ApiResponse<RagAskResponse> ask(@Valid @RequestBody RagAskRequest request) {
        return ApiResponse.ok(ragService.ask(request), "RAG 답변 생성 완료");
    }
}
