# Control Horario

App web que sustituye al Excel `HORAS_IZERTIS_2026-27`: registras los fichajes de cada día y la app
calcula las horas del día, del mes y el balance anual frente a las horas de convenio, con vacaciones,
jornada intensiva, teletrabajo e imputaciones JIRA/IZERTIA. Multiusuario con login.

## Probarla en local

Solo necesitas **Docker** con **Docker Compose v2**. No hace falta `.env` ni instalar Java, Maven o Node.

```bash
docker compose up --build
```

La primera vez tarda unos minutos (descarga dependencias y compila). Cuando el log muestre
`Started ControlHorarioApplication`:

| URL | Qué es |
|---|---|
| http://localhost:5173 | **La aplicación** |
| http://localhost:8080/swagger-ui.html | Documentación interactiva de la API |
| `127.0.0.1:5432` | PostgreSQL (usuario `ch_app`, contraseña `controlhorario-dev`) |

Entra con **`admin@controlhorario.local`** / **`controlhorario-dev`** y sigue el asistente:

1. **Ajustes → Periodos → Crear**: el formulario ya trae los valores de tu Excel (26/05/2026-25/05/2027,
   1760 h, 23 días, 8 h / 7 h, intensiva 15/06-15/09, festivos de Madrid).
2. **Ajustes → Importar Excel**: sube `HORAS_IZERTIS_2026-27.xlsx`, revisa la vista previa (días a
   importar, vacaciones deducidas, días futuros que se descartan) y confirma.
3. Mira **Inicio**, **Registro** (hoja mensual), **Calendario** y **Resumen** (hoja Horas).

`docker compose down` para pararla (los datos se conservan); `docker compose down -v` la deja a cero.

## Qué calcula (y qué corrige del Excel)

Todo se calcula en minutos enteros a partir de los fichajes; no se guarda ningún total.

- **Día:** `trabajado = (salida − entrada) − max(0, desayuno − 20 min) − max(comida, 30 min) − otras pausas`.
- **Redondeo a 15 min sin deriva:** se redondea el acumulado del mes y cada día recibe la diferencia.
- **Mes:** teóricas (laborables sin ausencia, medio día = mitad), hechas, diferencia **con signo**,
  saldo acumulado, vacaciones, puentes recuperables, teletrabajo (% y días) e imputaciones.
- **Periodo (hoja Horas):** horas calendario, margen sobre convenio, valor de las vacaciones, horas a
  recuperar, margen restante y proyección a fin de periodo.

Validado contra tu Excel (`backend/src/test`): los 222 días con fichaje representables dan exactamente
su "Total Día", y el periodo da 248 días, 1917 h de calendario, 157 h de margen, 13 días de vacaciones
disfrutadas (91 h), 10 restantes, 66 h de margen restante y 27 h / 15 h a recuperar en los ejemplos de
la hoja.

## Arquitectura

```
Internet ──► Caddy (HTTPS Let's Encrypt, :80/:443)
               │  red edge
               ▼
            frontend: nginx (React + proxy /api)
               │  red internal (sin salida a internet)
               ▼
            backend: Spring Boot ──► db: PostgreSQL 16
                                      ▲
                      backup: pg_dump cifrado con GPG cada 24 h
```

Frontend y API comparten dominio (`/` y `/api`): no hay CORS y la cookie de refresco es
`SameSite=Strict`. En local, Vite (puerto 5173) hace de proxy de `/api`.

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security (JWT + refresh rotativo), Spring Data JPA, Flyway, MapStruct, Bucket4j, Apache POI, springdoc |
| Base de datos | PostgreSQL 16 |
| Frontend | React 18, Vite, TypeScript, React Router, TanStack Query, React Hook Form + Zod, Tailwind CSS, date-fns |
| Tests | JUnit 5 + AssertJ + Testcontainers + MockMvc · Vitest + Testing Library · Playwright (e2e) |
| Infra | Docker multi-stage (amd64 + arm64), Docker Compose, Caddy, GHCR, GitHub Actions |

Documentación técnica: [`docs/API.md`](docs/API.md) (contrato de la API) y
[`docs/EXCEL.md`](docs/EXCEL.md) (formato del Excel para importar y exportar).

## Comandos

| Comando | Qué hace |
|---|---|
| `docker compose up --build` | Levanta todo en local (equivale a `make dev`) |
| `docker compose logs -f backend` | Logs de un servicio |
| `make reset-db` | Borra la base de datos local |
| `make test-back` / `make test-front` | Tests en contenedores |
| `make deploy TAG=abc1234` | Despliega (o vuelve atrás) en la VM |
| `make backup` / `make restore f=backups/<copia>.sql.gpg` | Copia manual / restauración con confirmación |

## Despliegue en Oracle Cloud

En cada push a `main`, el CI pasa los tests y publica en GHCR las imágenes **amd64 y arm64**, etiquetadas
con el SHA corto del commit. La VM solo descarga imágenes y ejecuta Docker Compose; Caddy obtiene y renueva
el certificado HTTPS solo. **No hace falta Cloudflare.**

### 1. Crear la VM (una vez)

1. Consola de OCI → **Compute → Instances → Create instance**:
   - Imagen **Canonical Ubuntu 24.04**.
   - Shape **VM.Standard.A1.Flex** (Ampere, arm64) con 1-2 OCPU y 6-12 GB (Always Free permite hasta
     4 OCPU y 24 GB). La **VM.Standard.E2.1.Micro** (AMD, 1 GB) también vale: el script añade swap.
   - Tu clave SSH pública y una IP pública (mejor **reservada**).
2. **Abre los puertos en la VCN**: Networking → Virtual Cloud Networks → tu VCN → Security Lists →
   Default Security List → Add Ingress Rules, origen `0.0.0.0/0`: **TCP 80**, **TCP 443** y, opcional,
   UDP 443 (HTTP/3).
3. **Importante:** en cuentas solo Always Free, Oracle puede reclamar instancias con poco uso durante
   7 días. Pasa la cuenta a **Pay As You Go** (lo que está dentro de Always Free sigue siendo gratis) y
   pon un presupuesto con alerta en *Billing → Budgets*.

### 2. Dominio

- Con dominio propio: registro `A` (p. ej. `horas.tudominio.com`) hacia la IP de la VM.
- Sin dominio: usa `horas.<IP-con-guiones>.sslip.io` (p. ej. `horas.129-151-10-20.sslip.io`). Funciona
  con un certificado válido igual.

### 3. Preparar la VM (una vez)

```bash
ssh ubuntu@<IP>
git clone https://github.com/LivanAG/tg-time.git control-horario   # repo privado: usa una deploy key
cd control-horario
bash deploy/setup-vps.sh   # Docker + Compose, firewall 80/443, SSH solo con clave, actualizaciones automáticas
exit                       # vuelve a entrar para usar docker sin sudo
```

El script abre los puertos también en el firewall interno (iptables) de la imagen de Ubuntu de Oracle.
No uses `ufw` (Oracle lo desaconseja). En Oracle Linux el equivalente es
`sudo firewall-cmd --permanent --add-service=http --add-service=https && sudo firewall-cmd --reload`.

### 4. Configurar y desplegar

```bash
docker login ghcr.io -u <usuario-github>      # token (classic) con read:packages
cd ~/control-horario
cp .env.example .env && chmod 600 .env
nano .env    # DOMAIN, DB_PASSWORD, JWT_SECRET, APP_ADMIN_EMAIL/PASSWORD, BACKUP_PASSPHRASE (openssl rand ...)
make deploy TAG=<sha corto del commit>         # el que publicó el último CI en main
```

Abre `https://<DOMAIN>` y entra con el administrador de `.env` (después puedes borrar
`APP_ADMIN_PASSWORD` del fichero). En producción el backend **no arranca** si `JWT_SECRET` falta, es corto
o es un valor de ejemplo, y docker compose se niega a arrancar si falta cualquier variable obligatoria.

**Despliegue automático (opcional):** con los secretos `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` y
`DEPLOY_KNOWN_HOSTS` (salida de `ssh-keyscan <IP>`) en GitHub, cada push a `main` hace
`git pull && make deploy TAG=<sha>` por SSH.

**Volver atrás:** `cat deploy-history.log` y `make deploy TAG=<anterior>`. Si la versión nueva cambió el
esquema de la base de datos, restaura también la copia de antes del despliegue.

## Copias de seguridad

- El contenedor `backup` hace un `pg_dump` cifrado con GPG (AES-256) al arrancar y cada 24 h en
  `backups/` (permisos 600) y borra las de más de 14 días.
- `make backup` crea una al momento (hazla antes de desplegar). `make restore f=...` pide confirmación,
  para el backend, restaura y lo vuelve a arrancar.
- Guarda `BACKUP_PASSPHRASE` fuera de la VM y saca las copias de ella con regularidad (`scp` u OCI Object
  Storage, 20 GB gratis). Prueba una restauración al mes.

## Seguridad

- Contraseñas con Argon2id (mínimo 12 caracteres, sin contraseñas comunes). Bloqueo de 15 min tras 5
  fallos y rate limit de 10/min por IP en login, registro y refresco. Mensajes de login genéricos.
- Access token JWT de 15 min solo en memoria del navegador; refresh token opaco rotativo de 7 días en
  cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, guardado en BD solo como hash; reutilizar uno
  ya rotado revoca toda la sesión.
- Cada consulta filtra por el usuario del token; un recurso ajeno devuelve 404.
- CSP estricta, HSTS, `nosniff`, `frame-ancestors 'none'`; contenedores sin root, de solo lectura y sin
  capacidades; PostgreSQL y backend sin acceso desde internet; `audit_log` de logins, cambios de
  contraseña, importaciones y borrados.
- Subida de Excel: máximo 2 MB, comprobación de tipo y firma, sin macros y sin evaluar fórmulas.

## CI

`.github/workflows/ci.yml`: tests del backend (unitarios + Testcontainers) y del frontend (tipos, lint,
tests, build) en cada push y PR; en `main`, imágenes amd64 + arm64 en GHCR y despliegue opcional por SSH.
