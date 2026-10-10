# WebSocket은 반드시 Future로 작성해야 할까?

작성일: 2026-10-10 · Java 21 / Spring WebSocket echo 학습 메모

**그렇지 않다. WebSocket 통신 규약과 Java 라이브러리의 API 모양을 구분해야 한다.**
현재 테스트에서 사용한 JDK WebSocket 클라이언트가 Future를 반환하고, 응답 대기를 위해 우리가 Future를 하나 더 사용하는 것이다.

## 1. 통신 규약은 메시지가 오가는 방법을 정한다

WebSocket은 연결을 시작하는 handshake와 이후의 양방향 데이터 전송으로 구성된다.
연결 후 양쪽은 상대의 전송과 독립적으로 메시지를 보낼 수 있다. Java 클래스나 반환 타입을 정하는 규약은 아니다.
[RFC 6455 §1.2 Protocol Overview](https://datatracker.ietf.org/doc/html/rfc6455#section-1.2)

따라서 다음도 가능하다.

```text
나 → 서버: 메시지 A
서버 → 나: 다른 사람의 메시지 B
서버 → 나: 다른 사람의 메시지 C
```

여기서 B가 A의 응답인 것은 아니다. 원문을 되돌려주는 것은 현재 echo 서버의 동작이다.
WebSocket 자체가 모든 전송에 일대일 응답을 약속하는 것은 아니라는 해석은 양방향 독립 전송 규정과 부합한다.
[RFC 6455 §1.2](https://datatracker.ietf.org/doc/html/rfc6455#section-1.2)

## 2. 동기 방식의 WebSocket 전송 API도 있다

Jakarta WebSocket은 전송을 두 가지 API로 제공한다.

| API | 호출한 코드의 동작 | 완료를 확인하는 방법 |
| --- | --- | --- |
| `RemoteEndpoint.Basic.sendText(String)` | 전체 데이터를 연결에 쓸 때까지 기다림 | 메서드 반환 또는 예외 |
| `RemoteEndpoint.Async.sendText(String)` | 비동기 전송 시작 | 반환된 `Future<Void>` |
| `RemoteEndpoint.Async.sendText(String, SendHandler)` | 비동기 전송 시작 | 전달한 콜백 호출 |

Basic은 동기·블로킹 전송이며, Async는 Future와 콜백을 모두 제공한다.
여기서 **쓰기 완료도 상대의 응답 도착을 뜻하지 않는다.** 이 API들의 완료 기준은 데이터를 연결에 쓴 시점이다.
[Jakarta Basic](https://jakarta.ee/specifications/websocket/2.2/apidocs/client/jakarta/websocket/remoteendpoint.basic),
[Jakarta Async](https://jakarta.ee/specifications/websocket/2.2/apidocs/client/jakarta/websocket/remoteendpoint.async)

현재 Spring 서버의 `session.sendMessage(response)`도 반환 타입이 `void`다.
즉 현재 작성한 서버 핸들러에서도 모든 작업을 Future로 표현하고 있지는 않다.
[Spring WebSocketSession.sendMessage](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/socket/WebSocketSession.html#sendMessage(org.springframework.web.socket.WebSocketMessage))

이 비교는 API를 이해하기 위한 것이고, 현재 프로젝트를 Jakarta API로 바꾸자는 제안은 아니다.

## 3. 현재 테스트에서는 세 가지 완료를 구별한다

| 작업 | 결과 객체 | 무엇이 끝났다는 뜻인가? |
| --- | --- | --- |
| `buildAsync(uri, listener)` | `CompletableFuture<WebSocket>` | 서버 연결 작업 |
| `webSocket.sendText(text, true)` | `CompletableFuture<WebSocket>` | 그 문자열 전송 작업 |
| 직접 선언한 `received` | `CompletableFuture<String>` | 우리가 기다리기로 한 echo 문자열 수신 |

처음 둘은 JDK API가 반환한다. 마지막 것은 테스트가 수신 결과를 기다리기 위해 직접 선택한 도구다.
연결 성공은 [Builder.buildAsync](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/WebSocket.Builder.html#buildAsync(java.net.URI,java.net.http.WebSocket.Listener)),
전송 성공은 [WebSocket.sendText](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/WebSocket.html#sendText(java.lang.CharSequence,boolean))의 완료 기준이다.

메시지 도착은 `sendText`의 반환값으로 받지 않고, 나중에 호출되는 `Listener.onText`에서 받는다.
콜백으로 받은 문자열을 `received.complete(문자열)`로 전달하는 것은 현재 테스트의 설계다.
[Java Listener.onText](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/WebSocket.Listener.html#onText(java.net.http.WebSocket,java.lang.CharSequence,boolean)),
[CompletableFuture.complete](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html#complete(T))

```text
테스트 코드: 연결 완료 → 전송 완료 → received.get(시간 제한) → 결과 비교
수신 콜백:                              문자열 도착 → received.complete
```

`received.get(3, SECONDS)`는 결과가 아직 없으면 **테스트를 실행하는 스레드를 기다리게 한다.**
결과가 오면 반환하고, 3초 동안 없으면 시간 초과 예외로 실패한다. Future를 쓴 코드에서도 이렇게 블로킹 대기를 할 수 있다.
[CompletableFuture.get(long, TimeUnit)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html#get(long,java.util.concurrent.TimeUnit))

## 4. 서로 다른 세 개념을 분리해서 읽자

| 개념 | 지금 코드에서 이해할 내용 |
| --- | --- |
| 사건에 따른 처리 | 메시지 도착 때 `onText`를 호출해서 처리 |
| 기다리지 않고 작업 시작 | 비동기 전송 API가 결과 객체를 반환하고 이후 완료 |
| Future | 완료 상태·결과·실패를 표현하고, 기다리거나 후속 작업을 연결하는 객체 |

이 구분은 [Java Listener](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/WebSocket.Listener.html),
[Jakarta Async](https://jakarta.ee/specifications/websocket/2.2/apidocs/client/jakarta/websocket/remoteendpoint.async),
[CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)의 책임을 나누어 설명한 것이다.

`new CompletableFuture<>()`는 미완료 결과 객체를 만드는 것이며, 그것만으로 새 스레드를 만드는 것은 아니다.
`complete`로 직접 채울 수 있고, 후속 작업을 어느 스레드에서 실행할지는 사용하는 메서드와 실행 설정에 따라 다르다.
[CompletableFuture 생성자와 실행 정책](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)

확인 질문: **sendText의 Future는 완료됐지만 received는 아직 미완료다. 이것은 오류일까?**
전송을 마쳤고 아직 echo가 도착하지 않은 정상 중간 상태일 수 있다. 이후 응답이나 실패, 시간 초과를 별도로 확인해야 한다.
