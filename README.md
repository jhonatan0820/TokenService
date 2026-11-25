# TokenService

TokenService issues, validates, refreshes, and decrypts encrypted JWT payloads. EncryptService owns the RSA and AES encryption keys; TokenService stores only its JWT signing secret and access tokens.

## Encryption contract

Current browser clients and backend integrations use EncryptService envelope version 4. Ciphertext is one Base64 string carried in the root `data` field:

```json
{ "data": "<Base64 ciphertext>" }
```

The browser encrypts locally with EncryptService profile `TOKN`. TokenService decrypts requests and encrypts responses through EncryptService. Backend integrations use the global profile. TokenService does not send its access tokens to EncryptService.

## Endpoints

| Method and path | Purpose |
| --- | --- |
| `GET /tokenService/config` | Returns the `TOKN` public key and web access token used by the browser. |
| `GET /tokenService/health` | Returns the service health status. |
| `POST /tokenService/generate` | Creates a signed JWT from encrypted payload data. |
| `POST /tokenService/validate` | Validates a token and returns `ACTIVO` or `VENCIDO`. |
| `POST /tokenService/refresh` | Issues a replacement token and disables the previous token until its original expiration. |
| `POST /tokenService/decrypt` | Decrypts a value through EncryptService; used by the browser response bridge. |

Protected POST requests carry an encrypted envelope in the root `data` field. TokenService requires `X-Web-Token` from the browser or `X-Encrypt-Token` from a backend integration. These headers authorize requests to TokenService only; do not send them to EncryptService.

The browser configuration endpoint obtains its key from EncryptService at `GET /api/crypto/public-key?profile=TOKN`. TokenService selects the web response profile inside the encrypted `data` body.

## Postman and backend integrations

EncryptService encrypt and decrypt calls do not require authorization headers. To call TokenService from Postman, encrypt the request with EncryptService, then send it to TokenService with `X-Encrypt-Token`.

For `POST /tokenService/generate`:

1. Encrypt the payload with `POST https://encryptservice.dotacioneszambrano.com/api/crypto/encrypt`:

```json
{ "data": { "userId": 123, "name": "Sample" } }
```

2. Take that ciphertext and encrypt an outer request object with EncryptService:

```json
{ "data": { "data": "<payload-ciphertext>" } }
```

3. Send the outer ciphertext to `POST https://tokenservice.dotacioneszambrano.com/tokenService/generate`:

```http
X-Encrypt-Token: <TokenService backend access token>
Content-Type: application/json
```

```json
{ "data": "<outer-ciphertext>" }
```

TokenService returns an encrypted `data` string. Decrypt it with `POST /api/crypto/decrypt` on EncryptService. A generated token response contains the token ciphertext; decrypt that value once more to inspect its JWT payload. EncryptService selects the correct key profile from each envelope.

## Configuration

Keep `application-secrets.yml` out of Git. TokenService requires the EncryptService base URL, separate API and web access tokens, and a JWT signing secret:

```yaml
crypto:
  base-url: "https://encryptservice.dotacioneszambrano.com"
  api-token: "<TokenService backend access token>"
  web-token: "<TokenService browser access token>"

jwt:
  secret: "<random secret of at least 32 bytes>"
  expiration-seconds: 120
```

The browser profile is non-secret configuration in `src/main/resources/application.properties`:

```properties
crypto.web-profile=TOKN
```

## Local development

Requirements: Java 17, Maven, and a running EncryptService. Start EncryptService on port 8080. In another terminal, start TokenService on port 8081 with its base URL pointed to EncryptService:

```powershell
$env:SERVER_PORT = "8081"
$env:CRYPTO_BASE_URL = "http://localhost:8080"
mvn spring-boot:run
```

The interface is available at `http://localhost:8081/`.

## Docker Compose

Configure `crypto.base-url` as `http://encrypt-service:8080`, mount TokenService `application-secrets.yml` read-only, and connect the container to the external `homelab` Docker network. The service listens on port 8080 inside that network.
