<!-- Plugin description -->
# Jira Branch Creator
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Version](https://img.shields.io/badge/version-0.1.0-blue)
![JetBrains Marketplace](https://img.shields.io/badge/jetbrains%20marketplace-compatible-orange)

Jira Branch Creator는 JIRA 이슈와 Git 워크플로우를 효과적으로 통합하는 IntelliJ IDEA 플러그인입니다. 이 플러그인을 사용하면 JIRA 이슈를 기반으로 Git 브랜치를 쉽게 생성하고, 이슈 번호를 포함한 커밋 메시지를 자동으로 생성할 수 있습니다.

## 주요 기능

- 유저에게 할당된 JIRA 이슈리스트에서 직접 Git 브랜치 생성
- 베이스 브랜치(예: main, develop)에서 새 브랜치 생성
- 커스텀 브랜치 프리픽스 지원 (feat/, bugfix/ 등)
- 자동 커밋 메시지에 JIRA 이슈 번호 포함
- 간편한 키보드 단축키

## 사용 방법

### 초기 설정

1. `Settings/Preferences` → `Tools` → `Jira Branch Creator` 선택
2. JIRA 연결 정보 입력:
  - JIRA URL (예: https://your-domain.atlassian.net)
  - 사용자명 (JIRA 계정 이메일)
  - API 토큰 (발급 버튼을 클릭하여 Atlassian 웹사이트에서 발급)
3. 베이스 브랜치 설정 (기본값: main)
4. 원하는 브랜치 프리픽스 선택

### JIRA 이슈에서 브랜치 생성하기

1. 키보드 단축키 `Cmd + Shift + J` (macOS) 또는 `Ctrl + Shift + J` (Windows/Linux) 누르기
2. 검색창에서 JIRA 이슈 검색 또는 선택
3. 선택한 이슈를 기반으로 브랜치가 자동 생성되고 체크아웃됨

### 커밋 메시지 자동 생성하기

1. 코드 변경 작업 완료 후 키보드 단축키 `Cmd + Ctrl + J` (macOS) 또는 `Ctrl + Alt + J` (Windows/Linux) 누르기
2. 커밋 메시지 입력 (JIRA 이슈 번호는 자동으로 추가됨)
3. 확인 버튼 클릭하여 커밋 완료

## 기능 상세

**Jira API 토큰 관리**
- 안전한 토큰 저장 및 관리
- 테스트 연결 기능으로 설정 검증
- 간편한 로그아웃 기능

**Git 브랜치 생성**
- 선택한 베이스 브랜치(main, develop 등)에서 새 브랜치 생성
- 설정된 브랜치 프리픽스 적용 (feat/, bugfix/ 등)
- JIRA 이슈 키를 포함한 브랜치 명명 규칙 (예: feat/DAP-999)

**Git 커밋 자동화**
- JIRA 이슈 번호가 포함된 커밋 메시지 자동 생성
- 변경사항 검증 후 커밋 진행
- 실패 시 상세 오류 메시지 제공

## 문제 해결

**Q: JIRA 연결이 되지 않습니다.**
A: API 토큰이 올바른지 확인하고, 설정 화면에서 '테스트 연결' 버튼을 클릭하여 자세한 오류 메시지를 확인하세요.

**Q: 브랜치가 생성되지 않습니다.**
A: Git 저장소가 제대로 초기화되어 있는지, 그리고 베이스 브랜치가 존재하는지 확인하세요.

## 라이센스

이 프로젝트는 [Apache License](LICENSE) 하에 배포됩니다.

---
*Jira Branch Creator는 Atlassian이 아닌 독립적인 개발자가 제작한 타사 플러그인입니다. JIRA 및 관련 상표는 Atlassian의 자산입니다.*
<!-- Plugin description end -->

## 설치 방법

### JetBrains 마켓플레이스에서 설치

1. IntelliJ IDEA 열기
2. `Settings/Preferences` → `Plugins` → `Marketplace` 선택
3. "Jira Branch Creator" 검색
4. `Install` 버튼 클릭

### 수동 설치

1. [Releases](https://github.com/your-username/jira-branch-creator/releases) 페이지에서 최신 버전의 ZIP 파일 다운로드
2. IntelliJ IDEA에서 `Settings/Preferences` → `Plugins` → ⚙️ → `Install Plugin from Disk...` 선택
3. 다운로드한 ZIP 파일 선택 후 설치

## 개발 정보

- 개발 언어: Kotlin
- 대상 플랫폼: IntelliJ IDEA 2021.2 이상
- 필수 플러그인: Git Integration

## 기여하기
이슈 제출, 풀 리퀘스트 환영합니다. 대규모 변경사항은 먼저 이슈를 열어 논의해주세요.
