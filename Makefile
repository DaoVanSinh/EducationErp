# Usage: make <target> [ENV=stg|prod]   (ENV defaults to stg)
#
#   make compose-up              # build + start the stg stack (this machine)
#   make compose-up ENV=prod     # build + start the prod stack (server)
#   make compose-down            # stop and remove the stg stack
#   make compose-down ENV=prod   # stop and remove the prod stack
#   make logs                    # tail logs of the stg stack
#   make logs ENV=prod           # tail logs of the prod stack
#   make ps                      # list containers of the stg stack

ENV ?= stg

ifeq ($(ENV),prod)
COMPOSE_FILE := docker-compose.prod.yml
ENV_FILE := backend/.env.docker.prod
else
COMPOSE_FILE := docker-compose.yml
ENV_FILE := backend/.env.docker
endif

SEPARATOR_LINE := ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

define EDUERP_LOGO
	@printf "███████╗██████╗ ██╗   ██╗███████╗██████╗ ██████╗ \n"
	@printf "██╔════╝██╔══██╗██║   ██║██╔════╝██╔══██╗██╔══██╗\n"
	@printf "█████╗  ██║  ██║██║   ██║█████╗  ██████╔╝██████╔╝\n"
	@printf "██╔══╝  ██║  ██║██║   ██║██╔══╝  ██╔══██╗██╔═══╝ \n"
	@printf "███████╗██████╔╝╚██████╔╝███████╗██║  ██║██║     \n"
	@printf "╚══════╝╚═════╝  ╚═════╝ ╚══════╝╚═╝  ╚═╝╚═╝     \n"
endef

define SEPARATOR
	@printf "$(SEPARATOR_LINE)\n"
endef

.DEFAULT_GOAL := help
.PHONY: help logo compose-up compose-down compose-restart logs ps

logo:
	@printf "\n"
	$(EDUERP_LOGO)
	@printf "\n"
	$(SEPARATOR)

help: logo
	@echo "Usage: make <target> [ENV=stg|prod]   (ENV defaults to stg)"
	@echo ""
	@echo "  compose-up       Build and start the stack ($(COMPOSE_FILE))"
	@echo "  compose-down     Stop and remove the stack ($(COMPOSE_FILE))"
	@echo "  compose-restart  Recreate the app+nginx containers after a code change"
	@echo "  logs             Tail logs of the running stack"
	@echo "  ps               List containers of the stack"
	@echo ""
	@echo "Examples:"
	@echo "  make compose-up"
	@echo "  make compose-up ENV=prod"
	@echo "  make compose-down ENV=prod"
	@echo "  make logs ENV=prod"

compose-up: logo
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) up -d --build

compose-down:
	docker compose -f $(COMPOSE_FILE) down

compose-restart: logo
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) up -d --build app nginx

logs:
	docker compose -f $(COMPOSE_FILE) logs -f

ps:
	docker compose -f $(COMPOSE_FILE) ps
