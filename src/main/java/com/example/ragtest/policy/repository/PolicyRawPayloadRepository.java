package com.example.ragtest.policy.repository;

import com.example.ragtest.policy.domain.PolicyRawPayload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRawPayloadRepository extends JpaRepository<PolicyRawPayload, Long> {
}
