# ws_lv_services_test

> Hub de **microservicios intermediarios (broker)** entre los POS y los sistemas
> backend de Liverpool. Centraliza integraciones POS↔backend bajo un mismo patrón
> *passthrough*, con configuración externalizada y logging para monitoreo.
> **Diseñado para crecer**: hoy incluye tokenización PCI y consulta de
> transacciones de débito Prosa (Base24), y se le irán sumando más servicios.

---

## Tabla de contenido

1. [Qué es y para qué sirve](#1-qué-es-y-para-qué-sirve)
2. [Arquitectura y stack](#2-arquitectura-y-stack)
3. [Estructura del proyecto](#3-estructura-del-proyecto)
4. [Endpoints](#4-endpoints)
5. [URLs de prueba](#5-urls-de-prueba)
6. [Configuración](#6-configuración)
7. [Cómo compilar y correr](#7-cómo-compilar-y-correr)
8. [Logging y monitoreo](#8-logging-y-monitoreo)
9. [Cómo agregar un servicio nuevo](#9-cómo-agregar-un-servicio-nuevo)
10. [Convención de ramas](#10-convención-de-ramas)
11. [Notas de seguridad / PCI](#11-notas-de-seguridad--pci)
12. [Documentación adicional](#12-documentación-adicional)

---

## 1. Qué es y para qué sirve

`ws_lv_services_test` es un microservicio que se inserta **"en medio"** de un
flujo existente: **recibe una petición del POS, la reenvía al sistema destino y
devuelve la respuesta tal cual** (patrón *passthrough*), agregando en el camino
lo que el destino necesite (autenticación, trazabilidad, monitoreo) **sin
modificar el contrato**.

No reproduce lógica de negocio: encamina. Es un punto único de entrada que
concentra varias integraciones, por eso el nombre dice `services` en plural.

Ejemplo del flujo de consulta de transacciones (forma alterna):

```
POS  ──>  ws_lv_services_test  ──>  API B24  ──>  Key Monitor
```

---

## 2. Arquitectura y stack

| Pieza | Detalle |
|---|---|
| Lenguaje | **Java 17** |
| Framework | **Spring Boot 3.3.5** + **Jersey (JAX-RS)** |
| Empaquetado | **Jar ejecutable** con Tomcat embebido |
| Logging | **Log4j2** (consola + archivo con rotación) |
| Build | **Maven** |
| Puertos | **HTTP 7074** y **HTTPS 7443** |
| Context path | `/ws_services_test` |
| Ruta Jersey | `/rest` |

> URL base → `http(s)://<host>:<puerto>/ws_services_test/rest/...`

**Patrón de diseño interno:**

- Cada capacidad es un **recurso JAX-RS independiente** registrado en
  `JerseyConfig`.
- La **configuración está externalizada** en `properties_wsst.properties`, se
  carga al arranque (`InitLoadProperties`) hacia un `Singleton` en memoria.
- Los **clientes HTTP salientes** (uno por integración) viven cacheados en el
  `Singleton` con sus propios timeouts.
- Cada request lleva un **`reqId`** de correlación para trazar todo su ciclo en
  los logs.

---

## 3. Estructura del proyecto

```
src/main/java/com/wsst/
├── Application.java                 # main de Spring Boot
├── JerseyConfig.java                # registra los recursos JAX-RS
├── HttpConnectorConfig.java         # habilita el conector HTTP 7074
├── ws_rest/
│   ├── WS_Version.java              # health / versión
│   ├── WS_BrokerIntermediario.java  # broker de tokenización PCI
│   └── WS_ConsultaProsa.java        # consulta de transacciones Prosa (B24)
├── init/
│   └── InitLoadProperties.java      # carga properties_wsst.properties al arranque
├── memory/
│   └── Singleton.java               # estado en memoria + clientes HTTP cacheados
├── beans/
│   ├── PropertiesForWsServices.java # config tipada
│   ├── RespDefault.java
│   └── RespTest.java
└── utils/
    └── Utilities.java               # helpers (enmascarado de PAN, etc.)

src/main/resources/
├── application.properties           # puertos, SSL, context-path
├── properties_wsst.properties       # config de las integraciones
├── log4j2.xml                       # configuración de logging
└── localhost-rsa.jks                # keystore de pruebas (cert autofirmado)
```

---

## 4. Endpoints

| Servicio | Método | Ruta (relativa a `/ws_services_test/rest`) | Descripción |
|---|---|---|---|
| Health / versión | `GET` | `/api/v1/test` | Devuelve `SERVICE up - <fecha hora>`. Sirve para saber si está arriba. |
| Versión | `GET` | `/api/v1/test/version` | Versión del servicio en JSON. |
| Broker tokenización PCI | `POST` | `/v1/mp/brk/proxy/orden` | Reenvía el payload a un servicio de tokenización (PAN → token). Passthrough. |
| Consulta Transacciones Prosa | `POST` | `/v1/return/ConsultaTransacciones` | Reenvía la consulta a la API B24 inyectando `Basic` auth. Passthrough. |

### Detalle: Consulta Transacciones Prosa

**Request** (lo manda el POS):

```json
{
  "fecha": "260608",
  "tienda": "0074",
  "terminal": "116",
  "boleta": "2823",
  "empresa": "L"
}
```

El POS **no envía** `Authorization`; el servicio inyecta el header
`Authorization: Basic ...` hacia B24 desde su configuración.

**Response** (passthrough de B24):

```json
{
  "codRespuesta": "00",
  "descRespuesta": "Registro Encontrado",
  "Transaccion": [
    {
      "autorizacion": "502865",
      "monto": "5415.0",
      "rrn": "L00741164642",
      "tarjeta": "6275358143008754"
    }
  ]
}
```

**Códigos de error propios del intermediario:**

| code | HTTP | Significado |
|---|---|---|
| `-1` | 400 | Body vacío (el origen no envió payload) |
| `-2` | 500 | Configuración inválida (URL o credenciales no definidas) |
| `-3` | 504 | Timeout al contactar el destino |
| `-4` | 502 | No se pudo contactar el destino (error de transporte) |
| `-5` | 500 | Error inesperado |

---

## 5. URLs de prueba

### Local (tu máquina, HTTP 7074)

```bash
# Health check
GET  http://localhost:7074/ws_services_test/rest/api/v1/test

# Versión
GET  http://localhost:7074/ws_services_test/rest/api/v1/test/version

# Broker tokenización PCI
POST http://localhost:7074/ws_services_test/rest/v1/mp/brk/proxy/orden

# Consulta Transacciones Prosa
POST http://localhost:7074/ws_services_test/rest/v1/return/ConsultaTransacciones
```

### Flujo en ambiente (según el diagrama)

```
POS  ──>  http://172.16.216.198:7074/ws_services_test/rest/v1/return/ConsultaTransacciones/
ws   ──>  http://172.16.216.113:6000/ConsultaTransacciones/   (API B24 destino)
```

### Ejemplo con PowerShell

```powershell
# ¿Está arriba?
Invoke-RestMethod -Uri "http://localhost:7074/ws_services_test/rest/api/v1/test"

# Consulta de transacción
$body = '{ "fecha":"260608", "tienda":"0074", "terminal":"116", "boleta":"2823", "empresa":"L" }'
Invoke-RestMethod -Uri "http://localhost:7074/ws_services_test/rest/v1/return/ConsultaTransacciones" `
  -Method Post -ContentType "application/json" -Body $body
```

> ⚠️ La prueba real contra B24 (`172.16.216.113:6000`) solo funciona **desde la
> red de Liverpool**. Fuera de ella verás timeout (`-3`/504), que igual confirma
> que el servicio recibió la consulta e intentó reenviarla.

---

## 6. Configuración

Toda la configuración de integraciones vive en
`src/main/resources/properties_wsst.properties`:

```properties
# ===== Broker / tokenización PCI =====
wsst.broker.destino.url=https://nnigma.liverpool.com.mx:443/get-online-token
wsst.broker.destino.timeout.connect.ms=5000
wsst.broker.destino.timeout.read.ms=15000

# ===== Consulta Transacciones Débito Prosa (API Base24) =====
wsst.prosa.consulta.url=http://172.16.216.113:6000/ConsultaTransacciones/
wsst.prosa.consulta.auth.user=admin
wsst.prosa.consulta.auth.password=++++++
wsst.prosa.consulta.timeout.connect.ms=5000
wsst.prosa.consulta.timeout.read.ms=15000
# false = PAN visible en logs (monitoreo) | true = PAN enmascarado (PCI)
wsst.prosa.consulta.log.mask.pan=false
```

Los puertos, SSL y context-path están en `application.properties`. **Cambiar
URLs/credenciales por ambiente** editando estas properties (no requiere tocar
código).

---

## 7. Cómo compilar y correr

```powershell
# 1. Compilar y empaquetar (genera target\ws_services_test.jar)
mvn clean package

# 2. Arrancar
java -jar target\ws_services_test.jar
```

Señales de que está arriba:

```
Tomcat started on port(s): 7443 (https) 7074 (http)
Started Application in 3.x seconds
```

Detener: `Ctrl + C`. Alternativa de desarrollo: `mvn spring-boot:run`.

> Detalles paso a paso en [COMO_CORRER.md](COMO_CORRER.md).

---

## 8. Logging y monitoreo

- Configuración en `src/main/resources/log4j2.xml`.
- Salida a **consola** y a **archivo** `./logs/ws_services_test.log` (relativo al
  directorio desde el que se arranca el jar).
- Cada transacción deja un bloque correlacionado por su `reqId`. Ejemplo:

```
[a1b2c3d4] CONSULTA-PROSA - REQUEST recibido del POS (ORIGEN)
[a1b2c3d4] DESTINO URL: http://172.16.216.113:6000/ConsultaTransacciones/
[a1b2c3d4] Autorizacion: Basic (usuario=admin, password=***)
[a1b2c3d4] B24 respondio en 331ms   |   B24 status: 200
[a1b2c3d4] Resultado: codRespuesta=00 descRespuesta=Registro Encontrado
[a1b2c3d4] Tx#1 | autorizacion=502865 | monto=5415.0 | rrn=L00741164642 | tarjeta=6275358143008754
[a1b2c3d4] Duracion total: 333ms
```

- **Enmascarado de PAN**: controlado por `wsst.prosa.consulta.log.mask.pan`.
  `false` → PAN completo (monitoreo). `true` → `627535******8754` (cumple PCI).
  El enmascarado **solo afecta los logs**, nunca la respuesta que se devuelve al POS.
- La **contraseña nunca** se escribe en los logs.

---

## 9. Cómo agregar un servicio nuevo

El servicio está pensado para crecer. Para sumar una integración, **seguir el
patrón existente** (no inventar otro):

1. Crear un **recurso JAX-RS** nuevo en `com.wsst.ws_rest` (usa
   `WS_ConsultaProsa` como plantilla).
2. Agregar su **bloque de config** en `properties_wsst.properties`
   (URL, credenciales, timeouts) y cargarlo en `InitLoadProperties` +
   `PropertiesForWsServices`.
3. Si llama a un destino externo, agregar su **cliente HTTP** cacheado en
   `Singleton`.
4. **Registrar** el recurso en `JerseyConfig`.
5. Mantener el patrón: passthrough + `reqId` + logging detallado.
6. **No tocar** las integraciones existentes.

---

## 10. Convención de ramas

Este repo sigue la convención de nombres de ramas de Liverpool.

- Ramas permanentes (protegidas): `main`, `dev`, `deploy_TEST`, `deploy_QA2`,
  `deploy_PREPROD`, `deploy_PROD`. **No** se hace push directo; todo entra por PR.
- Ramas de trabajo: `{github_username}/{tipo}/{descripcion}`
  - Ej.: `epenar01/feature/add_consulta_transacciones_prosa`
  - Tipos: `feature`, `fix`, `hotfix`, `refactor`, `docs`, `chore`, `test`,
    `perf`, `revert`.
- Flujo: `trabajo → dev → deploy_TEST → deploy_QA2 → deploy_PREPROD → deploy_PROD → main`.

---

## 11. Notas de seguridad / PCI

- ⚠️ La respuesta de B24 incluye el **PAN** (`tarjeta`). Por defecto el servicio
  lo deja **visible en logs** para monitoreo (`log.mask.pan=false`). Activar el
  enmascarado (`true`) para cumplir PCI-DSS cuando ya no se necesite ver el PAN.
- El keystore `localhost-rsa.jks` es un **certificado autofirmado de pruebas**
  (password `changeit`); no usar en producción.

---

## 12. Documentación adicional

- [BROKER_DOC.md](BROKER_DOC.md) — documentación detallada del broker intermediario.
- [COMO_CORRER.md](COMO_CORRER.md) — guía paso a paso para compilar y ejecutar.
