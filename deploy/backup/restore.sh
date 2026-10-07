#!/bin/sh
# Restaura una copia creada por backup.sh: restore.sh /backups/2026-10-07_030000.sql.gpg
# Sustituye el contenido actual de la base de datos (make restore pide confirmación antes).
set -eu
set -o pipefail
umask 077

: "${BACKUP_PASSPHRASE:?Falta BACKUP_PASSPHRASE}"
file="${1:?Uso: restore.sh /backups/<copia>.sql.gpg}"
[ -f "$file" ] || { echo "No existe $file" >&2; exit 1; }

gpg --batch --pinentry-mode loopback --passphrase-fd 3 --decrypt "$file" 3<<EOF \
  | psql --quiet -v ON_ERROR_STOP=1 --single-transaction
$BACKUP_PASSPHRASE
EOF
echo "$(date -Iseconds) restaurada: $file"
