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

**최신 검증 (2026-10-11):** 사용자가 Registry에 `synchronized (s)`를 적용했고 동시 방송 회귀 테스트가 통과했다. 사용자 위임으로 단일 서브에이전트가 읽기 전용 리뷰·테스트를 담당하고 AI가 Handler의 오류 처리를 수정했다. 잘못된 JSON·공백·141자 입력은 보낸 연결만 오류 JSON을 받은 뒤 같은 연결에서 재입력할 수 있다. 접속 후 사용자 만료·차단·방 종료는 오류 JSON 후 종료 코드 1008로 닫는다. 실제 네트워크 테스트 6개는 각각 실패 → 구현 → 통과를 확인했다. `./gradlew test --rerun-tasks` 전체 88개 통과, 실패·오류·건너뛰기 0이며 최종 리뷰의 actionable 지적은 없다.

**최신 검증 — 운영 구간 종료 메서드:** 사용자가 핸드셰이크에 closesAt을 추가하고 Registry의 순회·종료 조건·안내·등록 해제·close를 작성했다. 초기 IOException 미처리와 안내 실패 시 정리 누락을 리뷰로 발견한 뒤 사용자가 세션 잠금 안의 try/finally와 바깥 연결별 catch를 작성했다. 사용자 요청으로 AI가 catch에 roomId/sessionId/예외를 남기는 SLF4J WARN 로그만 추가했다. 같은 서브에이전트의 최종 리뷰는 지적 없음이며 Registry 테스트 6개·`./gradlew test --rerun-tasks` 전체 89개가 통과했다. 실패·오류·건너뛰기 0이다. 첫 정상 종료 테스트는 red → 사용자 구현 → green을 확인했다. 자동 종료 스케줄링은 아직 미구현이다.

**바로 다음 작업:** Registry.closeExpiredSessions를 호출하는 1초 종료 작업을 연결한다. 주입 Clock으로 현재 시각을 얻고 기존 Codec의 방 종료 오류 JSON을 notice로 만든다. 종료 전 유지·안내 실패 후 다른 연결 처리·종료 후 목록 제거를 공개 Registry 경계에서 보강하고, 메시지를 보내지 않는 실제 연결이 Clock 변경 후 자동 종료되는지 검증한다. 방송 직전에도 세션 잠금 안에서 입장 구간의 종료 시각을 검사해 늦게 커밋된 메시지의 전송을 막아야 한다. 느린 수신자의 전송 시간·버퍼 한도는 별도 후속 설계다. 기존 HTTP 작성 API 유지 여부와 프론트 연결은 계속 미정이다.

wayfinder의 [방 운영 종료 감지와 연결 종료 시점](../../.scratch/websocket-chat/issues/02-room-closing-contract.md)은 사용자 답변으로 해결했다. 1초 검사와 방 종료 우선으로 진행한다. 핸드셰이크의 closesAt 준비와 Registry 종료 메서드 첫 정상 경계 검증을 완료했다. 스케줄링과 방송 직전 종료 검사, 실제 유휴 연결 테스트는 아직 없다. 이 세 부분이 연결되기 전에는 WebSocket 운영 종료 기능 전체 완료라고 하지 않는다.

사용자가 작성한 Handler 성공 흐름에 AI가 ChatHandshakeInterceptor·공유 Bean·`/ws/rooms/{roomId}` 등록을 연결했다. 연결 속성 roomId/rawToken은 서버가 준비하고 원문 토큰은 요청 JSON에 추가하지 않는다. 핸드셰이크 8개, 실제 같은 방 방송·두 방 분리·재접속 3개, 입력 오류·종료 오류 6개, Registry 동시 방송 회귀 테스트를 유지한다. 기존 `/ws/echo`는 실제 채팅 Handler와 구분된다.

1. 사용자는 Report를 재작성하는 대신 분석했고, DB·Map 경계 검토 후 Message를 DB 임시 저장으로 전환하기로 합의했다. 질문·답변은 `261005.md`, 검토 이력은 `docs/design/message-db-proposal.md`에 있다.
2. 사용자가 V10을 작성·수정한 뒤 최종 정리와 다음 검증을 AI에게 요청했다. `V10__create_message.sql`을 Flyway로 로컬 DB에 적용하고 실제 구조를 확인했다(2026-10-06). 기존 테스트 39개 통과. 앞으로도 학습용 새 파일 준비는 AI가 맡고 핵심 내용은 사용자 작성 기회를 우선한다. 적용된 V9/V10은 수정하지 않는다.
3. 사용자 위임에 따라 Entity/Repository, Service DB 저장/신고 조회, MessageCleanup까지 전환했다. Map/상한/실패 제거 콜백을 삭제했다. 읽는 순서와 발견한 경계 문제는 `docs/reviews/chat-report.md`에 있다.
4. 합의 정책: 신고 가능 기한=min(작성+15분, 해당 운영 구간 종료), 원본 삭제 기한=운영 구간 종료+10분, 삭제 작업=1분 간격. 재시작 후에도 원래 기한까지 신고 가능. Report에 Message FK 없음. 전역 10,000개 상한 제거. 원본 확인 시 유효하면 만료 후 커밋도 접수.
5. 실제 HTTP 채팅/신고 API는 DB를 사용한다. 전체 48개 테스트 통과. 커밋/롤백·다른 트랜잭션의 커밋 전 미조회·만료 후 신고 커밋·자정 통과·마이크로초 정밀도·원본 삭제 후 증거 유지 검증 완료.
6. WebSocket 스타터 의존성을 사용자가 추가하고 학습용 Echo 설정·핸들러·실제 연결 테스트를 작성했다(2026-10-09~10). onError 전달과 불필요한 빌더 호출 수정 후 Echo 테스트 1개를 재실행해 통과했다(2026-10-10). 실제 채팅은 양방향 WebSocket으로 진행하기로 사용자가 결정했다. ChatService를 재사용하며, 연결·메시지·방별 전달의 핵심 흐름은 사용자가 작성하고 AI는 반복 작업과 검증을 지원하는 분담안을 제안했다. RoomSessionRegistry는 unregister의 null 처리와 broadcast의 연결별 IOException 처리·실패 연결 제거까지 사용자가 작성했다. code-review 두 축 검토에서 Standards 0건, Spec 0건이며, 합의한 공개 register/broadcast 경계에 AI가 첫 정상 테스트를 추가해 통과했다(2026-10-10). 실제 Registry와 모의 WebSocketSession을 사용해 같은 방의 A/B에 본문 전달을 확인한다. 다음은 제거·다른 방 미수신·전송 실패 등에서 한 행동씩 추가 검증하는 것이다. 실제 WS 접속을 검증하는 테스트와 다르며 첫 Registry 테스트가 실패했던 red 단계는 확인하지 않았다. 구체 분담과 메시지 성공/오류 형식, 쿠키 갱신, 방 종료 시 연결 처리 정책은 후속 확정이 필요하다. Registry는 아직 실제 채팅 Handler에 연결하지 않았으며 세션별 동시 전송 보호도 후속 작업이다. 실시간 전달은 커밋 이후 수행한다.
7. 관리자 신고 검토/자동 차단, 전송 제한, AWS 백업 보존과 디스크 모니터링은 별도 후속 기능/운영 설계로 남긴다.

## 유지 중인 원칙

- 2026-10-11 WebSocket 계약: 접속 전 HTTP로 익명 쿠키 준비·갱신, 본문만 담은 JSON 전송, 방 종료 안내 후 연결 종료. 입력 오류는 보낸 연결만 안내 후 유지하고 사용자 만료·차단·방 종료는 종료한다. source of truth는 decisions.md이며 논의 지도는 .scratch/websocket-chat/map.md다. Codec 단독 테스트 17개와 실제 접속·메시지 왕복을 유지한다. 메시지 작성 시 오류·종료 정책은 구현했고, 유휴 연결의 운영 종료 처리는 남아 있다.

- 사용자가 ChatWebSocketHandler의 연결·종료 콜백과 메시지 처리 성공 흐름을 작성했다(2026-10-11). 연결·종료 콜백은 같은 Registry를 사용한다. AI가 핸드셰이크에서 방 ID·익명 쿠키를 검증하고 연결 속성에 roomId/rawToken을 넣는 준비, 공유 Registry/Handler Bean과 채팅 URL 등록을 완성했다. 사용자가 방송의 동시 전송 보호를 적용했고 AI가 같은 잠금으로 오류 응답·종료 처리까지 연결했다. 유휴 연결의 운영 종료는 남아 있다.

- 네 번째 전송 실패 테스트의 isOpen 설정·verify 완성·두 본문 assertion을 사용자가 수정했고 Registry 테스트 4개가 통과했다(2026-10-11). 재검토는 Standards 선택적 이름 개선 1건·Spec 0건이다. alice의 전송 실패 후 정상 bob 수신과 다음 방송의 alice 제외를 검증했다. 남은 닫힌 연결 처리 검증과 실제 채팅 Handler 연결이 후속 범위다. 실제 네트워크·세션별 동시 전송 보호 검증은 아직 아니다.

- Registry의 제거 테스트를 사용자가 작성한 뒤, 리뷰에서 빠져 있던 bob 수신 본문 검증을 사용자 요청으로 AI가 추가했다(2026-10-10). 다른 방 미수신 테스트도 사용자가 완성했고 Registry 테스트 3개가 실제 실행에서 통과했다(2026-10-11). 같은 방 전달, 제거한 alice 미전송과 남은 bob 수신, 다른 방 미수신을 확인한다. 다음은 IOException 전송 실패 연결을 제거하면서 정상 연결에 전달을 계속하고 이후 방송에서도 실패 연결을 제외하는 동작 검증이다. 변수 A를 registry로 바꾸는 것은 선택적 이름 개선이다. 실제 소켓·실패 경로 검증은 아직 남아 있다. 2026-10-11 새벽 make fclean 후 오후 make로 개발 환경을 재시작했고 기록은 261011.md에 있다.

- Claude와는 페어 코딩 스타일 유지: 코드를 대신 작성하지 않고 힌트/리뷰 중심 (`CLAUDE.md`, `AGENTS.md` 참고)
- SSR은 이 프로젝트에서 다루지 않음. Spring=API, Next.js=SSR/CSR 담당. Thymeleaf/JSP 실험은 별도 프로젝트(Java_101)에서 진행
