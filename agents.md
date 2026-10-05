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
- 일반 메시지는 DB에 임시 보관하고 신고 증거는 별도 기한으로 보관

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

방이 닫히면 메시지의 신고 가능 기한이 종료된다. 원본 삭제 기한은 운영 구간 종료 10분 후다.

## AnonymousUser
로그인은 없지만 동일 브라우저를 구분하기 위한 익명 사용자.

닉네임:
- 사용자가 입력 가능
- 없으면 서버에서 임의 생성

상태:
- ACTIVE
- BANNED

## Message
특정 Room에서 AnonymousUser가 작성한 메시지. Message 테이블에 임시 보관하는 설계로 전환한다.

정책:
- 최대 140자
- 수정 불가
- 삭제 불가
- 신고 가능 기한과 원본 삭제 기한은 구분
- 구체 정책은 `docs/decisions.md`를 따른다.

## Report
사용자에 대한 신고. 서버 원본 메시지를 증거로 복사해 저장한다.

정책:
- 동일 사용자가 동일 메시지를 중복 신고할 수 없음
- 신고 증거 보관 기간 등 상세 정책은 `docs/decisions.md`를 따른다.
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

- Room 1:N Report
- AnonymousUser 1:N Report (신고자/신고 대상 각각)
- Room 1:N Message
- AnonymousUser 1:N Message
- Report는 원본 UUID와 증거를 복사하며 Message FK를 두지 않음
- Game 1:N GamePlay
- AnonymousUser 1:N GamePlay

# Design Principles

Message DB 저장은 합의한 목표 설계이며 현재 실행 코드는 아직 Map 방식이다. 구현 진행 상태는 `docs/devlog/NEXT.md`를 확인한다.

1. 기능을 넣기 위해 기술을 억지로 사용하지 않는다.
2. Redis 등은 실제 문제가 생겼을 때 도입한다.
3. 서비스 정책을 먼저 정의하고 DB를 설계한다.
4. DB가 저장해야 할 사실과 Spring이 판단해야 할 비즈니스 로직을 구분한다.
5. 프론트 validation만 믿지 않고 서버에서도 검증한다.
6. 로그인 없는 서비스이므로 진입 장벽을 최소화한다.
7. 실제 사용자 운영을 목표로 한다.

# AI Collaboration

## Session Workflow and Devlog

사용자가 "다시 시작해 보자", "계속 진행", 또는 그와 같은 작업 재개 의사를 표현하면, 모든 AI는 구현에 앞서 [`.agents/skills/breakground-session-start/SKILL.md`](.agents/skills/breakground-session-start/SKILL.md)를 읽고 따른다.

이 규칙은 개발 환경 시작, 당일 devlog 생성·이어서 쓰기, 학습 질문과 답변의 자동 기록에 적용한다.

사용자가 "오늘은 여기까지", "마무리하자", 또는 그와 같은 작업 종료 의사를 표현하면, 모든 AI는 [`.agents/skills/breakground-session-end/SKILL.md`](.agents/skills/breakground-session-end/SKILL.md)를 읽고 따른다. 이 규칙은 당일 devlog 마무리, `make fclean`, 변경 사항 커밋·GitHub 푸시에 적용한다.

AI는 코드를 대신 작성하는 도구이기도 하지만,
프로젝트 학습을 위해 다음 원칙을 우선한다.

학습 목표는 사용자가 구현 결과를 이해하고 검증하며,
작은 수정과 오류 추적을 직접 할 수 있게 되는 것이다.

- 설계 결정을 바로 대신하지 말 것
- 가능한 경우 질문과 힌트를 먼저 제시
- 내가 작성한 코드를 우선 리뷰
- 문제가 있으면 이유를 설명
- Spring/JPA/DB 내부 동작을 설명
- 구현보다 이해를 우선

## Task Scope, Implementation, and Verification

- 작업은 작은 기능 하나로 제한하고, 정상·실패·경계 상황을 먼저 정리한다. 새 정책이나 기술이 필요하면 작업 범위에 미치는 영향을 먼저 설명한다.
- 반복 코드, 표준 라이브러리 사용, 테스트와 환경 작업은 AI가 구현할 수 있다. 핵심 정책과 비즈니스 분기는 사용자의 판단과 작성 기회를 우선하며, 사용자가 구현을 맡기면 요청한 범위를 완성하고 핵심 흐름을 설명한다.
- 사용자가 막히면 현재 오류 하나를 해결할 힌트나 작은 예제를 먼저 제공한다. 설명은 입력·반환 타입, 제어 흐름, 상태 변경과 HTTP·DB에 미치는 영향을 연결한다.
- 중요한 동작은 적절한 테스트나 실제 실행 결과로 검증한다. 실행한 검증과 결과, 확인하지 못한 부분을 명시한다.
- 학습용 기능을 완료한 뒤 핵심 질문이나 작은 변경 과제 하나로 이해를 확인한다.
