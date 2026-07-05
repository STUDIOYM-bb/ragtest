package com.example.ragtest.ingest.service;

import com.example.ragtest.common.exception.BusinessException;
import com.example.ragtest.external.client.CentralWelfareApiClient;
import com.example.ragtest.external.client.LocalWelfareApiClient;
import com.example.ragtest.external.client.PublicServiceApiClient;
import com.example.ragtest.external.client.YouthCenterApiClient;
import com.example.ragtest.external.dto.ExternalPolicyRecord;
import com.example.ragtest.external.normalizer.CentralWelfareNormalizer;
import com.example.ragtest.external.normalizer.LocalWelfareNormalizer;
import com.example.ragtest.external.normalizer.PublicServiceNormalizer;
import com.example.ragtest.external.normalizer.YouthPolicyNormalizer;
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

    private static final List<String> PUBLIC_SERVICE_KEYWORDS = List.of(
            "청년", "청소년", "학생", "대학생", "대학원생", "재학생", "휴학생", "졸업생",
            "학자금", "장학금", "등록금", "대출이자", "국가장학금", "주거", "월세", "전세",
            "임대", "행복주택", "청년주택", "교통비", "기본소득", "면접수당", "취업", "구직",
            "일자리", "일경험", "인턴", "직무훈련", "직업훈련", "창업", "예비창업", "스타트업",
            "자산", "금융", "저축", "적금", "도약계좌", "내일저축", "햇살론", "문화", "복지",
            "건강", "상담", "신혼부부", "사회초년생", "자립준비청년"
    );

    private final PublicServiceApiClient publicServiceApiClient;
    private final LocalWelfareApiClient localWelfareApiClient;
    private final CentralWelfareApiClient centralWelfareApiClient;
    private final YouthCenterApiClient youthCenterApiClient;
    private final PublicServiceNormalizer publicServiceNormalizer;
    private final LocalWelfareNormalizer localWelfareNormalizer;
    private final CentralWelfareNormalizer centralWelfareNormalizer;
    private final YouthPolicyNormalizer youthPolicyNormalizer;
    private final PolicyRepository policyRepository;
    private final PolicyRawPayloadRepository rawPayloadRepository;
    private final YouthPolicyFilter youthPolicyFilter;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public ExternalPolicyIngestService(
            PublicServiceApiClient publicServiceApiClient,
            LocalWelfareApiClient localWelfareApiClient,
            CentralWelfareApiClient centralWelfareApiClient,
            YouthCenterApiClient youthCenterApiClient,
            PublicServiceNormalizer publicServiceNormalizer,
            LocalWelfareNormalizer localWelfareNormalizer,
            CentralWelfareNormalizer centralWelfareNormalizer,
            YouthPolicyNormalizer youthPolicyNormalizer,
            PolicyRepository policyRepository,
            PolicyRawPayloadRepository rawPayloadRepository,
            YouthPolicyFilter youthPolicyFilter
    ) {
        this.publicServiceApiClient = publicServiceApiClient;
        this.localWelfareApiClient = localWelfareApiClient;
        this.centralWelfareApiClient = centralWelfareApiClient;
        this.youthCenterApiClient = youthCenterApiClient;
        this.publicServiceNormalizer = publicServiceNormalizer;
        this.localWelfareNormalizer = localWelfareNormalizer;
        this.centralWelfareNormalizer = centralWelfareNormalizer;
        this.youthPolicyNormalizer = youthPolicyNormalizer;
        this.policyRepository = policyRepository;
        this.rawPayloadRepository = rawPayloadRepository;
        this.youthPolicyFilter = youthPolicyFilter;
    }

    @Transactional
    public IngestResult ingestPublicYouthServices() {
        return ingestPublicYouthServices(IngestOptions.defaults());
    }

    @Transactional
    public IngestResult ingestPublicYouthServices(IngestOptions options) {
        Counter counter = new Counter();
        Set<String> seenIds = new HashSet<>();
        int keywordBudget = Math.max(1, options.maxItems() * 4 / 5);
        for (String keyword : PUBLIC_SERVICE_KEYWORDS) {
            for (int page = 1; page <= options.maxPages() && counter.fetched < keywordBudget; page++) {
                if (!collectPublicPage(publicServiceApiClient.fetchList(page, options.pageSize(), keyword),
                        keywordBudget, seenIds, counter)) break;
            }
            if (counter.fetched >= keywordBudget) break;
        }

        // 키워드 검색에서 누락되는 항목을 보완한다. 중복 externalId는 저장하지 않는다.
        for (int page = 1; page <= options.maxPages() && counter.fetched < options.maxItems(); page++) {
            if (!collectPublicPage(publicServiceApiClient.fetchList(page, options.pageSize()),
                    options.maxItems(), seenIds, counter)) break;
        }
        return counter.toResult();
    }

    private boolean collectPublicPage(JsonNode response, int limit, Set<String> seenIds, Counter counter) {
        List<JsonNode> pageItems = items(response);
        for (JsonNode item : pageItems) {
            if (counter.fetched >= limit) break;
            ExternalPolicyRecord listRecord = publicServiceNormalizer.normalizeToRecord(item);
            if (!hasRequiredListFields(listRecord) || !seenIds.add(listRecord.externalId())) {
                counter.skipped++;
                continue;
            }
            counter.fetched++;
            JsonNode merged = mergeWithDetail(item, listRecord.externalId(), publicServiceApiClient::fetchDetail);
            saveRecord(publicServiceNormalizer.normalizeToRecord(merged), counter);
        }
        return !pageItems.isEmpty();
    }

    @Transactional
    public IngestResult ingestLocalWelfareServices() {
        return ingestLocalWelfareServices(IngestOptions.defaults());
    }

    @Transactional
    public IngestResult ingestLocalWelfareServices(IngestOptions options) {
        try {
            return ingestPagedItems(page -> localWelfareApiClient.fetchList(page, options.pageSize()),
                    localWelfareApiClient::fetchDetail, localWelfareNormalizer::normalizeToRecord, options);
        } catch (RestClientResponseException exception) {
            throw new BusinessException("지자체복지서비스 API 호출 실패: HTTP " + exception.getStatusCode().value()
                    + ". 공공데이터포털 활용신청과 승인 상태를 확인해주세요.");
        }
    }

    @Transactional
    public IngestResult ingestCentralWelfareServices() {
        return ingestCentralWelfareServices(IngestOptions.defaults());
    }

    @Transactional
    public IngestResult ingestCentralWelfareServices(IngestOptions options) {
        try {
            return ingestPagedItems(page -> centralWelfareApiClient.fetchList(page, options.pageSize()),
                    centralWelfareApiClient::fetchDetail, centralWelfareNormalizer::normalizeToRecord, options);
        } catch (RestClientResponseException exception) {
            throw new BusinessException("중앙부처복지서비스 API 호출 실패: HTTP " + exception.getStatusCode().value()
                    + ". 공공데이터포털 활용신청과 승인 상태를 확인해주세요.");
        }
    }

    @Transactional
    public IngestResult ingestYouthCenterPolicies(IngestOptions options) {
        Counter counter = new Counter();
        Set<String> seenIds = new HashSet<>();
        for (int page = 1; page <= options.maxPages() && counter.fetched < options.maxItems(); page++) {
            List<JsonNode> listItems = items(youthCenterApiClient.fetchList(page, options.pageSize()));
            if (listItems.isEmpty()) break;
            for (JsonNode item : listItems) {
                if (counter.fetched >= options.maxItems()) break;
                ExternalPolicyRecord record = youthPolicyNormalizer.normalizeToRecord(item);
                if (!hasRequiredListFields(record) || !seenIds.add(record.externalId())) {
                    counter.skipped++;
                    continue;
                }
                counter.fetched++;
                saveRecord(record, counter);
            }
        }
        return counter.toResult();
    }

    private IngestResult ingestPagedItems(Function<Integer, JsonNode> listFetcher,
                                           Function<String, JsonNode> detailFetcher,
                                           RecordNormalizer normalizer,
                                           IngestOptions options) {
        Counter counter = new Counter();
        Set<String> seenIds = new HashSet<>();
        for (int page = 1; page <= options.maxPages() && counter.fetched < options.maxItems(); page++) {
            List<JsonNode> listItems = items(listFetcher.apply(page));
            if (listItems.isEmpty()) break;
            for (JsonNode item : listItems) {
                if (counter.fetched >= options.maxItems()) break;
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
        if (listItem != null && listItem.isObject()) merged.setAll((ObjectNode) listItem);
        else if (listItem != null) merged.set("_listPayload", listItem);
        try {
            JsonNode detailResponse = detailFetcher.apply(externalId);
            JsonNode detailItem = firstItem(detailResponse);
            if (detailItem != null && detailItem.isObject()) merged.setAll((ObjectNode) detailItem);
            else if (detailItem != null) merged.set("_detailPayload", detailItem);
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
        if (upsert(record)) counter.saved++;
        else counter.skipped++;
    }

    private boolean upsert(ExternalPolicyRecord record) {
        String hash = hash(record.rawPayload());
        Policy policy = policyRepository.findBySourceTypeAndExternalId(record.sourceType(), record.externalId())
                .orElseGet(() -> Policy.create(record.sourceType(), record.externalId(), record.title()));
        policy.updateFrom(record.title(), record.summary(), record.supportTarget(), record.selectionCriteria(),
                record.applicationMethod(), record.applicationStartDate(), record.applicationEndDate(),
                record.regionName(), record.categoryName(), record.officialUrl(), hash);
        boolean youthRelated = youthPolicyFilter.isYouthRelated(policy);
        policy.updateYouthRelated(youthRelated);
        if (youthRelated) policy.markUnindexed();
        Policy saved = policyRepository.save(policy);
        rawPayloadRepository.save(PolicyRawPayload.create(saved, record.sourceType(), record.externalId(), record.rawPayload()));
        return youthRelated;
    }

    private JsonNode firstItem(JsonNode response) {
        List<JsonNode> found = items(response);
        return found.isEmpty() ? response : found.getFirst();
    }

    static List<JsonNode> items(JsonNode response) {
        List<JsonNode> found = new ArrayList<>();
        collectItems(response, found);
        return found;
    }

    private static void collectItems(JsonNode node, List<JsonNode> found) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            node.forEach(child -> collectItems(child, found));
            return;
        }
        if (isPolicyItem(node)) {
            found.add(node);
            return;
        }
        if (node.isObject()) node.properties().forEach(entry -> collectItems(entry.getValue(), found));
    }

    private static boolean isPolicyItem(JsonNode node) {
        return node.isObject()
                && hasAnyText(node, "서비스ID", "serviceId", "servId", "plcyNo", "bizId", "policyId", "id")
                && hasAnyText(node, "서비스명", "serviceName", "servNm", "plcyNm", "polyBizSjnm", "policyName", "title");
    }

    private static boolean hasAnyText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && !value.asText().isBlank()) return true;
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
