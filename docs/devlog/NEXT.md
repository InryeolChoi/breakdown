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

## 다음에 할 일

- Docker를 켠 상태에서 앱 또는 `./gradlew test`를 실행해 Flyway V2가 실제 DB에 적용되는지 확인
- 초기 Room 데이터와 운영 시간을 정한 뒤, V3 migration으로 삽입
- 초기 데이터가 준비되면 `GET /rooms`의 실제 응답과 시간대별 열림 여부를 확인
- 그 뒤 AnonymousUser, Message 쪽으로 확장 검토

## 유지 중인 원칙

- Claude와는 페어 코딩 스타일 유지: 코드를 대신 작성하지 않고 힌트/리뷰 중심 (`CLAUDE.md`, `AGENTS.md` 참고)
- SSR은 이 프로젝트에서 다루지 않음. Spring=API, Next.js=SSR/CSR 담당. Thymeleaf/JSP 실험은 별도 프로젝트(Java_101)에서 진행
