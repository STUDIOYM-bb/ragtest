package com.example.ragtest.external.client;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ExternalApiRawResponse(
        String apiType,
        String requestUrlMasked,
        int statusCode,
        String contentType,
        String redirectLocation,
        boolean looksLikeXml,
        boolean looksLikeJson,
        boolean looksLikeHtml,
        String bodyPreview,
        @JsonIgnore String body
) {
}
