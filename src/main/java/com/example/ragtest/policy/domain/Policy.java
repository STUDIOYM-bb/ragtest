package com.example.ragtest.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "policies",
        uniqueConstraints = @UniqueConstraint(name = "uk_policy_source_external", columnNames = {"source_type", "external_id"})
)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private PolicySourceType sourceType;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String supportTarget;

    @Column(columnDefinition = "TEXT")
    private String selectionCriteria;

    @Column(columnDefinition = "TEXT")
    private String applicationMethod;

    private LocalDate applicationStartDate;

    private LocalDate applicationEndDate;

    private String regionName;

    private String categoryName;

    @Column(length = 1000)
    private String officialUrl;

    @Column(length = 64)
    private String contentHash;

    @Column(nullable = false)
    private boolean indexed = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected Policy() {
    }

    public static Policy create(PolicySourceType sourceType, String externalId, String title) {
        Policy policy = new Policy();
        policy.sourceType = sourceType;
        policy.externalId = externalId;
        policy.title = title;
        return policy;
    }

    public void updateFrom(String title, String summary, String supportTarget, String selectionCriteria,
                           String applicationMethod, LocalDate applicationStartDate, LocalDate applicationEndDate,
                           String regionName, String categoryName, String officialUrl, String contentHash) {
        boolean changed = !contentHash.equals(this.contentHash);
        this.title = title;
        this.summary = summary;
        this.supportTarget = supportTarget;
        this.selectionCriteria = selectionCriteria;
        this.applicationMethod = applicationMethod;
        this.applicationStartDate = applicationStartDate;
        this.applicationEndDate = applicationEndDate;
        this.regionName = regionName;
        this.categoryName = categoryName;
        this.officialUrl = officialUrl;
        this.contentHash = contentHash;
        if (changed) {
            this.indexed = false;
        }
    }

    public void markIndexed() {
        this.indexed = true;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public PolicySourceType getSourceType() {
        return sourceType;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getSupportTarget() {
        return supportTarget;
    }

    public String getSelectionCriteria() {
        return selectionCriteria;
    }

    public String getApplicationMethod() {
        return applicationMethod;
    }

    public LocalDate getApplicationStartDate() {
        return applicationStartDate;
    }

    public LocalDate getApplicationEndDate() {
        return applicationEndDate;
    }

    public String getRegionName() {
        return regionName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getOfficialUrl() {
        return officialUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public boolean isIndexed() {
        return indexed;
    }
}
