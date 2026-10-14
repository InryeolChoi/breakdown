# 프런트 GitHub 이슈 인계

2026-10-15 사용자 요청으로 발행했다. 아래는 실제 GitHub 번호이며 제목 링크로 식별한다. 실행 상태·최신 대화·부모/선행 관계는 GitHub를 기준으로 확인한다. 준비 초안이 최신 이슈 상태를 대체하지 않는다.

## 설계 지도와 미결정 작업

부모 지도: [프런트 설계 지도: 라운지에서 잠깐 채팅하는 경험](https://github.com/InryeolChoi/breakdown/issues/1).

| 티켓 | 종류 | 선행 |
| --- | --- | --- |
| [GitHub 스킬 설정과 이슈 상태 라벨 확정](https://github.com/InryeolChoi/breakdown/issues/3) | 사용자 질문 | 없음 |
| [브라우저 HTTP·쿠키·WebSocket 접속 경로 결정](https://github.com/InryeolChoi/breakdown/issues/4) | 사용자 질문/검증 | 없음 |
| [라운지 첫 화면과 익명 입장 순서 결정](https://github.com/InryeolChoi/breakdown/issues/5) | 사용자 질문 | 없음 |
| [Stitch 디자인 MCP 인증·연결 검증](https://github.com/InryeolChoi/breakdown/issues/6) | 준비 작업 | 없음; 인증 관련 사용자 참여 필요 |
| [라운지 첫 화면 시안 비교와 승인](https://github.com/InryeolChoi/breakdown/issues/7) | 시안/사용자 검토 | 첫 화면 순서, Stitch 연결 |

Stitch라는 도구 선택은 완료됐지만 인증·연결 작업은 열려 있다. 현재 이슈들을 해결 완료로 닫지 않았다.

## 기능 구현

부모 명세: [프런트 MVP: 열린 방부터 채팅과 신고까지](https://github.com/InryeolChoi/breakdown/issues/2).

| 티켓 | 실제 선행 |
| --- | --- |
| [브라우저에서 지금 열린 방 확인](https://github.com/InryeolChoi/breakdown/issues/8) | 스킬 설정 범위, 접속 경로, 첫 화면 순서 |
| [닉네임 준비 후 열린 방에 입장](https://github.com/InryeolChoi/breakdown/issues/9) | 열린 방 확인 |
| [같은 방의 실시간 채팅과 종료 안내](https://github.com/InryeolChoi/breakdown/issues/10) | 익명 입장 |
| [수신한 메시지로 사용자 신고](https://github.com/InryeolChoi/breakdown/issues/11) | 실시간 채팅 |

작은 기능 이슈 하나당 PR 하나의 흐름이다. blockedBy 관계를 실제 GitHub native dependencies로 연결했으며 총 9개 자식의 부모·선행·OPEN 상태를 다시 조회해 확인했다. 부모 지도/명세 두 개를 포함해 총 11개 이슈를 발행했다.

## 설정 상태와 다음 행동

- 기본 triage 라벨 매핑과 main 보호 등 세부 setup은 미승인이다. 활성 docs/agents·.github·루트 AI 지침은 아직 수정하지 않았다.
- 설계 티켓 구분에 필요한 wayfinder:map/grilling/task/prototype 라벨 네 개를 생성했다. 구현 이슈는 기존 enhancement를 사용한다.
- unresolved 작업에 ready-for-agent를 적용하지 않았다. dependency가 열려 있으면 구현 착수하지 않는다.
- 다음 도구 작업은 Stitch 계정 조건 확인과 인증·등록·연결 검증이다. 요청/권한 범위를 확인하고 비밀값을 공개 기록에 넣지 않는다.
- 이번 발행은 구현·계정 연결·PR 생성·머지를 한 것이 아니다.

준비 문서와 정책·학습 기록은 이번 문서 커밋으로 보관한다. 기존 ZIP 삭제와 .DS_Store는 해당 커밋 범위에서 제외한다.
