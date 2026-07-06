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
import java.util.List;
import java.util.Optional;

@Component
public class YouthCenterOfficialApiClient {
    private static final String DEFAULT_LIST_URL = "https://www.youthcenter.go.kr/opi/youthPlcyList.do";
    private static final int LOG_BODY_LIMIT = 1200;
    private static final int ERROR_BODY_LIMIT = 300;
    private static final int DEBUG_BODY_LIMIT = 1000;
    private static final Logger log = LoggerFactory.getLogger(YouthCenterOfficialApiClient.class);

    private final RestClient restClient;
    private final ObjectMapper jsonMapper = JsonMapper.builder().build();
    private final XmlMapper xmlMapper = new XmlMapper();
    private final String apiKey;
    private final String baseUrl;

    public YouthCenterOfficialApiClient(RestClient.Builder builder,
                                        @Value("${external-api.youth-center.official-api-key:}") String youthCenterKey,
                                        @Value("${external-api.youth-center.official-base-url:" + DEFAULT_LIST_URL + "}") String baseUrl) {
        this.restClient = builder.build();
        this.apiKey = youthCenterKey == null ? "" : youthCenterKey.strip();
        this.baseUrl = hasText(baseUrl) ? baseUrl.strip() : DEFAULT_LIST_URL;
    }

    public JsonNode fetchList(int page, int size) {
        return fetchList(page, size, "");
    }

    public JsonNode fetchList(int page, int size, String query) {
        return fetchList(page, size, query, null, null, null);
    }

    public JsonNode fetchList(int page, int size, String query, String keyword,
                              String bizTycdSel, String srchPolyBizSecd) {
        requireApiKey();
        ExternalApiRawResponse raw = fetchRaw(page, size, query, keyword, bizTycdSel, srchPolyBizSecd);
        validateRawResponse(raw);
        JsonNode parsed = parse(raw);
        ApiError error = findApiError(parsed);
        if (error != null) {
            log.warn("온통청년 API 오류 응답: code={}, message={}, url={}, body={}",
                    error.code(), error.message(), raw.requestUrlMasked(), safeSnippet(raw.body(), LOG_BODY_LIMIT));
            throw new BusinessException("온통청년 API 오류 응답: " + error.displayMessage()
                    + " | requestUrl=" + raw.requestUrlMasked());
        }
        return parsed;
    }

    public ExternalApiRawResponse fetchRaw(int page, int size, String query) {
        return fetchRaw(page, size, query, null, null, null);
    }

    public ExternalApiRawResponse fetchRaw(int page, int size, String query, String keyword,
                                           String bizTycdSel, String srchPolyBizSecd) {
        requireApiKey();
        URI uri = buildUri(page, size, query, keyword, bizTycdSel, srchPolyBizSecd, normalizedApiKey());
        String maskedUrl = buildUri(page, size, query, keyword, bizTycdSel, srchPolyBizSecd, maskedApiKey()).toString();
        try {
            ExternalApiRawResponse raw = restClient.get().uri(uri).exchange((request, response) -> {
                String contentType = Optional.ofNullable(response.getHeaders().getContentType())
                        .map(MediaType::toString)
                        .orElse("");
                String redirectLocation = Optional.ofNullable(response.getHeaders().getLocation())
                        .map(URI::toString)
                        .orElse("");
                String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                return toExternalApiRawResponse(maskedUrl, response.getStatusCode().value(), contentType, redirectLocation, body);
            });
            log.debug("온통청년 API raw 응답: url={}, status={}, contentType={}, looksLikeXml={}, looksLikeHtml={}",
                    raw.requestUrlMasked(), raw.statusCode(), raw.contentType(), raw.looksLikeXml(), raw.looksLikeHtml());
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
            return toExternalApiRawResponse(maskedUrl, exception.getStatusCode().value(), contentType, redirectLocation, body);
        }
    }

    public boolean isConfigured() {
        return hasText(normalizedApiKey());
    }

    public String apiKeyPreview() {
        return isConfigured() ? maskedApiKey() : "";
    }

    private URI buildUri(int page, int size, String query, String keyword,
                         String bizTycdSel, String srchPolyBizSecd, String keyValue) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("openApiVlak", keyValue)
                .queryParam("pageIndex", Math.max(1, page))
                .queryParam("display", Math.max(1, Math.min(size, 10)));
        addOptionalParam(builder, "query", query);
        addOptionalParam(builder, "keyword", keyword);
        addOptionalParam(builder, "bizTycdSel", bizTycdSel);
        addOptionalParam(builder, "srchPolyBizSecd", srchPolyBizSecd);
        return builder.build().encode(StandardCharsets.UTF_8).toUri();
    }

    private void addOptionalParam(UriComponentsBuilder builder, String name, String value) {
        if (hasText(value)) {
            builder.queryParam(name, value);
        }
    }

    private JsonNode parse(ExternalApiRawResponse raw) {
        try {
            String body = raw.body();
            if (body != null && (body.stripLeading().startsWith("{") || body.stripLeading().startsWith("["))) {
                return jsonMapper.readTree(body);
            }
            return xmlMapper.readTree(body);
        } catch (Exception exception) {
            log.warn("온통청년 API 응답 파싱 실패: {}, url={}, status={}, contentType={}, body={}",
                    exception.getMessage(), raw.requestUrlMasked(), raw.statusCode(), raw.contentType(),
                    safeSnippet(raw.body(), LOG_BODY_LIMIT));
            throw new BusinessException("온통청년 API 응답 파싱 실패: " + exception.getMessage()
                    + ". HTML 응답인지 확인 필요. 관리자 테스트의 '온통청년 공식 원본 확인'으로 실제 응답을 확인하세요."
                    + " | statusCode=" + raw.statusCode()
                    + " | contentType=" + raw.contentType()
                    + " | redirectLocation=" + raw.redirectLocation()
                    + " | requestUrl=" + raw.requestUrlMasked()
                    + " | bodyPreview=" + safeSnippet(raw.body(), ERROR_BODY_LIMIT));
        }
    }

    private void validateRawResponse(ExternalApiRawResponse raw) {
        if (raw.statusCode() == 302) {
            log.warn("온통청년 공식 API 302 리다이렉트: url={}, location={}, contentType={}, body={}",
                    raw.requestUrlMasked(), raw.redirectLocation(), raw.contentType(), safeSnippet(raw.body(), LOG_BODY_LIMIT));
            throw new BusinessException("온통청년 공식 API가 302 리다이렉트를 반환했습니다. "
                    + "공식 API 키가 맞는지, 또는 공공데이터포털 키를 잘못 넣은 것은 아닌지 확인하세요. "
                    + "공공데이터포털에서 발급받은 키라면 공공데이터포털 온통청년 API 수집을 사용하세요."
                    + " | statusCode=" + raw.statusCode()
                    + " | contentType=" + raw.contentType()
                    + " | redirectLocation=" + raw.redirectLocation()
                    + " | requestUrl=" + raw.requestUrlMasked()
                    + " | bodyPreview=" + safeSnippet(raw.body(), ERROR_BODY_LIMIT));
        }
        if (raw.looksLikeHtml()) {
            log.warn("온통청년 공식 API HTML 응답: url={}, status={}, contentType={}, body={}",
                    raw.requestUrlMasked(), raw.statusCode(), raw.contentType(), safeSnippet(raw.body(), LOG_BODY_LIMIT));
            throw new BusinessException("공식 온통청년 API가 XML이 아닌 HTML을 반환했습니다. "
                    + "공식 API 키, 요청 URL, 파라미터(openApiVlak)를 확인하세요. "
                    + "공공데이터포털에서 발급받은 키라면 공공데이터포털 온통청년 API 수집을 사용하세요."
                    + " | statusCode=" + raw.statusCode()
                    + " | contentType=" + raw.contentType()
                    + " | redirectLocation=" + raw.redirectLocation()
                    + " | requestUrl=" + raw.requestUrlMasked()
                    + " | bodyPreview=" + safeSnippet(raw.body(), ERROR_BODY_LIMIT));
        }
        if (raw.statusCode() < 200 || raw.statusCode() >= 300) {
            log.warn("온통청년 API HTTP 오류: url={}, status={}, contentType={}, body={}",
                    raw.requestUrlMasked(), raw.statusCode(), raw.contentType(), safeSnippet(raw.body(), LOG_BODY_LIMIT));
            throw new BusinessException("온통청년 API 호출 실패: HTTP " + raw.statusCode()
                    + " - " + httpStatusMessage(raw.statusCode())
                    + " | contentType=" + raw.contentType()
                    + " | redirectLocation=" + raw.redirectLocation()
                    + " | requestUrl=" + raw.requestUrlMasked()
                    + " | bodyPreview=" + safeSnippet(raw.body(), ERROR_BODY_LIMIT));
        }
        if (!raw.looksLikeXml() && !looksLikeJson(raw.body())) {
            log.warn("온통청년 API 비XML 응답: url={}, status={}, contentType={}, body={}",
                    raw.requestUrlMasked(), raw.statusCode(), raw.contentType(), safeSnippet(raw.body(), LOG_BODY_LIMIT));
            throw new BusinessException("온통청년 API 응답이 XML로 보이지 않습니다. HTML 응답인지 확인 필요. "
                    + "관리자 테스트의 '온통청년 공식 원본 확인'으로 실제 응답을 확인하세요."
                    + " | statusCode=" + raw.statusCode()
                    + " | contentType=" + raw.contentType()
                    + " | redirectLocation=" + raw.redirectLocation()
                    + " | requestUrl=" + raw.requestUrlMasked()
                    + " | bodyPreview=" + safeSnippet(raw.body(), ERROR_BODY_LIMIT));
        }
    }

    private void requireApiKey() {
        if (!isConfigured()) {
            throw new BusinessException("YOUTH_CENTER_API_KEY가 설정되지 않았습니다.");
        }
    }

    private ApiError findApiError(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String code = firstText(node, "errorCode", "errCd", "resultCode", "code");
        String message = firstText(node, "errorMessage", "errMsg", "resultMsg", "message");
        if (!hasText(code) && !hasText(message)) return null;
        if (isSuccessCode(code)) return null;
        if (hasText(code) || looksLikeError(message)) {
            return new ApiError(code, message);
        }
        return null;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            List<JsonNode> values = node.findValues(name);
            for (JsonNode value : values) {
                if (value != null && !value.isNull() && hasText(value.asText())) {
                    return value.asText().strip();
                }
            }
        }
        return "";
    }

    private boolean isSuccessCode(String code) {
        if (!hasText(code)) return false;
        String normalized = code.strip();
        return "0".equals(normalized) || "00".equals(normalized)
                || "0000".equals(normalized) || "INFO-000".equalsIgnoreCase(normalized)
                || "SUCCESS".equalsIgnoreCase(normalized) || "OK".equalsIgnoreCase(normalized);
    }

    private boolean looksLikeError(String message) {
        if (!hasText(message)) return false;
        String lower = message.toLowerCase();
        return lower.contains("error") || message.contains("오류") || message.contains("실패")
                || message.contains("인증") || message.contains("권한") || message.contains("승인")
                || message.contains("허용") || message.contains("잘못");
    }

    private String httpStatusMessage(int status) {
        return switch (status) {
            case 401 -> "인증에 실패했습니다. YOUTH_CENTER_API_KEY 값이 온통청년 공식 openApiVlak 키인지 확인하세요.";
            case 403 -> "접근이 거부되었습니다. 온통청년 OPEN API 승인 상태를 확인하세요.";
            case 500 -> "온통청년 서버 오류가 발생했습니다.";
            default -> "온통청년 공식 API 응답 상태를 확인하세요.";
        };
    }

    private ExternalApiRawResponse toExternalApiRawResponse(String maskedUrl, int statusCode, String contentType,
                                                            String redirectLocation, String body) {
        return new ExternalApiRawResponse("OFFICIAL", maskedUrl, statusCode, contentType, safe(redirectLocation),
                looksLikeXml(contentType, body), looksLikeJson(body), looksLikeHtml(contentType, body),
                safeSnippet(body, DEBUG_BODY_LIMIT), body == null ? "" : body);
    }

    private boolean looksLikeXml(String contentType, String body) {
        String lowerContentType = safe(contentType).toLowerCase();
        String stripped = safe(body).stripLeading().toLowerCase();
        return (lowerContentType.contains("xml") || stripped.startsWith("<?xml") || stripped.startsWith("<"))
                && !looksLikeHtml(contentType, body);
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

    private boolean looksLikeJson(String body) {
        String stripped = safe(body).stripLeading();
        return stripped.startsWith("{") || stripped.startsWith("[");
    }

    private String safeSnippet(String body, int limit) {
        if (body == null) return "";
        String sanitized = body;
        if (hasText(normalizedApiKey())) {
            sanitized = sanitized.replace(normalizedApiKey(), "[REDACTED_API_KEY]");
        }
        sanitized = sanitized.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ").strip();
        return sanitized.length() <= limit ? sanitized : sanitized.substring(0, limit) + "...";
    }

    private String normalizedApiKey() {
        String value = apiKey == null ? "" : apiKey.strip();
        if (value.matches(".*%[0-9a-fA-F]{2}.*")) {
            try {
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                return value;
            }
        }
        return value;
    }

    private String maskedApiKey() {
        String value = normalizedApiKey();
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

    private record ApiError(String code, String message) {
        String displayMessage() {
            if (code == null || code.isBlank()) return message;
            if (message == null || message.isBlank()) return code;
            return code + " - " + message;
        }
    }
}
