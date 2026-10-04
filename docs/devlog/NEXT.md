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

## 다음에 할 일

- AnonymousUser Entity, Repository, 생성 Service, JSON 요청·응답 DTO, Controller까지 구현했고 PostgreSQL 저장과 MVC 요청 검증 테스트가 통과
- 사용자 정의 검증 오류 응답을 ControllerAdvice에서 구현 중이며, Postman에서 31자 입력 시 한국어 오류 메시지가 오는지 다시 확인
- `AnonymousUserResponse`가 브라우저에 필요한 값만 반환하는지 검토
- 같은 브라우저 재방문 식별을 구현: 무작위 토큰 cookie, DB에는 해시 저장, 마지막 방문 후 7일 비활성 만료
- 구현 전에 7일 비활성 만료 외에 토큰의 절대 만료 또는 교체가 필요한지 결정하고, 쓰기 API에서 클라이언트의 사용자 id를 신뢰하지 않는 규칙을 확정
- ERD에 브라우저 식별 hash를 반영한 뒤 Flyway V7을 작성하고 Entity/Repository/Service/Controller에 연결

## 유지 중인 원칙

- Claude와는 페어 코딩 스타일 유지: 코드를 대신 작성하지 않고 힌트/리뷰 중심 (`CLAUDE.md`, `AGENTS.md` 참고)
- SSR은 이 프로젝트에서 다루지 않음. Spring=API, Next.js=SSR/CSR 담당. Thymeleaf/JSP 실험은 별도 프로젝트(Java_101)에서 진행
