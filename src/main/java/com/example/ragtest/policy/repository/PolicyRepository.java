package com.example.ragtest.policy.repository;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    Optional<Policy> findBySourceTypeAndExternalId(PolicySourceType sourceType, String externalId);

    List<Policy> findAllByIndexedFalse();

    List<Policy> findAllByIndexedFalseAndYouthRelatedTrue();

    List<Policy> findAllBySourceTypeAndIndexedFalseAndYouthRelatedTrue(PolicySourceType sourceType);

    List<Policy> findAllBySourceTypeAndIndexedFalseAndYouthRelatedTrue(PolicySourceType sourceType, Pageable pageable);

    List<Policy> findAllBySourceTypeNotAndIndexedFalseAndYouthRelatedTrue(PolicySourceType sourceType);

    List<Policy> findAllBySourceTypeNotAndIndexedFalseAndYouthRelatedTrue(PolicySourceType sourceType, Pageable pageable);

    List<Policy> findAllBySourceTypeNotAndYouthRelatedTrue(PolicySourceType sourceType);

    List<Policy> findAllByOrderByIdDesc();

    Optional<Policy> findById(Long id);

    Optional<Policy> findByIdAndSourceTypeNotAndYouthRelatedTrueAndIndexedTrue(Long id, PolicySourceType sourceType);

    long countBySourceType(PolicySourceType sourceType);

    long countBySourceTypeNot(PolicySourceType sourceType);

    long countBySourceTypeAndYouthRelatedTrue(PolicySourceType sourceType);

    long countBySourceTypeAndYouthRelatedTrueAndIndexedTrue(PolicySourceType sourceType);

    long countBySourceTypeNotAndYouthRelatedTrue(PolicySourceType sourceType);

    long countBySourceTypeNotAndYouthRelatedTrueAndIndexedTrue(PolicySourceType sourceType);
}
