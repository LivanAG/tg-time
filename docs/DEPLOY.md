# Despliegue en Oracle Cloud

Guía completa para llevar Control Horario a la VM de Oracle Cloud y mantenerlo. Cada bloque de comandos
indica **dónde se ejecuta**:

- **PC (PowerShell)**: tu Windows.
- **VM (bash)**: dentro de la VM, después de conectarte por SSH.

## Resumen

| | |
|---|---|
| VM | Oracle Cloud Always Free, Madrid, VM.Standard.A1.Flex (arm64), 1 OCPU, 6 GB, Ubuntu 24.04 |
| IP pública | `158.179.220.119` |
| Código | repositorio privado en GitHub, clonado en la VM con una deploy key de solo lectura |
| Imágenes | se construyen **en la propia VM** (`make deploy`), nativas arm64 |
| Puertos publicados | solo Caddy: 80 y 443. PostgreSQL y backend sin puertos, en una red interna sin salida a internet |
| HTTPS provisional | `https://horas.158-179-220-119.sslip.io` con certificado de Let's Encrypt (Caddy lo obtiene y renueva solo) |
| HTTPS final | subdominio en Cloudflare (proxy activado, SSL "Full (strict)") con certificado de origen en Caddy |
| Copias | `pg_dump` cifrado con GPG cada día a las 03:00 (y al arrancar), 14 días de retención, en `~/control-horario/backups` |

```
Internet ──80/443──▶ caddy ──▶ frontend (nginx: estáticos + /api) ──▶ backend (Spring Boot) ──▶ db (PostgreSQL 16)
                                                                                         backup ──┘
```

### Memoria (6 GB)

| Servicio | Límite | Uso real aprox. |
|---|---|---|
| backend | 2304 MB | ~1,75 GB (heap fijo de 1,5 GB pre-reservado) |
| db | 1 GB | 50-300 MB (+ caché de disco del sistema) |
| caddy | 256 MB | ~15 MB |
| backup | 256 MB | ~1 MB (picos al hacer la copia) |
| frontend | 128 MB | ~15 MB |

Quedan ~2 GB para Ubuntu, la caché de disco y el build de las imágenes. La memoria usada de la VM queda
en torno al 30 %, por encima del 20 % de la política de reclamación de instancias inactivas de Oracle
(ver [Oracle y las instancias inactivas](#oracle-y-las-instancias-inactivas)).

---

## 0. Probarlo antes en tu PC (recomendado)

Levanta en Windows **la misma configuración de producción** (Caddy con HTTPS, perfil prod, límites de
memoria, JVM de 1,5 GB, copias cifradas) en `https://horas.localhost`. Usa su propio proyecto de Docker
(`ch-prodlocal`), su propio volumen de datos y su propio fichero `.env.local-prod` con secretos
aleatorios: no toca tu entorno de desarrollo. Necesita libres los puertos 80 y 443 del PC.

**PC (PowerShell)**, en la carpeta del proyecto:

```powershell
cd "D:\Control Horario\control-horario"
powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 up
```

```powershell
powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 ps
```

Cuando todo esté `healthy` (el backend tarda hasta 1-2 min):

1. Abre `https://horas.localhost`. El certificado es autofirmado: acepta el aviso del navegador
   ("Avanzado → Continuar").
2. Entra con `admin@horas.localhost` y la contraseña `APP_ADMIN_PASSWORD` de `.env.local-prod`.

Resto de comandos:

```powershell
powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 logs backend
```

```powershell
powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 backup
```

```powershell
powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 restore backups\<copia>.sql.gpg
```

```powershell
powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 down
```

`down` para los contenedores y conserva los datos. `destroy` además borra los datos de esta prueba
(solo los de `ch-prodlocal`, nunca los de desarrollo).

---

## 1. Subir el código a GitHub

**PC (PowerShell)**, en la carpeta del proyecto. Revisa antes los cambios (`git status`, `git diff`):

```powershell
cd "D:\Control Horario\control-horario"
git add -A
git status
git commit -m "Despliegue en Oracle Cloud: build en la VM, Caddy, copias y guía"
git push origin main
```

En GitHub, en el repositorio `LivanAG/tg-time`:

- **Settings → General → Danger Zone → Change repository visibility**: privado (si no lo es ya).
- **Settings → General → Default branch**: `main` (ahora mismo es `claude/modest-maxwell-i49yqo`; la VM
  clona `main` en cualquier caso).

## 2. Conectarte a la VM

**PC (PowerShell)**. Opcional pero cómodo: mueve la clave a `~\.ssh` y crea un alias `oracle-ch` para no
escribir la ruta cada vez.

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.ssh" | Out-Null
Copy-Item "C:\Users\livan\Downloads\ssh-key-2026-10-07.key" "$env:USERPROFILE\.ssh\oracle-ch.key"
icacls "$env:USERPROFILE\.ssh\oracle-ch.key" /inheritance:r /grant:r "$($env:USERNAME):(R)"
Add-Content "$env:USERPROFILE\.ssh\config" "`nHost oracle-ch`n  HostName 158.179.220.119`n  User ubuntu`n  IdentityFile ~/.ssh/oracle-ch.key`n  IdentitiesOnly yes`n"
```

(`icacls` deja la clave legible solo por tu usuario; sin eso OpenSSH puede negarse a usarla con
"UNPROTECTED PRIVATE KEY FILE".)

```powershell
ssh oracle-ch
```

Sin el alias: `ssh -i "C:\Users\livan\Downloads\ssh-key-2026-10-07.key" ubuntu@158.179.220.119`.
En el resto de la guía se usa `oracle-ch`.

## 3. Deploy key de solo lectura (una vez)

**VM (bash)**: crea una clave solo para leer este repositorio.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C "oracle-vm control-horario"
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/github_deploy
  IdentitiesOnly yes
EOF
chmod 600 ~/.ssh/config
cat ~/.ssh/github_deploy.pub
```

Copia la línea que empieza por `ssh-ed25519` y, en GitHub: **repositorio → Settings → Deploy keys → Add
deploy key**, título `oracle-vm`, pega la clave y **no** marques "Allow write access".

**VM (bash)**: comprueba el acceso. La primera vez pregunta si confías en `github.com`: compara la huella
con la que publica GitHub en
<https://docs.github.com/es/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints>
(ED25519: `SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU`) y responde `yes`.

```bash
ssh -T git@github.com
```

Debe responder `Hi LivanAG/tg-time! You've successfully authenticated, but GitHub does not provide shell access.`

## 4. Clonar el código (una vez)

**VM (bash)**:

```bash
git clone -b main git@github.com:LivanAG/tg-time.git ~/control-horario
cd ~/control-horario
```

Opcional: `bash deploy/setup-vps.sh` instala actualizaciones de seguridad automáticas y endurece SSH
(solo clave, sin root). Es idempotente: no reinstala Docker y no duplica las reglas de puertos que ya existan.
Si lo ejecutas, sal y vuelve a entrar por SSH al terminar.

## 5. Crear `.env` (una vez)

**VM (bash)**: copia la plantilla, protégela y genera todos los secretos.

```bash
cd ~/control-horario
cp .env.example .env
chmod 600 .env
sed -i \
  -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 24)|" \
  -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" \
  -e "s|^APP_ADMIN_PASSWORD=.*|APP_ADMIN_PASSWORD=$(openssl rand -base64 18)|" \
  -e "s|^BACKUP_PASSPHRASE=.*|BACKUP_PASSPHRASE=$(openssl rand -base64 32 | tr -d '\n')|" \
  -e "s|^HOST_UID=.*|HOST_UID=$(id -u)|" \
  -e "s|^HOST_GID=.*|HOST_GID=$(id -g)|" \
  .env
nano .env
```

En `nano` cambia `APP_ADMIN_EMAIL` por tu email y revisa que `DOMAIN=horas.158-179-220-119.sslip.io` y
`CADDYFILE=Caddyfile`. Guarda con `Ctrl+O`, `Enter`, y sal con `Ctrl+X`.

**Guarda fuera de la VM** (gestor de contraseñas) `BACKUP_PASSPHRASE` y `APP_ADMIN_PASSWORD`:

```bash
grep -E '^(BACKUP_PASSPHRASE|APP_ADMIN_PASSWORD)=' .env
```

Sin `BACKUP_PASSPHRASE` las copias no se pueden restaurar.

Qué es cada variable: [`.env.example`](../.env.example).

## 6. Primer despliegue

**VM (bash)**. El primer build tarda varios minutos con 1 OCPU (descarga dependencias de Maven y npm):

```bash
cd ~/control-horario
make deploy
```

`make deploy` comprueba `.env` (perfil prod, permisos 600, sin valores `cambia-esto`) y ejecuta
`docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --remove-orphans`.

## 7. Comprobaciones

**VM (bash)**:

```bash
make ps
```

Los cinco servicios (`db`, `backend`, `frontend`, `caddy`, `backup`) deben acabar en `Up ... (healthy)`.
El backend tarda hasta 2-3 min. Solo `caddy` muestra puertos `0.0.0.0:80` y `0.0.0.0:443`.

```bash
make logs s=backend
```

Busca `Started ControlHorarioApplication` y `Administrador inicial creado: <tu email>`. Sal con `Ctrl+C`.

```bash
make logs s=caddy
```

Busca `certificate obtained successfully` para `horas.158-179-220-119.sslip.io`.

```bash
curl -s https://horas.158-179-220-119.sslip.io/actuator/health
docker stats --no-stream
free -h
```

Debe responder `{"status":"UP"}`; el backend usa ~1,7 GB.

**Navegador**: abre `https://horas.158-179-220-119.sslip.io` y entra con tu `APP_ADMIN_EMAIL` y
`APP_ADMIN_PASSWORD`. Después cambia la contraseña desde la aplicación si quieres y **borra
`APP_ADMIN_PASSWORD` del `.env`** (ya no hace falta: solo se usa cuando no hay ningún usuario):

```bash
sed -i 's|^APP_ADMIN_PASSWORD=.*|APP_ADMIN_PASSWORD=|' ~/control-horario/.env
```

### Si algo falla

| Síntoma | Causa probable |
|---|---|
| `make deploy` dice que falta una variable | Revisa `.env` (`nano .env`) |
| backend reinicia en bucle; log "Configuración de producción inválida" | `JWT_SECRET` corto o de ejemplo |
| backend: "password authentication failed" | Cambiaste `DB_PASSWORD` después del primer arranque (ver "No hagas nunca") |
| caddy no obtiene certificado | Puertos 80/443 cerrados en la Security List o en iptables; prueba `curl -I http://158.179.220.119` desde el PC |
| La web carga pero el login da "Origen no permitido" | Abres una URL distinta de `https://$DOMAIN` |

Prueba aislada de conectividad (sin login): pon `CADDYFILE=Caddyfile.http` en `.env`, `make deploy`, y
abre `http://158.179.220.119`. Vuelve después a `CADDYFILE=Caddyfile`. Por HTTP la aplicación no permite
iniciar sesión: la cookie de sesión es `Secure` y la contraseña viajaría en claro.

---

## 8. Actualizar a una versión nueva

1. **PC (PowerShell)**: prueba en local (`deploy\local-prod.ps1 up`), haz commit y `git push origin main`.
2. **VM (bash)**:

```bash
cd ~/control-horario
make update
```

`make update` hace, en orden: una copia de seguridad, `git pull --ff-only`, el build de las imágenes
nuevas y el reinicio de los contenedores que cambian. **Los datos se conservan**: viven en el volumen
`control-horario_pgdata`, que no se toca. Las migraciones de Flyway nuevas se aplican solas al arrancar el
backend. Al final comprueba `make ps` y la web. Cada despliegue queda anotado en `deploy-history.log`.

**Volver a la versión anterior** si algo sale mal (VM, bash):

```bash
cd ~/control-horario
cat deploy-history.log
git checkout <commit-anterior>
make deploy
```

Si la versión nueva traía una migración de base de datos, restaura además la copia que `make update` hizo
justo antes (sección 9). Para volver a seguir `main`: `git checkout main && make update`.

## 9. Copias de seguridad y restauración

- El contenedor `backup` hace una copia al arrancar y cada día a las 03:00 (hora de Madrid) en
  `~/control-horario/backups/`, cifrada con GPG (AES-256) con `BACKUP_PASSPHRASE`, y borra las de más
  de 14 días.
- `make ps` muestra `backup` como `unhealthy` si no hay ninguna copia de menos de 26 h.

**Copia manual** (VM, bash):

```bash
cd ~/control-horario && make backup && ls -lh backups/
```

**Restaurar** (VM, bash). Pide escribir `restaurar`, para el backend, sustituye **todos** los datos por
los de la copia (en una transacción: si falla, no cambia nada) y vuelve a arrancar el backend:

```bash
cd ~/control-horario
ls -lh backups/
make restore f=backups/2026-10-08_030000.sql.gpg
```

**Sacar las copias de la VM** (si la VM se pierde, las copias de dentro también). **PC (PowerShell)**:

```powershell
New-Item -ItemType Directory -Force "D:\Backups\control-horario" | Out-Null
scp "oracle-ch:control-horario/backups/*.sql.gpg" D:\Backups\control-horario
```

Hazlo con regularidad (p. ej. una vez por semana) y prueba una restauración de vez en cuando con
`deploy\local-prod.ps1 restore` (copia el fichero a `backups\` del proyecto y pon la misma
`BACKUP_PASSPHRASE` en `.env.local-prod`).

## 10. Pasar al dominio final con Cloudflare

Cuando tengas el subdominio (ejemplo: `horas.tudominio.com`):

1. **Cloudflare → DNS**: registro `A`, nombre `horas`, valor `158.179.220.119`, **proxy activado** (nube
   naranja).
2. **Cloudflare → SSL/TLS → Overview**: modo **Full (strict)**.
3. **Cloudflare → SSL/TLS → Origin Server → Create Certificate**: clave RSA (2048) o ECC, hostnames
   `horas.tudominio.com`, validez 15 años, formato PEM. Copia el certificado y la clave privada (la clave
   solo se muestra una vez).
4. **VM (bash)**: guarda el certificado y la clave (pega cada uno en `nano`, guarda y sal). Caddy se
   ejecuta como root sin capacidades extra, así que los ficheros deben ser de root:

```bash
cd ~/control-horario
mkdir -p deploy/certs
nano deploy/certs/origin.pem
nano deploy/certs/origin.key
sudo chown root:root deploy/certs/origin.pem deploy/certs/origin.key
sudo chmod 644 deploy/certs/origin.pem
sudo chmod 600 deploy/certs/origin.key
```

5. **VM (bash)**: cambia el dominio y la configuración de Caddy, y despliega:

```bash
cd ~/control-horario
sed -i -e 's|^DOMAIN=.*|DOMAIN=horas.tudominio.com|' -e 's|^CADDYFILE=.*|CADDYFILE=Caddyfile.cloudflare|' .env
make deploy
make logs s=caddy
```

6. Abre `https://horas.tudominio.com` y vuelve a iniciar sesión (las sesiones del dominio anterior no
   valen en el nuevo). La URL de sslip.io deja de funcionar.

Con `Caddyfile.cloudflare`, Caddy:

- usa el certificado de origen (no pide certificados a Let's Encrypt);
- toma la IP real del visitante de `CF-Connecting-IP` solo si la petición viene de un rango de Cloudflare
  (rate limit y `audit_log` siguen viendo la IP real);
- corta cualquier conexión que no venga de Cloudflare, para que nadie se salte su protección entrando
  directamente por la IP.

Opcional: en Cloudflare activa **SSL/TLS → Edge Certificates → Always Use HTTPS**.

---

## No hagas nunca

- **`docker compose down -v`**, `docker volume rm control-horario_pgdata`, `docker volume prune` o
  `docker system prune --volumes`: **borran la base de datos**. Para parar sin perder nada:
  `make down`. Para limpiar imágenes viejas: `docker image prune -f` (ya lo hace `make deploy`).
- **`git clean -fdx`** o borrar la carpeta del proyecto: se llevan `.env`, `backups/` y `deploy/certs/`
  (no están en git).
- **`docker compose up` con el fichero `.env` sin `COMPOSE_FILE`**: cargaría la configuración de
  desarrollo (perfil dev, secretos de ejemplo). Usa `make` o deja la línea `COMPOSE_FILE` del `.env`.
- **Cambiar `DB_NAME`, `DB_USER` o `DB_PASSWORD` en `.env` después del primer arranque**: PostgreSQL solo
  las aplica al crear el volumen; el backend dejaría de conectar. (Cambiar la contraseña de verdad exige
  `ALTER USER` dentro de PostgreSQL y luego actualizar `.env`.)
- **Cambiar la versión mayor de PostgreSQL** (`postgres:16` → `17`) solo cambiando la imagen: los datos no
  son compatibles. Se hace con copia + restauración.
- **Perder `BACKUP_PASSPHRASE`**: las copias quedan inservibles.
- **Editar código en la VM**: `make update` se niega si hay cambios locales. Cambia el código en el PC y
  súbelo con git.
- **Subir `.env` o `.env.local-prod` a git**, o darle permisos distintos de 600.
- **Abrir puertos de PostgreSQL o del backend** en Oracle o en `docker-compose.prod.yml`.
- **`make reset-db` en la VM** (está bloqueado en prod, pero por si acaso).

## Oracle y las instancias inactivas

En cuentas solo Always Free, Oracle puede reclamar una instancia si durante 7 días **a la vez** el uso de
CPU (percentil 95), red y memoria está por debajo del 20 %. La aplicación casi no usa CPU ni red, así que
lo que la mantiene "activa" es la memoria: el heap fijo y pre-reservado de 1,5 GB
(`-Xms1500m -Xmx1500m -XX:+AlwaysPreTouch`) deja la VM en torno al 30 % de uso.

- Oracle mide la memoria con el **Oracle Cloud Agent**: en la consola, *Instance → Oracle Cloud Agent*,
  comprueba que el plugin **Compute Instance Monitoring** está activado.
- Comprueba la gráfica en *Instance → Metrics → Memory Utilization* al día siguiente del despliegue.
- La solución definitiva es pasar la cuenta a **Pay As You Go**: lo incluido en Always Free sigue siendo
  gratis y la regla de reclamación deja de aplicarse. Pon un presupuesto con alerta en
  *Billing → Budgets* para evitar sorpresas.

## Referencia de comandos (VM)

| Comando | Qué hace |
|---|---|
| `make deploy` | Construye las imágenes en la VM y (re)arranca todo |
| `make update` | Copia + `git pull` + rebuild, sin perder datos |
| `make ps` | Estado de los contenedores |
| `make logs` / `make logs s=backend` | Logs (todos o de un servicio); `Ctrl+C` para salir |
| `make backup` | Copia cifrada al momento |
| `make restore f=backups/<copia>.sql.gpg` | Restaura una copia (pide confirmación) |
| `make down` | Para los contenedores (conserva los datos) |
