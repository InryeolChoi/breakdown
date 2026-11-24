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
5. PR의 최신 커밋에서 main Ruleset과 `Required CI`를 확인한다. 실패 시 같은 브랜치에서 재현·수정한다. UI PR은 Draft로 생성하고 검사 통과 후에도 사용자 시각 승인까지 Draft로 유지한다. 승인된 UI PR과 비UI PR은 필수 검사와 보호 규칙을 충족한 뒤 머지한다.
6. Epic 행에 PR, CI 링크, 테스트 증거와 남은 작업을 기록한다. 머지한 뒤 SHA와 이슈 종료를 확인한다. GHCR 게시와 운영 배포는 각각 실제 게시·배포 결과를 기록한다.
7. 운영 승인이나 실기기 검증이 남은 항목은 Epic에 `BLOCKED`와 재개 조건을 적는다. BG-01은 운영 승인과 실제 복구 검증 후 완료한다. UI 시각 승인이나 운영 승인 대기 중에도 충돌하지 않는 티켓은 계속 진행한다.

## UI 검토 증거

- BG-03과 BG-05는 실제 브라우저의 데스크톱과 모바일 뷰포트에서 캡처한 스크린샷을 PR에 첨부한다. 캡처 대상 커밋, 화면 크기, 실행한 사용자 흐름과 결과를 적는다. 모바일 뷰포트와 실기기 검증을 구분한다.
- BG-03은 기본 온기 테마에서 활동을 고르지 않고 머무를 수 있는 첫 화면, 테마 선택, 활동 진입과 복귀를 확인한다. [제품 결정과 범위](decisions.md#bg-03-비공개-베타-첫-화면)
- BG-05는 첫 발사, 5통 완료, 게임 오버, 재시작과 홈 복귀를 검증한다. 터치와 키보드를 모두 확인한다.

베타의 접속 범위나 일요일 방 운영 결정, 운영 서버·DB 변경, 자격 증명 설정, 신규 비용, 실제 운영 배포는 운영자 승인 항목이다. [릴리스와 복구 절차](beta-release.md)
