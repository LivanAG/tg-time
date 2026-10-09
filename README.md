# Control Horario

App web que sustituye al Excel `HORAS_IZERTIS_2026-27`: registras los fichajes de cada día y la app
calcula las horas del día, del mes y el balance anual frente a las horas de convenio, con vacaciones,
jornada intensiva y teletrabajo. Multiusuario con login.

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
  Un día **mixto** tiene dos tramos (oficina y casa, cada uno con su entrada y su salida): cuenta la suma
  de los dos y el hueco entre ellos no se trabaja.
- **Redondeo a 15 min sin deriva:** se redondea el acumulado del mes y cada día recibe la diferencia.
- **Mes:** teóricas (laborables sin ausencia, medio día = mitad), hechas, diferencia **con signo**,
  saldo acumulado, vacaciones, puentes recuperables y teletrabajo (el límite es solo el %; los días en
  casa son informativos).
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
                      backup: pg_dump cifrado con GPG cada día
```

Frontend y API comparten dominio (`/` y `/api`): no hay CORS y la cookie de refresco es
`SameSite=Strict`. En local, Vite (puerto 5173) hace de proxy de `/api`.

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security (JWT + refresh rotativo), Spring Data JPA, Flyway, MapStruct, Bucket4j, Apache POI, springdoc |
| Base de datos | PostgreSQL 16 |
| Frontend | React 18, Vite, TypeScript, React Router, TanStack Query, React Hook Form + Zod, Tailwind CSS, date-fns |
| Tests | JUnit 5 + AssertJ + Testcontainers + MockMvc · Vitest + Testing Library · Playwright (e2e) |
| Infra | Docker multi-stage (amd64 + arm64), Docker Compose, Caddy, GitHub Actions |

Documentación técnica: [`docs/API.md`](docs/API.md) (contrato de la API) y
[`docs/EXCEL.md`](docs/EXCEL.md) (formato del Excel para importar y exportar).

## Comandos

| Comando | Qué hace |
|---|---|
| `docker compose up --build` | Levanta todo en local (equivale a `make dev`) |
| `docker compose logs -f backend` | Logs de un servicio |
| `make reset-db` | Borra la base de datos local |
| `make test-back` / `make test-front` | Tests en contenedores |
| `make deploy` / `make update` | Construye y despliega en la VM / copia + `git pull` + redespliegue |
| `make backup` / `make restore f=backups/<copia>.sql.gpg` | Copia manual / restauración con confirmación |

## Despliegue en Oracle Cloud

Para instalar tu propia copia, guía completa paso a paso en **[docs/DEPLOY.md](docs/DEPLOY.md)**: prueba
previa en local con la configuración de producción, crear la VM y abrir los puertos en Oracle, descargar el
código, `.env`, build en la VM (arm64), HTTPS provisional con sslip.io y opcional con un dominio propio en
Cloudflare, actualizaciones, copias y lo que no hay que hacer nunca.

Resumen (en la VM): `cp .env.example .env && chmod 600 .env`, rellenar, `make deploy`. Para actualizar:
`make update`.

## Copias de seguridad

- El contenedor `backup` hace un `pg_dump` cifrado con GPG (AES-256) al arrancar y cada día a las 03:00 en
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
tests, build) en cada push y PR. Las imágenes se construyen en la VM (`make deploy`).
