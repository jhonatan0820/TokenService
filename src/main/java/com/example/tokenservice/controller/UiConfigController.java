package com.example.tokenservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/tokenService")
public class UiConfigController {
    private final String webKeyBase64;

    public UiConfigController(@Value("${crypto.aes.web-key}") String webKeyBase64) {
        this.webKeyBase64 = webKeyBase64;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> config() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("webKeyBase64", webKeyBase64));
    }
}
