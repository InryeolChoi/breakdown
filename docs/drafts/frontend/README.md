# 프런트 구축 준비 — 검토용 초안

작성일: 2026-10-13. 확인 기준: 로컬 `main` / `a92cbab` 및 GitHub 읽기 전용 조회.

이 폴더의 setup·양식은 적용 전 자료다. `docs/agents/`, `.github/`, 루트 AI 지침에 적용하지 않았다. 2026-10-15 사용자 요청으로 [설계/준비·기능 이슈](../../frontend-issues.md)를 발행했다. 최신 이슈 상태는 GitHub를 따르며 이 초안을 승인된 실행 설정으로 취급하지 않는다.

## 먼저 읽을 순서

1. [결정할 질문](questions.md): 사용자 답변이 필요한 부분.
2. [작업 흐름](workflow.md): 스킬 역할, 이슈 하나/PR 하나와 통합 PR 비교.
3. [저장소 설정](repo-settings.md): 현재 상태와 적용 후보.
4. [첫 작업과 티켓 분할](tickets.md): 정상·실패·경계 상황, 완료 기준.
5. [MVP 명세 초안](spec.md): 전체 방향과 범위. 아직 게시·승인되지 않음.

GitHub에 설계 지도를 만들게 되면 [map 초안](design-map.md)을 검토한다. `setup/` 네 파일과 `templates/` 네 양식은 적용 전 내용이며 그대로 활성 파일로 옮기지 않는다.

구현자가 기존 서버에 연결할 때는 [API 계약 메모](api-contract.md)를 함께 읽는다. 설정에 승인할 내용은 `setup/`, GitHub에 옮길 양식은 `templates/`에 있다.

## 이번에 확인한 사실

| 항목 | 상태 |
| --- | --- |
| 실제 GitHub 저장소 | `InryeolChoi/breakdown` — 로컬 폴더명은 `breakground` |
| 기본 브랜치·공개 여부 | `main`, public |
| GitHub Issues | 활성화됨; 조회 시 열린 이슈 0건, 열린 PR 0건 |
| main 보호 | branch `protected=false`, main에 적용된 rules 조회 결과 없음 |
| 병합 설정 | merge/squash/rebase 모두 허용, auto-merge 꺼짐, 병합 후 브랜치 자동 삭제 꺼짐 |
| 기존 라벨 | accessibility, bug, documentation, duplicate, enhancement, good first issue, help wanted, invalid, question, wontfix |
| 스킬 설정 | `docs/agents/issue-tracker.md`, `domain.md`, `triage-labels.md` 없음 |
| 도메인 문서 | 루트 `GLOSSARY.md` 있음; ADR·GLOSSARY-MAP 없음 |
| 기존 설계 지도 | `.scratch/websocket-chat/`의 로컬 지도·질문 기록 있음 |
| 프런트 | package.json·프런트 구현·workspace 설정 없음 |
| 양식·CI | 추적 중인 `.github/` 양식·워크플로 없음 |
| 이전 백엔드 검증 | 2026-10-11 기록상 전체 97개 통과; 이번에는 재실행하지 않음 |

Git에서 추적하는 루트 지침 이름은 `agents.md`, `claude.md`다. 현재 macOS에서 대문자 이름으로도 읽히지만 이후 편집은 실제 이름을 유지한다. Linux에서 대소문자를 혼동하지 않는다.

작업 시작 전 `breakground.zip` 삭제와 미추적 `.DS_Store`가 있었다. 이 변경은 이번 준비 작업에서 수정·복구·스테이징하지 않는다.

## 사용자가 확정한 방향

- Next.js + TypeScript.
- 같은 저장소 루트의 `frontend/`에 배치하고 기존 Spring 구조는 유지한다. 위치만 승인됐으며 실제 생성은 아직 하지 않았다.
- 기존 Spring 서버 정책을 유지하고 프런트 구현은 AI 활용 비중을 높인다.
- 접속 직후 무엇을 할 수 있는지 직관적으로 보이는 서비스.
- 탁 트인 창문이 보이는 라운지에서 잠깐 쉬는 분위기. 게시판 중심·메타버스 방식은 제외.
- 추가 MCP는 디자인 도구에 한정. 2026-10-15 Stitch부터 사용하기로 선택했고 인증·등록·설치·연결 검증은 아직 하지 않았다. Figma 추가는 실제 필요가 생기면 판단한다.
- 커피 컵 중심 아이디어는 사용자가 라운지 느낌으로 수정했다. 구체적인 첫 화면과 시안은 미정이다.
- 작은 기능 이슈 하나 / PR 하나, 모바일 360px·데스크톱 1280px 검토, 첫 공개의 채팅·신고, 첫 목록 기능과 테스트 경계에 동의했다.

이 내용은 사용자 답변으로 확인한 방향이다. 확정된 내용을 기존 `docs/decisions.md`에 반영했다. 이 초안은 기존 정책 문서를 대체하지 않으며 setup/세부 저장소 설정·접속 경로·첫 화면은 승인 전 상태다. 이슈 단위와 기능 분할 방향의 동의는 실제 게시 권한과 구분한다.

## 이번 작업의 완료 상태

- 요청한 일곱 스킬과 관련 setup seed 문서를 읽었다.
- GitHub 저장소 상태를 읽기 전용으로 확인했다.
- 설정·양식·명세·티켓·질문과 구현 인계 순서를 준비했다.
- 2026-10-13에는 초안만 작성했다. 이후 사용자 요청으로 2026-10-15 이슈를 게시하고 준비 문서를 커밋·푸시한다. 앱 코드, 도구 설치·계정 연결, 활성 설정 변경, PR·머지는 하지 않았다.
- 문서 준비만 하므로 Docker/DB/앱을 시작하지 않았다.

다음 세션은 질문에 답하고 setup 초안을 승인하는 데서 시작한다. 답변 없는 정책은 `ready-for-agent`로 만들지 않는다.
