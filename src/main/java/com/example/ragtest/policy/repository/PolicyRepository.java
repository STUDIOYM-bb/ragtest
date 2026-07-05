package com.example.ragtest.policy.repository;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {
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

    @Query("""
            select p from Policy p
            where p.sourceType <> :excludedSource
              and p.youthRelated = true
              and p.indexed = true
              and (
                lower(coalesce(p.title, '')) like lower(concat('%', :keyword, '%')) or
                lower(coalesce(p.summary, '')) like lower(concat('%', :keyword, '%')) or
                lower(coalesce(p.supportTarget, '')) like lower(concat('%', :keyword, '%')) or
                lower(coalesce(p.selectionCriteria, '')) like lower(concat('%', :keyword, '%')) or
                lower(coalesce(p.applicationMethod, '')) like lower(concat('%', :keyword, '%')) or
                lower(coalesce(p.categoryName, '')) like lower(concat('%', :keyword, '%')) or
                lower(coalesce(p.regionName, '')) like lower(concat('%', :keyword, '%'))
              )
            """)
    List<Policy> searchIndexedYouthPoliciesByKeyword(
            @Param("excludedSource") PolicySourceType excludedSource,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
