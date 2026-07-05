package com.example.ragtest.policy.controller;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.common.response.ApiResponse;
import com.example.ragtest.policy.repository.PolicyRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyRepository policyRepository;

    public PolicyController(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @GetMapping
    public ApiResponse<List<PolicyResponse>> findAll() {
        List<PolicyResponse> responses = policyRepository.findAllByOrderByIdDesc().stream()
                .map(PolicyResponse::from)
                .toList();
        return ApiResponse.ok(responses);
    }

    @GetMapping("/{policyId}")
    public ApiResponse<PolicyResponse> findById(@PathVariable Long policyId) {
        return policyRepository.findById(policyId)
                .map(PolicyResponse::from)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new BusinessException("정책을 찾을 수 없습니다."));
    }
}
