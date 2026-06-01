# Tech Stack — WhatDevice

> 자동 생성: `/auto setup` (2026-06-01)

## 언어

| 영역 | 언어 | 버전 |
|------|------|------|
| 빌드 스크립트 | Java | 21 |
| 프론트엔드 | HTML5 + CSS3 + JavaScript (ES5 호환) | — |
| 데이터 | JSON | — |
| 메타·명세 | Markdown | — |

## 프레임워크 / 라이브러리

### 백엔드 (빌드 도구)

| 라이브러리 | 버전 | 역할 |
|-----------|------|------|
| Jackson Databind | 2.17.2 | JSON 파싱·직렬화 (devices.json, overrides.json) |

**의도적 미사용:**
- 템플릿 엔진 (Freemarker, Thymeleaf 등) — 단순 `String.replace()` 치환으로 충분, 의존성 증가 회피 (DECISIONS 명시)
- 빌드 도구 (Maven 등) — Gradle 단일 도구로 일관성

### 프론트엔드

| 항목 | 선택 | 비고 |
|------|------|------|
| CSS 프레임워크 | 없음 (커스텀 CSS) | Tailwind, Bootstrap 미사용 — 페이지 크기·로드 속도 최우선 |
| JS 프레임워크 | 없음 (바닐라 JS) | React, Vue, Svelte 미사용 |
| 빌드러 / 번들러 | 없음 | webpack, vite, esbuild 미사용 |
| 폰트 | 시스템 폰트 스택 + 모노스페이스 스택 | 웹폰트 미사용 |
| CDN 의존성 | 없음 | 모든 자산이 같은 도메인에서 서빙 |

## 빌드 도구

| 도구 | 버전 | 역할 |
|------|------|------|
| Gradle | 8.10.2 (wrapper) | 빌드 오케스트레이션 |
| Gradle `application` 플러그인 | — | `./gradlew run`으로 `main()` 실행 |

**Gradle Wrapper 주의:**
`gradle/wrapper/gradle-wrapper.jar` 파일이 저장소에 없다 (DECISIONS D-08). 최초 클론 시:
```bash
gradle wrapper --gradle-version 8.10.2   # gradle 미설치 시 brew install gradle 선행
```

## 빌드·실행 명령

| 명령 | 동작 |
|------|------|
| `./gradlew run` | `cleanDist` task 실행 → `BuildSite.main()` 실행 → `dist/` 재생성 |
| `./gradlew clean` | Gradle 캐시 정리 |
| `./gradlew tasks` | 사용 가능한 task 목록 |
| `python -m http.server 8000 -d dist` | 로컬 미리보기 (http://localhost:8000) |

## 빌드 파이프라인 (BuildSite.main 흐름)

```
1. loadMergedData()       devices.json + overrides.json 병합
2. buildDevices()         iPhone/iPad 필터 + slug + 한글명 + 정렬
3. groupByFamily()        familyKey(예: iPhone18)별 그룹화
4. copyStatic()           static/* → dist/ 복사
5. (template load)        layout.html, device.html 읽기
6. generateDevicePages()  기기 160개 × 상세 HTML 생성
7. writeDataJs()          dist/data.js (window.DEVICES) 생성
8. generateIndex()        dist/index.html 생성
9. generateContentPages() 가이드 2 + 정책 3 생성
10. writeSitemap()
11. writeRobots()
```

## 테스트

**현재 없음.** `src/test/` 디렉터리 미존재, JUnit 미설정.

DECISIONS E에 따르면 빌드 검증은 Python 레퍼런스 포팅(별도 스크립트)으로 수행되었다.

권장 우선 테스트 대상 (순수 함수):
- `toSlug(String)` — 슬러그 변환 규칙·충돌 회귀
- `toKorean(String)` — 한글 best-effort 변환 토큰 매칭
- `majorMinor(String)` — `iPhone18,1` → `(18, 1)` 파싱
- `cleanName(String)` — 모델명 정규화

## 아키텍처 패턴

### 패턴 1: 빌드 타임 정적 생성 (SSG)
모든 동적 처리는 빌드 타임에 끝난다. 런타임 서버 없음. 결과물은 HTML/CSS/JS만으로 동작한다.

### 패턴 2: 토큰 치환 템플릿
`{{IDENTIFIER}}`, `{{TITLE}}`, `{{BASE}}` 등 단순 placeholder를 `String.replace()`로 치환한다. 템플릿 엔진 의존성 없음.

### 패턴 3: 데이터 오버라이드 머지
원본(`devices.json`)을 직접 수정하지 않고 별도 파일(`overrides.json`)에 추가·교체 항목을 두어 머지한다. 원본 업스트림 업데이트와 충돌하지 않는다.

### 패턴 4: 클라이언트 사이드 검색
160개 항목을 `data.js`로 전체 전달 (gzip 후 수 KB) → 메모리 substring 검색. 서버·인덱서 불필요.

### 패턴 5: 다크모드 FOUC 방지
`layout.html` 내 인라인 `<script>`가 DOM 렌더 전 `localStorage`를 읽고 `html.dark` 클래스를 토글한다.

### 패턴 6: 경로 깊이 기반 BASE 치환
`{{BASE}}` 토큰을 페이지 위치에 따라 `""` (루트) 또는 `"../"` (하위 폴더)로 치환 → 서브패스 배포 호환.

## 배포

| 항목 | 현황 |
|------|------|
| 호스팅 | GitHub Pages (예정) |
| 배포 방식 | `dist/` → `gh-pages` 브랜치 푸시 (수동) |
| CI | `.github/workflows/build.yml.disabled` (비활성) |
| 활성화 방법 | 파일명을 `build.yml`로 변경 + `on:` 블록 주석 해제 |
| HTTPS | GitHub Pages 자동 제공 |
| 커스텀 도메인 | 미설정 |

## 외부 의존 데이터 출처

- `data/devices.json` ← [clo4/apple_device_identifiers](https://github.com/clo4/apple_device_identifiers) (퍼블릭 도메인)
- `data/overrides.json` ← 프로젝트 자체 관리

## 브라우저 호환성

타깃: Evergreen 브라우저 (Chrome/Edge 90+, Firefox 88+, Safari 14+)

필요 기능:
- `classList`, `localStorage`, `addEventListener` (ES5 이상)
- CSS Custom Properties (`--var`)
- CSS Grid + `auto-fill`

폴리필·트랜스파일 없음.
