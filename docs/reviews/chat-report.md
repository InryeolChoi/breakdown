# 채팅·신고 DB 전환 읽기

메시지 원본을 PostgreSQL에 임시 저장하고 신고 증거를 별도로 복사하는 구현이다. 정책은 [decisions.md](../decisions.md), 구조는 [erd.dbml](../erd.dbml)을 따른다. WebSocket은 사용자가 직접 구현하기로 했으며 아직 추가하지 않았다.

## 10분 동안 읽는 순서

1. [ChatService.create](../../src/main/java/com/breakground/chat/ChatService.java): 쿠키 작성자 → 방 운영 확인 → 본문 검증 → 두 기한 계산 → Message 저장 → 불변 스냅샷 반환.
2. [Message](../../src/main/java/com/breakground/chat/Message.java): Room/작성자는 FK 관계, nickname은 작성 당시 값이다. UUID는 Hibernate가 서버에서 생성한다.
3. [MessageRepository](../../src/main/java/com/breakground/chat/MessageRepository.java): 신고 원본 조회는 expires_at > 기준 시각, 실제 삭제는 delete_after <= 기준 시각이다.
4. [ReportService.create](../../src/main/java/com/breakground/report/ReportService.java): DB 원본을 찾고 방·자기 신고·중복을 확인한 뒤 내용을 Report에 복사한다.
5. [MessageCleanup](../../src/main/java/com/breakground/chat/MessageCleanup.java): 삭제 대상 기한이 지난 원본을 1분 간격 정리한다.

## 핵심 흐름

```text
POST /rooms/{roomId}/messages
  Controller → ChatService 트랜잭션
    AnonymousUser.lastSeenAt 변경
    Message INSERT
  같은 DB 트랜잭션 커밋 → Controller의 201 응답

POST /rooms/{roomId}/reports
  Controller → ReportService 트랜잭션
    유효한 DB Message 조회
    원문·작성자·작성 시각·UUID를 Report로 복사
  커밋 → Controller의 201 응답
```

API 입력/응답 형태는 유지했다. Entity를 직접 JSON으로 직렬화하지 않고 트랜잭션 안에서 ChatMessage 스냅샷으로 바꾼다. 응답을 만드는 중 LAZY 관계를 조회하는 일을 피한다. 신고 대상과 증거 내용은 브라우저가 정하지 않는다.

## 두 시각을 구분하는 예

| 상황 | 시각 |
| --- | --- |
| 메시지 작성 | 10:00 |
| 신고 가능 기한 expires_at | 10:15 |
| 방 종료 | 11:30 |
| 삭제 대상 기한 delete_after | 11:40 |

10:15부터 신고할 수 없지만 DB 행은 11:40까지 남는다. 실제 삭제는 그 이후 첫 정리 실행에서 수행한다. 앱이 꺼지거나 작업이 지연되면 물리 삭제도 늦어진다. 야근방의 자정 전·후 메시지는 같은 운영 구간의 종료 + 10분을 사용한다.

Report에는 원문을 복사하므로 Message 삭제와 무관하게 신고 후 30일 동안 증거가 남는다. message_event_id는 추적용 UUID이며 Message FK가 아니다. 원본 확인 당시 유효했다면 신고 커밋이 만료 이후 끝나도 접수한다.

## 이번에 제거하거나 고친 부분

- Map, 10,000개 상한, Map 롤백용 TransactionSynchronization을 제거했다. 방문 시각과 메시지 저장은 같은 DB 트랜잭션에 참여한다.
- Java 나노초 시각을 PostgreSQL 마이크로초 정밀도로 절삭했다. 방 종료 100나노초 전 작성 시각이 종료 시각으로 반올림되어 CHECK를 위반하던 경계를 막는다. 삭제 기준 시각도 절삭한다.
- PostgreSQL이 저장할 수 없는 NUL 문자가 메시지나 신고 사유에 있으면 DB 오류 대신 HTTP 400으로 거절한다.
- 신고 원본은 사용자/방 조회 후의 기준 시각으로 확인한다. 요청 시작 시각만 계속 사용하면 앞선 작업 지연만큼 유효 기간이 늘어날 수 있다.
- 설정은 reportable-duration=15m, deletion-delay=10m, cleanup-interval-ms=60000으로 분리했다. 이전 message-retention/max-buffered-messages는 사용하지 않는다.

## 한번 생각해볼 포인트

1. 왜 ChatService는 Message Entity 대신 ChatMessage record를 반환할까? 트랜잭션 경계와 LAZY 로딩을 연결해보자.
2. requireActiveUser와 ChatService 양쪽에 Transactional이 있으면 따로 커밋할까? 기본 전파 방식에서는 바깥 트랜잭션에 참여하므로 메시지가 롤백되면 방문 시각도 롤백된다.
3. DB 저장이면 여러 서버의 WebSocket 전달까지 해결될까? DB 원본 조회는 공유할 수 있지만 각 서버의 연결에 전달하는 문제는 별개다.
4. bulk DELETE 후 이미 읽은 Message 객체가 자동으로 사라질까? 영속성 컨텍스트에는 남을 수 있다. 정리 작업은 원본을 재사용하지 않고 테스트는 clear 후 재조회한다.
5. 원본 삭제가 백업까지 지울까? row 정리와 백업/WAL 보존은 별개의 운영 정책이다.

## 검증과 범위

전체 테스트 48개가 통과했다.

- [HTTP 통합 테스트](../../src/test/java/com/breakground/report/ChatReportIntegrationTest.java): 쿠키/차단, 서버 원본, 길이/이모지/NUL, 정확한 신고 만료, 자정 통과, 원본·증거 정리.
- [실제 트랜잭션 테스트](../../src/test/java/com/breakground/chat/ChatPersistenceTransactionTest.java): 성공 커밋, 메시지/방문 시각 함께 롤백, 다른 트랜잭션의 커밋 전 미조회, 새 영속성 컨텍스트 신고, 만료 이후 신고 커밋.
- [Repository 테스트](../../src/test/java/com/breakground/chat/MessageRepositoryTest.java): UUID/FK 재조회, 두 기한 분리, 원본 삭제 후 증거/중복 키 유지.

실제 JVM 재시작과 여러 서버 전달은 테스트하지 않았다. 별개의 커밋된 트랜잭션/영속성 컨텍스트에서 DB 원본을 조회하는 것까지 검증했다. 정리 작업은 직접 호출하며 실제 1분을 기다리는 테스트는 하지 않는다.

WebSocket 전송 경로와 방 종료 시 연결 처리 정책은 퇴근 후 사용자가 결정한다. 다음 세션에서는 구현을 대신 진행하기 전에 질문과 작은 예제로 설계를 함께 검토한다.
