# Architecture — WhatDevice

> 자동 생성: `/auto setup` (2026-06-01)
> 갱신: `/auto sync` 시 코드 상태에 맞춰 재작성

## 개요

WhatDevice는 **Apple 기기 식별자(예: `iPhone18,1`)로 기기를 찾아주는 정적 가이드 사이트**다.
런타임 서버 없이 빌드 시점에 모든 HTML을 생성하고 GitHub Pages에 정적 배포하는 SSG (Static Site Generation) 구조다.

핵심 설계 철학:
- **빌드 타임 정적 생성**: 런타임 의존성 0 — 브라우저만 있으면 동작
- **단일 빌드 도구**: Java 21 + Gradle만 사용, Node.js 도구 없음
- **외부 의존성 최소화**: Jackson Databind 1개만 사용, 프론트엔드는 순수 바닐라
- **검색 = 클라이언트 사이드**: 160개 기기 정도는 JS 메모리 검색으로 충분

## 도메인

### Domain 1: 빌드 (Build)
- **위치**: `src/main/java/com/whatdevice/BuildSite.java`
- **역할**: 데이터 로드 → 가공 → 템플릿 치환 → 파일 출력의 10단계 파이프라인
- **입력**: `data/devices.json` (오픈소스 원본) + `data/overrides.json` (직접 작성 보강 데이터) + `templates/*.html` + `content/*.html` + `static/*`
- **출력**: `dist/` 디렉터리에 완성된 정적 사이트

### Domain 2: 데이터 (Data)
- **devices.json**: clo4/apple_device_identifiers 프로젝트에서 가져온 식별자 → 모델명 매핑
- **overrides.json**: 원본에 누락된 최신 기기를 직접 추가한 보강 데이터 (현재 29개)
- **병합 정책**: overrides가 동일 식별자에 대해 devices를 덮어쓴다

### Domain 3: 콘텐츠 (Content)
- **기기 상세 페이지** (160개): 템플릿 기반 자동 생성
- **가이드 페이지** (2개): 식별자 기초, 로그에서 식별자 찾는 법
- **정책 페이지** (3개): 이용약관, 개인정보, 문의

### Domain 4: 프론트엔드 (Frontend)
- **검색** (`search.js`): 클라이언트 사이드 substring 검색, 80ms 디바운스
- **테마** (`theme.js`): 다크/라이트 토글, localStorage 영속화
- **스타일** (`style.css`): CSS Custom Properties로 라이트/다크 토큰 분리, 모바일 우선 반응형

## 레이어 의존성

```
┌─────────────────────────────────────────────┐
│  Build (Java)                                │
│  BuildSite.java                              │
│   ├─ loadMergedData()  ─── data/*.json      │
│   ├─ buildDevices()                          │
│   ├─ groupByFamily()                         │
│   ├─ copyStatic()      ─── static/*         │
│   ├─ generateDevicePages() ─ templates/*    │
│   ├─ writeDataJs()                           │
│   ├─ generateIndex()                         │
│   ├─ generateContentPages() ─ content/*     │
│   ├─ writeSitemap()                          │
│   └─ writeRobots()                           │
└──────────────────┬──────────────────────────┘
                   ▼
            dist/  (빌드 산출물)
                   │
                   ▼
         GitHub Pages (gh-pages 브랜치)
                   │
                   ▼
            ┌──────────────┐
            │  브라우저     │
            │  ├─ data.js  │  (window.DEVICES[])
            │  ├─ search.js│  (메모리 검색)
            │  ├─ theme.js │
            │  └─ style.css│
            └──────────────┘
```

## 의존성 맵

### Java 의존성 (build.gradle)
- `com.fasterxml.jackson.core:jackson-databind:2.17.2` — JSON 파싱 (유일한 외부 의존성)

### 외부 데이터 출처
- `data/devices.json` ← https://github.com/clo4/apple_device_identifiers (퍼블릭 도메인)
- `data/overrides.json` ← 프로젝트에서 직접 작성·관리

### 프론트엔드 의존성
- 없음. CDN, npm, Tailwind 모두 미사용. 시스템 폰트.

## 디렉터리 → 도메인 매핑

| 디렉터리 | 도메인 | git 추적 | 비고 |
|---------|-------|---------|------|
| `src/` | Build | ✓ | Java 빌드 소스 |
| `data/` | Data | ✓ | JSON 원본 + 보강 |
| `templates/` | Content (생성) | ✓ | layout.html, device.html |
| `content/` | Content (정적) | ✓ | 가이드·정책 본문 조각 |
| `static/` | Frontend | ✓ | 그대로 dist로 복사 |
| `docs/` | Meta | ✓ | 개발 명세, 로드맵 |
| `.github/workflows/` | Deploy | ✓ | Pages 배포 (현재 .disabled) |
| `dist/` | Build Output | ✗ | `.gitignore` — 재생성 가능 |
| `build/`, `.gradle/` | Build Cache | ✗ | Gradle 캐시 |

## 규칙 위반 (검토 필요)

### V1: 파일 크기 한도 초과
- `src/main/java/com/whatdevice/BuildSite.java` — **522줄** (한도 300줄 초과)
- 영향도: medium — 단일 클래스에 데이터 로드, 가공, 렌더링, SEO 생성이 모두 응집되어 있어 향후 기능 추가 시 분리 비용 증가
- 권장 분리:
  - `DataLoader` — JSON 병합·필터·정규화
  - `PageGenerator` — 템플릿 치환·페이지 출력
  - `SeoGenerator` — sitemap·robots·canonical
  - `BuildSite` — main + 파이프라인 조립

### V2: 테스트 부재
- `src/test/` 디렉터리 없음, JUnit 미설정
- `toSlug()`, `toKorean()`, `majorMinor()`, `cleanName()` 등 순수 함수는 단위 테스트가 매우 용이
- 권장: 슬러그 충돌 회귀 테스트와 식별자 파싱 엣지케이스 (`iPad14,8-A`, `Watch7,3` 등) 우선 추가

### V3: 배포 URL placeholder 미교체
- `BuildSite.java:43` `SITE_URL = "https://YOUR-USERNAME.github.io/whatdevice"` 그대로
- 영향도: high — 현재 빌드된 `dist/`의 canonical, og:url, sitemap.xml 모두 더미 URL을 가리킨다
- 배포 전 필수 교체 + 재빌드

## 빌드·실행

| 명령어 | 동작 |
|-------|------|
| `./gradlew run` | 전체 빌드 (cleanDist → main 실행 → `dist/` 재생성) |
| `./gradlew clean` | Gradle 캐시 정리 |
| `python -m http.server 8000 -d dist` | 로컬 미리보기 |
| (수동) `dist/` → `gh-pages` 브랜치 푸시 | 배포 |

## 배포 토폴로지

```
[로컬] WhatDevice/dist/   ──push──>   [GitHub] gh-pages 브랜치 root   ──CDN──>   [브라우저] https://{user}.github.io/whatdevice/
```

GitHub Actions 워크플로(`build.yml.disabled`)는 초안만 작성된 상태로, 활성화 시 `main` push → Gradle 빌드 → `dist/` → gh-pages 자동 배포가 가능하다.
