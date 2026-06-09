# Como correr ws_services_test

Guia para compilar, ejecutar y entregar el JAR ejecutable.

---

## Que es este proyecto

Aplicacion web Java basada en **Spring Boot 3.3** + **Jersey 3.1** que expone
dos endpoints REST:

1. **Test de conexion** — para verificar que el servicio esta arriba.
2. **Broker PCI** — intermediario entre un sistema ORIGEN y un servicio de
   tokenizacion DESTINO. Ver `BROKER_DOC.md` para el detalle.

El proyecto produce un **JAR ejecutable autocontenido** con Tomcat embebido.
No necesita instalar Tomcat por separado en el server destino.

---

## Requisitos

| Componente | Version |
|---|---|
| Java JDK | 17 |
| Apache Maven (solo para compilar) | 3.6+ |

En el server donde se ejecutara: **solo Java 17**. Nada mas.

---

## Compilar (genera el JAR)

```powershell
mvn clean package
```

Tarda ~30 segundos la primera vez (descarga deps). Genera:

```
target/ws_services_test.jar    (~25 MB - este es el ejecutable)
```

---

## Ejecutar localmente

```powershell
java -jar target/ws_services_test.jar
```

Spring Boot arranca Tomcat embebido en **HTTPS 8443**. Veras logs como:

```
Tomcat started on port 8443 (https) with context path '/ws_services_test'
Started Application in 4.6 seconds
```

Para detenerlo: `Ctrl+C`.

### Cambiar el puerto en runtime

```powershell
java -jar target/ws_services_test.jar --server.port=9443
```

### Cambiar el nivel de log

```powershell
java -jar target/ws_services_test.jar --logging.level.com.wsst=DEBUG
```

---

## Probar que funciona

### Test de conexion

```powershell
curl.exe -k https://localhost:8443/ws_services_test/rest/api/v1/test/version
```

**Esperado:**
```json
{"code":"0","version":"0.0.2"}
```

### Broker (caso body vacio, no necesita destino arriba)

```powershell
curl.exe -k -i -X POST -H "Content-Type: application/json" -H "Content-Length: 0" https://localhost:8443/ws_services_test/rest/v1/mp/brk/proxy/orden
```

**Esperado:** HTTP 400 + `{"code":"-1","message":"Body vacio: el origen no envio payload"}`.

Mas casos de prueba en `BROKER_DOC.md` seccion 6.

---

## Endpoints

| Metodo | URL | Que hace |
|---|---|---|
| GET | `https://<host>:8443/ws_services_test/rest/api/v1/test` | Mensaje de bienvenida |
| GET | `https://<host>:8443/ws_services_test/rest/api/v1/test/version` | Devuelve la version |
| POST | `https://<host>:8443/ws_services_test/rest/v1/mp/brk/proxy/orden` | Broker PCI (ver `BROKER_DOC.md`) |

---

## Que viene dentro del JAR

- Codigo de la app
- Tomcat embebido (Spring Boot)
- `properties_wsst.properties` con la URL del destino productiva ya configurada
- `localhost-rsa.jks` (keystore PKCS12 con cert autofirmado para HTTPS)
- `application.properties` con la config de Spring Boot

**El JAR se entrega solo. No requiere archivos auxiliares para arrancar.**

---

## Para desplegarlo en otro server (entrega)

Le pasas a la persona **solo el JAR** y le dices:

1. Instalar Java JDK 17 si no lo tiene.
2. Copiar `ws_services_test.jar` a cualquier carpeta del server.
3. Abrir el puerto 8443 inbound en el firewall (o el que vayan a usar).
4. Ejecutar:
   ```bash
   java -jar ws_services_test.jar
   ```
5. Probar con: `curl -k https://<host>:8443/ws_services_test/rest/api/v1/test/version`

Si quieren correrlo como servicio (background) en Windows o Linux, ver
seccion "Ejecutar como servicio" mas abajo.

---

## Cambiar configuracion sin recompilar

El JAR trae las properties adentro pero los flags estandar de Spring Boot
permiten sobreescribirlas:

```powershell
# Cambiar puerto del servidor
java -jar ws_services_test.jar --server.port=9443

# Cambiar nivel de log
java -jar ws_services_test.jar --logging.level.com.wsst=DEBUG
```

> Para cambiar la URL del destino sin recompilar habria que externalizar
> `properties_wsst.properties`. Hoy esta embebido. Si lo necesitas, hay que
> hacer un pequeno cambio en `InitLoadProperties` para que lea de un
> archivo externo cuando exista.

---

## Ejecutar como servicio (opcional)

### Windows (con NSSM)

Instala NSSM (https://nssm.cc) y crea un servicio:

```powershell
nssm install ws_services_test "C:\Program Files\Java\jdk-17\bin\java.exe" "-jar C:\apps\ws_services_test.jar"
nssm start ws_services_test
```

### Linux (con systemd)

Crear `/etc/systemd/system/ws_services_test.service`:

```ini
[Unit]
Description=ws_services_test broker PCI
After=network.target

[Service]
ExecStart=/usr/bin/java -jar /opt/ws_services_test/ws_services_test.jar
Restart=on-failure
User=tomcat

[Install]
WantedBy=multi-user.target
```

Luego:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now ws_services_test
```

---

## Estructura del proyecto

```
src/main/
|-- java/com/wsst/
|   |-- Application.java                  <- main class (entry point)
|   |-- JerseyConfig.java                 <- registra los endpoints REST
|   |-- ws_rest/
|   |   |-- WS_Version.java               <- test de conexion
|   |   `-- WS_BrokerIntermediario.java   <- broker PCI
|   |-- init/InitLoadProperties.java      <- carga properties_wsst al arranque
|   |-- beans/                            <- request/response y config
|   |-- memory/Singleton.java             <- estado en memoria (HTTP client)
|   `-- utils/Utilities.java              <- helpers
`-- resources/
    |-- application.properties            <- config Spring Boot (puerto, HTTPS)
    |-- properties_wsst.properties        <- URL destino, timeouts del broker
    `-- localhost-rsa.jks                 <- keystore HTTPS (autofirmado)
```

---

## Problemas frecuentes

**"Port 8443 already in use"**
Algo mas (Tomcat externo, otra instancia) usa el puerto. Cambialo con
`--server.port=9443`, o cierra la otra instancia.

**"Cannot find or load main class"**
Estas ejecutando el JAR con Java 8 o 11. Necesitas **Java 17**.
Verificar con: `java -version`.

**Error SSL al hacer curl sin -k**
Esto es normal: el cert que viene es autofirmado. Para produccion hay que
reemplazar `src/main/resources/localhost-rsa.jks` por un cert firmado por la
CA interna de Liverpool y volver a empaquetar.

**El JAR arranca pero el endpoint da 404**
Verifica el context path. La URL completa lleva `/ws_services_test/rest/...`.

---

## Glosario rapido

| Termino | Que es |
|---|---|
| **Spring Boot** | Framework Java que empaqueta tu app + un servidor (Tomcat embebido) en un solo JAR ejecutable. |
| **Jersey** | Implementacion de JAX-RS. Convierte tus clases con `@Path` en endpoints REST. |
| **JAR ejecutable** | Un `.jar` que se ejecuta con `java -jar`. Spring Boot mete Tomcat adentro. |
| **Keystore** | Archivo con cert + llave privada para HTTPS. Va embebido en `src/main/resources/`. |
| **Context path** | Prefijo de URL: `/ws_services_test` (definido en `application.properties`). |
