#!/bin/sh
# Copia de seguridad cifrada de PostgreSQL.
#   backup.sh loop   copia cada 24 h y borra las de más de RETENTION_DAYS días (por defecto)
#   backup.sh once   una sola copia (make backup)
# Conexión con PGHOST, PGDATABASE, PGUSER y PGPASSWORD.
set -eu
set -o pipefail
umask 077

: "${BACKUP_PASSPHRASE:?Falta BACKUP_PASSPHRASE}"
BACKUP_DIR=/backups
RETENTION_DAYS="${RETENTION_DAYS:-14}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-86400}"

backup_once() {
  file="$BACKUP_DIR/$(date +%Y-%m-%d_%H%M%S).sql.gpg"
  # --clean --if-exists: la restauración sustituye las tablas existentes.
  # La contraseña va por un descriptor de fichero, no en la línea de comandos.
  pg_dump --clean --if-exists --no-owner \
    | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 3 \
          --symmetric --cipher-algo AES256 --output "$file.partial" 3<<EOF
$BACKUP_PASSPHRASE
EOF
  mv "$file.partial" "$file"
  echo "$(date -Iseconds) copia creada: $file"
}

prune() {
  find "$BACKUP_DIR" -name '*.sql.gpg' -type f -mtime +"$RETENTION_DAYS" -print -delete
  find "$BACKUP_DIR" -name '*.partial' -type f -mmin +60 -delete
}

case "${1:-loop}" in
  once)
    backup_once
    ;;
  loop)
    while true; do
      backup_once || echo "$(date -Iseconds) ERROR: la copia ha fallado" >&2
      prune
      sleep "$INTERVAL_SECONDS"
    done
    ;;
  *)
    echo "Uso: backup.sh [loop|once]" >&2
    exit 2
    ;;
esac
