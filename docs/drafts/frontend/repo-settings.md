# 저장소 설정 제안

상태: 초안. GitHub 설정과 활성 저장소 설정은 변경하지 않았다.

## 실제 상태와 변경 후보

| 설정 | 현재 | 제안 | 적용 조건 |
| --- | --- | --- | --- |
| Issues | 활성 | 그대로 사용 | 이미 사용자 지정 |
| main 보호 | 미보호 | PR 경유, force push/삭제 차단, 리뷰 대화 해결 요구 | 사용자 승인 후 |
| 승인 리뷰 수 | 적용 규칙 없음 | 혼자 개발하는 동안 외부 승인 1명을 필수로 만들지 않음 | 본인 검토와 AI 리뷰는 별도로 수행 |
| 필수 CI | 로컬에 워크플로 없음 | 먼저 동작하는 CI를 만들고 실제 check 이름을 필수 등록 | 존재하지 않는 check를 먼저 요구하지 않음 |
| 병합 방식 | merge/squash/rebase 가능 | 기본 squash 사용 | 다른 방식 전체 비활성화는 별도 결정 |
| 자동 머지 | 꺼짐 | 유지 | 사용자 이해·검토 후 머지 |
| 병합 후 branch 삭제 | 꺼짐 | 자동 삭제 후보 | 기능 브랜치의 보관 필요 확인 |
| labels | 일반 라벨 10개 | triage 라벨 부족분만 추가 | 아래 기본 매핑 확인 후 |
| Issue/PR 양식 | 없음 | templates 초안 적용 | 확인 후 .github에 배치 |

브랜치 보호는 PR·검증 요구를 만들 수 있지만 관리자 우회 허용 여부도 따로 정해야 한다. 외부 승인자 없이 승인 1개를 필수로 걸어 혼자 머지할 수 없는 설정을 만들지 않는다. AI가 글로 리뷰했다는 사실과 GitHub 승인 리뷰 수는 다른 개념이다. [GitHub 브랜치 보호](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).

## 승인 후 적용할 파일

| 초안 | 적용 대상 |
| --- | --- |
| setup/issue-tracker.md | docs/agents/issue-tracker.md |
| setup/triage-labels.md | docs/agents/triage-labels.md |
| setup/domain.md | docs/agents/domain.md |
| setup/agent-skills.md | claude.md의 Agent skills 블록 및 agents.md에서 소비할 포인터 |
| templates/feature.md | .github/ISSUE_TEMPLATE/feature.md |
| templates/decision.md | .github/ISSUE_TEMPLATE/decision.md |
| templates/bug.md | .github/ISSUE_TEMPLATE/bug.md |
| templates/pull_request_template.md | .github/pull_request_template.md |

Markdown 이슈 양식을 기본 제안으로 두었다. 이슈 생성 화면에서 본문을 수정하기 쉽고 스킬의 명세/티켓 구조와 맞는다. 필요하면 승인 뒤 Issue Forms로 바꿀 수 있다. 로컬 drafts는 GitHub에서 양식으로 활성화되지 않는다. [GitHub 양식 배치 규칙](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository).

템플릿에 있는 `needs-triage`는 아직 GitHub에 없다. 라벨 확인·생성을 먼저 하고 양식을 적용한다. `ready-for-agent`는 자동 기본 라벨로 쓰지 않으며 명세·테스트 경계·미결정 확인 뒤에만 부여한다. 기존 `wontfix`는 재사용한다.

## CI 준비 순서

1. 프런트 실행 위치는 같은 저장소 루트 frontend로 승인됐다. 첫 기능과 함께 패키지 관리자·버전·잠금 파일을 확정한다. 이번에는 설치나 버전 선택을 하지 않았다.
2. 프런트 typecheck/lint/build와 사용자 동작 검증을 재현 가능한 명령으로 정의한다.
3. 백엔드 검증에는 PostgreSQL이 필요한 기존 테스트가 있다. 향후 CI에서 DB 서비스를 준비해 Gradle 테스트를 실행한다. 로컬 Docker Desktop 종료/시작 명령을 CI에 가져가지 않는다.
4. 첫 CI 실행 성공 후 실제 job/check 이름과 영향을 기록하고 필수 checks를 지정한다. frontend 디렉터리 부재나 path filter 때문에 필수 check가 영원히 대기하지 않도록 한다.
5. 이후 프런트/서버 계약을 건드리는 PR은 관련 검증을 함께 실행한다.

CI YAML도 이번에는 만들지 않았다. React/Next 초기 파일이나 없는 package scripts를 가정한 workflow는 적용할 수 없다.

## 실제 게시 전 순서

1. setup 파일 내용과 기본 triage 라벨 확인.
2. 활성 설정·AI 지침 포인터·양식 적용 범위를 승인.
3. 필요한 라벨 추가 및 설정 반영.
4. 설계 map/spec/작업 티켓 초안 승인.
5. GitHub 이슈를 생성하고 반환된 실제 ID로 parent·blocking 관계 연결.
6. 승인된 첫 기능 이슈만 구현 착수.

GitHub에는 부모 sub-issue 관계와 blocking 관계가 별도로 있다. 부모 밑에 놓였다고 모두 서로 선행 관계가 생기는 것은 아니다. native issue dependencies는 일반 요금제에서도 제공된다고 공식 문서로 확인했다. 이 저장소에 실제 edge를 추가하는 작업은 수행하지 않았다. [GitHub issue dependencies](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/creating-issue-dependencies).
