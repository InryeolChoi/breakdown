UNAME_S := $(shell uname -s)

.PHONY: all up down clean fclean nuke-db reset-db docker-start docker-stop wait-for-docker

all: up

# Docker(엔진/Desktop)를 실행
docker-start:
ifeq ($(UNAME_S),Darwin)
	@echo "Opening Docker Desktop..."
	@open -a Docker
else
	@echo "Starting docker service..."
	@sudo systemctl start docker
endif

# docker info가 성공할 때까지 대기 (Docker Desktop은 실행 후 데몬 준비까지 시간이 걸림)
wait-for-docker:
	@echo "Waiting for docker daemon..."
	@until docker info > /dev/null 2>&1; do sleep 1; done
	@echo "Docker daemon is ready."

# Docker(엔진/Desktop)를 종료
docker-stop:
ifeq ($(UNAME_S),Darwin)
	@echo "Quitting Docker Desktop..."
	@osascript -e 'quit app "Docker"'
else
	@echo "Stopping docker service..."
	@sudo systemctl stop docker
endif

# make: Docker(Desktop) 실행 -> 데몬 대기 -> compose 기동
up: docker-start wait-for-docker
	docker-compose up -d

# compose만 내리고 Docker는 유지
down:
	docker-compose down

# make clean: compose 내리기 (볼륨 유지), Docker는 유지
clean: down

# make fclean: compose 내리기 (볼륨 유지) + Docker(Desktop) 종료
fclean: down docker-stop

# DB 볼륨을 삭제하고 다시 기동 (데이터 초기화용)
reset-db:
	docker-compose down -v
	docker-compose up -d

# DB 볼륨을 삭제만 하고 기동은 하지 않음 (위험한 작업임을 명시)
nuke-db:
	docker-compose down -v
