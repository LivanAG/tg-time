# Prueba en tu PC (Windows + Docker Desktop) la configuración de PRODUCCIÓN, la misma que en la VM:
# Caddy con HTTPS, perfil prod, límites de memoria, JVM de 1,5 GB, copias cifradas...
# Se sirve en https://horas.localhost con un certificado autofirmado de Caddy (acepta el aviso del
# navegador). Usa su propio proyecto (ch-prodlocal), su propio volumen de datos y su propio fichero
# .env.local-prod (secretos aleatorios generados la primera vez): no toca tu entorno de desarrollo.
# Necesita libres los puertos 80 y 443 del PC.
#
#   powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 up              construye y arranca
#   powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 ps              estado
#   powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 logs backend    logs (Ctrl+C para salir)
#   powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 backup          copia cifrada en backups\
#   powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 restore backups\<copia>.sql.gpg
#   powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 down            para (conserva los datos)
#   powershell -ExecutionPolicy Bypass -File deploy\local-prod.ps1 destroy         para y BORRA los datos de esta prueba
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'ps', 'logs', 'backup', 'restore', 'down', 'destroy')]
    [string]$Command = 'up',
    [Parameter(Position = 1)]
    [string]$Arg
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$envFile = '.env.local-prod'
$compose = @('compose', '-p', 'ch-prodlocal', '--env-file', $envFile,
    '-f', 'docker-compose.yml', '-f', 'docker-compose.prod.yml')

function New-RandomBytes([int]$count) {
    $bytes = New-Object byte[] $count
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return $bytes
}
function New-Base64([int]$count) { [Convert]::ToBase64String((New-RandomBytes $count)) }
function New-Hex([int]$count) { -join ((New-RandomBytes $count) | ForEach-Object { $_.ToString('x2') }) }

function Invoke-Compose {
    & docker @compose @args
    if ($LASTEXITCODE -ne 0) { throw "docker compose $args ha fallado (código $LASTEXITCODE)" }
}

if (-not (Test-Path $envFile)) {
    # Misma plantilla que en la VM, con secretos aleatorios y el dominio local.
    # COMPOSE_FILE se quita: aquí se pasan los -f explícitamente (y en Windows el separador sería ';').
    $lines = Get-Content -Encoding UTF8 .env.example | Where-Object { $_ -notmatch '^COMPOSE_FILE=' } | ForEach-Object {
        switch -Regex ($_) {
            '^DOMAIN=' { 'DOMAIN=horas.localhost'; break }
            '^CADDYFILE=' { 'CADDYFILE=Caddyfile'; break }
            '^DB_PASSWORD=' { 'DB_PASSWORD=' + (New-Hex 24); break }
            '^JWT_SECRET=' { 'JWT_SECRET=' + (New-Base64 48); break }
            '^APP_ADMIN_EMAIL=' { 'APP_ADMIN_EMAIL=admin@horas.localhost'; break }
            '^APP_ADMIN_PASSWORD=' { 'APP_ADMIN_PASSWORD=' + (New-Base64 18); break }
            '^BACKUP_PASSPHRASE=' { 'BACKUP_PASSPHRASE=' + (New-Base64 32); break }
            '^HOST_UID=' { 'HOST_UID=1000'; break }
            '^HOST_GID=' { 'HOST_GID=1000'; break }
            default { $_ }
        }
    }
    [IO.File]::WriteAllLines((Join-Path $root $envFile), [string[]]$lines)   # UTF-8 sin BOM
    Write-Host "Creado $envFile con secretos aleatorios (no se sube a git)."
}

switch ($Command) {
    'up' {
        New-Item -ItemType Directory -Force backups, deploy\certs | Out-Null
        Invoke-Compose up -d --build --remove-orphans
        $email = (Select-String -Path $envFile -Pattern '^APP_ADMIN_EMAIL=(.*)$').Matches[0].Groups[1].Value
        Write-Host ''
        Write-Host 'Arrancando. El backend tarda 1-2 min en estar sano (comprueba con: ... local-prod.ps1 ps).'
        Write-Host 'Abre https://horas.localhost  (certificado autofirmado: acepta el aviso del navegador).'
        Write-Host "Usuario: $email   Contraseña: APP_ADMIN_PASSWORD en $envFile"
    }
    'ps' { Invoke-Compose ps }
    'logs' { if ($Arg) { Invoke-Compose logs -f --tail=200 $Arg } else { Invoke-Compose logs -f --tail=200 } }
    'backup' { Invoke-Compose exec -T backup backup.sh once }
    'restore' {
        if (-not $Arg -or -not (Test-Path $Arg)) { throw 'Uso: local-prod.ps1 restore backups\<copia>.sql.gpg' }
        $name = Split-Path -Leaf $Arg
        $answer = Read-Host "Se sustituirán TODOS los datos de la prueba local por $name. Escribe 'restaurar'"
        if ($answer -ne 'restaurar') { Write-Host 'Cancelado'; return }
        Invoke-Compose stop backend
        try { Invoke-Compose exec -T backup restore.sh "/backups/$name" }
        finally { Invoke-Compose start backend }
    }
    'down' { Invoke-Compose down }
    'destroy' {
        $answer = Read-Host "Se BORRARÁN los datos de la prueba local (proyecto ch-prodlocal). Escribe 'borrar'"
        if ($answer -ne 'borrar') { Write-Host 'Cancelado'; return }
        Invoke-Compose down -v
    }
}
