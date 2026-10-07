# pg_dump de la misma versión que la base de datos + gpg para cifrar las copias.
FROM postgres:16-alpine
RUN apk add --no-cache gnupg
COPY backup.sh restore.sh /usr/local/bin/
RUN chmod 0755 /usr/local/bin/backup.sh /usr/local/bin/restore.sh
ENTRYPOINT ["backup.sh"]
CMD ["loop"]
