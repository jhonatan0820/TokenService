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
 @PostMapping("/refresh") public DataResponse<String> refresh(@RequestBody DataRequest r){return refresh(r,false);}
 @PostMapping("/web/generate") public DataResponse<String> webGenerate(@RequestBody DataRequest r){return new DataResponse<>(tokens.createEncryptedFromEncryptedData(data(r),true));}
 @PostMapping("/web/validate") public DataResponse<String> webValidate(@RequestBody DataRequest r){return new DataResponse<>(tokens.tokenStatus(data(r),true));}
 @PostMapping("/web/refresh") public DataResponse<String> webRefresh(@RequestBody DataRequest r){return refresh(r,true);}
 @PostMapping("/web/decrypt") public DataResponse<?> webDecrypt(@RequestBody DataRequest r){return new DataResponse<>(tokens.decryptPayload(data(r),true));}
 private DataResponse<String> refresh(DataRequest r,boolean web){try{return new DataResponse<>(tokens.refresh(data(r),web));}catch(Exception e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token inválido o expirado",e);}}
 private String data(DataRequest r){if(r==null||r.data()==null||!r.data().isTextual()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El campo data debe ser una cadena cifrada");return r.data().asText();}
}
