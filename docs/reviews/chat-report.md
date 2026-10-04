# 메시지 임시 보관·신고 구현 읽기

## 이번에 작동하는 것

- `POST /anonymoususer`로 브라우저 익명 사용자를 준비한다.
- `POST /rooms/{roomId}/messages`에 쿠키와 `{"content":"안녕"}`을 보내면 서버가 만든 메시지 UUID를 받는다.
- 다른 익명 사용자가 `POST /rooms/{roomId}/reports`에 쿠키와 `{"messageId":"응답 UUID","reason":"신고 사유"}`를 보내면 서버 원본이 Report로 저장된다.
- 일반 메시지는 DB에 쓰지 않는다. 최대 15분, 또는 해당 운영 구간 종료 시점 중 빠른 시점에 만료된다.
- 신고 증거는 신고 접수 시점부터 30일을 보관 기한으로 기록한다.

이 단계는 HTTP 작성·신고 흐름을 완성한다. 연결된 여러 브라우저로 실시간 전달하는 WebSocket 작업은 다음 단계다. 일반 메시지 목록을 조회하는 API는 제공하지 않는다.

## 먼저 읽을 두 메서드

1. [ChatService.create](../../src/main/java/com/breakground/chat/ChatService.java): 쿠키 작성자 확인 → 방 운영 여부 확인 → 내용 검증 → UUID·만료 시각 생성 → 메모리에 넣기.
2. [ReportService.create](../../src/main/java/com/breakground/report/ReportService.java): 쿠키 신고자 확인 → 방 운영 여부 확인 → UUID로 원본 조회 → 방·자기 신고·중복 확인 → 원본을 Report Entity에 복사 → PostgreSQL 저장.

Controller는 JSON/쿠키를 받고 Service 결과를 HTTP 응답으로 바꾼다. 작성자·신고 대상·증거 내용을 브라우저 입력으로 결정하지 않는다. 성공한 작성/신고는 last_seen_at과 쿠키 만료를 갱신한다. 없는/만료된 익명 쿠키는 새 사용자를 만들지 않고 401을 반환하므로 초기화 API를 다시 호출해야 한다. 차단 중인 사용자는 403이다.

## 낯설 수 있는 세 부분

- [RecentMessageStore](../../src/main/java/com/breakground/chat/RecentMessageStore.java): Spring이 관리하는 객체의 Map이다. 한 서버가 꺼지면 사라진다. 용량 확인과 추가를 하나로 잠그기 위해 synchronized와 HashMap을 사용했다. ConcurrentHashMap 단독으로는 여러 작업의 원자성을 보장하지 않는다. 10,000개가 차면 503으로 새 전송을 거절하며, 아직 신고 가능한 원본을 조용히 버리지 않는다.
- `Clock`: 실제 대기 없이 테스트 시간을 바꿀 수 있고, 운영에서는 Asia/Seoul 기준으로 방 시간을 판단한다.
- `TransactionSynchronization`: JPA 트랜잭션은 PostgreSQL만 롤백한다. Map에 넣은 메시지는 직접 제거해야 하므로 실패 시 제거 콜백을 등록했다. 신고 시 읽은 메시지는 불변 record라 Map에서 삭제되더라도 이미 확보한 원본 객체가 바뀌지 않는다.

## 만료와 정리

메시지는 expiresAt부터 즉시 조회 불가다. 메모리의 실제 제거는 조회/새 전송 시, 또는 1초 간격 정리 작업에서 수행한다. 방 종료 시각도 이 만료 시각에 반영하므로 다음 날 방이 다시 열려도 원본이 되살아나지 않는다. 방 운영 시간을 실행 중에 관리자가 변경하는 기능은 현재 없다.

Report는 evidenceExpiresAt 이하인 row를 1분 간격 정리 작업이 삭제한다. 따라서 실제 DB 삭제는 만료 후 다음 정리 실행 시점이다. 단일 서버의 메모리 원본이 사라진 뒤에는 브라우저 내용으로 대신 신고할 수 없다. 이미 신고 접수된 증거는 별도 보관 기한을 따른다.

신고를 보냈다고 자동 차단하지는 않는다. 신고 누적 임계값·경고·자동 차단 정책은 아직 결정할 항목이다. 자체 신고를 거절하며, 사유는 1~500자다. 메시지 최대 140자는 Unicode 코드 포인트 기준으로 계산한다.

## 검증과 이해 확인

HTTP 요청에서 PostgreSQL 저장까지 이어지는 테스트를 우선했다. 서버 원본 검증, 140자 경계/이모지, 익명 쿠키·차단, 메시지 만료와 방 종료, 중복 신고와 DB UNIQUE 제약, 30일 증거 정리, 동시 메모리 용량 제한, DB 롤백 시 Map 정리를 검증했다. 일반 채팅용 Message 테이블은 없으며 V9는 Report 테이블을 생성한다.

전체 테스트 39개가 통과했다. [HTTP·DB 통합 테스트](../../src/test/java/com/breakground/report/ChatReportIntegrationTest.java), [메모리 보관 테스트](../../src/test/java/com/breakground/chat/RecentMessageStoreTest.java), [V9 스키마](../../src/main/resources/db/migration/V9__create_report.sql)를 함께 보면 검증한 범위를 확인할 수 있다.

검토 질문 하나: 브라우저가 Report 요청에 reportedUserId와 evidenceContent를 추가해서 보내도, 왜 신고 대상과 증거 내용이 바뀌지 않을까? ReportService.create의 해당 부분을 찾아 설명해보면 된다.

## 다음 단계의 진행 방식

`/grill-with-docs → /to-spec → /to-tickets → /implement`는 정책을 사람이 검토하고 승인한 작은 작업을 AI가 구현하는 흐름이다. 다음 WebSocket 기능에 적용하는 것이 적절하다. Wayfinder는 더 큰 작업의 미해결 설계 결정을 여러 세션에 걸쳐 정리할 때 쓴다. 이 스킬들의 내용은 확인했지만 이번 작업에서 트래커 이슈를 발행하거나 워크플로를 자동 시작하지 않았다.
