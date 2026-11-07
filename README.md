# Token Service

Microservicio para generar, validar y renovar tokens JWT cifrados con AES-256-GCM.

La interfaz está disponible en [https://tokenservice.dotacioneszambrano.com/](https://tokenservice.dotacioneszambrano.com/).

## Funcionalidades

- Genera JWT firmados con HS256 y expiración configurable (2 minutos por defecto).
- Valida y renueva tokens cifrados.
- Mantiene flujos separados para las integraciones de servicio y la interfaz web.
- Incluye una interfaz para generar, inspeccionar, validar y renovar tokens. Muestra alertas y un indicador circular mientras procesa las solicitudes.

## Tecnologías

Java 17 · Spring Boot 3.3.5 · Maven · JJWT 0.12.6 · AES-256-GCM

## API

Las solicitudes usan `Content-Type: application/json` y el campo `data`.

### Integraciones de servicio

Estas rutas usan el flujo de servicio de `EncryptService`, que cifra y descifra con `service-key`:

| Método y ruta | Uso |
| --- | --- |
| `POST /tokenService/generate` | Recibe la carga útil cifrada y devuelve un JWT cifrado. |
| `POST /tokenService/validate` | Comprueba firma y expiración; devuelve `{"data":"ACTIVO"}` o `{"data":"VENCIDO"}`. |
| `POST /tokenService/refresh` | Renueva un token vigente y devuelve el nuevo token cifrado. |

Ejemplo de solicitud:

```json
{
  "data": "<valor_cifrado_en_base64>"
}
```

### Interfaz web

La interfaz usa `web-key` y las rutas web correspondientes:

- `GET /tokenService/config`: obtiene la clave web para el navegador en `webKeyBase64`.
- `POST /tokenService/web/generate`
- `POST /tokenService/web/validate`
- `POST /tokenService/web/refresh`

La clave de servicio no se envía al navegador.

## Secretos y configuración

Crea `application-secrets.yml` en la raíz del proyecto. El archivo se importa automáticamente al ejecutar desde la raíz y está excluido de Git. No subas sus valores reales al repositorio.

```yaml
crypto:
  base-url: "http://localhost:8081"
  aes:
    service-key: "<service-key de EncryptService>"
    web-key: "<web-key de EncryptService>"

jwt:
  secret: "<secreto aleatorio de al menos 32 bytes>"
  expiration-seconds: 120
```

Las claves AES deben coincidir con las correspondientes de `EncryptService` y ser claves AES-256 codificadas en Base64. `EncryptService` usa `service-key` en sus rutas de servicio y `web-key` en sus rutas web. `TokenService` entrega al navegador solamente `web-key`.

En Docker, configura `crypto.base-url` como `http://encrypt-service:8080`. Para ejecutar `TokenService` desde el IDE mientras `EncryptService` corre localmente, usa `http://localhost:8081`.

Variables de entorno compatibles:

| Variable | Uso |
| --- | --- |
| `SERVER_PORT` | Puerto HTTP de TokenService (por defecto `8080`). |
| `CRYPTO_BASE_URL` | URL base de EncryptService. |
| `JWT_SECRET` | Secreto HS256 de al menos 32 bytes. |
| `JWT_EXPIRATION_SECONDS` | Duración del JWT en segundos (por defecto `120`). |

## Ejecución local

Requisitos: JDK 17+, Maven 3.8+ y `EncryptService` disponible en `http://localhost:8081`.

1. Crea `application-secrets.yml` con la configuración anterior.
2. Desde la raíz del proyecto, inicia el servicio:

```powershell
mvn spring-boot:run
```

La interfaz estará en `http://localhost:8080/`.

## Docker Compose

`Dockerfile` compila el proyecto con Maven y ejecuta la aplicación como usuario sin privilegios. Compose conecta el contenedor a la red externa `homelab`, compartida con `EncryptService`.

La red debe existir antes de iniciar los servicios. Configura `application-secrets.yml` con `crypto.base-url: "http://encrypt-service:8080"` y ejecuta:

```powershell
docker compose up -d --build
```

Para revisar los registros:

```powershell
docker compose logs -f token-service
```

El contenedor expone el puerto `8080` dentro de la red Docker; un proxy inverso conectado a `homelab` puede dirigir el tráfico hacia `token-service:8080`.
