# Next

작업을 이어받을 때(Claude든 Codex든) 이 파일부터 확인한다.
자세한 논의/이유는 `docs/devlog/<날짜>.md`를 참고한다.

## 지금까지

- Room → RoomRepository → RoomService → RoomController 흐름 완성 (2026-09-18)
  - `Room.isOpen(LocalTime now)`: 자정을 넘는 운영 시간(예: 18:00~02:00) 처리 포함
  - `RoomService.findOpenRooms(now)`: 스트림 필터링, 생성자 주입
  - `GET /rooms`: `RoomController`에서 열려있는 방 목록을 `RoomResponse` DTO로 반환
  - 자세한 Q&A: `docs/devlog/260918.md`
- Room API 응답과 운영 시간 정책 정리 (2026-09-19)
  - `RoomResponse`: Entity를 API에 직접 노출하지 않기 위한 응답 DTO
  - 운영 시간은 `[open_at, close_at)`: 시작 시각 포함, 종료 시각 제외
  - 시작·종료 시각이 같은 24시간 방은 제공하지 않음
  - `V2__add_room_operating_time_constraint.sql`: `CHECK (open_at <> close_at)` 제약 추가
  - 자세한 Q&A: `docs/devlog/260919.md`
- Room의 평일·주말 운영 정책 적용 (2026-09-20)
  - `weekday_open`, `weekend_open`: Room이 평일·주말에 열리는지 저장
  - V4가 기존 초기 Room 세 건을 평일 전용으로 설정
  - `Room.isOpen(LocalDateTime now)`: 자정 통과 시 전날 운영 여부까지 판단
  - 자세한 Q&A: `docs/devlog/260920.md`
- Room 운영 규칙 단위 테스트 작성 (2026-09-21)
  - 일반 구간의 시작 포함·종료 제외를 검증
  - 자정 통과 방의 금요일 시작, 토요일 새벽 연장, 토요일 밤 차단을 검증
  - 자세한 Q&A: `docs/devlog/260921.md`

## AnonymousUser 완료 범위

- 생성·재방문 API, 선택 입력 닉네임 검증과 한국어 오류 응답 구현
- 쿠키 원문 토큰은 브라우저에, SHA-256 해시는 DB에 저장
- 마지막 방문 후 7일 비활성 만료, 유효한 재방문 시 방문 시각과 쿠키 만료 갱신
- V8의 banned_until과 7일 임시 차단 구현; 초기화 API는 차단 사용자 정보를 반환
- 전체 테스트 24개 통과 (2026-10-05)
- 운영 전 토큰 절대 만료/교체 및 프록시 뒤 HTTPS 쿠키 설정 검토는 남음

## 다음에 할 일 — Message

1. 사용자가 ERD Message의 컬럼 타입·NOT NULL·identity/FK를 구체화하고 `V9__create_message.sql`을 작성한다. 현재 Message는 ERD 초안에만 있고 실제 테이블은 없다.
2. Message Entity/Repository를 만든 뒤, HTTP 메시지 작성·조회 API로 DB 저장과 도메인 규칙을 먼저 확인한다.
3. 작성자는 요청 본문의 user id가 아니라 서버가 쿠키 토큰을 검증해 찾는다. 닫힌 방, 차단된 사용자, 잘못된 내용의 메시지 작성을 거절한다.
4. 방이 닫힐 때 메시지를 삭제하는 정책과 재개방 시 과거 내용이 보이지 않는 동작을 구현·검증한다.
5. 그 뒤 WebSocket으로 실시간 전달을 추가한다. 작성 검증·저장 로직은 Service에서 재사용한다.

## 유지 중인 원칙

- Claude와는 페어 코딩 스타일 유지: 코드를 대신 작성하지 않고 힌트/리뷰 중심 (`CLAUDE.md`, `AGENTS.md` 참고)
- SSR은 이 프로젝트에서 다루지 않음. Spring=API, Next.js=SSR/CSR 담당. Thymeleaf/JSP 실험은 별도 프로젝트(Java_101)에서 진행
