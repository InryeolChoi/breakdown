UNAME_S := $(shell uname -s)
DOCKER_STOP_TIMEOUT ?= 30
COMPOSE = docker compose

.PHONY: all up down clean fclean repair-db reset-db nuke-db docker-start docker-stop wait-for-docker

all: up

docker-start:
ifeq ($(UNAME_S),Darwin)
	@echo "Opening Docker Desktop..."
	@open -a Docker
else
	@sudo systemctl start docker
endif

# 의존 관계로 순서를 보장한다. make -j에서도 시작 후 준비를 기다린다.
wait-for-docker: docker-start
	@echo "Waiting for Docker engine (Ctrl+C to cancel if stuck)..."
	@until docker info >/dev/null 2>&1; do sleep 1; done
	@echo "Docker engine is ready."

up: wait-for-docker
	@$(COMPOSE) up -d || { echo "DB startup failed. For network-not-found errors, run: make repair-db"; exit 1; }

# 컨테이너만 재생성한다. 기존 DB 데이터 볼륨은 유지한다.
repair-db: wait-for-docker
	$(COMPOSE) up -d --force-recreate postgres

down:
	@$(COMPOSE) down || { echo "DB cleanup failed. Desktop shutdown was not attempted. Docker may have partially completed cleanup."; exit 1; }

clean: down

# down이 성공한 다음에만 종료 요청. 병렬 make에서도 순서를 보장한다.
fclean: down
	@$(MAKE) docker-stop

docker-stop:
ifeq ($(UNAME_S),Darwin)
	@echo "Stopping Docker Desktop (up to $(DOCKER_STOP_TIMEOUT)s)..."
	@docker desktop stop --timeout $(DOCKER_STOP_TIMEOUT) || { echo "Desktop did not stop. Check other workloads before manually running: docker desktop stop --force --timeout $(DOCKER_STOP_TIMEOUT)"; exit 1; }
else
	@sudo systemctl stop docker
endif

# 아래 두 명령은 DB 데이터 볼륨도 삭제한다.
reset-db: wait-for-docker
	$(COMPOSE) down -v
	$(COMPOSE) up -d

nuke-db:
	$(COMPOSE) down -v
