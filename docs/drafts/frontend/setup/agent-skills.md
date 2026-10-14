# 루트 AI 지침에 넣을 내용 초안

현재 파일을 자동 지침으로 취급하지 않는다. setup-matt-pocock-skills가 요구하는 사용자 확인 후 적용한다.

## claude.md에 추가할 블록

설치 스킬은 CLAUDE가 있으면 그 파일을 우선 수정하도록 한다. 실제 추적 이름 claude.md를 유지하며 주변 학습 규칙을 보존한다.

```markdown
## Agent skills

### Issue tracker

이슈·명세·설계 지도·PR 작업을 시작할 때 GitHub 작업 규칙을 읽는다: docs/agents/issue-tracker.md.

### Triage labels

이슈의 준비 상태를 표시할 때 기본 역할과 GitHub 라벨 매핑을 읽는다: docs/agents/triage-labels.md.

### Domain docs

설계·명세·구현·리뷰 전에 단일 도메인의 문서 우선순위와 용어 규칙을 읽는다: docs/agents/domain.md.
```

## 다른 AI에도 적용할 포인터 후보

Codex 등은 agents.md를 직접 읽기 때문에 CLAUDE 블록만으로 모든 도구가 소비한다고 보장하지 않는다. setup 원형의 CLAUDE 우선 규칙을 지키면서, agents.md에 아래 포인터 한 줄을 추가하는 별도 변경을 제안한다:

```markdown
이슈·명세·PR 작업에서는 claude.md의 Agent skills 블록을 읽고 연결된 docs/agents 문서를 따른다.
```

두 루트 파일에 설정 전체를 중복 복사하지 않는다. 사용자 확인 후 기존 포인터가 있다면 중복을 만들지 않고 갱신한다. 현재는 둘 다 수정하지 않았다.
