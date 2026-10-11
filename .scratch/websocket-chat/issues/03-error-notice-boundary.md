# WebSocket 오류 안내 생성 경계

Type: grilling
Label: wayfinder:grilling
Status: resolved
Assignee: Codex
Parent: [양방향 채팅 연결 설계 지도](../map.md)
Blocked by: [방 운영 종료 감지와 연결 종료 시점](02-room-closing-contract.md) (resolved)

## Question

정기적인 방 종료 안내를 만들기 위해 ResponseStatusException을 생성하는 현재 구조를 언제, 어느 범위까지 정리할까?

## Comments

2026-10-11: 사용자가 오류 안내를 예외 객체 없이 생성하는 개선을 이번 작업에 포함할지 물었다. Codex가 담당하며 현재는 구현하지 않는다.

현재 사실:

- Codec.encodeError는 ResponseStatusException을 받고 ERROR/status/message JSON을 반환한다.
- Handler는 Service/Codec에서 발생한 실제 예외를 잡아 전달한다.
- RoomConnectionCloser는 예외를 throw하지 않으며 안내를 생성하기 위해 예외 객체를 만든다.
- 최신 전체 테스트는 89개 중 1개 실패다. 방 종료 후 Bob에게도 안내가 오는 새 동작과 기존 무이벤트 기대가 충돌한다.

추천 순서:

1. 기존 방 종료 테스트 기대를 복구하고 동작 기준을 확보한다.
2. Codec.encodeError의 입력을 ChatErrorResponse로 바꾸고 Handler·RoomConnectionCloser 호출부를 함께 수정한다. Handler는 실제 예외에서 데이터 객체로 변환하며 기존 기본 문구를 보존한다. 정기 작업은 안내 데이터 객체를 직접 만든다.
3. 기존 JSON의 type/status/message, 숫자 400/401/403, 안내 문구와 종료 코드 1008은 유지한다. Service 예외·DB·새 오류 enum은 이번 수정 대상에 넣지 않는다.
4. 유휴 연결의 자동 안내와 종료를 검증한다. 방송 직전 종료 검사는 후속 작은 단계다.

사용자에게 확인한 질문: 테스트 복구 후 위 범위로 작게 수정할지, 지금은 종료 테스트만 완성할지.

## Answer

2026-10-11: 사용자는 오류 안내 부분 전체 수정을 AI에게 명시적으로 위임했다. Codec은 ChatErrorResponse를 입력받아 직렬화하고 Handler에서 실제 예외를 안내 데이터로 변환한다. 정기 작업은 예외 없이 방 종료 데이터를 만든다. 기존 JSON과 상태 번호, Service 예외, 연결 종료 정책은 유지한다. 기존 방 종료 테스트와 사용자 만료·차단 테스트를 구분하고 현재 동작을 검증한 뒤 범위 내 리팩터링을 진행한다. 결정은 확정됐으며 구현·실행 결과는 devlog에 별도로 기록한다.
