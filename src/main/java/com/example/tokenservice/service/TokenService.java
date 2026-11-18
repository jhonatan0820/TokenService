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
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {
    private final SecretKey key;
    private final ObjectMapper mapper;
    private final RestClient crypto;
    private final String serviceToken;
    private final String webToken;
    private final long expirationSeconds;
    private final ConcurrentHashMap<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    public TokenService(SecretKey key, ObjectMapper mapper,
            @Value("${crypto.base-url}") String cryptoUrl,
            @Value("${crypto.service-token}") String serviceToken,
            @Value("${crypto.web-token}") String webToken,
            @Value("${jwt.expiration-seconds:120}") long expirationSeconds) {
        this.key = key;
        this.mapper = mapper;
        this.crypto = RestClient.builder().baseUrl(cryptoUrl).build();
        this.serviceToken = serviceToken;
        this.webToken = webToken;
        this.expirationSeconds = expirationSeconds;
    }

    public String webToken() { return webToken; }
    public boolean acceptsWebToken(String supplied) { return tokenMatches(supplied, webToken); }
    public boolean acceptsServiceToken(String supplied) { return tokenMatches(supplied, serviceToken); }

    private boolean tokenMatches(String supplied, String expected) {
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private String prefix(boolean web) { return web ? "/cryptoService/web" : "/cryptoService"; }
    private String token(boolean web) { return web ? webToken : serviceToken; }
    private String tokenHeader(boolean web) { return web ? "X-Web-Token" : "X-Service-Token"; }

    public String createEncryptedFromEncryptedData(String data, boolean web) {
        JsonNode original = decryptData(data, web);
        if (!original.isObject()) throw new IllegalArgumentException("EncryptService debe devolver data como objeto JSON");
        return createEncrypted(original, web);
    }

    private String createEncrypted(JsonNode data, boolean web) {
        String jwt = Jwts.builder().id(UUID.randomUUID().toString()).issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(expirationSeconds)))
                .claim("data", data).signWith(key).compact();
        ObjectNode text = mapper.createObjectNode().put("text", jwt);
        return encryptPayload(text, null, web);
    }

    public String tokenStatus(String encryptedToken, boolean web) {
        JsonNode text = decryptData(encryptedToken, web).path("text");
        if (!text.isTextual()) throw new IllegalArgumentException("El token descifrado no contiene el JWT");
        try {
            Claims claims = parseClaims(text.asText());
            return isRevoked(claims) ? "VENCIDO" : "ACTIVO";
        } catch (ExpiredJwtException ex) {
            return "VENCIDO";
        }
    }

    public JsonNode validate(String encryptedToken, boolean web) {
        JsonNode text = decryptData(encryptedToken, web).path("text");
        if (!text.isTextual()) throw new IllegalArgumentException("El token descifrado no contiene el JWT");
        Claims claims = parseClaims(text.asText());
        if (isRevoked(claims)) throw revokedTokenException();
        return mapper.valueToTree(claims.get("data"));
    }

    /** Creates the replacement first, then revokes the old JWT until its original expiration. */
    public synchronized String refresh(String encryptedToken, boolean web) {
        JsonNode text = decryptData(encryptedToken, web).path("text");
        if (!text.isTextual()) throw new IllegalArgumentException("El token descifrado no contiene el JWT");
        Claims claims = parseClaims(text.asText());
        if (isRevoked(claims)) throw revokedTokenException();

        JsonNode data = mapper.valueToTree(claims.get("data"));
        String replacement = createEncrypted(data, web);
        String tokenId = claims.getId();
        Date expiration = claims.getExpiration();
        if (tokenId == null || expiration == null)
            throw new IllegalArgumentException("El token no contiene identificador o expiración");
        revokedTokens.put(tokenId, expiration.toInstant());
        return replacement;
    }

    private Claims parseClaims(String jwt) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();
    }

    private boolean isRevoked(Claims claims) {
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        return claims.getId() != null && revokedTokens.containsKey(claims.getId());
    }

    private ResponseStatusException revokedTokenException() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token vencido o desactivado por renovación");
    }

    public String publicKeyBase64(boolean web) {
        JsonNode response = crypto.get().uri(web ? "/cryptoService/web/public-key" : "/cryptoService/public-key")
                .retrieve().body(JsonNode.class);
        JsonNode value = response == null ? null : response.get("publicKeyBase64");
        if (value == null || !value.isTextual()) throw new IllegalStateException("EncryptService no entregó su llave pública");
        return value.asText();
    }

    public JsonNode decryptPayload(String data, boolean web) {
        var request = crypto.post().uri(prefix(web) + "/decrypt").header(tokenHeader(web), token(web));
        if (!web) request.header("X-Service-Code", "TOKN");
        JsonNode response = request
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("data", data)).retrieve().body(JsonNode.class);
        JsonNode result = response == null ? null : response.get("data");
        if (result == null || result.isNull()) throw new IllegalArgumentException("Respuesta decrypt inválida");
        return result;
    }

    public String encryptPayload(JsonNode payload, String recipientPublicKey, boolean web) {
        ObjectNode request = mapper.createObjectNode().set("data", payload);
        if (recipientPublicKey != null && !recipientPublicKey.isBlank()) request.put("recipientPublicKey", recipientPublicKey);
        var call = crypto.post().uri(prefix(web) + "/encrypt").header(tokenHeader(web), token(web));
        if (!web && (recipientPublicKey == null || recipientPublicKey.isBlank())) call.header("X-Service-Code", "TOKN");
        JsonNode response = call
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);
        JsonNode data = response == null ? null : response.get("data");
        if (data == null || !data.isTextual()) throw new IllegalStateException("Respuesta encrypt inválida");
        return data.asText();
    }

    private JsonNode decryptData(String value, boolean web) {
        if (value == null || value.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo data cifrado es obligatorio");
        return decryptPayload(value, web);
    }
}
