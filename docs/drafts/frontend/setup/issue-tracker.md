# Issue tracker: GitHub — 적용 후보

활성 대상: docs/agents/issue-tracker.md. 현재는 drafts이며 승인 후 옮긴다.

이 저장소의 신규 이슈·명세·설계 지도는 GitHub `InryeolChoi/breakdown`을 사용한다. 로컬 폴더명과 저장소명을 혼동하지 않는다. 기존 `.scratch/websocket-chat/`는 이전 결정 이력으로 읽으며 자동으로 이동하거나 다시 게시하지 않는다.

## Conventions

- 이슈 본문·댓글을 읽을 때 labels/assignee와 최신 댓글도 확인한다.
- GitHub CLI를 기본으로 한다. 사용 가능한 읽기 전용 connector는 조회에 쓸 수 있다.
- 여러 줄 이슈·댓글·PR 본문은 파일에 저장해 --body-file로 전달한다.
- `publish to tracker`는 GitHub 이슈 생성이다. 로컬 초안 저장과 게시를 구분한다.
- `fetch ticket`은 실제 저장소/번호/제목을 확인한 후 본문과 댓글을 읽는 것이다.
- 이슈·PR 번호는 실제 생성 결과로만 사용한다. 초안 순번을 GitHub 번호로 바꾸지 않는다.

## Pull requests as a triage surface

PRs as a request surface: no.

## Wayfinding operations

- Map: wayfinder:map 라벨의 부모 이슈. destination과 notes, 해결된 결정 링크, 아직 구체화하지 못한 영역, 제외 범위를 담는다.
- Child: native sub-issue로 map에 연결. wayfinder:research/prototype/grilling/task 중 해당 라벨을 쓴다. child 관계는 blocking과 다르다.
- Blocking: native issue dependencies 사용. 현재 gh help에서 parent/blocked-by 및 add-blocked-by/add-sub-issue 옵션을 확인했다. 실제 생성된 번호로 관계를 두 번째 단계에서 연결한다. REST fallback을 쓰면 issue database id와 화면 번호를 구분한다.
- Frontier: map의 open children 중 열린 blocker가 없고 assignee가 없는 항목. map 밖의 모든 open issue를 같이 실행하지 않는다.
- Claim: 작업 시작 전에 담당자를 assign한다. 질문용 HITL 이슈는 사용자 답변 없이 완료 처리하지 않는다.
- Resolve: 결정 답변을 comment로 기록하고 이슈를 닫은 뒤 map의 Decisions so far에 이름과 링크를 추가한다. map에 답변 전체를 중복 복사하지 않는다.
- native 관계가 접근 권한/환경 때문에 실제로 불가능하면 원인을 남기고 부모/Blocked by 본문 링크로 임시 표현한다. 가능하면 native 관계를 우선한다.

## Implementation issues and PRs

기본 제안은 기능 이슈 하나 / 기능 브랜치 하나 / main 대상 PR 하나다. 기능 PR 본문은 해당 이슈만 닫도록 연결하고 상위 명세는 전체 완료 전 닫지 않는다. main 머지 전 이슈를 완료로 닫거나 그 이슈에 막힌 작업을 구현 완료로 간주하지 않는다.

implement-spec의 통합 브랜치/PR 방식은 별도 선택이다. 내부 PR을 integration branch에 머지한 상태는 main에 출시된 완료와 다르다.

## Authorization

실행 시 사용자의 최신 범위를 확인한다. 이슈 게시·댓글·라벨·설정 변경·PR 생성·머지는 외부 쓰기다. 초안 준비만 요청한 세션에는 실행하지 않는다. '자고 있으니 준비'는 미답변 정책을 승인하거나 GitHub 게시를 허용하는 의미가 아니다.
