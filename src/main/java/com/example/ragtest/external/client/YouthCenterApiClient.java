package com.example.ragtest.external.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class YouthCenterApiClient {

    public JsonNode fetchList(int page, int size) {
        // TODO: 온통청년 API 활용 신청 완료 후 실제 목록 엔드포인트와 응답 필드를 매핑한다.
        return null;
    }

    public JsonNode fetchDetail(String externalId) {
        // TODO: 온통청년 API 상세 응답 샘플 확보 후 DTO 매핑을 완성한다.
        return null;
    }
}
