# Atajos. Todo corre en contenedores: no hace falta Java, Maven ni Node en la máquina.
# Con SPRING_PROFILE=prod en .env (la VM de Oracle) los comandos usan docker-compose.prod.yml.
PROFILE := $(shell sed -n 's/^SPRING_PROFILE=//p' .env 2>/dev/null)

COMPOSE_DEV := docker compose -f docker-compose.yml -f docker-compose.override.yml
COMPOSE_PROD := docker compose -f docker-compose.yml -f docker-compose.prod.yml
COMPOSE := $(if $(filter prod,$(PROFILE)),$(COMPOSE_PROD),$(COMPOSE_DEV))

MAVEN_IMAGE := maven:3.9-eclipse-temurin-21
NODE_IMAGE := node:22-alpine

.DEFAULT_GOAL := help
.PHONY: help dev down logs ps reset-db rebuild-backend test-back test-front test check-prod deploy update backup restore

help: ## Muestra esta ayuda
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

# ---------- desarrollo ----------
dev: ## Levanta db, backend y frontend con recarga en caliente (http://localhost:5173)
	$(COMPOSE_DEV) up --build

down: ## Para los contenedores (conserva los datos)
	$(COMPOSE) down

logs: ## Sigue los logs: make logs s=backend
	$(COMPOSE) logs -f --tail=200 $(s)

ps: ## Estado de los contenedores
	$(COMPOSE) ps

reset-db: ## Borra la base de datos de DESARROLLO (nunca en prod)
	@if [ "$(PROFILE)" = "prod" ]; then echo "reset-db está prohibido en prod"; exit 1; fi
	$(COMPOSE_DEV) down -v

rebuild-backend: ## Reconstruye y reinicia solo el backend (dev)
	$(COMPOSE_DEV) up -d --build backend

# ---------- tests ----------
test-back: ## Tests del backend (unitarios + Testcontainers)
	docker run --rm \
		-v "$(CURDIR)/backend":/app -w /app \
		-v control-horario-m2:/root/.m2 \
		-v /var/run/docker.sock:/var/run/docker.sock \
		--add-host=host.docker.internal:host-gateway \
		-e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
		$(MAVEN_IMAGE) mvn -B -ntp verify

test-front: ## Typecheck, lint y tests del frontend
	docker run --rm -v "$(CURDIR)/frontend":/app -v /app/node_modules -w /app $(NODE_IMAGE) \
		sh -c "npm ci && npm run typecheck && npm run lint && npm test"

test: test-back test-front ## Todos los tests

# ---------- producción (en la VM) ----------
check-prod: .env
	@if [ "$(PROFILE)" != "prod" ]; then echo "Requiere SPRING_PROFILE=prod en .env"; exit 1; fi
	@if [ "$$(stat -c %a .env)" != "600" ]; then echo "Protege .env: chmod 600 .env"; exit 1; fi
	@if grep -q '=cambia-esto' .env; then echo "Quedan valores 'cambia-esto' en .env"; exit 1; fi
	@if grep -q '^DOMAIN=.*TU-IP' .env; then echo "Pon en DOMAIN la IP de tu VM (horas.1-2-3-4.sslip.io) o tu dominio"; exit 1; fi

deploy: check-prod ## Construye las imágenes en la VM y (re)arranca todo
	mkdir -p backups deploy/certs
	$(COMPOSE_PROD) up -d --build --remove-orphans
	@echo "$$(date -Iseconds) $$(git rev-parse --short HEAD) $$(git log -1 --format=%s)" >> deploy-history.log
	docker image prune -f
	@echo "Desplegado $$(git rev-parse --short HEAD). Comprueba con: make ps  /  make logs s=backend"

update: check-prod ## Copia de seguridad + git pull + rebuild (sin perder datos)
	@if [ -n "$$(git status --porcelain --untracked-files=no)" ]; then \
		echo "Hay cambios locales en ficheros del repositorio (git status). No edites código en la VM."; exit 1; fi
	@if $(COMPOSE_PROD) ps --status running --services | grep -qx backup; then \
		$(COMPOSE_PROD) exec -T backup backup.sh once; \
	else echo "El contenedor backup no está en marcha: se actualiza sin copia previa"; fi
	git pull --ff-only
	$(MAKE) deploy

backup: check-prod ## Copia de seguridad manual (cifrada en backups/)
	$(COMPOSE_PROD) exec -T backup backup.sh once

restore: check-prod ## Restaura una copia: make restore f=backups/2026-10-07_030000.sql.gpg
	@test -n "$(f)" || { echo "Uso: make restore f=backups/<copia>.sql.gpg   (copias: ls -lh backups/)"; exit 1; }
	@test -f "$(f)" || { echo "No existe $(f)"; exit 1; }
	@printf "Se sustituirán TODOS los datos actuales por %s. Escribe 'restaurar' para continuar: " "$(f)"; \
		read answer; [ "$$answer" = "restaurar" ] || { echo "Cancelado"; exit 1; }
	$(COMPOSE_PROD) stop backend
	$(COMPOSE_PROD) exec -T backup restore.sh /backups/$(notdir $(f)) || { $(COMPOSE_PROD) start backend; exit 1; }
	$(COMPOSE_PROD) start backend
	@echo "Restaurado. El backend tarda 1-2 min en estar sano: make ps"

.env:
	@echo "Falta .env: cp .env.example .env && chmod 600 .env, y cambia los valores (docs/DEPLOY.md)." && exit 1
