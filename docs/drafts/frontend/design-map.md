# 첫 서비스 경험과 프런트 연결 설계 지도 — 준비 원본

2026-10-15 [GitHub 설계 지도](https://github.com/InryeolChoi/breakdown/issues/1)를 발행했다. 아래는 준비 이력이며 최신 child/선행 관계·결정 댓글은 GitHub를 따른다. [이슈 인계](../../frontend-issues.md)를 함께 확인한다.

## Destination

로그인 없이 짧게 쉬는 경험을 기존 서버에 연결하는 최소 프런트 명세와 승인된 기능 티켓으로 인계할 수 있는 상태를 만든다. 현재는 destination 자체도 사용자 확인 전 제안이며 HITL 결정을 대신 해결하지 않는다.

## Notes

- GitHub의 wayfinder:map으로 게시할 본문 후보. 아직 map/child 이슈는 만들지 않았다.
- 첫 화면·라운지 표현·도구·연결 방식을 결정하고 실제 구현은 별도 단계로 둔다.
- Next.js + TypeScript와 기존 서버 정책 유지는 사용자의 이번 지시로 확정됐다.
- 프런트 저장 위치는 같은 저장소 루트 frontend로 사용자 승인됐다. 기존 Spring 구조를 유지한다. 접속 경로 결정과는 구분하며 정식 기록은 docs/decisions.md를 따른다.
- 사용자가 탁 트인 창문이 보이는 라운지 분위기, 모바일 360px/데스크톱 1280px, 채팅+신고 공개 범위, 작은 기능별 PR과 첫 브라우저 테스트 경계에 동의했다. 정식 정책은 docs/decisions.md에 기록했다. 게시된 티켓의 resolution으로 대체 기록하지 않는다.
- 2026-10-15 Stitch를 먼저 사용하기로 결정했다. 인증·연결 검증은 미수행이며 근거는 docs/research/design-mcp-comparison.md에 있다.
- 용어와 도메인은 GLOSSARY.md/decisions.md를 따른다. 판단 질문은 [questions.md](questions.md)에 모았다.

## Decisions so far

이번의 새 결정 티켓은 아직 해결하지 않았다. 게시 후 사용자와 해결한 티켓의 이름·링크만 추가한다.

## Not yet specified

- 첫 경험의 방향을 정한 뒤 구체화할 여백·색·타이포·조작 배치.
- 연결 방식을 정한 뒤 구체화할 개발/운영 경로와 증거 재현 절차.

## Out of scope

- 이 map 안에서 완성 앱을 구현하거나 배포하는 작업.
- 게시판 중심·메타버스·과거 대화 목록.
- 게임·관리자·자동 차단과 새로운 서버 기술.

## Child issue 후보 — map 본문과 분리해 게시

| 이름 후보 | 종류 | 정할 것 | 선행 관계 |
| --- | --- | --- | --- |
| 첫 화면과 익명 입장 순서 | grilling / HITL | Q4, 화면을 본 직후의 행동. 기기 기준은 확정됨 | 없음 |
| 라운지 첫 화면의 구체적인 표현 | prototype / HITL | 분위기 방향과 Stitch 우선 선택은 확정. 실제 시안과 배치는 사용자 검토 필요 | Stitch 인증·연결 검증 |
| 프런트 HTTP/WS 접속 경로 | grilling / HITL | Q3의 남은 cookie/Origin 경로. 저장 위치는 확정됨 | 없음 |
| 구현 인계의 구체적인 경계 | grilling / HITL | 첫 기능·분할 방향·범위는 동의 받음. 화면/연결 결정 후 세부 blocking 점검 | 첫 화면과 익명 입장 순서, 프런트 HTTP/WS 접속 경로 |

게시 구조 후보이며 child의 실제 ID·담당자·의존 edge는 없다. 실제 논의는 grilling/domain-modeling 절차로 진행하고 한 세션에서 여러 HITL 티켓을 AI 독단으로 닫지 않는다. 도구·방향을 결정한 뒤 구체적인 화면을 비교한다면 prototype 티켓을 새로 만든다.
