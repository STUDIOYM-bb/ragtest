package com.example.ragtest.external.client;

import com.example.ragtest.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CentralWelfareApiClient {

    private static final String BASE_URL = "https://apis.data.go.kr/B554287/NationalWelfareInformationsV001";

    private final RestClient restClient;
    private final XmlMapper xmlMapper = new XmlMapper();
    private final String serviceKey;

    public CentralWelfareApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${external-api.data-go-kr.service-key:}") String serviceKey
    ) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.serviceKey = serviceKey;
    }

    public JsonNode fetchList(int page, int size) {
        requireServiceKey();
        String xml = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/NationalWelfarelist")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("pageNo", page)
                        .queryParam("numOfRows", size)
                        .build())
                .retrieve()
                .body(String.class);
        return readXml(xml);
    }

    public JsonNode fetchDetail(String externalId) {
        requireServiceKey();
        String xml = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/NationalWelfaredetailed")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("servId", externalId)
                        .build())
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
            throw new BusinessException("공공데이터포털 API 키가 설정되지 않았습니다.");
        }
    }
}
