# 양방향 채팅 연결 설계 지도

Label: wayfinder:map

## Destination

사용자가 ChatWebSocketHandler의 핵심 흐름을 작성하고 AI가 핸드셰이크·연결 지원 코드·테스트를 맡아, 같은 방의 두 연결 사이에서 DB 커밋 이후 메시지를 전달하는 구현 계약을 정한다.

## Notes

- wayfinder, grilling, domain-modeling을 적용한다. 로컬 Markdown 트래커를 사용한다.
- 정책의 source of truth는 docs/decisions.md다. 결정 티켓은 논의 근거를 보관하고 지도는 인덱스 역할을 한다.
- 사용자 작성 기회를 유지한다. Handler의 수신·서비스 호출·방송 흐름을 AI가 먼저 완성하지 않는다.
- 기존 ChatService의 검증·DB 저장과 Registry를 재사용한다. Redis·STOMP 등 새 기술은 범위에 넣지 않는다.
- 계획 지도이며 Java 구현은 별도 단계다.

## Decisions so far

- [WebSocket 접속·메시지·종료 계약](issues/01-connection-contract.md): HTTP 익명 준비·본문 JSON·종료 안내·입력 오류 처리·쿠키 갱신과 사용자/AI 분담을 확인했다.

## Not yet specified

핵심 계약 확정 후 사용자가 작성할 Handler의 정확한 지원 인터페이스와 실제 네트워크 검증 순서를 정리한다.

## Out of scope

- 프론트 전체, AWS 배포, 게임 및 관리자 자동 차단.
- 과거 대화 조회 및 새로운 메시지 저장 구조.
