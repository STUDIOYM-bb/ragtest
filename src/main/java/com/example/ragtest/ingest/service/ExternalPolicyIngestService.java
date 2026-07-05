package com.example.ragtest.ingest.service;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.external.client.CentralWelfareApiClient;
import com.example.ragtest.external.client.LocalWelfareApiClient;
import com.example.ragtest.external.client.PublicServiceApiClient;
import com.example.ragtest.external.dto.ExternalPolicyRecord;
import com.example.ragtest.external.normalizer.CentralWelfareNormalizer;
import com.example.ragtest.external.normalizer.LocalWelfareNormalizer;
import com.example.ragtest.external.normalizer.PublicServiceNormalizer;
import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicyRawPayload;
import com.example.ragtest.policy.repository.PolicyRawPayloadRepository;
import com.example.ragtest.policy.repository.PolicyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class ExternalPolicyIngestService {

    private static final List<String> PUBLIC_SERVICE_KEYWORDS = List.of("청년", "취업", "구직", "주거", "월세", "자산");

    private final PublicServiceApiClient publicServiceApiClient;
    private final LocalWelfareApiClient localWelfareApiClient;
    private final CentralWelfareApiClient centralWelfareApiClient;
    private final PublicServiceNormalizer publicServiceNormalizer;
    private final LocalWelfareNormalizer localWelfareNormalizer;
    private final CentralWelfareNormalizer centralWelfareNormalizer;
    private final PolicyRepository policyRepository;
    private final PolicyRawPayloadRepository rawPayloadRepository;

    public ExternalPolicyIngestService(
            PublicServiceApiClient publicServiceApiClient,
            LocalWelfareApiClient localWelfareApiClient,
            CentralWelfareApiClient centralWelfareApiClient,
            PublicServiceNormalizer publicServiceNormalizer,
            LocalWelfareNormalizer localWelfareNormalizer,
            CentralWelfareNormalizer centralWelfareNormalizer,
            PolicyRepository policyRepository,
            PolicyRawPayloadRepository rawPayloadRepository
    ) {
        this.publicServiceApiClient = publicServiceApiClient;
        this.localWelfareApiClient = localWelfareApiClient;
        this.centralWelfareApiClient = centralWelfareApiClient;
        this.publicServiceNormalizer = publicServiceNormalizer;
        this.localWelfareNormalizer = localWelfareNormalizer;
        this.centralWelfareNormalizer = centralWelfareNormalizer;
        this.policyRepository = policyRepository;
        this.rawPayloadRepository = rawPayloadRepository;
    }

    @Transactional
    public int ingestPublicYouthServices() {
        int savedCount = 0;
        for (String keyword : PUBLIC_SERVICE_KEYWORDS) {
            JsonNode response = publicServiceApiClient.fetchList(1, 20, keyword);
            for (JsonNode item : items(response)) {
                ExternalPolicyRecord record = publicServiceNormalizer.normalizeToRecord(item);
                if (hasRequiredFields(record) && isYouthOrWelfarePolicy(record)) {
                    upsert(record);
                    savedCount++;
                }
            }
        }
        return savedCount;
    }

    @Transactional
    public int ingestLocalWelfareServices() {
        try {
            JsonNode response = localWelfareApiClient.fetchList(1, 20);
            int savedCount = 0;
            for (JsonNode item : items(response)) {
                ExternalPolicyRecord record = localWelfareNormalizer.normalizeToRecord(item);
                if (hasRequiredFields(record) && isYouthOrWelfarePolicy(record)) {
                    upsert(record);
                    savedCount++;
                }
            }
            return savedCount;
        } catch (RestClientResponseException exception) {
            throw new BusinessException("지자체복지서비스 API 호출 실패: HTTP " + exception.getStatusCode().value()
                    + ". 공공데이터포털에서 해당 API 활용신청/승인 여부를 확인하세요.");
        }
    }

    @Transactional
    public int ingestCentralWelfareServices() {
        try {
            JsonNode response = centralWelfareApiClient.fetchList(1, 20);
            int savedCount = 0;
            for (JsonNode item : items(response)) {
                ExternalPolicyRecord record = centralWelfareNormalizer.normalizeToRecord(item);
                if (hasRequiredFields(record) && isYouthOrWelfarePolicy(record)) {
                    upsert(record);
                    savedCount++;
                }
            }
            return savedCount;
        } catch (RestClientResponseException exception) {
            throw new BusinessException("중앙부처복지서비스 API 호출 실패: HTTP " + exception.getStatusCode().value()
                    + ". 공공데이터포털에서 해당 API 활용신청/승인 여부를 확인하세요.");
        }
    }

    private void upsert(ExternalPolicyRecord record) {
        String hash = hash(record.rawPayload());
        Policy policy = policyRepository.findBySourceTypeAndExternalId(record.sourceType(), record.externalId())
                .orElseGet(() -> Policy.create(record.sourceType(), record.externalId(), record.title()));
        policy.updateFrom(
                record.title(),
                record.summary(),
                record.supportTarget(),
                record.selectionCriteria(),
                record.applicationMethod(),
                record.applicationStartDate(),
                record.applicationEndDate(),
                record.regionName(),
                record.categoryName(),
                record.officialUrl(),
                hash
        );
        Policy saved = policyRepository.save(policy);
        rawPayloadRepository.save(PolicyRawPayload.create(saved, record.sourceType(), record.externalId(), record.rawPayload()));
    }

    private List<JsonNode> items(JsonNode response) {
        List<JsonNode> items = new ArrayList<>();
        collectItems(response, items);
        return items;
    }

    private void collectItems(JsonNode node, List<JsonNode> items) {
        if (node == null || node.isNull()) {
            return;
        }
        JsonNode data = node.get("data");
        if (data != null && data.isArray()) {
            data.forEach(items::add);
            return;
        }
        JsonNode item = node.get("item");
        if (item != null) {
            if (item.isArray()) {
                item.forEach(items::add);
            } else if (item.isObject()) {
                items.add(item);
            }
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectItems(entry.getValue(), items));
        }
    }

    private boolean hasRequiredFields(ExternalPolicyRecord record) {
        return record.externalId() != null && !record.externalId().isBlank()
                && record.title() != null && !record.title().isBlank();
    }

    private boolean isYouthOrWelfarePolicy(ExternalPolicyRecord record) {
        String text = String.join(" ",
                safe(record.title()),
                safe(record.summary()),
                safe(record.supportTarget()),
                safe(record.selectionCriteria()),
                safe(record.categoryName()));
        return PUBLIC_SERVICE_KEYWORDS.stream().anyMatch(text::contains);
    }

    private String hash(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 hash algorithm is not available.", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
