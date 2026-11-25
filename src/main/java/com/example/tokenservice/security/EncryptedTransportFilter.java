package com.example.tokenservice.security;

import com.example.tokenservice.service.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class EncryptedTransportFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final ObjectMapper mapper;
    public EncryptedTransportFilter(TokenService tokens, ObjectMapper mapper) { this.tokens = tokens; this.mapper = mapper; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/") || path.equals("/tokenService/config") || path.equals("/tokenService/health")
                || path.matches(".+\\.(html|css|js|svg|ico|png|jpg|jpeg|woff2?)$");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean web = request.getHeader("X-Web-Token") != null;
        String suppliedToken = request.getHeader(web ? "X-Web-Token" : "X-Encrypt-Token");
        boolean authorized = web ? tokens.acceptsWebToken(suppliedToken) : tokens.acceptsServiceToken(suppliedToken);
        if (!authorized) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token de integración inválido"); return; }
        byte[] input = request.getInputStream().readAllBytes();
        HttpServletRequest clearRequest = request;
        if (input.length > 0 && request.getContentType() != null && request.getContentType().contains("application/json")) {
            JsonNode encryptedBody = mapper.readTree(input);
            JsonNode encryptedData = encryptedBody == null ? null : encryptedBody.get("data");
            if (encryptedData == null || !encryptedData.isTextual()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Envía el body cifrado");
                return;
            }
            byte[] clear = mapper.writeValueAsBytes(tokens.decryptPayload(encryptedData.asText(), web));
            clearRequest = new BodyRequest(request, clear);
        } else if (input.length > 0) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        BufferResponse buffered = new BufferResponse(response);
        chain.doFilter(clearRequest, buffered);
        byte[] clear = buffered.bytes();
        if (request.getRequestURI().equals("/tokenService/decrypt")) {
            response.setStatus(buffered.getStatus());
            response.setContentType("application/json;charset=UTF-8");
            response.setContentLength(clear.length);
            response.getOutputStream().write(clear);
            return;
        }
        if (clear.length == 0 || response.getStatus() == HttpServletResponse.SC_NO_CONTENT) {
            response.setContentLength(clear.length);
            response.getOutputStream().write(clear);
            return;
        }
        JsonNode payload = mapper.readTree(clear);
        String encrypted = tokens.encryptPayload(payload, web);
        ObjectNode body = mapper.createObjectNode();
        body.put("data", encrypted);
        byte[] output = mapper.writeValueAsBytes(body);
        response.resetBuffer();
        response.setStatus(buffered.getStatus());
        response.setHeader("X-Encrypted-Response", "AES-256-GCM-GLOBAL");
        response.setContentType("application/json;charset=UTF-8");
        response.setContentLength(output.length);
        response.getOutputStream().write(output);
    }

    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            };
        }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public String getContentType() { return "application/json"; }
    }

    private static final class BufferResponse extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private ServletOutputStream stream;
        private PrintWriter writer;
        BufferResponse(HttpServletResponse response) { super(response); }
        @Override public ServletOutputStream getOutputStream() {
            if (writer != null) throw new IllegalStateException("Writer ya obtenido");
            if (stream == null) stream = new ServletOutputStream() {
                @Override public void write(int value) { buffer.write(value); }
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(jakarta.servlet.WriteListener listener) { }
            };
            return stream;
        }
        @Override public PrintWriter getWriter() {
            if (stream != null) throw new IllegalStateException("OutputStream ya obtenido");
            if (writer == null) writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);
            return writer;
        }
        byte[] bytes() { if (writer != null) writer.flush(); return buffer.toByteArray(); }
        @Override public void flushBuffer() { if (writer != null) writer.flush(); }
        @Override public void resetBuffer() { buffer.reset(); }
    }
}





