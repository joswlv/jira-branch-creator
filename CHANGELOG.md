# Jira Branch Creator 릴리스 노트

## [Unreleased]

## [0.1.3] - 2025-09-16

- JIRA API V2 support termination

## [0.1.2] - 2025-04-03

- Replace deprecated API
- Bug fix Jira connection test button

## [0.1.0] - 2025-03-16

**기능 개요**

- **Jira API 토큰 관리**
   - 플러그인을 처음 실행할 때 Jira API 토큰이 설정되어 있지 않으면, 설정 패널로 이동하여 토큰 입력을 유도합니다.
   - 설정 패널에서 API 토큰을 입력하고 관리할 수 있으며, Jira API 토큰 발급 페이지로 연결되는 링크를 제공합니다.
   - `로그아웃` 버튼을 통해 저장된 토큰을 삭제할 수 있습니다.
   - `테스트 연결` 버튼을 통해 Jira 서버와의 연결을 확인할 수 있습니다.
- **Jira 이슈 검색 및 Git 브랜치 생성**
   - 단축키 Cmd + Shift + J (macOS) 또는 Ctrl + Shift + J (Windows/Linux)를 누르면 Jira 이슈 검색 모달 창이 나타납니다.
   - 사용자 Jira 계정에 할당된 이슈 목록을 불러와 검색할 수 있고, 키워드 입력 시 자동 완성 기능을 지원합니다.
   - 선택한 이슈를 기반으로 새 Git 브랜치를 생성하며, 설정된 브랜치 명명 규칙을 사용합니다.
   - 사용자는 설정에서 브랜치 이름 포맷을 변경하거나 새로운 포맷을 추가할 수 있습니다.
   - 브랜치를 생성하면 해당 브랜치로 자동으로 체크아웃합니다.
   - **개선된 기능**: 새 브랜치는 설정된 기본 베이스 브랜치(Base Branch)에서 생성됩니다.
- **Git 커밋 기능**
   - 단축키 Cmd + Control + J (macOS) 또는 Ctrl + Alt + J (Windows/Linux)를 누르면 커밋 메시지 입력 모달 창이 열립니다.
   - 사용자가 입력한 커밋 메시지 앞에 자동으로 [jira-이슈번호] 태그를 추가합니다.
   - git add . 명령을 실행한 후 커밋을 완료합니다.
   - **새로운 기능**: 변경사항이 없을 경우 커밋 대화상자가, 자동으로 표시되지 않으며 변경사항이 없다는 메시지가 표시됩니다.
   - **개선된 기능**: 커밋 명령 실행 결과를 확인하여 실패 시 상세한 오류 메시지를 표시합니다.
- **브랜치 프리픽스 설정**: 브랜치 프리픽스를 설정해 feat/DAP-999형태로 사용할 수 있습니다.
- **베이스 브랜치 설정**: 새 브랜치 생성 시 기준이 되는 베이스 브랜치를 설정할 수 있으며, 기본값은 'main'입니다.
- **Jira API 토큰 관리**: 설정 화면에서 Jira API 토큰을 입력 및 삭제할 수 있으며, 토큰 발급 페이지로 이동하는 링크를 제공합니다.
- **단축키 변경**: 각 기능을 실행하는 키보드 단축키를 사용자의 선호에 따라 변경할 수 있습니다.
- Git 커밋 명령 실행 시 결과 확인 로직 추가로 커밋 실패 문제 해결
- 자동 커밋 시 변경사항 존재 여부 확인 로직 추가
- 브랜치 생성 시 베이스 브랜치를 기준으로 생성하는 기능 개선
- Jira API 연결 테스트 기능 추가로 설정 오류 디버깅 용이성 향상

[Unreleased]: https://github.com/joswlv/jira-branch-creator/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/joswlv/jira-branch-creator/compare/v0.1.0...v0.1.2
[0.1.1]: https://github.com/joswlv/jira-branch-creator/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/joswlv/jira-branch-creator/commits/v0.1.0
