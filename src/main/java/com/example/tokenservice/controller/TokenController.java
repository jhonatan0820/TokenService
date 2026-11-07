package com.example.tokenservice.controller;

import com.example.tokenservice.dto.DataRequest;
import com.example.tokenservice.dto.DataResponse;
import com.example.tokenservice.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/tokenService")
public class TokenController {
    private final TokenService tokens;

    public TokenController(TokenService tokens) { this.tokens = tokens; }

    // Endpoints service-to-service: EncryptService usa su service-key.
    @PostMapping("/generate")
    public DataResponse<String> create(@RequestBody DataRequest request) {
        return new DataResponse<>(tokens.createEncryptedFromEncryptedData(encryptedValue(request)));
    }

    @PostMapping("/validate")
    public DataResponse<String> validate(@RequestBody DataRequest request) {
        return new DataResponse<>(tokens.tokenStatus(encryptedValue(request)));
    }

    @PostMapping("/refresh")
    public DataResponse<String> refresh(@RequestBody DataRequest request) {
        try { return new DataResponse<>(tokens.refresh(encryptedValue(request))); }
        catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido o expirado", ex); }
    }

    // Endpoints de la interfaz web: EncryptService usa su web-key.
    @PostMapping("/web/generate")
    public DataResponse<String> createFromWeb(@RequestBody DataRequest request) {
        return new DataResponse<>(tokens.createEncryptedFromEncryptedData(encryptedValue(request), true));
    }

    @PostMapping("/web/validate")
    public DataResponse<String> validateFromWeb(@RequestBody DataRequest request) {
        return new DataResponse<>(tokens.tokenStatus(encryptedValue(request), true));
    }

    @PostMapping("/web/refresh")
    public DataResponse<String> refreshFromWeb(@RequestBody DataRequest request) {
        try { return new DataResponse<>(tokens.refresh(encryptedValue(request), true)); }
        catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido o expirado", ex); }
    }

    private String encryptedValue(DataRequest request) {
        if (request == null || request.data() == null || !request.data().isTextual() || request.data().asText().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo data debe ser un string cifrado");
        return request.data().asText();
    }
}
