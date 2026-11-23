# Breakground 비공개 베타 작업 큐

[GitHub Beta Epic #12](https://github.com/InryeolChoi/breakdown/issues/12)의 BETA-TRACKER 체크리스트를 진행 상태의 기준으로 사용한다. 이 파일은 작업 번호와 고정 링크를 제공한다.

| 작업 | Issue | 선행 |
| --- | --- | --- |
| BG-00 CI와 이미지 릴리스, 자율 작업 큐 | [#13](https://github.com/InryeolChoi/breakdown/issues/13) | 없음 |
| BG-01 운영 환경과 복구 경로 | [#14](https://github.com/InryeolChoi/breakdown/issues/14) | BG-00 |
| BG-02 채팅 남용 방지와 신고 대응 | [#11](https://github.com/InryeolChoi/breakdown/issues/11) | BG-00 |
| BG-03 사내 카페 첫 화면 | [#5](https://github.com/InryeolChoi/breakdown/issues/5) | BG-00 |
| BG-04 채팅 UI와 빈 상태 | [#10](https://github.com/InryeolChoi/breakdown/issues/10) | BG-00 |
| BG-05 메일비행기 조작과 피드백 | [#15](https://github.com/InryeolChoi/breakdown/issues/15) | BG-00 |
| BG-06 베타 배포와 통합 검증 | [#16](https://github.com/InryeolChoi/breakdown/issues/16) | BG-00~05 및 운영 승인 |

## 한 티켓씩 진행

1. Epic과 해당 Issue 및 기존 PR을 읽는다. 닫힌 Issue, 기존 Issue, 열린 PR을 우선 재사용한다. 요구사항이나 선행 조건이 충족되지 않은 Issue는 사유를 기록하고 착수 가능한 다음 Issue를 고른다.
2. 최신 `origin/main`에서 `codex/BG-번호-작업명` 브랜치를 준비한다. 기존 변경과 겹치면 작업용 체크아웃을 분리한다. 사용자 파일과 로컬 전용 스킬 폴더를 유지하고 해당 티켓 파일만 스테이징한다.
3. 기존 테스트를 먼저 찾는다. 성공·실패·경계에서 관찰 가능한 동작을 검증하고, 버그는 재현 후 수정한다. 금지사항은 실패한 테스트의 삭제·완화, 임의 skip 추가, 우회 머지, main 직접 푸시 및 강제 푸시다.
4. 독립 검증에서 요구사항·diff·테스트 실행 결과를 대조한다. 구현과 관련된 파일만 커밋하고 작업 브랜치를 푸시한다. PR 본문에 `Closes #N`, 완료 조건과 실제 로컬 실행 결과를 적는다.
5. PR의 최신 커밋에서 `Required CI`와 브랜치 보호를 조회한다. 모두 통과하고 보호 규칙을 충족했을 때만 GitHub 자동 머지를 사용한다. 실패 시 같은 브랜치에서 로그를 읽고 재현·수정한다. 권한 부족이나 누락된 검사에는 Epic에 상태를 기록한다.
6. 머지 결과와 Issue 종료를 확인한 뒤 Epic 행에 PR·머지 SHA·Actions 링크를 남긴다. 다음 실행도 Epic에서 미완료 행을 찾는다. 이미지 릴리스와 운영 배포 결과는 머지 상태와 별도로 기록한다.

베타의 접속 범위나 일요일 방 운영 결정, 운영 서버·DB 변경, 자격 증명 설정, 신규 비용, 실제 운영 배포는 운영자 승인 항목이다. [릴리스와 복구 절차](beta-release.md)
