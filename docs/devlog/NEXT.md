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

- Message 테이블에 DB 임시 보관으로 전환 완료(2026-10-06). 신고 가능 기한과 실제 삭제 기한을 분리했다.
- HTTP 메시지 작성·신고 API, 쿠키 작성자 검증, 운영 중인 방·차단·내용 검증 구현.
- V9 Report 테이블에 서버 원본 증거만 복사하며 같은 메시지 중복 신고를 DB UNIQUE로 차단.
- 신고 증거는 접수 후 30일을 보관 기한으로 기록하고 정리 작업으로 삭제.
- 코드 검토 순서와 제한은 `docs/reviews/chat-report.md` 참고. 구현은 사용자가 요청해 AI가 작성했으며 사용자 검토가 다음 순서다.

## 다음에 할 일 — 구현 이해와 실시간 전달

1. 사용자는 Report를 재작성하는 대신 분석했고, DB·Map 경계 검토 후 Message를 DB 임시 저장으로 전환하기로 합의했다. 질문·답변은 `261005.md`, 검토 이력은 `docs/design/message-db-proposal.md`에 있다.
2. 사용자가 V10을 작성·수정한 뒤 최종 정리와 다음 검증을 AI에게 요청했다. `V10__create_message.sql`을 Flyway로 로컬 DB에 적용하고 실제 구조를 확인했다(2026-10-06). 기존 테스트 39개 통과. 앞으로도 학습용 새 파일 준비는 AI가 맡고 핵심 내용은 사용자 작성 기회를 우선한다. 적용된 V9/V10은 수정하지 않는다.
3. 사용자 위임에 따라 Entity/Repository, Service DB 저장/신고 조회, MessageCleanup까지 전환했다. Map/상한/실패 제거 콜백을 삭제했다. 읽는 순서와 발견한 경계 문제는 `docs/reviews/chat-report.md`에 있다.
4. 합의 정책: 신고 가능 기한=min(작성+15분, 해당 운영 구간 종료), 원본 삭제 기한=운영 구간 종료+10분, 삭제 작업=1분 간격. 재시작 후에도 원래 기한까지 신고 가능. Report에 Message FK 없음. 전역 10,000개 상한 제거. 원본 확인 시 유효하면 만료 후 커밋도 접수.
5. 실제 HTTP 채팅/신고 API는 DB를 사용한다. 전체 48개 테스트 통과. 커밋/롤백·다른 트랜잭션의 커밋 전 미조회·만료 후 신고 커밋·자정 통과·마이크로초 정밀도·원본 삭제 후 증거 유지 검증 완료.
6. WebSocket 스타터 의존성을 사용자가 추가하고 학습용 Echo 설정·핸들러·실제 연결 테스트를 작성했다(2026-10-09~10). onError 전달과 불필요한 빌더 호출 수정 후 Echo 테스트 1개를 재실행해 통과했다(2026-10-10). 실제 채팅은 양방향 WebSocket으로 진행하기로 사용자가 결정했다. ChatService를 재사용하며, 연결·메시지·방별 전달의 핵심 흐름은 사용자가 작성하고 AI는 반복 작업과 검증을 지원하는 분담안을 제안했다. RoomSessionRegistry는 unregister의 null 처리와 broadcast의 연결별 IOException 처리·실패 연결 제거까지 사용자가 작성했다. code-review 두 축 검토에서 Standards 0건, Spec 0건이며, 합의한 공개 register/broadcast 경계에 AI가 첫 정상 테스트를 추가해 통과했다(2026-10-10). 실제 Registry와 모의 WebSocketSession을 사용해 같은 방의 A/B에 본문 전달을 확인한다. 다음은 제거·다른 방 미수신·전송 실패 등에서 한 행동씩 추가 검증하는 것이다. 실제 WS 접속을 검증하는 테스트와 다르며 첫 Registry 테스트가 실패했던 red 단계는 확인하지 않았다. 구체 분담과 메시지 성공/오류 형식, 쿠키 갱신, 방 종료 시 연결 처리 정책은 후속 확정이 필요하다. Registry는 아직 실제 채팅 Handler에 연결하지 않았으며 세션별 동시 전송 보호도 후속 작업이다. 실시간 전달은 커밋 이후 수행한다.
7. 관리자 신고 검토/자동 차단, 전송 제한, AWS 백업 보존과 디스크 모니터링은 별도 후속 기능/운영 설계로 남긴다.

## 유지 중인 원칙

- Registry의 두 번째 제거 테스트를 사용자가 작성한 뒤, 리뷰에서 빠져 있던 bob 수신 본문 검증을 사용자 요청으로 AI가 추가했다(2026-10-10). Registry 테스트 2개를 실제 실행해 통과했다. alice 미전송과 bob의 1회 전송·본문 일치를 함께 검증한다. 다음은 다른 방 미수신 테스트다: alice는 방 1, bob은 방 2에 등록하고 방 1 방송 시 alice만 본문을 받는지 확인한다. 변수 A를 registry로 바꾸는 것은 선택적 이름 개선이다. 실제 소켓·실패 경로 검증은 아직 남아 있다. 2026-10-11 새벽 종료 시 make fclean이 성공했고 마무리 기록은 261011.md에 있다.

- Claude와는 페어 코딩 스타일 유지: 코드를 대신 작성하지 않고 힌트/리뷰 중심 (`CLAUDE.md`, `AGENTS.md` 참고)
- SSR은 이 프로젝트에서 다루지 않음. Spring=API, Next.js=SSR/CSR 담당. Thymeleaf/JSP 실험은 별도 프로젝트(Java_101)에서 진행
