# Workspace — WhatDevice

> 자동 생성: `/auto setup` (2026-06-01)

## 루트 저장소 역할

WhatDevice는 **단일 product repo**다. 모노레포·서브모듈·meta-repo 패턴 아님.

- 루트 (`/Users/focusone/workspace/WhatDevice`) = 유일한 git 저장소
- 모든 소스, 빌드 스크립트, 데이터, 문서가 한 곳에 모여 있음
- 외부에서 가져온 데이터(`data/devices.json`)는 git submodule이 아닌 **파일 복사**로 관리

## 중첩 저장소 경계

**없음.** `find . -name .git -type d` 결과는 루트의 `.git` 하나뿐.

## 생성 / 런타임 산출물 경로

| 경로 | 분류 | git 추적 | 재생성 방법 |
|------|------|---------|-----------|
| `dist/` | 빌드 산출물 (정적 사이트) | ✗ | `./gradlew run` |
| `dist/data.js` | 자동 생성 JS 데이터 | ✗ | `BuildSite.writeDataJs()` |
| `dist/sitemap.xml` | 자동 생성 SEO | ✗ | `BuildSite.writeSitemap()` |
| `dist/robots.txt` | 자동 생성 SEO | ✗ | `BuildSite.writeRobots()` |
| `dist/device/*.html` | 자동 생성 기기 페이지 (160개) | ✗ | `BuildSite.generateDevicePages()` |
| `dist/guide/*.html`, `dist/policy/*.html` | 자동 생성 콘텐츠 | ✗ | `BuildSite.generateContentPages()` |
| `build/` | Gradle 빌드 캐시 | ✗ | gradle 자동 |
| `.gradle/` | Gradle 데몬 메타데이터 | ✗ | gradle 자동 |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle Wrapper jar | ✗ | `gradle wrapper --gradle-version 8.10.2` |

## 소스 오브 트루스 위치

| 영역 | 소스 위치 | 비고 |
|------|----------|------|
| 빌드 로직 | `src/main/java/com/whatdevice/BuildSite.java` | 단일 클래스 |
| 데이터 원본 | `data/devices.json` | 외부 프로젝트 미러 |
| 데이터 보강 | `data/overrides.json` | 직접 작성 — 신규 기기 추가 지점 |
| 페이지 레이아웃 | `templates/layout.html` | 헤더/푸터/메타태그 |
| 기기 상세 본문 | `templates/device.html` | |
| 가이드·정책 본문 | `content/*.html` | 5개 정적 페이지 본문 |
| 스타일 | `static/style.css` | dist로 복사됨 |
| 클라이언트 스크립트 | `static/search.js`, `static/theme.js` | dist로 복사됨 |
| 사이트 메타 상수 | `BuildSite.java` 상단 상수 블록 | `SITE_URL`, `CONTACT_EMAIL`, `CATEGORIES` |
| 개발 명세 | `docs/Cowork-build-spec.md` | AI 개발 지시서 |
| 자율 결정 로그 | `DECISIONS.md` | 빌드 도구·설계 선택 근거 |

## 추적 / 커밋 정책

### git 추적 대상
```
src/, data/, templates/, content/, static/, docs/
build.gradle, settings.gradle
gradlew, gradlew.bat
gradle/wrapper/gradle-wrapper.properties  (jar는 미포함)
.github/workflows/
.gitignore
README.md, DECISIONS.md, ARCHITECTURE.md
.autopus/   ← 본 문서 포함
```

### git 추적 제외 (`.gitignore`)
```
dist/
build/
.gradle/
.idea/
.vscode/
.DS_Store
*.iml
```

### 커밋 규칙
- 커밋 메시지 언어: 한국어 (project CLAUDE.md 정책)
- 커밋 메시지 형식: Lore 형식 권장 (`<type>(<scope>): <subject>` + trailers)
- AI/도구 사인오프 trailer 금지 (`Co-Authored-By: Claude`, `🐙 Autopus` 등)
- `dist/` 절대 커밋 금지 — `.gitignore` 명시

### 단일 단계 커밋
WhatDevice는 단일 저장소 — sync 시 2-Phase Commit 불필요. 모든 변경은 루트 저장소 1회 커밋으로 충분.

## 원격 저장소 상태
- 현재 원격 미연결 (DECISIONS D-11 — 사용자가 직접 GitHub에 push 예정)
- 푸시 후 `SITE_URL` 상수의 `YOUR-USERNAME` 부분을 실제 GitHub 사용자명으로 교체 필요

## CWD 주의사항

**현재 작업 디렉터리: `/Users/focusone/workspace/WhatDevice/dist`**

이 경로는 **빌드 산출물 디렉터리**다. 다음 규칙을 지킨다:
- 소스 수정은 반드시 상위 경로(`/Users/focusone/workspace/WhatDevice`) 기준
- `dist/` 내 파일을 직접 편집하면 다음 `./gradlew run` 호출 시 사라진다
- 모든 git 명령은 작동하지만 결과는 루트 저장소를 향한다
- 로컬 미리보기는 `python -m http.server 8000 -d dist` (현재 위치라면 `-d dist` 생략)

## 향후 워크스페이스 진화 가능성

현재는 단일 repo가 적절하다. 다음 조건이 모두 성립할 때만 분리 고려:
- 데이터 정의가 다른 프로젝트(예: 안드로이드 식별자 사이트)와 공유될 때 → `data/`를 별도 repo로 분리 + submodule
- 빌드 도구가 라이브러리화될 때 → `src/`를 Maven Central에 publish하는 별도 repo로 분리
- 콘텐츠 관리자가 코드를 보지 않고 가이드를 편집해야 할 때 → CMS 도입 + `content/` 분리

지금은 모두 해당 없음.
