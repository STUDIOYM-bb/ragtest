package com.example.ragtest.policy.controller;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.common.response.ApiResponse;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.example.ragtest.policy.repository.PolicyRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<List<PolicyResponse>> findAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) PolicySourceType sourceType,
            @RequestParam(defaultValue = "true") boolean youthOnly,
            @RequestParam(defaultValue = "false") boolean indexedOnly,
            @RequestParam(defaultValue = "true") boolean sampleExcluded
    ) {
        Specification<Policy> specification = (root, query, builder) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();
            if (sampleExcluded) predicates.add(builder.notEqual(root.get("sourceType"), PolicySourceType.SAMPLE));
            if (youthOnly) predicates.add(builder.isTrue(root.get("youthRelated")));
            if (indexedOnly) predicates.add(builder.isTrue(root.get("indexed")));
            if (sourceType != null) predicates.add(builder.equal(root.get("sourceType"), sourceType));
            if (hasText(region)) predicates.add(builder.like(builder.lower(root.get("regionName")), "%" + region.strip().toLowerCase() + "%"));
            if (hasText(keyword)) {
                String pattern = "%" + keyword.strip().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("summary")), pattern),
                        builder.like(builder.lower(root.get("supportTarget")), pattern),
                        builder.like(builder.lower(root.get("selectionCriteria")), pattern),
                        builder.like(builder.lower(root.get("applicationMethod")), pattern),
                        builder.like(builder.lower(root.get("categoryName")), pattern)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        List<PolicyResponse> responses = policyRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "id")).stream()
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
