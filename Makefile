# Atajos. Todo corre en contenedores: no hace falta Java, Maven ni Node en la máquina.
# Con SPRING_PROFILE=prod en .env (la VM de Oracle) los comandos usan docker-compose.prod.yml.
PROFILE := $(shell sed -n 's/^SPRING_PROFILE=//p' .env 2>/dev/null)
REGISTRY ?= $(or $(shell sed -n 's/^REGISTRY=//p' .env 2>/dev/null),local)
# Último tag desplegado (lo guarda make deploy) para que logs/restart usen las mismas imágenes.
TAG ?= $(or $(shell cat .deployed-tag 2>/dev/null),latest)
export REGISTRY TAG

COMPOSE_DEV := docker compose -f docker-compose.yml -f docker-compose.dev.yml
COMPOSE_PROD := docker compose -f docker-compose.yml -f docker-compose.prod.yml
COMPOSE := $(if $(filter prod,$(PROFILE)),$(COMPOSE_PROD),$(COMPOSE_DEV))

MAVEN_IMAGE := maven:3.9-eclipse-temurin-21
NODE_IMAGE := node:22-alpine
PLATFORMS := linux/amd64,linux/arm64

.DEFAULT_GOAL := help
.PHONY: help dev down logs ps reset-db rebuild-backend test-back test-front test build push deploy backup restore

help: ## Muestra esta ayuda
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

# ---------- desarrollo ----------
dev: .env ## Levanta db, backend y frontend con recarga en caliente (http://localhost:5173)
	$(COMPOSE_DEV) up --build

down: ## Para los contenedores (conserva los datos)
	$(COMPOSE) down

logs: ## Sigue los logs: make logs s=backend
	$(COMPOSE) logs -f $(s)

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

# ---------- imágenes ----------
build: ## Imágenes de prod para tu arquitectura: make build TAG=<sha>
	docker compose -f docker-compose.yml build backend frontend

push: ## Construye amd64+arm64 y publica en el registro (normalmente lo hace el CI)
	@if [ "$(REGISTRY)" = "local" ]; then echo "Define REGISTRY (p. ej. ghcr.io/usuario)"; exit 1; fi
	docker buildx build --platform $(PLATFORMS) -t $(REGISTRY)/control-horario-backend:$(TAG) --push backend
	docker buildx build --platform $(PLATFORMS) -t $(REGISTRY)/control-horario-frontend:$(TAG) --push frontend

# ---------- producción (en la VM) ----------
deploy: .env ## Despliega o vuelve atrás: make deploy TAG=abc1234
	@if [ "$(PROFILE)" != "prod" ]; then echo "deploy requiere SPRING_PROFILE=prod en .env"; exit 1; fi
	@if [ "$(TAG)" = "latest" ]; then echo "Indica la versión: make deploy TAG=<sha del commit>"; exit 1; fi
	mkdir -p backups deploy/certs
	$(COMPOSE_PROD) pull --ignore-buildable
	$(COMPOSE_PROD) up -d --build --remove-orphans
	@echo "$(TAG)" > .deployed-tag
	@echo "$$(date -Iseconds) $(TAG)" >> deploy-history.log
	@echo "Desplegado $(TAG). Historial para volver atrás: deploy-history.log"

backup: ## Copia de seguridad manual (cifrada en backups/)
	$(COMPOSE_PROD) exec backup backup.sh once

restore: ## Restaura una copia: make restore f=backups/2026-10-07_030000.sql.gpg
	@test -n "$(f)" || { echo "Uso: make restore f=backups/<copia>.sql.gpg"; exit 1; }
	@test -f "$(f)" || { echo "No existe $(f)"; exit 1; }
	@printf "Se sustituirán TODOS los datos actuales por %s. Escribe 'restaurar' para continuar: " "$(f)"; \
		read answer; [ "$$answer" = "restaurar" ] || { echo "Cancelado"; exit 1; }
	$(COMPOSE_PROD) stop backend
	$(COMPOSE_PROD) exec -T backup restore.sh /backups/$(notdir $(f)) || { $(COMPOSE_PROD) start backend; exit 1; }
	$(COMPOSE_PROD) start backend

.env:
	@echo "Falta .env: cópialo con 'cp .env.example .env' y cambia los valores." && exit 1
