package com.example.tokenservice.controller;

import com.example.tokenservice.service.TokenService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/tokenService")
public class UiConfigController {
    private final TokenService tokens;
    public UiConfigController(TokenService tokens) { this.tokens = tokens; }
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("publicKeyBase64", tokens.publicKeyBase64(true), "webToken", tokens.webToken()));
    }
}


