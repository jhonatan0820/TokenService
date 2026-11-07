# Token Service

Microservicio de autenticación para generar, validar y renovar tokens JWT firmados y cifrados.

## Despliegue

La interfaz web del microservicio está desplegada en:

**[https://tokenService.dotacioneszambrano.com/](https://tokenService.dotacioneszambrano.com/)**

## Problema

Las aplicaciones que comparten información de sesión necesitan comprobar que las credenciales corresponden a un token legítimo, que no fueron alteradas y que todavía están vigentes. También necesitan un ciclo de vida claro para emitir y renovar esos tokens sin exponer su contenido durante el transporte.

## Solución

Este servicio centraliza el ciclo de vida del token:

- Recibe la carga útil cifrada y la descifra a través de `cryptoService`.
- Genera un JWT firmado con HS256, con identificador único y expiración de **2 minutos** por defecto.
- Cifra el JWT antes de devolverlo.
- Descifra y valida los tokens recibidos, comprobando firma y expiración.
- Renueva un token vigente conservando su carga útil.
- Incluye una interfaz web para generar, inspeccionar, validar y renovar tokens.

La interfaz separa visualmente el JWT legible del token cifrado. Para validar o renovar, el usuario debe pegar el token cifrado; la página rechaza el JWT en texto claro.

## Stack tecnológico

| Componente | Tecnología |
| --- | --- |
| Lenguaje | Java 17 |
| Framework | Spring Boot 3.3.5 |
| Build | Maven |
| API del microservicio | Spring Web con `@RestController` |
| JWT | JJWT 0.12.6, algoritmo HS256 |
| Cifrado | AES-256-GCM mediante `cryptoService` y Web Crypto en el frontend |
| Interfaz | HTML, CSS y JavaScript estáticos servidos por Spring Boot |

## Flujo

1. El cliente cifra el JSON de entrada con AES-256-GCM.
2. `POST /tokenService/generate` descifra esa carga llamando a `cryptoService`, crea el JWT y solicita cifrarlo.
3. El microservicio devuelve el token cifrado.
4. Para validar o renovar, el cliente envía el token cifrado. El servicio lo descifra y comprueba el JWT.
5. Renovar crea otro JWT con una nueva expiración y la misma carga útil.

## Endpoints

Las solicitudes usan `Content-Type: application/json` y un campo `data`.

### Generar

`POST /tokenService/generate`

```json
{
  "data": "<carga_util_cifrada_en_base64>"
}
```

Responde con el token JWT cifrado:

```json
{
  "data": "<token_cifrado>"
}
```

### Validar

`POST /tokenService/validate`

```json
{
  "data": "<token_cifrado>"
}
```

Responde `{"data":"ACTIVO"}` si la firma es válida y el token no ha vencido, o `{"data":"VENCIDO"}` si expiró. Una carga sin cifrar o alterada se rechaza.

### Renovar

`POST /tokenService/refresh`

```json
{
  "data": "<token_cifrado>"
}
```

Responde con un nuevo token cifrado si el token recibido es válido y sigue vigente.

## `cryptoService`

Por defecto, el microservicio espera `cryptoService` en `http://localhost:8081`.

- `POST /cryptoService/encrypt`: recibe `{"data":{"text":"<jwt>"}}` y devuelve `{"data":"<texto_cifrado>"}`.
- `POST /cryptoService/decrypt`: recibe `{"data":"<texto_cifrado>"}` y devuelve la carga en `data`. Para un JWT, `data.text` contiene el JWT descifrado.

El frontend cifra con AES-256-GCM y representa los bytes como Base64 con el IV de 12 bytes antepuesto al ciphertext y al tag GCM de 128 bits. Ese formato debe coincidir con `cryptoService`.

## Ejecutar localmente

Requisitos: JDK 17+, Maven 3.8+ y `cryptoService` disponible.

```powershell
$env:JWT_SECRET = 'reemplaza-por-una-clave-aleatoria-de-al-menos-32-bytes'
$env:CRYPTO_BASE_URL = 'http://localhost:8081'
$env:JWT_EXPIRATION_SECONDS = '120'
mvn spring-boot:run
```

La interfaz estará disponible en `http://localhost:8080/`.

### Configuración

| Variable | Valor predeterminado | Descripción |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Puerto HTTP del microservicio |
| `CRYPTO_BASE_URL` | `http://localhost:8081` | URL base de `cryptoService` |
| `JWT_SECRET` | Clave de desarrollo del proyecto | Secreto HS256; configura uno aleatorio de al menos 32 bytes fuera de desarrollo |
| `JWT_EXPIRATION_SECONDS` | `120` | Vigencia del JWT en segundos |

