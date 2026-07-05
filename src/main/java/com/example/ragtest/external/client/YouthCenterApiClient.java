package com.example.ragtest.external.client;

import com.example.ragtest.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class YouthCenterApiClient {
    private static final String LIST_URL = "https://www.youthcenter.go.kr/opi/youthPlcyList.do";

    private final RestClient restClient;
    private final ObjectMapper jsonMapper = JsonMapper.builder().build();
    private final XmlMapper xmlMapper = new XmlMapper();
    private final String apiKey;

    public YouthCenterApiClient(RestClient.Builder builder,
                                @Value("${external-api.youth-center.api-key:}") String youthCenterKey,
                                @Value("${external-api.data-go-kr.youth-policy-key:}") String dataGoKrYouthPolicyKey) {
        this.restClient = builder.build();
        this.apiKey = hasText(youthCenterKey) ? youthCenterKey : dataGoKrYouthPolicyKey;
    }

    public JsonNode fetchList(int page, int size) {
        requireApiKey();
        URI uri = URI.create(LIST_URL + "?openApiVlak=" + encode(apiKey)
                + "&pageIndex=" + page + "&display=" + size);
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        return parse(body);
    }

    public boolean isConfigured() {
        return hasText(apiKey);
    }

    private JsonNode parse(String body) {
        try {
            if (body != null && (body.stripLeading().startsWith("{") || body.stripLeading().startsWith("["))) {
                return jsonMapper.readTree(body);
            }
            return xmlMapper.readTree(body);
        } catch (Exception exception) {
            throw new BusinessException("온통청년 API 응답 파싱 실패: " + exception.getMessage());
        }
    }

    private void requireApiKey() {
        if (!isConfigured()) {
            throw new BusinessException("YOUTH_CENTER_API_KEY 또는 DATA_GO_KR_YOUTH_POLICY_KEY가 설정되지 않았습니다.");
        }
    }

    private String encode(String value) {
        if (value.matches(".*%[0-9a-fA-F]{2}.*")) return value;
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
