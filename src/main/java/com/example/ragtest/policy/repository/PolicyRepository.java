package com.example.ragtest.policy.repository;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    Optional<Policy> findBySourceTypeAndExternalId(PolicySourceType sourceType, String externalId);

    List<Policy> findAllByIndexedFalse();

    List<Policy> findAllByOrderByIdDesc();

    Optional<Policy> findById(Long id);
}
