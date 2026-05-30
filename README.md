# WhatDevice

애플 기기 식별자(모델 코드)와 기종명을 **양방향**으로 변환·검색하는 한국어 정적 웹사이트입니다.
예) `iPhone15,2` ↔ `iPhone 14 Pro` ↔ `아이폰 14 프로`

- **프레임워크 없음**: 화면은 순수 HTML + CSS + 바닐라 JS.
- **서버·DB 없음**: Java(Gradle) 빌드 스크립트가 데이터를 읽어 정적 HTML(`dist/`)을 한 번에 생성.
- **데이터**: [clo4/apple_device_identifiers](https://github.com/clo4/apple_device_identifiers)(퍼블릭 도메인) + 누락 보강용 `data/overrides.json`.
- **범위(MVP)**: iPhone + iPad (총 160종). 카테고리는 코드 상수로 분리되어 있어 확장이 쉽습니다.

---

## 폴더 구조

```
WhatDevice/
├── data/
│   ├── devices.json        # clo4 원본 (동봉)
│   └── overrides.json      # 누락/최신 기기 보강 (직접 작성)
├── templates/
│   ├── layout.html         # 공통 레이아웃(헤더/푸터/메타/다크모드)
│   └── device.html         # 기기 상세 본문 템플릿
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
   - 자동화 초안은 `.github/workflows/build.yml.disabled` 에 있습니다(현재 **비활성**).
4. 배포 후 Google Search Console에 사이트를 등록하고 `sitemap.xml`을 제출합니다.

### 분석·광고 (선택, 현재 placeholder)

`templates/layout.html` 의 다음 항목들은 주석 처리된 **placeholder**입니다. 실제 ID 발급 후 교체·주석 해제하세요.

- Google Search Console 소유권 확인 메타태그
- Google Analytics(GA4) 스니펫 — `G-XXXXXXXXXX`
- Google AdSense 스니펫 — `ca-pub-XXXXXXXXXXXXXXXX`

각 페이지의 `.ad-slot` 영역이 광고 자리입니다.

---

## 데이터 갱신 방법

1. 최신 원본이 필요하면 `data/devices.json`을 clo4 저장소에서 다시 받아 덮어씁니다.
2. 원본에 빠진 최신 기기는 `data/overrides.json`에 `"기종명": "식별자"` 형태로 추가합니다.
   - 추가 시 **신뢰할 수 있는 출처를 확인**하고, 불확실하면 넣지 마세요(`DECISIONS.md` 정책 참고).
3. `./gradlew run` 으로 재빌드합니다.

---

## 면책

비공식 참고용 데이터입니다. 출처: clo4/apple_device_identifiers. 본 사이트는 Apple Inc.와 제휴 관계가 없습니다.
