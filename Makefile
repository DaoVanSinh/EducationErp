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
	@echo ███████╗██████╗ ██╗   ██╗███████╗██████╗ ██████╗
	@echo ██╔════╝██╔══██╗██║   ██║██╔════╝██╔══██╗██╔══██╗
	@echo █████╗  ██║  ██║██║   ██║█████╗  ██████╔╝██████╔╝
	@echo ██╔══╝  ██║  ██║██║   ██║██╔══╝  ██╔══██╗██╔═══╝
	@echo ███████╗██████╔╝╚██████╔╝███████╗██║  ██║██║
	@echo ╚══════╝╚═════╝  ╚═════╝ ╚══════╝╚═╝  ╚═╝╚═╝
endef

define SEPARATOR
	@echo $(SEPARATOR_LINE)
endef

.DEFAULT_GOAL := help
.PHONY: help logo compose-up compose-down compose-restart logs ps demo-seed demo-cleanup

DEMO_SCRIPT := powershell -ExecutionPolicy Bypass -File scripts/demo-data/demo-data.ps1

logo:
	@echo.
	$(EDUERP_LOGO)
	@echo.
	$(SEPARATOR)

help: logo
	@echo Usage: make ^<target^> [ENV=stg^|prod]   (ENV defaults to stg)
	@echo.
	@echo   compose-up       Build and start the stack ($(COMPOSE_FILE))
	@echo   compose-down     Stop and remove the stack ($(COMPOSE_FILE))
	@echo   compose-restart  Recreate the app+nginx containers after a code change
	@echo   logs             Tail logs of the running stack
	@echo   ps               List containers of the stack
	@echo   demo-seed        Backup DB then load demo data (MAILBOX=you@gmail.com required)
	@echo   demo-cleanup     Remove all demo data
	@echo.
	@echo Examples:
	@echo   make compose-up
	@echo   make compose-up ENV=prod
	@echo   make compose-down ENV=prod
	@echo   make logs ENV=prod

compose-up: logo
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) up -d --build

compose-down:
	docker compose -f $(COMPOSE_FILE) down

compose-restart: logo
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) up -d --build app nginx

demo-seed:
	$(DEMO_SCRIPT) -Action seed -Mailbox $(MAILBOX)

demo-cleanup:
	$(DEMO_SCRIPT) -Action cleanup

logs:
	docker compose -f $(COMPOSE_FILE) logs -f

ps:
	docker compose -f $(COMPOSE_FILE) ps
