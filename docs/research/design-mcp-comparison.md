# Breakground 디자인 MCP 선택

확인일: 2026-10-15. 사용자는 Stitch부터 사용하기로 선택했다. 계정 인증·MCP 등록·플러그인 설치는 아직 수행하지 않았다.

## 확인한 사실

Stitch는 공식 원격 MCP를 제공한다. 화면 생성, 설명으로 수정, 변형안 생성 도구를 제공하며 API key 또는 OAuth로 인증한다. API key 방식은 Stitch 설정에서 발급하며 원격 endpoint는 https://stitch.googleapis.com/mcp 이다. 공식 페이지는 일반 검색 도구에서 본문이 비어 브라우저로 공개 문서를 읽어 확인했다. [Stitch 공식 MCP 문서](https://stitch.withgoogle.com/docs/mcp/setup).

Google Labs의 SDK도 같은 endpoint와 화면 생성·HTML/스크린샷 취득 흐름을 설명한다. SDK나 서드파티 bridge 설치가 이번 선택의 필수 결과는 아니다. [Google Labs Stitch SDK](https://github.com/google-labs-code/stitch-sdk).

Figma 공식 원격 MCP는 디자인 읽기뿐 아니라 캔버스 생성·편집과 코드/화면에서 디자인으로 가져오는 기능도 제공한다. 'Figma MCP는 읽기 전용'이라는 예전 설명으로 비교하지 않았다. [Figma tools](https://developers.figma.com/docs/figma-mcp-server/tools-and-prompts/).

Figma는 공식 문서에 Codex 앱 연결 경로와 OAuth를 안내한다. 계정 plan/seat에 따라 읽기 도구의 사용량 제한이 달라진다. 사용자 계정 조건을 확인하지 않았으므로 무료/무제한 사용을 보장하지 않았다. [Figma 연결](https://developers.figma.com/docs/figma-mcp-server/remote-server-installation/), [사용량/접근 조건](https://developers.figma.com/docs/figma-mcp-server/rate-limits-access/).

## 현재 연결 경로 확인

Plugin Management 검색에서 Figma는 사용 가능한 미설치 도구로 반환됐다. Stitch는 검색 결과가 없었다. 검색 디렉터리 결과가 전체 외부 MCP 존재 여부를 뜻하지는 않는다. Stitch 공식 문서·Google Labs 자료에서 별도의 원격 MCP 경로를 확인했다.

이 조사에서 플러그인 제안·설치·권한 변경, Google/Figma 로그인, API key 생성/조회, 프로젝트 업로드는 하지 않았다. 인증된 실제 도구 목록이나 계정 quota는 아직 확인하지 않았다.

## 선택 이유

지금은 기존 Figma 파일을 구현하는 단계보다, 탁 트인 창문이 보이는 라운지라는 느낌을 구체적인 시안으로 비교하는 단계다. 설명→화면 생성→변형안을 만들 수 있는 Stitch를 먼저 사용하자는 추천을 사용자가 승인했다. 이는 프로젝트 목적에 따른 판단이며 Figma가 새 디자인을 만들 수 없다는 의미가 아니다.

Figma는 공유·편집·컴포넌트 관리가 실제 필요해지면 다시 판단한다. 지금 두 도구를 모두 연결하지 않는다.

## 연결 단계의 완료 기준

1. 사용자 본인의 Stitch 접근 가능 여부와 사용량·요금 조건을 확인한다. 현재 조사로 가격/계정 제한은 확정하지 않았다.
2. 인증 방식과 저장 위치를 정한다. 키를 사용하는 경우 채팅·GitHub·프런트 공개 환경값에 원문을 넣지 않는다.
3. 개발 AI의 원격 MCP 설정에 등록한다. Spring dependency나 서비스 런타임에 Stitch SDK를 추가하지 않는다.
4. 읽기 전용 조회로 인증과 실제 도구 접근을 확인한다. 설치/등록 시도만으로 연결 완료라고 기록하지 않는다.
5. 이후 승인된 디자인 요청으로 시안 생성·수정·변형을 진행한다. 첫 화면의 사용자 진입 흐름은 여전히 별도 결정이다.

이번 선택은 도구 확정이다. 계정 생성·권한 승인·키 발급·구독·외부 디자인 프로젝트 생성의 실행은 연결 단계에서 사용자 요청과 실제 화면을 확인한다.
