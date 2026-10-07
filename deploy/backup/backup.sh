#!/bin/sh
# Copia de seguridad cifrada de PostgreSQL.
#   backup.sh loop   una copia al arrancar y después cada día a las BACKUP_TIME (por defecto 03:00, hora
#                    de TZ); borra las de más de RETENTION_DAYS días
#   backup.sh once   una sola copia (make backup)
# Conexión con PGHOST, PGDATABASE, PGUSER y PGPASSWORD.
set -eu
set -o pipefail
umask 077

: "${BACKUP_PASSPHRASE:?Falta BACKUP_PASSPHRASE}"
BACKUP_DIR=/backups
RETENTION_DAYS="${RETENTION_DAYS:-14}"
BACKUP_TIME="${BACKUP_TIME:-03:00}"

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
  echo "$(date -Iseconds) copia creada: $file ($(du -h "$file" | cut -f1))"
}

prune() {
  find "$BACKUP_DIR" -name '*.sql.gpg' -type f -mtime +"$RETENTION_DAYS" -print -delete
  find "$BACKUP_DIR" -name '*.partial' -type f -mmin +60 -delete
}

# Segundos hasta la próxima BACKUP_TIME (en los cambios de horario puede desviarse una hora).
seconds_until_next() {
  now=$(date +%s)
  target=$(date -d "$(date +%Y-%m-%d) $BACKUP_TIME" +%s)
  [ "$target" -gt "$now" ] || target=$((target + 86400))
  echo $((target - now))
}

case "${1:-loop}" in
  once)
    backup_once
    ;;
  loop)
    while true; do
      backup_once || echo "$(date -Iseconds) ERROR: la copia ha fallado" >&2
      prune
      wait_seconds=$(seconds_until_next)
      echo "$(date -Iseconds) próxima copia en $((wait_seconds / 60)) min"
      sleep "$wait_seconds"
    done
    ;;
  *)
    echo "Uso: backup.sh [loop|once]" >&2
    exit 2
    ;;
esac
