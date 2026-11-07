package com.example.tokenservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {
    private final SecretKey key;
    private final ObjectMapper mapper;
    private final RestClient cryptoClient;
    private final long expirationSeconds;

    public TokenService(SecretKey key, ObjectMapper mapper,
            @Value("${crypto.base-url}") String cryptoUrl,
            @Value("${jwt.expiration-seconds:120}") long expirationSeconds) {
        this.key = key;
        this.mapper = mapper;
        this.cryptoClient = RestClient.builder().baseUrl(cryptoUrl).build();
        this.expirationSeconds = expirationSeconds;
    }

    public String createEncryptedFromEncryptedData(String encryptedData) {
        return createEncryptedFromEncryptedData(encryptedData, false);
    }

    public String createEncryptedFromEncryptedData(String encryptedData, boolean web) {
        JsonNode originalData = decryptData(encryptedData, web);
        if (!originalData.isObject()) {
            throw new IllegalArgumentException("cryptoService debe devolver data como objeto JSON");
        }
        return createEncrypted(originalData, web);
    }

    private String createEncrypted(JsonNode data, boolean web) {
        String jwt = Jwts.builder().id(UUID.randomUUID().toString()).issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(expirationSeconds)))
                .claim("data", data).signWith(key).compact();
        return encrypt(jwt, web);
    }

    public String tokenStatus(String encryptedToken) {
        return tokenStatus(encryptedToken, false);
    }

    public String tokenStatus(String encryptedToken, boolean web) {
        JsonNode decrypted = decryptData(encryptedToken, web);
        JsonNode text = decrypted.path("text");
        if (!text.isTextual()) {
            throw new IllegalArgumentException("Respuesta decrypt inválida: se esperaba data.text con el JWT");
        }
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(text.asText());
            return "ACTIVO";
        } catch (ExpiredJwtException ex) {
            return "VENCIDO";
        }
    }

    public JsonNode validate(String encryptedToken) {
        return validate(encryptedToken, false);
    }

    public JsonNode validate(String encryptedToken, boolean web) {
        JsonNode decrypted = decryptData(encryptedToken, web);
        JsonNode text = decrypted.path("text");
        if (!text.isTextual()) {
            throw new IllegalArgumentException("Respuesta decrypt inválida: se esperaba data.text con el JWT");
        }
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(text.asText()).getPayload();
        return mapper.valueToTree(claims.get("data"));
    }

    public String refresh(String encryptedToken) {
        return refresh(encryptedToken, false);
    }

    public String refresh(String encryptedToken, boolean web) {
        JsonNode data = validate(encryptedToken, web);
        return createEncrypted(data, web);
    }

    private String encrypt(String jwt, boolean web) {
        ObjectNode cryptoData = mapper.createObjectNode().put("text", jwt);
        ObjectNode body = mapper.createObjectNode().set("data", cryptoData);
        String path = web ? "/cryptoService/web/encrypt" : "/cryptoService/encrypt";
        JsonNode response = cryptoClient.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        JsonNode encrypted = response == null ? null : response.path("data");
        if (encrypted == null || !encrypted.isTextual()) {
            throw new IllegalStateException("Respuesta de encrypt inválida: se esperaba data string");
        }
        return encrypted.asText();
    }

    private JsonNode decryptData(String encryptedValue, boolean web) {
        validateEncryptedFormat(encryptedValue);
        ObjectNode body = mapper.createObjectNode().put("data", encryptedValue);
        JsonNode response;
        String path = web ? "/cryptoService/web/decrypt" : "/cryptoService/decrypt";
        try {
            response = cryptoClient.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La data está cifrada, pero está corrupta o fue alterada.", ex);
            }
            throw ex;
        }
        JsonNode data = response == null ? null : response.get("data");
        if (data == null || data.isNull()) {
            throw new IllegalArgumentException("Respuesta de decrypt inválida: se esperaba el campo data");
        }
        return data;
    }

    private void validateEncryptedFormat(String value) {
        if (isCompactJwt(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La data no está cifrada. Envía el token cifrado en Base64.");
        }

        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length <= 28) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La data no está cifrada. Envía el token cifrado en Base64.");
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La data no está cifrada. Envía el token cifrado en Base64.", ex);
        }
    }

    private boolean isCompactJwt(String value) {
        String[] parts = value.split("\\.", -1);
        return parts.length == 3 && !parts[0].isBlank() && !parts[1].isBlank() && !parts[2].isBlank();
    }
}
