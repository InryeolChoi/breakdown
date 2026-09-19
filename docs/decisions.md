# Project Decisions

## Product
- 직장인이 3~10분 잠깐 놀다 가는 익명 웹 서비스
- 로그인 없음
- 모바일 앱보다 웹 우선
- 과거 채팅 기록은 보존하지 않음
- 방이 닫히면 메시지 즉시 삭제
- 메시지 최대 140자
- 수정/삭제 불가
- 닉네임은 사용자가 정하거나 자동 생성

## Anonymous User
- 개인정보 식별이 목적이 아님
- 동일 브라우저를 구분할 최소 식별자 필요
- ACTIVE / BANNED 상태 관리
- 신고 3회 누적 시 차단 정책 고려

## Chat
- Room은 반복적으로 열림
- 점심/간식/저녁 방을 별도 테이블로 만들지 않고 Room row로 관리
- open_at / close_at은 DB에 저장
- 방이 열려 있는지 판단하는 로직은 Spring에서 처리
- 자정을 넘는 운영 시간을 허용한다. 예: 23:00~02:00
- open_at은 포함하고 close_at은 제외한다. 예: 12:00~13:00 방은 12:00부터 열리고 13:00부터 닫힌다.
- open_at과 close_at이 같은 방은 허용하지 않는다. 24시간 방은 제공하지 않는다.
- 초기 Room은 점심메뉴방(10:00~11:30), 저녁메뉴방(16:00~17:30), 야근메뉴방(21:00~다음 날 02:00) 세 개로 시작한다.

## Report
- 신고 대상은 Message
- 신고자는 AnonymousUser
- 같은 사용자가 같은 메시지를 중복 신고할 수 없음
- warning_count는 별도 저장하지 않고 Report 기준 계산

## Game
- 게임 내부 상태는 브라우저에서 처리
- 새로고침 시 초기화
- 서버는 Game 목록과 GamePlay 결과만 저장
- 플레이 1회 = GamePlay 1 row
- 랭킹/최고점/인기 게임 계산 가능

## Architecture
- Java 21
- Spring Boot 4
- PostgreSQL
- Docker
- Redis는 필요할 때만
- Kafka / RabbitMQ / K8s / MSA는 이번 프로젝트에서 제외
- AI 기능은 부가 기능으로만 고려
