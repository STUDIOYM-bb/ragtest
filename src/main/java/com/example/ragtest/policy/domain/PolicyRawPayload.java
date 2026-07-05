package com.example.ragtest.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

@Entity
public class PolicyRawPayload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Policy policy;

    @Enumerated(EnumType.STRING)
    private PolicySourceType sourceType;

    private String externalId;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    private LocalDateTime fetchedAt;

    protected PolicyRawPayload() {
    }

    public static PolicyRawPayload create(Policy policy, PolicySourceType sourceType, String externalId, String rawPayload) {
        PolicyRawPayload payload = new PolicyRawPayload();
        payload.policy = policy;
        payload.sourceType = sourceType;
        payload.externalId = externalId;
        payload.rawPayload = rawPayload;
        payload.fetchedAt = LocalDateTime.now();
        return payload;
    }
}
