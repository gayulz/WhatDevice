# WhatDevice

애플 기기 식별자(모델 코드)와 기종명을 **양방향**으로 변환·검색하는 한국어 정적 웹사이트입니다.
예) `iPhone15,2` ↔ `iPhone 14 Pro` ↔ `아이폰 14 프로`

- **프레임워크 없음**: 화면은 순수 HTML + CSS + 바닐라 JS.
- **서버·DB 없음**: Java(Gradle) 빌드 스크립트가 데이터를 읽어 정적 HTML(`dist/`)을 한 번에 생성.
- **데이터**: [adamawolf/apple_device_identifiers](https://gist.github.com/adamawolf/3048717) gist 를 단일 권위 출처로 미러링(`data/devices.txt`). CI 가 주 1회 자동 동기화합니다.
- **범위**: iPhone · iPad · Watch · iPod · Simulator (총 232종). 카테고리는 `BuildSite.CATEGORIES` 상수라 확장이 쉽습니다.

---

## 폴더 구조

```
WhatDevice/
├── data/
│   └── devices.txt         # adamawolf gist 미러 (CI 주간 자동 동기화 — 수기 편집 금지)
├── templates/
│   ├── layout.html         # 공통 레이아웃(헤더/푸터/메타/다크모드)
│   └── device.html         # 기기 상세 본문 템플릿
├── .github/workflows/build.yml                   # 동기화 + 빌드 + Pages 배포
├── content/                # 가이드·정책 페이지 본문(HTML 조각)
├── static/                 # 그대로 dist/ 로 복사 (style.css, search.js, theme.js, favicon.svg)
├── src/main/java/com/whatdevice/BuildSite.java   # 빌드 스크립트
├── build.gradle / settings.gradle
├── dist/                   # 빌드 결과물 (배포 대상, git 추적 안 함)
├── DECISIONS.md            # 자율 판단 결정 기록
└── README.md
```

---

## 요구 환경

- **JDK 21** (다른 버전을 쓰려면 `build.gradle`의 `JavaLanguageVersion.of(21)` 숫자만 변경)
- 인터넷 연결 (최초 빌드 시 Gradle 배포본과 Jackson 의존성 다운로드)

---

## 빌드 & 로컬 실행

이 저장소에는 Gradle Wrapper 스크립트(`gradlew`, `gradlew.bat`)와 설정은 포함돼 있지만,
바이너리 `gradle-wrapper.jar`는 포함돼 있지 않습니다(생성 환경 제약. 자세한 내용은 `DECISIONS.md` D-08).
**최초 1회만** 아래로 wrapper를 완성한 뒤 사용하세요.

### 1) Gradle이 설치돼 있는 경우 (권장)

```bash
# 최초 1회: wrapper jar 생성
gradle wrapper --gradle-version 8.10.2

# 사이트 빌드 (dist/ 전체 생성)
./gradlew run
```

> Gradle 설치(macOS): `brew install gradle`

### 2) wrapper 없이 시스템 Gradle로 바로 실행

```bash
gradle run
```

빌드가 끝나면 `dist/` 에 전체 사이트가 생성됩니다.

### 로컬 미리보기

`dist/index.html`을 브라우저로 열어도 되지만, 상대경로/검색 스크립트는 간단한 정적 서버로 보는 것이 정확합니다.

```bash
cd dist
python3 -m http.server 8000
# http://localhost:8000 접속
```

---

## 배포 (GitHub Pages)

1. `BuildSite.java` 상단 상수 `SITE_URL`을 실제 배포 주소로 교체합니다.
   (예: `https://<사용자명>.github.io/whatdevice`)
2. `./gradlew run` 으로 다시 빌드합니다.
3. `dist/` 폴더의 내용을 GitHub Pages가 바라보는 위치에 올립니다.
   - 가장 단순한 방법: `dist/` 내용을 `gh-pages` 브랜치 루트에 푸시.
   - `main` 에 푸시하면 `.github/workflows/build.yml` 이 빌드·배포까지 자동으로 합니다(현재 **활성**).
4. 배포 후 Google Search Console에 사이트를 등록하고 `sitemap.xml`을 제출합니다.

### 분석·광고 (현재 운영 중)

`templates/layout.html` 에 실제 ID 가 들어가 있습니다.

- Google Search Console 소유권 확인 메타태그 · Google Tag Manager (`GTM-5N22XGPL`)
- 카카오 애드핏 — 슬롯 정의는 `BuildSite.AD_SLOTS`, 마크업은 `.ad-slot`
- 쿠팡 파트너스 추천 배너 — 페이지당 1개, 마크업은 `.coupang-slot`

정책 페이지(`policy/*`)에는 광고를 넣지 않습니다.

---

## 데이터 갱신 방법

**평소에는 손댈 일이 없습니다.** CI 가 매주 월요일 03:00 KST 에 gist 를 받아
변경이 있을 때만 커밋하고 배포까지 합니다. 동기화는 가드 3종(HTTP 성공 / 단말 수 비감소 /
생성된 페이지 수 일치)을 통과해야 반영됩니다.

- 지금 당장 반영하려면: Actions 탭에서 `Sync, Build & Deploy` 를 `sync=true` 로 수동 실행합니다.
- `data/devices.txt` 는 gist 미러입니다. **수기로 항목을 추가하면 다음 동기화에 덮어써집니다.**
  원본에 없는 기기가 필요하면 gist 에 먼저 반영하거나 별도 보강 경로를 새로 설계해야 합니다.
- 로컬 재빌드는 `./gradlew run` 입니다.

---

## 면책

비공식 참고용 데이터입니다. 출처: adamawolf/apple_device_identifiers (gist 3048717). 본 사이트는 Apple Inc.와 제휴 관계가 없습니다.
