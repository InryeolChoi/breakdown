# 양방향 채팅 연결 설계 지도

Label: wayfinder:map

## Destination

같은 방의 두 연결 사이에서 DB 커밋 이후 메시지를 전달하고, 입장한 운영 구간이 끝나면 메시지를 보내지 않는 연결도 안내 후 닫는 계약을 정한다. Handler 성공 흐름과 메시지 작성 시 오류 처리는 구현됐으며 다음으로 운영 종료 감지 경계를 결정한다.

## Notes

- wayfinder, grilling, domain-modeling을 적용한다. 로컬 Markdown 트래커를 사용한다.
- 정책의 source of truth는 docs/decisions.md다. 결정 티켓은 논의 근거를 보관하고 지도는 인덱스 역할을 한다.
- 사용자 작성 기회를 유지한다. Handler의 수신·서비스 호출·방송 흐름을 AI가 먼저 완성하지 않는다.
- 기존 ChatService의 검증·DB 저장과 Registry를 재사용한다. Redis·STOMP 등 새 기술은 범위에 넣지 않는다.
- 계획 지도이며 Java 구현은 별도 단계다.

## Decisions so far

- [WebSocket 접속·메시지·종료 계약](issues/01-connection-contract.md): HTTP 익명 준비·본문 JSON·종료 안내·입력 오류 처리·쿠키 갱신과 사용자/AI 분담을 확인했다.
- [방 운영 종료 감지와 연결 종료 시점](issues/02-room-closing-contract.md): 1초 검사와 방 종료 우선으로 결정했고 입장한 운영 구간의 종료 시각을 기준으로 구현한다.
- [WebSocket 오류 안내 생성 경계](issues/03-error-notice-boundary.md): 기존 JSON을 유지하고 안내 데이터 생성과 직렬화를 분리하는 작은 수정을 AI에게 위임했다.

## Not yet specified

현재 목표의 미확정 정책은 없다. 확정한 종료 계약과 오류 안내 생성 경계에 따라 구현·검증을 작은 단위로 진행한다. Java 구현은 별도 단계다.

## Out of scope

- 프론트 전체, AWS 배포, 게임 및 관리자 자동 차단.
- 과거 대화 조회 및 새로운 메시지 저장 구조.
- 이번 방 종료 작업에서 사용자 만료·차단을 별도로 주기 검사하는 기능과 느린 수신자의 시간·버퍼 제한.
