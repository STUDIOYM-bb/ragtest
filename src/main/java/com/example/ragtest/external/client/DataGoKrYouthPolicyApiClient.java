package com.example.ragtest.external.client;

import com.example.ragtest.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class DataGoKrYouthPolicyApiClient {
    private static final int LOG_BODY_LIMIT = 1200;
    private static final int ERROR_BODY_LIMIT = 300;
    private static final int DEBUG_BODY_LIMIT = 1000;
    private static final Logger log = LoggerFactory.getLogger(DataGoKrYouthPolicyApiClient.class);

    private final RestClient restClient;
    private final ObjectMapper jsonMapper = JsonMapper.builder().build();
    private final XmlMapper xmlMapper = new XmlMapper();
    private final String serviceKey;
    private final String baseUrl;

    public DataGoKrYouthPolicyApiClient(
            RestClient.Builder builder,
            @Value("${external-api.data-go-kr.youth-policy-key:}") String serviceKey,
            @Value("${external-api.data-go-kr.youth-policy-base-url:}") String baseUrl
    ) {
        this.restClient = builder.build();
        this.serviceKey = serviceKey == null ? "" : serviceKey.strip();
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
    }

    public JsonNode fetchList(int page, int size) {
        return fetchList(page, size, "");
    }

    public JsonNode fetchList(int page, int size, String query) {
        ExternalApiRawResponse raw = fetchRaw(page, size, query);
        validateRawResponse(raw);
        return parse(raw);
    }

    public ExternalApiRawResponse fetchRaw(int page, int size, String query) {
        requireConfigured();
        URI uri = buildUri(page, size, query, normalizedServiceKey());
        String maskedUrl = buildUri(page, size, query, maskedServiceKey()).toString();
        try {
            ExternalApiRawResponse raw = restClient.get().uri(uri).exchange((request, response) -> {
                String contentType = Optional.ofNullable(response.getHeaders().getContentType())
                        .map(MediaType::toString)
                        .orElse("");
                String redirectLocation = Optional.ofNullable(response.getHeaders().getLocation())
                        .map(URI::toString)
                        .orElse("");
                String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                return toRawResponse(maskedUrl, response.getStatusCode().value(), contentType, redirectLocation, body);
            });
            log.debug("공공데이터포털 온통청년 raw 응답: url={}, status={}, contentType={}, looksLikeJson={}, looksLikeXml={}, looksLikeHtml={}",
                    raw.requestUrlMasked(), raw.statusCode(), raw.contentType(),
                    raw.looksLikeJson(), raw.looksLikeXml(), raw.looksLikeHtml());
            return raw;
        } catch (RestClientResponseException exception) {
            String body = exception.getResponseBodyAsString(StandardCharsets.UTF_8);
            String contentType = Optional.ofNullable(exception.getResponseHeaders())
                    .map(headers -> headers.getContentType())
                    .map(MediaType::toString)
                    .orElse("");
            String redirectLocation = Optional.ofNullable(exception.getResponseHeaders())
                    .map(headers -> headers.getLocation())
                    .map(URI::toString)
                    .orElse("");
            return toRawResponse(maskedUrl, exception.getStatusCode().value(), contentType, redirectLocation, body);
        }
    }

    public boolean isConfigured() {
        return hasText(normalizedServiceKey()) && isBaseUrlConfigured();
    }

    public boolean isKeyConfigured() {
        return hasText(normalizedServiceKey());
    }

    public boolean isBaseUrlConfigured() {
        return hasText(baseUrl);
    }

    public String keyPreview() {
        return isKeyConfigured() ? maskedServiceKey() : "";
    }

    public String baseUrlMasked() {
        if (!hasText(baseUrl)) return "";
        String masked = baseUrl;
        if (hasText(normalizedServiceKey())) {
            masked = masked.replace(normalizedServiceKey(), "[REDACTED_SERVICE_KEY]");
        }
        return masked;
    }

    private URI buildUri(int page, int size, String query, String keyValue) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .replaceQueryParam("serviceKey", keyValue)
                .replaceQueryParam("pageNo", Math.max(1, page))
                .replaceQueryParam("numOfRows", Math.max(1, size))
                .replaceQueryParam("_type", "json")
                .replaceQueryParam("type", "json");
        if (hasText(query)) {
            builder.replaceQueryParam("query", query.strip());
            builder.replaceQueryParam("keyword", query.strip());
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUri();
    }

    private JsonNode parse(ExternalApiRawResponse raw) {
        try {
            String body = raw.body();
            if (raw.looksLikeJson()) {
                return jsonMapper.readTree(body);
            }
            return xmlMapper.readTree(body);
        } catch (Exception exception) {
            log.warn("공공데이터포털 온통청년 응답 파싱 실패: {}, url={}, status={}, contentType={}, body={}",
                    exception.getMessage(), raw.requestUrlMasked(), raw.statusCode(), raw.contentType(),
                    safeSnippet(raw.body(), LOG_BODY_LIMIT));
            throw new BusinessException("공공데이터포털 온통청년 API 응답 파싱 실패: " + exception.getMessage()
                    + ". HTML 응답인지 확인 필요. 관리자 테스트의 '공공데이터 온통청년 원본 확인'으로 실제 응답을 확인하세요."
                    + " | statusCode=" + raw.statusCode()
                    + " | contentType=" + raw.contentType()
                    + " | redirectLocation=" + raw.redirectLocation()
                    + " | requestUrl=" + raw.requestUrlMasked()
                    + " | bodyPreview=" + safeSnippet(raw.body(), ERROR_BODY_LIMIT));
        }
    }

    private void validateRawResponse(ExternalApiRawResponse raw) {
        if (raw.looksLikeHtml()) {
            throw new BusinessException("공공데이터포털 온통청년 API가 JSON/XML이 아닌 HTML 응답을 반환했습니다. "
                    + "DATA_GO_KR_YOUTH_POLICY_BASE_URL과 serviceKey 파라미터를 확인하세요."
                    + " | statusCode=" + raw.statusCode()
                    + " | contentType=" + raw.contentType()
                    + " | redirectLocation=" + raw.redirectLocation()
                    + " | requestUrl=" + raw.requestUrlMasked()
                    + " | bodyPreview=" + safeSnippet(raw.body(), ERROR_BODY_LIMIT));
        }
        if (raw.statusCode() < 200 || raw.statusCode() >= 300) {
            throw new BusinessException("공공데이터포털 온통청년 API 호출 실패: HTTP " + raw.statusCode()
                    + " | contentType=" + raw.contentType()
                    + " | redirectLocation=" + raw.redirectLocation()
                    + " | requestUrl=" + raw.requestUrlMasked()
                    + " | bodyPreview=" + safeSnippet(raw.body(), ERROR_BODY_LIMIT));
        }
        if (!raw.looksLikeJson() && !raw.looksLikeXml()) {
            throw new BusinessException("공공데이터포털 온통청년 API 응답이 JSON/XML로 보이지 않습니다."
                    + " | statusCode=" + raw.statusCode()
                    + " | contentType=" + raw.contentType()
                    + " | requestUrl=" + raw.requestUrlMasked()
                    + " | bodyPreview=" + safeSnippet(raw.body(), ERROR_BODY_LIMIT));
        }
    }

    private void requireConfigured() {
        if (!isBaseUrlConfigured()) {
            throw new BusinessException("DATA_GO_KR_YOUTH_POLICY_BASE_URL이 설정되지 않았습니다. "
                    + "공공데이터포털 활용신청 상세의 요청 URL을 .env에 입력하세요.");
        }
        if (!isKeyConfigured()) {
            throw new BusinessException("DATA_GO_KR_YOUTH_POLICY_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        }
    }

    private ExternalApiRawResponse toRawResponse(String maskedUrl, int statusCode, String contentType,
                                                 String redirectLocation, String body) {
        return new ExternalApiRawResponse("DATA_GO_KR", maskedUrl, statusCode, safe(contentType), safe(redirectLocation),
                looksLikeXml(contentType, body), looksLikeJson(body), looksLikeHtml(contentType, body),
                safeSnippet(body, DEBUG_BODY_LIMIT), body == null ? "" : body);
    }

    private boolean looksLikeXml(String contentType, String body) {
        String lowerContentType = safe(contentType).toLowerCase();
        String stripped = safe(body).stripLeading().toLowerCase();
        return (lowerContentType.contains("xml") || stripped.startsWith("<?xml") || stripped.startsWith("<"))
                && !looksLikeHtml(contentType, body);
    }

    private boolean looksLikeJson(String body) {
        String stripped = safe(body).stripLeading();
        return stripped.startsWith("{") || stripped.startsWith("[");
    }

    private boolean looksLikeHtml(String contentType, String body) {
        String lowerContentType = safe(contentType).toLowerCase();
        String lowerBody = safe(body).stripLeading().toLowerCase();
        return lowerContentType.contains("text/html")
                || lowerBody.startsWith("<html")
                || lowerBody.startsWith("<!doctype html")
                || lowerBody.startsWith("<body")
                || lowerBody.contains("<script")
                || lowerBody.contains("<head")
                || lowerBody.contains("</body>");
    }

    private String safeSnippet(String body, int limit) {
        if (body == null) return "";
        String sanitized = body;
        if (hasText(normalizedServiceKey())) {
            sanitized = sanitized.replace(normalizedServiceKey(), "[REDACTED_SERVICE_KEY]");
        }
        sanitized = sanitized.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ").strip();
        return sanitized.length() <= limit ? sanitized : sanitized.substring(0, limit) + "...";
    }

    private String normalizedServiceKey() {
        String value = serviceKey == null ? "" : serviceKey.strip();
        if (value.matches(".*%[0-9a-fA-F]{2}.*")) {
            try {
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                return value;
            }
        }
        return value;
    }

    private String maskedServiceKey() {
        String value = normalizedServiceKey();
        if (!hasText(value)) return "";
        if (value.length() <= 8) return value.charAt(0) + "****" + value.charAt(value.length() - 1);
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
