# Broker Intermediario - Documentacion tecnica

Servicio que actua como intermediario entre un sistema **ORIGEN** y un servicio
de **tokenizacion PCI-DSS** (DESTINO). Recibe un PAN del origen, lo reenvia al
destino, recibe un token, y lo devuelve al origen en la misma respuesta HTTP.

---

## 0. Estado actual (al 26/may/2026)

| Capa | Estado |
|---|---|
| JAR ejecutable Spring Boot empacado | OK |
| HTTPS en 8443 con certificado autofirmado (embebido) | OK |
| Tomcat embebido (sin instalacion externa) | OK |
| Lectura de configuracion desde properties embebido | OK |
| Reenvio passthrough al destino | OK |
| Manejo de errores (400/500/502/504/etc.) | OK |
| **Prueba end-to-end con destino real** | **BLOQUEADO: TLS handshake al destino se resetea (probable mTLS o whitelist)** |
| **Integracion con origen real** | **PENDIENTE: depende de que el equipo origen apunte a esta URL** |

---

## 1. Contrato HTTP

### Endpoint

| Campo | Valor |
|---|---|
| Metodo | `POST` |
| URL (local) | `https://localhost:8443/ws_services_test/rest/v1/mp/brk/proxy/orden` |
| URL (red interna) | `https://172.22.177.14:8443/ws_services_test/rest/v1/mp/brk/proxy/orden` |
| Content-Type | `application/json` |
| Protocolo | **HTTPS unicamente.** HTTP en 8080 esta cerrado. |

### Request (lo que envia el ORIGEN)

```json
{
  "pan": "4152313308708721",
  "keyLabel": "saltpcicteux"
}
```

El broker NO valida la forma del JSON, lo reenvia tal cual al destino
(passthrough). El unico requisito es que el body no este vacio.

### Response exitosa

```
HTTP/1.1 200 OK
Content-Type: application/json

{"trackingId":"5F4B657CF42E2FF368F23A9465CEA0556DE4A54A0004BCC4D3E6C74D1031"}
```

El campo `trackingId` contiene el valor extraido del campo `token` que devolvio
el destino. El nombre del campo destino es configurable (ver seccion 4).

### Response de error

| HTTP | code | message | Cuando ocurre |
|---|---|---|---|
| 400 | -1 | "Body vacio: el origen no envio payload" | El origen mando body vacio o null |
| 500 | -2 | "Configuracion invalida: wsst.broker.destino.url no esta definida" | Falta la URL del destino en properties |
| 504 | -3 | "Timeout al contactar al destino" | El destino no respondio dentro del timeout configurado |
| 502 | -4 | "No se pudo contactar al destino: ..." | Connection refused, host unreachable, DNS, SSL handshake, etc. |
| 500 | -5 | "Error inesperado: ..." | Cualquier otra excepcion |
| 502 | -6 | "Respuesta del destino no es JSON valido" | El destino respondio 200 pero su body no se pudo parsear |
| 502 | -7 | "El destino no devolvio el campo '<nombre>'" | El JSON del destino no traia el campo configurado |
| 4xx/5xx | (cuerpo del destino) | (cuerpo del destino) | El destino respondio 4xx/5xx; se propaga tal cual |

---

## 2. Estructura del proyecto

Tras la refactorizacion a **JAR ejecutable con Spring Boot** (2026-05-26),
el proyecto contiene **solamente** dos endpoints:

- **Test de conexion** (`WS_Version`): `GET /rest/api/v1/test` y
  `GET /rest/api/v1/test/version`. Sirve para verificar que el servicio
  esta levantado.
- **Broker PCI** (`WS_BrokerIntermediario`): `POST /rest/v1/mp/brk/proxy/orden`.
  Es la funcionalidad principal documentada en este archivo.

| Archivo | Proposito |
|---|---|
| `src/main/java/com/wsst/Application.java` | Main class (entry point Spring Boot) |
| `src/main/java/com/wsst/JerseyConfig.java` | Registra los endpoints REST en Jersey |
| `src/main/java/com/wsst/ws_rest/WS_BrokerIntermediario.java` | Endpoint REST del broker |
| `src/main/java/com/wsst/ws_rest/WS_Version.java` | Endpoint REST de test de conexion |
| `src/main/java/com/wsst/init/InitLoadProperties.java` | Carga `properties_wsst.properties` al arranque |
| `src/main/java/com/wsst/memory/Singleton.java` | `Client` JAX-RS reutilizable para el broker |
| `src/main/java/com/wsst/beans/PropertiesForWsServices.java` | Estructura con la config del broker |
| `src/main/resources/application.properties` | Config Spring Boot (puerto 8443, HTTPS, keystore) |
| `src/main/resources/properties_wsst.properties` | URL destino y timeouts del broker |
| `src/main/resources/localhost-rsa.jks` | Keystore PKCS12 con cert autofirmado (HTTPS embebido) |

**Importante:** todo el HTTPS (keystore, puerto, context path) vive dentro del
JAR. No hay configuracion externa de Tomcat porque Tomcat va embebido.

---

## 3. Donde se guarda la URL del destino y otras variables

### Archivo de configuracion

**Fuente (en codigo, queda embebido en el JAR):**
```
src/main/resources/properties_wsst.properties
```

### Contenido relacionado con el broker

```properties
# URL del sistema DESTINO al que se reenviara el payload del ORIGEN.
wsst.broker.destino.url=https://nnigma.liverpool.com.mx:443/get-online-token

# Nombre del campo JSON en la respuesta del DESTINO que trae el token.
wsst.broker.destino.trackingid.field=token

# Timeouts del cliente HTTP saliente (milisegundos)
wsst.broker.destino.timeout.connect.ms=5000
wsst.broker.destino.timeout.read.ms=15000
```

### Como cambiar el destino

Como el archivo queda embebido en el JAR, hay que recompilar:

1. Editar `src/main/resources/properties_wsst.properties`.
2. `mvn clean package`.
3. Reemplazar el JAR en el server destino y reiniciarlo.

Si se requiere cambio sin recompilar, hay que pequeniar `InitLoadProperties`
para que primero busque el archivo en disco al lado del JAR y, si no existe,
caiga al embebido.

### Cuales son las IPs/URLs del flujo y donde aparecen

| Quien | Que es | Donde aparece |
|---|---|---|
| **172.22.164.239** | Maquina del ORIGEN (quien llama al broker) | No esta en codigo. El broker acepta llamadas de cualquier IP autorizada por firewall |
| **172.22.177.14:8443** | Esta computadora (el broker, HTTPS) | No esta en codigo. Es la IP que DHCP asigna a esta laptop |
| **nnigma.liverpool.com.mx:443** | Servicio de tokenizacion DESTINO | `properties_wsst.properties` -> `wsst.broker.destino.url` |

---

## 4. Como funciona internamente

### Flujo paso a paso (codigo en `WS_BrokerIntermediario.java`)

```
1. Llega POST /v1/mp/brk/proxy/orden con un String JSON via HTTPS
2. Si el body esta vacio  -> HTTP 400
3. Lee del Singleton la URL destino y el nombre del campo del token
4. Si la URL no esta definida -> HTTP 500
5. Loggea (INFO) "reenviando al destino [URL]"
6. Loggea (DEBUG) el payload del origen
7. Obtiene el Client JAX-RS reutilizable del Singleton
8. Hace POST al destino con el body tal cual (passthrough)
9. Maneja errores:
     - SocketTimeoutException -> HTTP 504
     - Otras ProcessingException -> HTTP 502
     - Cualquier otra -> HTTP 500
10. Si el destino respondio NO-2xx -> propaga su status y body al origen
11. Si el destino respondio 2xx:
     - Parsea el body como JSON con Jackson
     - Extrae el valor del campo configurado (token)
     - Si no esta -> HTTP 502
     - Si esta -> loggea (INFO) el trackingId obtenido (auditoria)
12. Devuelve al origen: HTTP 200 con {"trackingId":"<valor>"}
```

### Por que el Client esta en el Singleton

El `jakarta.ws.rs.client.Client` de Jersey mantiene un pool de conexiones.
Crearlo en cada request seria caro. Se construye una sola vez con doble-check
locking para garantizar thread-safety.

---

## 5. Como compilar y arrancar

### Compilar el JAR

```powershell
mvn -f c:\Users\epenar01\Downloads\ws_services_test\ws_services_test\pom.xml clean package -DskipTests
```

Genera `target/ws_services_test.jar` (~25 MB).

### Arrancar localmente

```powershell
java -jar c:\Users\epenar01\Downloads\ws_services_test\ws_services_test\target\ws_services_test.jar
```

Para detener: `Ctrl+C` en la ventana donde corre.

### Cualquier cambio (codigo, properties, keystore) requiere recompilar

A diferencia del modelo Tomcat externo, aqui todo va dentro del JAR. Editar
codigo, properties o keystore -> `mvn clean package` -> entregar el nuevo JAR.

---

## 6. CASOS DE PRUEBA

Antes de cada caso, ten **dos ventanas de PowerShell abiertas**:

- **Ventana A** (logs en vivo):
  ```powershell
  Get-Content "C:\tools\tomcat11\logs\catalina.$(Get-Date -Format yyyy-MM-dd).log" -Wait -Tail 0 | Select-String "BROKER"
  ```
- **Ventana B**: para correr los curl.

### TC-01 - Happy path (cuando el destino este arriba)

**Objetivo:** verificar el flujo completo end-to-end.

**Precondiciones:**
- Destino `nnigma.liverpool.com.mx:443` respondiendo OK.
- Tomcat arriba con HTTPS 8443.

**Comando:**
```powershell
curl.exe -k -i -X POST `
  -H "Content-Type: application/json" `
  -d '{\"pan\":\"4152313308708721\",\"keyLabel\":\"saltpcicteux\"}' `
  https://localhost:8443/ws_services_test/rest/v1/mp/brk/proxy/orden
```

**Resultado esperado en Ventana B:**
```
HTTP/1.1 200
Content-Type: application/json
{"trackingId":"5F4B657CF42E2FF368F23A9465CEA0556DE4A54A0004BCC4D3E6C74D1031"}
```

**Resultado esperado en Ventana A (logs):**
```
BROKER - reenviando al destino [https://nnigma.liverpool.com.mx:443/get-online-token]
BROKER - destino respondio status=200 body={"token":"5F4B..."}
BROKER - trackingId obtenido del destino: 5F4B...
```

---

### TC-02 - Body vacio (validacion local, no llega al destino)

**Objetivo:** verificar que el broker valida entrada antes de molestar al destino.

**Comando:**
```powershell
curl.exe -k -i -X POST `
  -H "Content-Type: application/json" `
  -d "" `
  https://localhost:8443/ws_services_test/rest/v1/mp/brk/proxy/orden
```

**Resultado esperado:**
```
HTTP/1.1 400
{"code":"-1","message":"Body vacio: el origen no envio payload"}
```

**En logs:** NO debe aparecer "reenviando al destino". El broker se debe cortar antes.

---

### TC-03 - Timeout al destino (estado actual al 25/may/2026)

**Objetivo:** verificar que cuando el destino no responde, el broker devuelve 504 estructurado.

**Precondiciones:** Destino caido o inalcanzable (es el estado de hoy).

**Comando:** mismo que TC-01.

**Resultado esperado:**
```
HTTP/1.1 504
{"code":"-3","message":"Timeout al contactar al destino"}
```

**Tiempo esperado:** ~5 segundos (el `connectTimeout`).

**En logs:**
```
BROKER - reenviando al destino [...]
BROKER - timeout llamando al destino [...]
```

---

### TC-04 - Solo HTTPS (HTTP debe estar cerrado)

**Objetivo:** confirmar que nadie puede hablarle al broker en HTTP.

**Comando:**
```powershell
curl.exe -i --max-time 5 http://localhost:8080/ws_services_test/rest/v1/mp/brk/proxy/orden
```

**Resultado esperado:**
- curl falla con "Failed to connect" / "Connection refused".
- Exit code distinto de 0.
- HTTP_CODE = 000.

**En logs:** nada. La conexion ni siquiera llega a Tomcat.

---

### TC-05 - Certificado autofirmado rechazado sin `-k`

**Objetivo:** confirmar que el certificado esta funcionando (aunque sea autofirmado).

**Comando (sin el -k):**
```powershell
curl.exe -i -X POST `
  -H "Content-Type: application/json" `
  -d '{\"pan\":\"4152313308708721\",\"keyLabel\":\"saltpcicteux\"}' `
  https://localhost:8443/ws_services_test/rest/v1/mp/brk/proxy/orden
```

**Resultado esperado:**
```
curl: (60) SSL certificate problem: self-signed certificate
```

Esto **es lo esperado** y prueba que el cert esta activo. Cuando IT de Liverpool
te de un cert firmado por su CA interna, este comando funcionara sin `-k`.

---

### TC-06 - JSON malformado

**Objetivo:** ver como reacciona el broker a JSON sintacticamente invalido.

**Comando:**
```powershell
curl.exe -k -i -X POST `
  -H "Content-Type: application/json" `
  -d '{esto no es json}' `
  https://localhost:8443/ws_services_test/rest/v1/mp/brk/proxy/orden
```

**Resultado esperado:** el broker hace passthrough del string sin validar, lo
manda al destino, y este responde con un 400 que se propaga al origen (o
timeout si el destino esta caido).

**Nota:** el broker NO valida la estructura JSON. Por eso este caso es util
mas adelante para decidir si queremos agregar validacion de schema.

---

### TC-07 - Verificar conectividad de red basica

**Objetivo:** descartar problemas de red antes de pruebas funcionales.

**Comandos:**
```powershell
# El destino responde a ping y al puerto 443
Test-NetConnection nnigma.liverpool.com.mx -Port 443

# El origen (cuando este probando) es alcanzable
Test-Connection 172.22.164.239 -Count 2

# Tu broker escucha en 8443
Test-NetConnection localhost -Port 8443
```

**Resultado esperado:** `TcpTestSucceeded : True` en los tres.

---

### TC-08 - Probar desde otra maquina de la red

**Objetivo:** confirmar que el broker es alcanzable desde la red, no solo localhost.

**Desde otra maquina en la misma red:**
```bash
curl -k -i -X POST \
  -H "Content-Type: application/json" \
  -d '{"pan":"4152313308708721","keyLabel":"saltpcicteux"}' \
  https://172.22.177.14:8443/ws_services_test/rest/v1/mp/brk/proxy/orden
```

**Resultado esperado:** misma respuesta que TC-01 o TC-03 (segun estado del destino).

**Si falla con "Connection timed out" desde la otra maquina:** firewall de
Windows bloqueando el puerto 8443 inbound. Abrirlo asi (PowerShell como Admin):
```powershell
New-NetFirewallRule -DisplayName "Tomcat 8443 HTTPS" -Direction Inbound -LocalPort 8443 -Protocol TCP -Action Allow
```

---

## 7. Matriz de resultados esperados

| Caso | HTTP | Body de respuesta | Lo veo en logs |
|---|---|---|---|
| TC-01 Happy path | 200 | `{"trackingId":"..."}` | reenviando + respondio 200 + trackingId obtenido |
| TC-02 Body vacio | 400 | `{"code":"-1",...}` | nada (cortado antes) |
| TC-03 Timeout | 504 | `{"code":"-3",...}` | reenviando + timeout |
| TC-04 HTTP 8080 | n/a | connection refused | nada |
| TC-05 sin `-k` | n/a | SSL error en cliente | nada |
| TC-06 JSON invalido | depende destino | passthrough | reenviando + respondio (status real del destino) |
| TC-07 Test-NetConnection 443 destino | n/a | TcpTestSucceeded:True/False | n/a |
| TC-08 Desde otra maquina | mismo que TC-01/TC-03 | mismo | mismo + IP origen en access log |

---

## 8. Como interpretar el log de acceso de Tomcat

Cada request a tu broker queda en:
```
C:\tools\tomcat11\logs\localhost_access_log.YYYY-MM-DD.txt
```

Formato:
```
<IP-origen> - - [fecha] "POST /ws_services_test/rest/v1/mp/brk/proxy/orden HTTP/1.1" <status> <bytes>
```

Para ver quien te ha llamado hoy:
```powershell
Get-Content "C:\tools\tomcat11\logs\localhost_access_log.$(Get-Date -Format yyyy-MM-dd).txt" | Select-String "brk/proxy"
```

Si la IP no es `127.0.0.1` ni `172.22.164.239`, alguien no esperado te esta
llamando y vale la pena investigar.

---

## 9. Limitaciones conocidas / pendientes

1. **Logueo del PAN en nivel DEBUG.** El broker imprime el payload completo en
   nivel DEBUG. Si el `log4j2.xml` esta en DEBUG en prod, viola PCI-DSS.
   Antes de prod: o subir el nivel a INFO/WARN, o enmascarar el PAN.

2. **Certificado autofirmado.** Los clientes ven warning de "no confiable".
   Para prod pedir cert firmado por la CA interna de Liverpool.

3. **Sin autenticacion al destino.** Si el destino exige Bearer/Basic/API-Key,
   agregar el header en el `Invocation.Builder` con la credencial leida de
   `properties_wsst.properties`.

4. **Sin reintentos.** Falla un request al destino, el broker no reintenta.
   Agregar retry con backoff si se quiere robustez extra.

5. **Sin metricas.** No hay contadores ni latencia promedio. Para
   observabilidad seria agregar Micrometer o similar.

6. **IP DHCP.** La `172.22.177.14` puede cambiar. Para algo estable pedir
   reserva DHCP, hostname/DNS, o desplegar en servidor con IP fija.

7. **Sin validacion de schema en el body.** El broker hace passthrough sin
   revisar que el JSON tenga `pan` y `keyLabel`. Si se quiere validacion,
   agregarla en `fnReenviarOrden`.

---

## 10. Diagrama del flujo completo

```
ORIGEN (172.22.164.239)
   |
   | (1) HTTPS POST {"pan":"...","keyLabel":"..."}
   v
BROKER (172.22.177.14:8443)
   |
   | -> log INFO: reenviando al destino
   | -> log DEBUG: payload origen
   |
   | (2) HTTPS POST tal cual
   v
DESTINO (nnigma.liverpool.com.mx:443/get-online-token)
   |
   | (3) responde {"token":"5F4B..."}
   |
   v
BROKER
   |
   | -> parsea JSON, extrae campo "token"
   | -> log INFO: trackingId obtenido
   |
   | (4) responde {"trackingId":"5F4B..."}
   v
ORIGEN
```

---

## 11. Contactos

- **Owner del broker (este proyecto):** tu equipo
- **Owner del ORIGEN (172.22.164.239):** equipo que dispara las peticiones de tokenizacion
- **Owner del DESTINO (`nnigma.liverpool.com.mx`):** equipo del servicio de tokenizacion PCI / Liverpool
