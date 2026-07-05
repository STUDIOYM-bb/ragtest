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
import com.example.ragtest.policy.filter.YouthPolicyFilter;
import com.example.ragtest.policy.repository.PolicyRawPayloadRepository;
import com.example.ragtest.policy.repository.PolicyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Service
public class ExternalPolicyIngestService {

    private static final int DEFAULT_PAGE_SIZE = 40;
    private static final int DEFAULT_MAX_PAGES = 2;
    private static final int DEFAULT_MAX_ITEMS_PER_SOURCE = 100;
    private static final List<String> PUBLIC_SERVICE_KEYWORDS = List.of("청년", "취업", "구직", "주거", "월세", "자산", "창업");

    private final PublicServiceApiClient publicServiceApiClient;
    private final LocalWelfareApiClient localWelfareApiClient;
    private final CentralWelfareApiClient centralWelfareApiClient;
    private final PublicServiceNormalizer publicServiceNormalizer;
    private final LocalWelfareNormalizer localWelfareNormalizer;
    private final CentralWelfareNormalizer centralWelfareNormalizer;
    private final PolicyRepository policyRepository;
    private final PolicyRawPayloadRepository rawPayloadRepository;
    private final YouthPolicyFilter youthPolicyFilter;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public ExternalPolicyIngestService(
            PublicServiceApiClient publicServiceApiClient,
            LocalWelfareApiClient localWelfareApiClient,
            CentralWelfareApiClient centralWelfareApiClient,
            PublicServiceNormalizer publicServiceNormalizer,
            LocalWelfareNormalizer localWelfareNormalizer,
            CentralWelfareNormalizer centralWelfareNormalizer,
            PolicyRepository policyRepository,
            PolicyRawPayloadRepository rawPayloadRepository,
            YouthPolicyFilter youthPolicyFilter
    ) {
        this.publicServiceApiClient = publicServiceApiClient;
        this.localWelfareApiClient = localWelfareApiClient;
        this.centralWelfareApiClient = centralWelfareApiClient;
        this.publicServiceNormalizer = publicServiceNormalizer;
        this.localWelfareNormalizer = localWelfareNormalizer;
        this.centralWelfareNormalizer = centralWelfareNormalizer;
        this.policyRepository = policyRepository;
        this.rawPayloadRepository = rawPayloadRepository;
        this.youthPolicyFilter = youthPolicyFilter;
    }

    @Transactional
    public IngestResult ingestPublicYouthServices() {
        Counter counter = new Counter();
        Set<String> seenIds = new HashSet<>();
        for (String keyword : PUBLIC_SERVICE_KEYWORDS) {
            for (int page = 1; page <= DEFAULT_MAX_PAGES && counter.fetched < DEFAULT_MAX_ITEMS_PER_SOURCE; page++) {
                JsonNode response = publicServiceApiClient.fetchList(page, DEFAULT_PAGE_SIZE, keyword);
                List<JsonNode> listItems = items(response);
                if (listItems.isEmpty()) {
                    break;
                }
                for (JsonNode item : listItems) {
                    if (counter.fetched >= DEFAULT_MAX_ITEMS_PER_SOURCE) {
                        break;
                    }
                    ExternalPolicyRecord listRecord = publicServiceNormalizer.normalizeToRecord(item);
                    if (!hasRequiredListFields(listRecord) || !seenIds.add(listRecord.externalId())) {
                        counter.skipped++;
                        continue;
                    }
                    counter.fetched++;
                    JsonNode merged = mergeWithDetail(item, listRecord.externalId(), publicServiceApiClient::fetchDetail);
                    saveRecord(publicServiceNormalizer.normalizeToRecord(merged), counter);
                }
            }
        }
        return counter.toResult();
    }

    @Transactional
    public IngestResult ingestLocalWelfareServices() {
        try {
            return ingestPagedItems(
                    page -> localWelfareApiClient.fetchList(page, DEFAULT_PAGE_SIZE),
                    localWelfareApiClient::fetchDetail,
                    localWelfareNormalizer::normalizeToRecord
            );
        } catch (RestClientResponseException exception) {
            throw new BusinessException("지자체복지서비스 API 호출 실패: HTTP " + exception.getStatusCode().value()
                    + ". 공공데이터포털에서 해당 API 활용신청/승인 여부를 확인해주세요.");
        }
    }

    @Transactional
    public IngestResult ingestCentralWelfareServices() {
        try {
            return ingestPagedItems(
                    page -> centralWelfareApiClient.fetchList(page, DEFAULT_PAGE_SIZE),
                    centralWelfareApiClient::fetchDetail,
                    centralWelfareNormalizer::normalizeToRecord
            );
        } catch (RestClientResponseException exception) {
            throw new BusinessException("중앙부처복지서비스 API 호출 실패: HTTP " + exception.getStatusCode().value()
                    + ". 공공데이터포털에서 해당 API 활용신청/승인 여부를 확인해주세요.");
        }
    }

    private IngestResult ingestPagedItems(
            Function<Integer, JsonNode> listFetcher,
            Function<String, JsonNode> detailFetcher,
            RecordNormalizer normalizer
    ) {
        Counter counter = new Counter();
        Set<String> seenIds = new HashSet<>();
        for (int page = 1; page <= DEFAULT_MAX_PAGES && counter.fetched < DEFAULT_MAX_ITEMS_PER_SOURCE; page++) {
            JsonNode response = listFetcher.apply(page);
            List<JsonNode> listItems = items(response);
            if (listItems.isEmpty()) {
                break;
            }
            for (JsonNode item : listItems) {
                if (counter.fetched >= DEFAULT_MAX_ITEMS_PER_SOURCE) {
                    break;
                }
                ExternalPolicyRecord listRecord = normalizer.normalize(item);
                if (!hasRequiredListFields(listRecord) || !seenIds.add(listRecord.externalId())) {
                    counter.skipped++;
                    continue;
                }
                counter.fetched++;
                JsonNode merged = mergeWithDetail(item, listRecord.externalId(), detailFetcher);
                saveRecord(normalizer.normalize(merged), counter);
            }
        }
        return counter.toResult();
    }

    private JsonNode mergeWithDetail(JsonNode listItem, String externalId, Function<String, JsonNode> detailFetcher) {
        ObjectNode merged = objectMapper.createObjectNode();
        if (listItem != null && listItem.isObject()) {
            merged.setAll((ObjectNode) listItem);
        } else if (listItem != null) {
            merged.set("_listPayload", listItem);
        }

        try {
            JsonNode detailResponse = detailFetcher.apply(externalId);
            JsonNode detailItem = firstItem(detailResponse);
            if (detailItem != null && detailItem.isObject()) {
                merged.setAll((ObjectNode) detailItem);
            } else if (detailItem != null) {
                merged.set("_detailPayload", detailItem);
            }
            merged.set("_rawDetailResponse", detailResponse);
        } catch (Exception exception) {
            merged.put("_detailFetchError", exception.getMessage());
        }
        return merged;
    }

    private void saveRecord(ExternalPolicyRecord record, Counter counter) {
        if (!hasRequiredListFields(record)) {
            counter.skipped++;
            return;
        }
        boolean youthRelated = upsert(record);
        if (youthRelated) {
            counter.saved++;
        } else {
            counter.skipped++;
        }
    }

    private boolean upsert(ExternalPolicyRecord record) {
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
        boolean youthRelated = youthPolicyFilter.isYouthRelated(policy);
        policy.updateYouthRelated(youthRelated);
        Policy saved = policyRepository.save(policy);
        rawPayloadRepository.save(PolicyRawPayload.create(saved, record.sourceType(), record.externalId(), record.rawPayload()));
        return youthRelated;
    }

    private JsonNode firstItem(JsonNode response) {
        List<JsonNode> found = items(response);
        return found.isEmpty() ? response : found.get(0);
    }

    static List<JsonNode> items(JsonNode response) {
        List<JsonNode> items = new ArrayList<>();
        collectItems(response, items);
        return items;
    }

    private static void collectItems(JsonNode node, List<JsonNode> items) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectItems(child, items));
            return;
        }
        if (isPolicyItem(node)) {
            items.add(node);
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> collectItems(entry.getValue(), items));
        }
    }

    private static boolean isPolicyItem(JsonNode node) {
        return node.isObject()
                && hasAnyText(node, "서비스ID", "serviceId", "servId", "id")
                && hasAnyText(node, "서비스명", "serviceName", "servNm", "title");
    }

    private static boolean hasAnyText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRequiredListFields(ExternalPolicyRecord record) {
        return record.externalId() != null && !record.externalId().isBlank()
                && record.title() != null && !record.title().isBlank();
    }

    private String hash(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 hash algorithm is not available.", exception);
        }
    }

    private interface RecordNormalizer {
        ExternalPolicyRecord normalize(JsonNode item);
    }

    private static class Counter {
        private int fetched;
        private int saved;
        private int skipped;

        private IngestResult toResult() {
            return new IngestResult(fetched, saved, 0, skipped);
        }
    }
}
