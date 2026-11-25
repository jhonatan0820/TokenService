package com.example.tokenservice.controller;
import com.example.tokenservice.dto.DataRequest;
import com.example.tokenservice.dto.DataResponse;
import com.example.tokenservice.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/tokenService")
public class TokenController {
 private final TokenService tokens;
 public TokenController(TokenService tokens){this.tokens=tokens;}
 @PostMapping("/generate") public DataResponse<String> generate(@RequestBody DataRequest r){return new DataResponse<>(tokens.createEncryptedFromEncryptedData(data(r),false));}
 @PostMapping("/validate") public DataResponse<String> validate(@RequestBody DataRequest r){return new DataResponse<>(tokens.tokenStatus(data(r),false));}
 @PostMapping("/refresh") public DataResponse<String> refresh(@RequestBody DataRequest r){try{return new DataResponse<>(tokens.refresh(data(r),false));}catch(Exception e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token inválido o expirado",e);}}
 @PostMapping("/decrypt") public DataResponse<?> decrypt(@RequestBody DataRequest r){return new DataResponse<>(tokens.decryptPayload(data(r),false));}
 private String data(DataRequest r){if(r==null||r.data()==null||!r.data().isTextual()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El campo data debe ser una cadena cifrada");return r.data().asText();}
}
