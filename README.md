# Token Service

Microservicio para generar, validar y renovar JWT firmados con HS256. Los JWT
se conservan como sobres cifrados con la llave pública de EncryptService; el
navegador solo recibe el sobre opaco.

La interfaz está disponible en [https://tokenservice.dotacioneszambrano.com/](https://tokenservice.dotacioneszambrano.com/).

## Cifrado híbrido

EncryptService publica una llave RSA. El navegador cifra cada solicitud con
RSA-OAEP/SHA-256 y una llave AES-256-GCM aleatoria. Cada página también crea
un par RSA efímero: TokenService cifra la respuesta para su llave pública y el
navegador la descifra con la llave privada que mantiene en memoria.

TokenService llama a las rutas normales `/cryptoService/decrypt` y
`/cryptoService/encrypt`. Solo envía `X-Service-Token` al descifrar; nunca
necesita ni entrega la llave privada RSA de EncryptService.

## API

Las solicitudes HTTP de la interfaz llevan un body cifrado y el encabezado
`X-Client-Public-Key`. TokenService devuelve `X-Encrypted-Response` y un sobre
cifrado para esa llave efímera.

- `GET /tokenService/config`: obtiene `publicKeyBase64` desde EncryptService.
- `POST /tokenService/generate`: descifra la carga útil, firma el JWT y devuelve
  el JWT cifrado para EncryptService.
- `POST /tokenService/validate`: valida el sobre y devuelve el estado.
- `POST /tokenService/refresh`: renueva un token vigente y devuelve el nuevo
  sobre cifrado.

Los endpoints `/tokenService/web/*` se conservan como alias de ruta, pero también
requieren el nuevo sobre asimétrico. Los tokens generados con el formato AES anterior deben regenerarse.

## Secretos y configuración

`application-secrets.yml` está excluido de Git. Configúralo así:

```yaml
crypto:
  base-url: "http://localhost:8081"
  service-token: "<token de integración compartido con EncryptService>"

jwt:
  secret: "<secreto aleatorio de al menos 32 bytes>"
  expiration-seconds: 120
```

En Docker, usa `crypto.base-url: "http://encrypt-service:8080"`. El token debe
coincidir con `crypto.service-token` de EncryptService.

Variables de entorno compatibles: `SERVER_PORT`, `CRYPTO_BASE_URL`,
`CRYPTO_SERVICE_TOKEN`, `JWT_SECRET` y `JWT_EXPIRATION_SECONDS`.

## Ejecución local

Requisitos: JDK 17+, Maven 3.8+ y EncryptService disponible en
`http://localhost:8081`.

1. Crea `application-secrets.yml` con la configuración anterior.
2. Desde la raíz del proyecto, inicia el servicio:

```powershell
mvn spring-boot:run
```

La interfaz estará en `http://localhost:8080/`.

## Docker Compose

Compose conecta el contenedor a la red externa `homelab`, compartida con
EncryptService. La red debe existir antes de iniciar los servicios. Configura
`application-secrets.yml` con `crypto.base-url: "http://encrypt-service:8080"`
y ejecuta:

```powershell
docker compose up -d --build
```

El contenedor expone el puerto `8080` dentro de la red Docker; un proxy inverso
conectado a `homelab` puede dirigir el tráfico hacia `token-service:8080`.




## Alcances de cifrado

Las rutas normales usan ENCRYPT_SERVICE_TOKEN y la llave RSA de servicio. Las rutas /tokenService/web/* usan el token web y la llave pública web. El filtro cifra las respuestas para la llave efímera del navegador. El campo data es una cadena Base64 compacta; no se envía un objeto con wrappedKey, iv y ciphertext.


## Revocación al renovar

Cuando `POST /tokenService/refresh` emite el reemplazo, marca el `jti` anterior como revocado hasta su expiración original. Desde entonces, `/validate` lo reporta como `VENCIDO`, y `/refresh` o cualquier validación de datos lo rechaza. El registro de revocación vive en memoria del proceso y se limpia cuando el JWT habría expirado; el despliegue Compose actual ejecuta una instancia.
