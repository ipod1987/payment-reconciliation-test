.PHONY: help build up down logs ps clean seed

# Variables
COMPOSE = docker compose
APP     = app

include .env
export

help: ## Muestra esta ayuda
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: ## Construye la imagen de la aplicación
	$(COMPOSE) build --no-cache $(APP)

up: ## Levanta todos los servicios (postgres + wiremock-json + wiremock-soap + app)
	$(COMPOSE) up -d
	@echo ""
	@echo "  API:              http://localhost:8080/api/v1/reconciliation/{paymentId}"
	@echo "  Swagger:          http://localhost:8080/api/swagger-ui.html"
	@echo "  WireMock JSON:    http://localhost:9091/__admin/mappings"
	@echo "  WireMock SOAP:    http://localhost:9092/__admin/mappings"
	@echo ""

up-db: ## Levanta postgres + ambos WireMock (útil para desarrollar localmente sin Docker app)
	$(COMPOSE) up -d postgres wiremock-json wiremock-soap

down: ## Para y elimina los contenedores (los volúmenes persisten)
	$(COMPOSE) down

down-v: ## Para contenedores Y elimina volúmenes (borra datos de la BD)
	$(COMPOSE) down -v

logs: ## Sigue los logs de todos los servicios
	$(COMPOSE) logs -f

logs-app: ## Sigue los logs solo de la aplicación
	$(COMPOSE) logs -f $(APP)

ps: ## Muestra el estado de los contenedores
	$(COMPOSE) ps

seed: ## Inserta datos de prueba en la BD (ejecutar después de `make up`)
	docker exec -i reconciliation-postgres \
	  psql -U $(POSTGRES_USER) -d $(POSTGRES_DB) \
	  < docker/postgres/init/01_seed_data.sql
	@echo "Seed data inserted."

clean: ## Elimina contenedores, volúmenes e imagen construida
	$(COMPOSE) down -v --rmi local
