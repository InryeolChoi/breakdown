# breakground

직장인이 업무 중 3~10분 정도 잠깐 놀다 가는 웹 서비스.
로그인 없이 시간대별 채팅방에 들어가거나 짧은 미니게임을 하는 게 목표다.

Java / Spring / PostgreSQL을 직접 배우면서 만든다.
AWS에 배포하고 실제 사용자도 모아볼 생각이다.

## 만들려는 것

- 점심방, 간식방, 야근방처럼 정해진 시간에 열리는 채팅방
- 로그인 없는 익명 사용자와 닉네임
- 최대 140자 메시지. 방이 닫히면 메시지는 삭제
- 메시지 신고와 경고 / 차단
- 짧은 미니게임과 플레이 결과, 랭킹

방은 매일 반복해서 열리고, 자정을 넘는 시간도 허용한다.
운영 시간은 DB에 저장하고, 지금 열려 있는지는 Spring에서 판단한다.

## 현재 상태

기획과 ERD 초안, 초기 환경설정을 마쳤다.
Spring Boot 실행과 로컬 PostgreSQL 연결까지 확인했고, 도메인 기능은 이제 구현할 단계다.

- Spring MVC / JPA / Validation / Flyway 의존성 설정
- Docker Compose로 PostgreSQL 실행
- Makefile로 Docker 실행과 종료 관리
- Flyway V1으로 Room 테이블 생성 및 로컬 적용 확인
- Room 클래스에 필드와 getter 작성. JPA 매핑과 API는 아직 없음

다음은 Room 클래스에 JPA 매핑을 추가하고, Room 조회부터 구현한다.
이후 AnonymousUser와 Message를 추가할 예정이다.

## 기술

| 구분 | 사용 기술 |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC |
| DB 접근 | Spring Data JPA |
| Database | PostgreSQL 17 |
| Schema 관리 | Flyway |
| Build | Gradle Wrapper |
| 로컬 환경 | Docker Compose, Make |

프론트엔드는 React 또는 Next.js를 고려 중이다. AWS 배포는 아직 진행하지 않았다.
Redis는 필요해지면 검토하고, Kafka / RabbitMQ / Kubernetes / MSA는 이번 범위에서 제외한다.

## 로컬 실행

JDK 21, Docker Desktop, Make가 필요하다.
아래는 macOS 기준이다. 현재 Makefile은 `docker-compose` 명령을 사용하므로 해당 명령도 실행 가능해야 한다.
로컬 5432 포트는 비어 있어야 한다.

프로젝트 루트에서 DB를 실행한다.

```bash
make
```

Docker Desktop을 열고 Docker 엔진이 준비되면 PostgreSQL 컨테이너를 실행한다.
Spring 애플리케이션은 별도로 실행한다.

```bash
./gradlew bootRun
```

IntelliJ에서 `BreakgroundApplication`을 실행해도 된다.
DB 이름, 사용자, 비밀번호는 로컬 개발용으로 모두 `breakground`이며,
접속 주소는 `localhost:5432`다. 배포할 때는 별도 설정으로 분리할 예정이다.

아직 API가 없으므로 `http://localhost:8080`에서 404가 나올 수 있다.
기동 여부는 콘솔의 `Started BreakgroundApplication` 로그로 확인한다.

종료할 때는 애플리케이션을 먼저 멈추고 실행한다.

```bash
make down
```

DB 데이터 볼륨은 유지된다. Docker Desktop까지 종료하려면 `make fclean`을 사용한다.
`make reset-db`와 `make nuke-db`는 DB 데이터도 삭제한다.

## 설계 문서

- [의사결정](docs/decisions.md)
- [ERD 원본 (DBML)](docs/erd.dbml)

이 두 문서를 설계 기준으로 사용한다.
정책이나 스키마를 바꾸면 문서도 같이 수정한다.

## 개발 방향

핵심 설계와 코드는 직접 작성한다.
AI는 설계 검토, 힌트, 코드 리뷰에 활용하고, 왜 그렇게 동작하는지 이해하는 걸 우선한다.
기능을 위해 필요한 기술만 쓰고, 운영하면서 생기는 문제와 해결 과정을 기록할 예정이다.
