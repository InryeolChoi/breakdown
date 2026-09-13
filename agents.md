# Project Overview

직장인이 업무 중 3~10분 정도 가볍게 놀 수 있는
로그인 없는 시간대 기반 웹 서비스.

핵심 컨셉:
- 설치 없음
- 로그인 없음
- 익명 사용자
- 시간대별 채팅방
- 짧은 미니게임
- 잠깐 즐기고 나가는 서비스
- 과거 메시지는 보존하지 않음

# Goal

이 프로젝트의 1차 목적은 Java/Spring/PostgreSQL 기반의
실제 웹 서비스를 처음부터 설계하고 운영하는 것.

특히 다음을 직접 경험하는 것이 목표:
- ERD 설계
- Spring MVC
- JPA / JDBC
- PostgreSQL
- transaction
- validation
- anonymous session
- real-time communication
- moderation
- Docker
- AWS deployment
- monitoring / logging

MSA 학습은 이 프로젝트의 목적이 아님.

# Tech Stack

Backend
- Java 21
- Spring Boot 4
- Spring MVC
- Spring Data JPA
- 필요시 JdbcClient

Database
- PostgreSQL

Frontend
- React 또는 Next.js
- 프론트엔드 구현은 AI 활용 비중 높여도 됨

Infra
- Docker
- AWS 우선 고려
- HTTPS

Not planned initially
- Kafka
- RabbitMQ
- Kubernetes
- Vector DB
- RAG
- MSA

Redis는 실제 필요성이 생겼을 때만 도입.

# Core Domain

## Room
시간대별로 반복해서 열리는 채팅방.

예:
- 점심방
- 간식방
- 퇴근방

방이 닫히면 해당 방의 메시지는 삭제.

## AnonymousUser
로그인은 없지만 동일 브라우저를 구분하기 위한 익명 사용자.

닉네임:
- 사용자가 입력 가능
- 없으면 서버에서 임의 생성

상태:
- ACTIVE
- BANNED

## Message
특정 Room에서 AnonymousUser가 작성한 메시지.

정책:
- 최대 140자
- 수정 불가
- 삭제 불가
- 방이 닫히면 삭제

## Report
특정 메시지에 대한 신고.

정책:
- 동일 사용자가 동일 메시지를 중복 신고할 수 없음
- 신고가 누적되면 작성자에게 경고
- 일정 횟수 이상이면 차단

## Game
사이트에서 제공하는 미니게임 정보.

게임 내부 상태는 브라우저에서 관리.

## GamePlay
사용자의 게임 플레이 결과.

플레이 1회당 1 row.
주요 용도:
- 오늘 최고점
- 전체 랭킹
- 플레이 횟수
- 인기 게임 순위

# Current ERD

- Room 1:N Message
- AnonymousUser 1:N Message
- Message 1:N Report
- AnonymousUser 1:N Report
- Game 1:N GamePlay
- AnonymousUser 1:N GamePlay

# Design Principles

1. 기능을 넣기 위해 기술을 억지로 사용하지 않는다.
2. Redis 등은 실제 문제가 생겼을 때 도입한다.
3. 서비스 정책을 먼저 정의하고 DB를 설계한다.
4. DB가 저장해야 할 사실과 Spring이 판단해야 할 비즈니스 로직을 구분한다.
5. 프론트 validation만 믿지 않고 서버에서도 검증한다.
6. 로그인 없는 서비스이므로 진입 장벽을 최소화한다.
7. 실제 사용자 운영을 목표로 한다.

# AI Collaboration

AI는 코드를 대신 작성하는 도구이기도 하지만,
프로젝트 학습을 위해 다음 원칙을 우선한다.

- 설계 결정을 바로 대신하지 말 것
- 가능한 경우 질문과 힌트를 먼저 제시
- 내가 작성한 코드를 우선 리뷰
- 문제가 있으면 이유를 설명
- Spring/JPA/DB 내부 동작을 설명
- 구현보다 이해를 우선
