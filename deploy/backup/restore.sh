#!/bin/sh
# Restaura una copia creada por backup.sh: restore.sh /backups/2026-10-07_030000.sql.gpg
# Sustituye TODO el contenido de la base de datos (make restore pide confirmación y para el backend).
# Se borra el esquema entero antes de cargar la copia: así una copia anterior a una migración nueva
# no deja tablas sobrantes que harían fallar a Flyway. Todo va en una transacción: si algo falla, la
# base de datos queda como estaba.
set -eu
set -o pipefail
umask 077
export PGOPTIONS="-c client_min_messages=warning"   # sin los NOTICE del DROP ... CASCADE

: "${BACKUP_PASSPHRASE:?Falta BACKUP_PASSPHRASE}"
file="${1:?Uso: restore.sh /backups/<copia>.sql.gpg}"
[ -f "$file" ] || { echo "No existe $file" >&2; exit 1; }

# Descifra primero a memoria temporal: con una contraseña errónea falla aquí, sin tocar la base de datos.
plain=$(mktemp)
trap 'rm -f "$plain"' EXIT
gpg --batch --quiet --pinentry-mode loopback --passphrase-fd 3 --decrypt "$file" > "$plain" 3<<EOF
$BACKUP_PASSPHRASE
EOF

{
  echo 'DROP SCHEMA IF EXISTS public CASCADE;'
  echo 'CREATE SCHEMA public;'
  cat "$plain"
} | psql --quiet -v ON_ERROR_STOP=1 --single-transaction >/dev/null
echo "$(date -Iseconds) restaurada: $file"
