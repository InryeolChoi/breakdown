# Triage labels — 기본 매핑 후보

활성 대상: docs/agents/triage-labels.md. 기본값 사용 여부가 확인되기 전에는 적용하지 않는다.

| 스킬의 역할 | GitHub 라벨 후보 | 의미 |
| --- | --- | --- |
| needs-triage | needs-triage | 범위·정책 검토 필요 |
| needs-info | needs-info | 정보/답변 대기 |
| ready-for-agent | ready-for-agent | 범위와 완료 기준이 정리되어 AI 구현 가능 |
| ready-for-human | ready-for-human | 사용자 참여/직접 구현 필요 |
| wontfix | wontfix | 수행하지 않을 작업 |

triage 스킬은 설치되어 있다. GitHub에는 wontfix가 이미 있고 나머지 기본 라벨은 없다. 승인 후 기존 라벨을 재사용하고 부족한 것만 추가한다.

Wayfinder의 map/research/prototype/grilling/task 라벨은 작업 유형이고 이 표의 준비 상태와 별개다. HITL 질문은 답변 전 ready-for-agent로 표시하지 않는다. 정확한 미답변 이유는 이슈에 남긴다.
