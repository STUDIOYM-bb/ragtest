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
public class CentralWelfareApiClient {

    private static final String BASE_URL = "https://apis.data.go.kr/B554287/NationalWelfareInformationsV001";

    private final RestClient restClient;
    private final XmlMapper xmlMapper = new XmlMapper();
    private final String serviceKey;

    public CentralWelfareApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${external-api.data-go-kr.central-welfare-key:}") String serviceKey
    ) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.serviceKey = serviceKey;
    }

    public JsonNode fetchList(int page, int size) {
        requireServiceKey();
        String xml = restClient.get()
                .uri(buildUri("/NationalWelfarelistV001")
                        .queryParam("callTp", "L")
                        .queryParam("pageNo", page)
                        .queryParam("numOfRows", size)
                        .queryParam("srchKeyCode", "003")
                        .build(true).toUri())
                .retrieve()
                .body(String.class);
        return readXml(xml);
    }

    public JsonNode fetchDetail(String externalId) {
        requireServiceKey();
        String xml = restClient.get()
                .uri(buildUri("/NationalWelfaredetailedV001")
                        .queryParam("callTp", "D")
                        .queryParam("servId", externalId)
                        .build(true).toUri())
                .retrieve()
                .body(String.class);
        return readXml(xml);
    }

    private JsonNode readXml(String xml) {
        try {
            return xmlMapper.readTree(xml);
        } catch (Exception exception) {
            throw new BusinessException("중앙부처복지서비스 API XML 응답 파싱에 실패했습니다.");
        }
    }

    private void requireServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new BusinessException("DATA_GO_KR_CENTRAL_WELFARE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        }
    }

    private UriComponentsBuilder buildUri(String path) {
        return UriComponentsBuilder.fromUri(URI.create(BASE_URL + path))
                .queryParam("serviceKey", encodedServiceKey());
    }

    private String encodedServiceKey() {
        if (serviceKey.matches(".*%[0-9a-fA-F]{2}.*")) {
            return serviceKey;
        }
        return URLEncoder.encode(serviceKey, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
