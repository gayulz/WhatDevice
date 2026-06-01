# Structure — WhatDevice

> 자동 생성: `/auto setup` (2026-06-01)

## 디렉터리 구조

```
WhatDevice/
├── src/main/java/com/whatdevice/
│   └── BuildSite.java                  # 빌드 엔트리포인트 (522줄, 단일 클래스)
├── data/
│   ├── devices.json                    # clo4/apple_device_identifiers 원본 (퍼블릭 도메인)
│   └── overrides.json                  # 누락 최신 기기 보강 (직접 작성, 29개 항목)
├── templates/
│   ├── layout.html                     # 공통 레이아웃 (헤더/푸터/meta/다크모드 인라인 스크립트)
│   └── device.html                     # 기기 상세 본문 템플릿
├── content/                            # 가이드·정책 페이지 본문 조각 (5개)
│   ├── guide-identifier-basics.html
│   ├── guide-find-from-logs.html
│   ├── policy-terms.html
│   ├── policy-privacy.html
│   └── policy-contact.html
├── static/                             # dist/에 그대로 복사되는 정적 자산
│   ├── style.css                       # 238줄, CSS Custom Properties로 라이트/다크 토큰
│   ├── search.js                       # 73줄, 클라이언트 사이드 substring 검색
│   ├── theme.js                        # 12줄, 다크모드 토글 + localStorage
│   └── favicon.svg
├── dist/                               # 빌드 산출물 (.gitignore — 추적 안 함)
│   ├── index.html
│   ├── data.js                         # window.DEVICES[] 자동 생성, 163줄
│   ├── style.css / search.js / theme.js / favicon.svg / robots.txt / sitemap.xml
│   ├── device/                         # 기기 상세 페이지 160개 ({slug}.html)
│   ├── guide/                          # 가이드 2개
│   └── policy/                         # 정책 3개
├── docs/
│   ├── Cowork-build-spec.md            # AI 개발 명세
│   └── Device-id-tool-roadmap.md       # 배경, 큰 그림, 확장 로드맵
├── .github/workflows/
│   └── build.yml.disabled              # GitHub Actions 초안 (현재 비활성)
├── build.gradle                        # Gradle 빌드 설정
├── settings.gradle                     # Gradle 프로젝트 이름
├── gradlew, gradlew.bat                # Gradle Wrapper (jar는 미포함 — DECISIONS D-08)
├── gradle/wrapper/                     # Wrapper properties
├── README.md
├── DECISIONS.md                        # AI 자율 판단 결정사항 로그
├── ARCHITECTURE.md                     # 시스템 아키텍처
└── .autopus/                           # ADK 하니스 설정 (개발 도구)
    └── project/
        ├── product.md
        ├── structure.md
        ├── tech.md
        ├── workspace.md
        ├── scenarios.md
        └── canary.md
```

## 패키지 역할

### src/main/java/com/whatdevice/
유일한 패키지. `BuildSite` 단일 클래스가 모든 빌드 로직을 담당한다.

**책임 영역 (현재 단일 파일에 응집):**
- 데이터 로드·병합 (`loadMergedData`, `loadJsonObject`)
- 데이터 가공 (`buildDevices`, `groupByFamily`, `toSlug`, `toKorean`, `cleanName`)
- 템플릿 치환 (`generateDevicePages`, `generateContentPages`, `generateIndex`)
- 정적 자산 복사 (`copyStatic`)
- SEO 파일 생성 (`writeSitemap`, `writeRobots`, `writeDataJs`)

> 향후 분리 후보: `DataLoader`, `PageGenerator`, `SeoGenerator`. (ARCHITECTURE.md V1 참조)

## 엔트리포인트

| 종류 | 파일 | 비고 |
|------|------|------|
| 빌드 엔트리 | `src/main/java/com/whatdevice/BuildSite.java` → `main(String[] args)` | Gradle `application` 플러그인 `mainClass` |
| 웹 엔트리 | `dist/index.html` | 검색 인터페이스 + 가이드 링크 |
| 검색 데이터 | `dist/data.js` (자동 생성) | `window.DEVICES = [{i,n,k,s,c}, ...]` |

## 파일 통계

| 카테고리 | 파일 수 | 비고 |
|---------|--------|------|
| Java 소스 | 1 | BuildSite.java (522줄) |
| 템플릿 | 2 | layout.html, device.html |
| 콘텐츠 조각 | 5 | guide×2 + policy×3 |
| 정적 자산 (소스) | 4 | style.css, search.js, theme.js, favicon.svg |
| 데이터 파일 | 2 | devices.json, overrides.json |
| 빌드 산출물 (dist/) | ~170 | 기기 페이지 160 + 가이드 2 + 정책 3 + 인덱스 + 정적 자산 |
| 메타 문서 | 3 | README.md, DECISIONS.md, ARCHITECTURE.md |
| Git 추적 파일 | 24 | `git ls-files | wc -l` 기준 |

## 슬러그 규칙

기기 식별자 → URL 슬러그 변환은 `BuildSite.toSlug()`가 담당:
- `iPhone18,1` → `iphone18-1`
- `iPad14,8-A` → `ipad14-8-a`
- `Watch7,3` → `watch7-3`

규칙: 소문자화 → 비영숫자 문자를 모두 하이픈으로 치환 → 연속 하이픈 압축 → 양끝 하이픈 제거.

## 계열 그룹핑

`groupByFamily()`는 식별자의 메이저 숫자로 그룹을 만든다:
- `iPhone18,1`, `iPhone18,2`, `iPhone18,3` → `iPhone18` 계열
- 상세 페이지에서 "같은 계열 기기" 섹션에 노출

## 빌드 산출물 vs 소스

| 위치 | 추적 | 재생성 가능 | 비고 |
|------|-----|-----------|------|
| `src/`, `data/`, `templates/`, `content/`, `static/`, `docs/` | git | — | 소스 (편집 대상) |
| `dist/` | gitignore | `./gradlew run` | 빌드 산출물 (편집 금지) |
| `build/`, `.gradle/` | gitignore | gradle 자동 | Gradle 캐시 |

**현재 세션의 CWD `dist/`는 빌드 산출물이다.** 소스 수정은 반드시 상위 경로(`/Users/focusone/workspace/WhatDevice`)를 기준으로 한다.
