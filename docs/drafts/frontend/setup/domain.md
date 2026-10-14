# Domain docs — 적용 후보

활성 대상: docs/agents/domain.md. 사용자 확인 후 옮긴다.

단일 도메인 문서 체계를 유지한다. 프런트가 추가된다는 이유만으로 multi-context 구조를 만들지 않는다.

## 읽는 순서

1. 프로젝트 목적과 학습 분담: 루트 agents.md / claude.md.
2. 서비스 정책: docs/decisions.md.
3. DB 구조: docs/erd.dbml 및 적용된 Flyway/JPA 매핑.
4. 용어: 루트 GLOSSARY.md.
5. 해당 기능 ADR이 docs/adr에 있다면 함께 읽는다. 현재는 없으며 존재하지 않아도 진행한다.
6. 구현 인계: docs/devlog/NEXT.md 및 해당 명세·기능 이슈.

용어를 바꾸거나 새로운 정책이 기존 결정과 충돌하면 해당 문서를 인용하고 결정 확인을 먼저 받는다. UI 디자인이 서버 정책을 덮어쓰지 않는다. GitHub 이슈는 작업의 범위·결정 대화를 추적하며 확정 정책은 기존 decisions.md/ERD에 반영한다.

GLOSSARY/ADR은 필요한 용어나 결정이 실제로 확정될 때 보강한다. 이번 준비를 위해 빈 ADR 체계나 새로운 서비스 용어를 양산하지 않는다.
