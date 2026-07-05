package com.example.ragtest.external.client;

import com.example.ragtest.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class PublicServiceApiClient {

    private static final String BASE_URL = "https://api.odcloud.kr/api/gov24/v3";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serviceKey;

    public PublicServiceApiClient(
            @Value("${external-api.data-go-kr.service-key:}") String serviceKey
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = JsonMapper.builder().build();
        this.serviceKey = serviceKey;
    }

    public JsonNode fetchList(int page, int size) {
        return fetchList(page, size, null);
    }

    public JsonNode fetchList(int page, int size, String serviceNameLike) {
        requireServiceKey();
        String url = BASE_URL + "/serviceList?page=" + page
                + "&perPage=" + size
                + (serviceNameLike == null || serviceNameLike.isBlank()
                ? ""
                : "&cond%5B%EC%84%9C%EB%B9%84%EC%8A%A4%EB%AA%85%3A%3ALIKE%5D=" + encode(serviceNameLike))
                + "&serviceKey=" + encode(serviceKey);
        String json = get(url);
        return readJson(json);
    }

    public JsonNode fetchDetail(String externalId) {
        requireServiceKey();
        String url = BASE_URL + "/serviceDetail?page=1&perPage=1"
                + "&cond%5B%EC%84%9C%EB%B9%84%EC%8A%A4ID%3A%3AEQ%5D=" + encode(externalId)
                + "&serviceKey=" + encode(serviceKey);
        String json = get(url);
        return readJson(json);
    }

    private String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new BusinessException("공공서비스 API 호출 실패: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("공공서비스 API 호출에 실패했습니다: " + exception.getMessage());
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            String preview = json == null ? "null" : json.substring(0, Math.min(json.length(), 160));
            throw new BusinessException("공공서비스 API JSON 응답 파싱에 실패했습니다. 응답 시작: " + preview);
        }
    }

    private void requireServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new BusinessException("공공데이터포털 API 키가 설정되지 않았습니다.");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
