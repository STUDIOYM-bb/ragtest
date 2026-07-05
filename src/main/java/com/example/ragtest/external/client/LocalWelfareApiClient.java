package com.example.ragtest.external.client;

import com.example.ragtest.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class LocalWelfareApiClient {

    private static final String BASE_URL = "https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations";

    private final RestClient restClient;
    private final XmlMapper xmlMapper = new XmlMapper();
    private final String serviceKey;

    public LocalWelfareApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${external-api.data-go-kr.local-welfare-key:}") String serviceKey
    ) {
        this.restClient = restClientBuilder.build();
        this.serviceKey = serviceKey;
    }

    public JsonNode fetchList(int page, int size) {
        requireServiceKey();
        return readXml(get(BASE_URL + "/LcgvWelfarelist", page, size, null));
    }

    public JsonNode fetchDetail(String externalId) {
        requireServiceKey();
        return readXml(get(BASE_URL + "/LcgvWelfaredetailed", null, null, externalId));
    }

    private String get(String url, Integer page, Integer size, String externalId) {
        return restClient.get()
                .uri(buildUrl(url, page, size, externalId))
                .retrieve()
                .body(String.class);
    }

    private URI buildUrl(String url, Integer page, Integer size, String externalId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url)
                .queryParam("serviceKey", encodedServiceKey());
        if (page != null) {
            builder.queryParam("pageNo", page);
        }
        if (size != null) {
            builder.queryParam("numOfRows", size);
        }
        if (externalId != null && !externalId.isBlank()) {
            builder.queryParam("servId", externalId);
        }
        return builder.build(true).toUri();
    }

    private JsonNode readXml(String xml) {
        try {
            return xmlMapper.readTree(xml);
        } catch (Exception exception) {
            throw new BusinessException("지자체복지서비스 API XML 응답 파싱에 실패했습니다.");
        }
    }

    private void requireServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new BusinessException("DATA_GO_KR_LOCAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        }
    }

    private String encodedServiceKey() {
        if (serviceKey.matches(".*%[0-9a-fA-F]{2}.*")) {
            return serviceKey;
        }
        return URLEncoder.encode(serviceKey, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
