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

## 채팅 임시 보관·신고 API 완료 범위

- 일반 Message 테이블 없이 Java 메시지 객체와 단일 서버 메모리에 최대 15분 임시 보관. 방 종료 시점이 먼저면 즉시 만료.
- HTTP 메시지 작성·신고 API, 쿠키 작성자 검증, 운영 중인 방·차단·내용 검증 구현.
- V9 Report 테이블에 서버 원본 증거만 복사하며 같은 메시지 중복 신고를 DB UNIQUE로 차단.
- 신고 증거는 접수 후 30일을 보관 기한으로 기록하고 정리 작업으로 삭제.
- 코드 검토 순서와 제한은 `docs/reviews/chat-report.md` 참고. 구현은 사용자가 요청해 AI가 작성했으며 사용자 검토가 다음 순서다.

## 다음에 할 일 — 구현 이해와 실시간 전달

1. 학습 복습부터 시작한다. 사용자가 ReportRepository.deleteExpired 또는 ReportCleanup.removeExpired 중 작은 메서드 하나를 설명 없이 먼저 다시 작성하고, AI는 힌트와 리뷰만 제공한다. 사용자는 Report를 직접 작성하지 못한 점이 아쉽다고 했으므로 곧바로 다음 기능 전체 구현으로 넘어가지 않는다.
2. ChatService.create의 DB 커밋 전 Map 공개 문제를 검토한다. 현재 종료 콜백은 실패 후 제거만 담당하며 DB와 메모리 사이의 원자성을 보장하지 않는다. 해결 방향을 설명하고 사용자와 결정한다.
3. WebSocket 연결·방 참여·실시간 전달·Origin 검증 정책을 정한다. 필요하면 `/grill-with-docs → /to-spec → /to-tickets → /implement`를 적용하되 트래커 설정과 승인 지점을 먼저 확인한다.
4. HTTP 구현에서 검증된 Service를 실시간 전송 경로에서도 재사용한다.
5. 신고 누적 경고/자동 차단 기준은 아직 결정되지 않았으므로 별도 기능으로 설계한다.

## 유지 중인 원칙

- Claude와는 페어 코딩 스타일 유지: 코드를 대신 작성하지 않고 힌트/리뷰 중심 (`CLAUDE.md`, `AGENTS.md` 참고)
- SSR은 이 프로젝트에서 다루지 않음. Spring=API, Next.js=SSR/CSR 담당. Thymeleaf/JSP 실험은 별도 프로젝트(Java_101)에서 진행
