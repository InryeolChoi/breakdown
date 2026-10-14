# 프런트 연결용 서버 계약 메모

2026-10-13 / a92cbab 소스 읽기 기준. 이번에는 API를 실행하지 않았다. 이 메모는 계약의 캐시이며 변경 시 decisions.md 및 아래 원본 코드가 우선한다. 새로운 API 제안이 아니다.

## 공개 HTTP 경계

| 동작 | 메서드·주소 | 요청 | 성공 응답 |
| --- | --- | --- | --- |
| 현재 열린 방 | GET /rooms | 없음 | 200 배열: id, name, openAt, closeAt |
| 익명 준비/재방문 | POST /anonymoususer | JSON nickname 선택 입력; 생략은 빈 객체로 전달 가능 | 200: id, nickname, createdAt, lastSeenAt, status + Set-Cookie |
| 사용자 신고 | POST /rooms/{roomId}/reports | JSON messageId(UUID), reason | 201: id, createdAt + Set-Cookie |

원본: [RoomController](../../../src/main/java/com/breakground/room/RoomController.java), [RoomResponse](../../../src/main/java/com/breakground/room/dto/RoomResponse.java), [AnonymousUserController](../../../src/main/java/com/breakground/anonymoususer/AnonymousUserController.java), [AnonymousUserService](../../../src/main/java/com/breakground/anonymoususer/AnonymousUserService.java), [ReportController](../../../src/main/java/com/breakground/report/ReportController.java), [ReportService](../../../src/main/java/com/breakground/report/ReportService.java).

닫힌 방 전체 목록·다음 개장 날짜·현재 참가자 수 API는 없다. Room API는 현재 열린 방만 반환한다. 빈 배열을 받으면 다음 개장 시각을 아는 것처럼 표시하지 않는다.

익명 준비 API는 RequestBody를 요구한다. 닉네임을 생략해도 빈 JSON 객체를 보내며 Params를 JSON으로 취급하지 않는다. 새 사용자의 닉네임은 선택이고 재방문 유효 사용자는 기존 이름을 유지한다. BANNED 사용자도 준비 API에서 200/상태 데이터로 반환될 수 있으므로 HTTP 200만으로 입장 가능을 판단하지 않는다.

닉네임 DTO의 @Size와 서비스 길이 검사는 Java UTF-16 길이를 기준으로 한다. 채팅의 코드 포인트 길이와 같은 검사라고 가정하지 않는다. 오류 HTTP 본문도 전부 WebSocket ERROR 형태는 아니다. 익명 validation과 채팅/신고 advice의 대표 형식은 `message` 필드이며 JSON 파싱 실패 같은 기본 오류는 다를 수 있다.

신고 HTTP 오류는 400 입력/자기 신고, 401 식별 문제, 403 차단/닫힌 방, 404 없는 방/다른 방 메시지, 409 중복, 410 원본 없음/만료가 있다. 여러 실패가 겹치면 서비스 검사 순서에 따라 먼저 발견된 오류가 반환된다. 방 종료 이후에는 410만 온다고 기대하지 않는다.

## 쿠키와 출처

쿠키 이름은 `anonymous_user_token`; HttpOnly, SameSite=Lax, Path=/, Max-Age=7일이다. Secure는 현재 servletRequest.isSecure()에 따라 설정한다. 원문은 JS에서 읽어 보관하지 않는다. 프록시가 개입하면 Set-Cookie와 요청 Cookie, 원래 HTTPS 정보 전달을 검증한다.

브라우저 출처는 scheme/host/port를 포함한다. 개발 Next와 Spring이 서로 다른 포트이면 같은 출처가 아니다. 일반 HTTP CORS와 WebSocket Origin 허용도 별개다. 현재 채팅 경로에 임의 출처 허용은 설정하지 않았으며 echo 경로만 wildcard다. 프런트 구현자는 echo 설정을 채팅의 허용 정책으로 착각하지 않는다. [Spring allowed origins](https://docs.spring.io/spring-framework/reference/web/websocket/server.html#websocket-server-allowed-origins).

확인할 후보는 (1) 브라우저가 같은 출처로 접근하도록 HTTP/WS를 프록시하거나 (2) 명시적인 개발/운영 출처와 cookie/credentials 조건으로 별도 출처를 허용하는 안이다. Next rewrite/route handler가 WebSocket Upgrade까지 그대로 중계한다고 가정하지 않는다. URL의 localhost와 127.0.0.1도 혼용하지 않는다. 선택한 연결 경로를 실제 브라우저에서 확인한 뒤 확정한다.

## WebSocket 경계

주소: `/ws/rooms/{roomId}`. 입장 전 HTTP로 익명 사용자를 준비한다. 서버는 쿠키·방·사용자 상태를 검증한 뒤 roomId/rawToken/closesAt을 연결 속성으로 준비한다.

클라이언트 송신은 content 문자열을 담은 JSON이다. roomId·authorId·rawToken을 메시지 본문에 넣지 않는다.

정상 메시지 응답 필드: id(UUID), roomId, authorId, nickname, content, sentAt, expiresAt. 현재 정상 응답에 type 필드는 없다. 오류만 type=ERROR, status, message를 사용한다. 성공을 가정해 임의 MESSAGE type을 요구하는 파서를 만들지 않는다.

원본: [ChatHandshakeInterceptor](../../../src/main/java/com/breakground/chat/ChatHandshakeInterceptor.java), [ChatMessageCodec](../../../src/main/java/com/breakground/chat/ChatMessageCodec.java), [ChatMessageResponse](../../../src/main/java/com/breakground/chat/dto/ChatMessageResponse.java), [ChatErrorResponse](../../../src/main/java/com/breakground/chat/dto/ChatErrorResponse.java), [ChatWebSocketHandler](../../../src/main/java/com/breakground/chat/ChatWebSocketHandler.java).

| 시점 | 서버가 하는 일 | 프런트에서 보장해선 안 되는 것 |
| --- | --- | --- |
| 접속 전 | HTTP handshake 거절: 400/401/403/404 등 | browser onerror에서 해당 HTTP status/본문을 직접 얻는다고 가정 |
| 연결 후 입력 오류 | ERROR status=400, 연결 유지 | 입력 오류를 받았다고 방 전체를 종료 |
| 연결 후 만료/차단/닫힘 | ERROR status=401/403 후 close 1008 | 모든 403/1008을 방 종료라고 단정 |
| 네트워크 실패 | 오류/비정상 종료 가능 | 정책 안내가 항상 먼저 도착한다고 가정 |
| 서버 저장 성공 | 저장 트랜잭션 완료 뒤 같은 방 전달 | socket.send() 호출만으로 저장 성공을 확정 |

브라우저 error 이벤트는 일반 Event이며 CloseEvent에는 종료 정보가 있다. 이 자료와 서버 코드를 함께 보면, 핸드셰이크 실패를 연결 후 정책 JSON처럼 분기하는 요구는 현재 클라이언트 계약으로 보장할 수 없다는 결론이다. [MDN error event](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket/error_event), [MDN close event](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket/close_event). 입장 실패 안내를 더 구체화하는 API 변경이 필요하면 별도 범위를 제안한다.

## 시각·문자열·재연결 주의

- 서버 응답 LocalDateTime에는 offset이 없다. ChatConfiguration Clock은 Asia/Seoul이고 RoomController/익명 준비는 LocalDateTime.now()를 직접 사용한다. 운영 JVM 시간대·프록시 뒤 설정을 확인하기 전 브라우저 로컬 시간으로 임의 변환하지 않는다. Room의 LocalTime만으로 당일 종료 날짜를 추정해 최종 운영 여부를 판단하지 않는다.
- 채팅은 서버에서 앞뒤 공백 제거 후 Unicode 코드 포인트 최대 140자를 검사한다. JavaScript 문자열 length와 무조건 동일하지 않다. NUL은 서버가 거절한다.
- 신고 expiresAt은 원본 삭제 시각과 다르다. 화면에 남아 있다고 계속 신고할 수 있는 것은 아니다. 브라우저 시계 판단은 UX 보조이며 서버가 최종 판단한다.
- 재접속은 과거 메시지를 복원하지 않는다. DB에 임시 저장한다고 목록 조회 API가 존재하는 것은 아니다.
- client message ID/재전송 중복 방지 계약이 없다. 자동 재전송은 중복 저장을 만들 수 있어 별도 합의가 필요하다.
- 메시지 본문과 닉네임은 텍스트로 렌더링하고 HTML을 실행하지 않는다.

이러한 주의점 때문에 프런트 구현 전에 서버 전체를 리팩터링하는 것으로 범위를 확대하지 않는다. 실제 연결을 막는 부분부터 기능별로 검증한다.
