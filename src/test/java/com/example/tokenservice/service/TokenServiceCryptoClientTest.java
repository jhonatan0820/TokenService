package com.example.tokenservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TokenServiceCryptoClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WEB_TOKEN = "web-ui-access-token";

    @Test
    void backendEncryptionUsesTheGlobalPublicContractWithoutAuthorizationHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://crypto.test/api/crypto/encrypt"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"data\":{\"subject\":\"test\"}}"))
                .andRespond(withSuccess("{\"data\":\"ciphertext-v4\"}", MediaType.APPLICATION_JSON));
        assertThat(service(builder).encryptPayload(MAPPER.createObjectNode().put("subject", "test"), false))
                .isEqualTo("ciphertext-v4");
        server.verify();
    }

    @Test
    void backendDecryptionUsesTheGlobalPublicContractWithoutAuthorizationHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://crypto.test/api/crypto/decrypt"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"data\":\"ciphertext-v4\"}"))
                .andRespond(withSuccess("{\"data\":{\"subject\":\"test\"}}", MediaType.APPLICATION_JSON));
        assertThat(service(builder).decryptPayload("ciphertext-v4", false).path("subject").asText())
                .isEqualTo("test");
        server.verify();
    }

    @Test
    void webResponsesUseTheTokenServiceProfileInsideData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://crypto.test/api/crypto/encrypt"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"data\":{\"__crypto\":{\"profile\":\"TOKN\"},\"payload\":{\"subject\":\"test\"}}}"))
                .andRespond(withSuccess("{\"data\":\"web-ciphertext\"}", MediaType.APPLICATION_JSON));
        assertThat(service(builder).encryptPayload(MAPPER.createObjectNode().put("subject", "test"), true))
                .isEqualTo("web-ciphertext");
        server.verify();
    }

    @Test
    void webRequestsReceiveTheTokenServicePublicKeyProfile() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://crypto.test/api/crypto/public-key?profile=TOKN"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(withSuccess("{\"publicKeyBase64\":\"token-web-key\"}", MediaType.APPLICATION_JSON));
        assertThat(service(builder).publicKeyBase64(true)).isEqualTo("token-web-key");
        server.verify();
    }

    private TokenService service(RestClient.Builder builder) {
        return new TokenService(Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")), MAPPER, builder,
                "https://crypto.test", "token-service-api-key", WEB_TOKEN, "TOKN", 120);
    }
}
