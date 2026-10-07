# Control Horario

App web que sustituye al Excel `HORAS_IZERTIS_2026-27`: fichajes diarios, horas del mes y balance anual
frente a las horas de convenio. Monorepo con backend Spring Boot y frontend React, desplegado entero en
**una VM de Oracle Cloud** con Docker Compose.

> Estado: **Fase 1 — base del proyecto** (ver el plan de trabajo). Aún no hay login ni pantallas de negocio.

## Arquitectura

```
Internet ──► [Cloudflare, opcional] ──► Caddy (HTTPS, :80/:443)
                                          │  red edge
                                          ▼
                                   frontend: nginx (SPA + proxy /api)
                                          │  red internal (sin salida a internet)
                                          ▼
                                   backend: Spring Boot (:8080) ──► db: PostgreSQL 16
                                                                     ▲
                                                     backup: pg_dump cifrado diario
```

Frontend y API comparten origen (`/` y `/api`), así que no hay CORS y la cookie de refresco puede ser
`SameSite=Strict`. Solo Caddy publica puertos; la base de datos y el backend no son accesibles desde fuera.

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, springdoc, MapStruct |
| Base de datos | PostgreSQL 16 |
| Frontend | React 18, Vite, TypeScript, React Router, TanStack Query, Tailwind CSS |
| Tests | JUnit 5 + Testcontainers + MockMvc · Vitest + Testing Library |
| Infra | Docker multi-stage (amd64 + arm64), Docker Compose, Caddy, nginx, GHCR, GitHub Actions |

## Desarrollo local

Solo necesitas **Docker** y **Docker Compose v2** (no hace falta Java, Maven ni Node).

```bash
cp .env.example .env      # en local basta con cambiar DB_PASSWORD
make dev
```

| URL | Qué es |
|---|---|
| http://localhost:5173 | Frontend (Vite con recarga en caliente) |
| http://localhost:8080/actuator/health | Health del backend |
| http://localhost:8080/swagger-ui.html | Swagger (solo perfil `dev`) |
| `127.0.0.1:5432` | PostgreSQL, para DBeaver/IntelliJ |

Para iterar en el backend lo más cómodo es ejecutarlo desde el IDE contra la base de datos del
contenedor (`localhost:5432`); `make rebuild-backend` reconstruye solo ese servicio.

### Comandos

| Comando | Qué hace |
|---|---|
| `make dev` | Levanta db, backend y frontend |
| `make logs s=backend` | Sigue los logs de un servicio |
| `make down` | Para los contenedores y conserva los datos |
| `make reset-db` | Borra la base de datos de desarrollo (prohibido en prod) |
| `make test-back` | `mvn verify` en un contenedor (unitarios + Testcontainers) |
| `make test-front` | Typecheck, lint y tests del frontend en un contenedor |
| `make build TAG=$(git rev-parse --short HEAD)` | Imágenes de prod para tu arquitectura |
| `make push TAG=...` | Imágenes amd64 + arm64 al registro (normalmente lo hace el CI) |
| `make deploy TAG=abc1234` | Despliega (o vuelve atrás) en la VM |
| `make backup` / `make restore f=backups/<copia>.sql.gpg` | Copia manual / restauración con confirmación |

## Estructura

```
backend/                 Spring Boot (Dockerfile multi-stage, imagen JRE mínima sin root)
frontend/                React + Vite (Dockerfile → nginx sin root; Dockerfile.dev → Vite)
  nginx.conf             SPA + proxy /api y /actuator/health hacia el backend
deploy/
  Caddyfile              HTTPS con Let's Encrypt
  Caddyfile.cloudflare   HTTPS detrás de Cloudflare con certificado de origen
  backup/                imagen de copias (pg_dump + gpg), backup.sh y restore.sh
  setup-vps.sh           preparación de la VM de Oracle (Ubuntu)
docker-compose.yml       base común
docker-compose.dev.yml   override de desarrollo
docker-compose.prod.yml  override de producción (hardening, Caddy, backup)
.env.example             plantilla de variables (el .env real nunca se sube)
```

## Despliegue en Oracle Cloud

El CI publica en GHCR imágenes **amd64 y arm64** en cada push a `main`, etiquetadas con el SHA corto del
commit. La VM solo descarga imágenes y ejecuta `docker compose`.

### 1. Crear la VM (una vez)

1. Consola de OCI → **Compute → Instances → Create instance**.
   - Imagen: **Canonical Ubuntu 24.04**.
   - Shape: **VM.Standard.A1.Flex** (Ampere, arm64). Con 1–2 OCPU y 6–12 GB sobra; el nivel
     Always Free permite hasta 4 OCPU y 24 GB en total. También vale **VM.Standard.E2.1.Micro**
     (AMD, 1 GB): el script añade swap, pero va justa.
   - Sube tu clave SSH pública y asigna una IP pública (mejor **reservada**, para que no cambie).
2. **Abre los puertos en la VCN**: Networking → Virtual Cloud Networks → tu VCN → Security Lists →
   *Default Security List* → *Add Ingress Rules*, con origen `0.0.0.0/0`:
   - TCP 80 y TCP 443 (obligatorios: HTTPS y certificado de Let's Encrypt).
   - UDP 443 (opcional, HTTP/3).
3. **Importante (cuentas Always Free):** Oracle puede reclamar instancias *idle* (CPU, red y memoria por
   debajo del 20 % durante 7 días), y una app de uso personal lo estará casi siempre. Para evitarlo,
   pasa la cuenta a **Pay As You Go**: lo que está dentro de los límites Always Free sigue siendo
   gratis. Pon un presupuesto con alerta en *Billing → Budgets* por si acaso.

### 2. Dominio

- **Con dominio propio:** registro `A` (p. ej. `horas.tudominio.com`) hacia la IP pública de la VM.
- **Sin dominio:** usa `horas.<IP-con-guiones>.sslip.io` (p. ej. `horas.129-151-10-20.sslip.io`).
  Resuelve solo a esa IP y Caddy obtiene el certificado igual.
- **Con Cloudflare delante (opcional):** registro proxied (nube naranja), *SSL/TLS → Full (strict)*,
  crea un *Origin Certificate* y guárdalo en la VM como `deploy/certs/origin.pem` y
  `deploy/certs/origin-key.pem` (fuera de git). En `.env`: `CADDYFILE=Caddyfile.cloudflare`.
  Puedes limitar los puertos 80/443 de la Security List a los
  [rangos de Cloudflare](https://www.cloudflare.com/ips/).

### 3. Preparar la VM (una vez)

```bash
ssh ubuntu@<IP>
git clone https://github.com/LivanAG/tg-time.git control-horario   # repo privado: usa una deploy key
cd control-horario
bash deploy/setup-vps.sh      # Docker + Compose, firewall 80/443, SSH solo con clave, actualizaciones automáticas
exit                          # vuelve a entrar para usar docker sin sudo
```

El script abre los puertos también en el **firewall interno** de la imagen de Ubuntu de OCI (iptables).
No uses `ufw`: Oracle lo desaconseja en sus imágenes. En **Oracle Linux** el equivalente es
`sudo firewall-cmd --permanent --add-service=http --add-service=https && sudo firewall-cmd --reload`
e instalar Docker a mano.

### 4. Acceso a las imágenes de GHCR

Los paquetes de GHCR de un repo privado son privados. En la VM:

```bash
docker login ghcr.io -u <usuario-github>    # contraseña: token (classic) con permiso read:packages
```

(O haz públicos los paquetes `control-horario-backend` y `control-horario-frontend` en GitHub.)

### 5. Configurar y desplegar

```bash
cd ~/control-horario
cp .env.example .env && chmod 600 .env
nano .env     # SPRING_PROFILE=prod, DOMAIN, DB_PASSWORD, JWT_SECRET, BACKUP_PASSPHRASE (openssl rand ...)
make deploy TAG=<sha corto del commit>      # p. ej. el tag que publicó el último CI en main
```

Comprueba `https://<DOMAIN>` y `https://<DOMAIN>/actuator/health` (`{"status":"UP"}`). En `prod` el
backend **no arranca** si `JWT_SECRET` falta, es corto o conserva el valor de ejemplo.

### Despliegue automático desde GitHub Actions (opcional)

Con estos secretos en *Settings → Secrets and variables → Actions*, cada push a `main` despliega solo
(`git pull` + `make deploy TAG=<sha>` por SSH):

| Secreto | Valor |
|---|---|
| `DEPLOY_HOST` | IP o dominio de la VM |
| `DEPLOY_USER` | `ubuntu` |
| `DEPLOY_SSH_KEY` | clave privada de un par creado solo para esto (pública en `~/.ssh/authorized_keys` de la VM) |
| `DEPLOY_KNOWN_HOSTS` | salida de `ssh-keyscan <IP>` |

Sin ellos, el job de deploy no hace nada y se despliega a mano.

### Volver a una versión anterior

```bash
cat deploy-history.log          # versiones desplegadas, con fecha
make deploy TAG=<tag anterior>  # las imágenes antiguas siguen en GHCR
```

Las migraciones de Flyway solo van hacia delante: si la versión nueva cambió el esquema y la antigua no
es compatible, restaura también la copia de antes del despliegue.

## Copias de seguridad

- El contenedor `backup` hace un `pg_dump` **cifrado con GPG (AES-256)** al arrancar y cada 24 h en
  `backups/`, y borra las de más de 14 días. Los ficheros quedan con permisos `600`.
- `make backup` crea una copia en el momento (hazlo antes de cada despliegue con migraciones).
- `make restore f=backups/2026-10-07_030000.sql.gpg` pide confirmación, para el backend, restaura y lo
  vuelve a arrancar.
- Guarda `BACKUP_PASSPHRASE` **fuera de la VM** (gestor de contraseñas): sin ella las copias no sirven.
- Las copias viven en el disco de la VM. Sácalas fuera con regularidad (`scp`, o a OCI Object Storage,
  que tiene 20 GB en Always Free) y prueba una restauración al mes.

## CI

`.github/workflows/ci.yml`, en cada push y PR:

1. **Backend:** `mvn verify` (unitarios + integración con Testcontainers/PostgreSQL).
2. **Frontend:** typecheck, lint, tests y build.
3. **En `main`:** imágenes amd64 + arm64 → `ghcr.io/livanag/control-horario-{backend,frontend}:<sha>` y `:latest`.
4. **En `main`, opcional:** despliegue por SSH en la VM.

Pendiente para la fase 8: Trivy, OWASP dependency-check, `npm audit`, Dependabot, e2e con Playwright,
copias fuera de la VM y monitorización.

## Cambios respecto al documento de especificación

- **Node 22** en lugar de 20 (Node 20 está fuera de soporte desde abril de 2026).
- Imágenes **multi-arquitectura**: la VM gratuita de Oracle es arm64. La compilación (Maven/npm) se
  hace en la plataforma del builder y solo la capa final cambia por arquitectura.
- **Imagen de backup propia** (`postgres:16-alpine` + `gnupg`): la imagen oficial no trae `gpg`.
- **nginx:** resuelve `backend` en cada petición (si no, tras un redeploy del backend seguiría
  apuntando a la IP antigua) y repite las cabeceras de seguridad en cada `location`
  (`add_header` no se hereda si el bloque define las suyas).
- **Caddy** fija `X-Forwarded-For` a la IP real del cliente (`{client_ip}`, y con Cloudflare la de
  `CF-Connecting-IP` solo si la petición viene de sus rangos), así que el backend no se fía de
  cabeceras que mande el cliente.
- Un único `.env` para dev y prod, seleccionado con `SPRING_PROFILE`.
