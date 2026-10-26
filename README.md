# WhatDevice

애플 기기 식별자(모델 코드)와 기종명을 **양방향**으로 변환·검색하는 한국어 정적 웹사이트입니다.
예) `iPhone15,2` ↔ `iPhone 14 Pro` ↔ `아이폰 14 프로`

- **프레임워크 없음**: 화면은 순수 HTML + CSS + 바닐라 JS.
- **서버·DB 없음**: Java(Gradle) 빌드 스크립트가 데이터를 읽어 정적 HTML(`dist/`)을 한 번에 생성.
- **데이터**: [adamawolf/apple_device_identifiers](https://gist.github.com/adamawolf/3048717) gist 미러(`data/devices.txt`)를 기본으로 사용합니다. CI가 주 1회 자동 동기화하며, 원본에 없는 출시 기기는 출처를 확인한 `data/devices-supplement.txt`로 보강합니다. 같은 식별자는 gist 값을 우선합니다.
- **범위**: iPhone · iPad · Watch · iPod · Simulator (2026-10-26 기준 총 236개 식별자). 같은 모델의 지역별 식별자는 각각 수록합니다. 카테고리는 `BuildSite.CATEGORIES` 상수입니다.

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

- **JDK 17** (`build.gradle`의 Java toolchain과 동일)
- **Gradle 8.x** (CI의 wrapper 버전은 8.10.2)
- 인터넷 연결 (최초 wrapper 사용 시 Gradle 배포본 다운로드). 프로젝트 외부 라이브러리 의존성은 없습니다.

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
./gradlew check run --no-daemon
```

> Gradle 설치(macOS): `brew install gradle`

### 2) wrapper 없이 시스템 Gradle로 바로 실행

```bash
gradle check run --no-daemon
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
원본·보강 파일을 합친 고유 식별자 수와 생성된 페이지 수 일치)과 `gradle check`를 통과해야 반영됩니다.

- 지금 당장 반영하려면: Actions 탭에서 `Sync, Build & Deploy` 를 `sync=true` 로 수동 실행합니다.
- `data/devices.txt` 는 gist 미러입니다. **수기로 항목을 추가하면 다음 동기화에 덮어써집니다.**
  원본에 없는 기기는 `data/devices-supplement.txt`에 출처와 확인일을 함께 기록합니다. 이 파일은 자동 동기화로 덮어쓰지 않습니다.
- 두 파일은 식별자를 키로 합치며 gist가 우선합니다. gist에 같은 식별자가 추가되어도 페이지와 검색 결과가 중복되지 않습니다.
- 보강 데이터 확인: 제품명·출시는 [Apple](https://www.apple.com/kr/iphone/), Pro 식별자는 [DeviceKit](https://github.com/devicekit/DeviceKit/blob/master/Source/Device.generated.swift), Duo 및 지역별 식별자는 [UFADE](https://github.com/prosch88/UFADE/blob/main/ufade/irecv_devices.py)를 대조했습니다.
- 2026-10-26 추가 항목은 다음과 같습니다. iPhone 17·17 Pro·17 Pro Max·Air·17e·16e는 기존 미러에 이미 포함되어 있습니다.

| 식별자 | 기종명 | 구분 |
|---|---|---|
| `iPhone19,2` | iPhone 18 Pro | 기본 모델 |
| `iPhone19,3` | iPhone 18 Pro Max | 미국형 |
| `iPhone19,7` | iPhone 18 Pro Max | 글로벌형 |
| `iPhone19,4` | iPhone Duo | 폴더블 모델 |

- 로컬 검증·재빌드는 `gradle check run --no-daemon`입니다. wrapper가 준비된 환경은 `./gradlew check run --no-daemon`을 사용합니다.
- `src/test/java/com/whatdevice/DeviceDataTest.java`가 최신 iPhone 식별자·지역별 모델·한글 표기를 외부 테스트 라이브러리 없이 검증합니다.

---

## 면책

비공식 참고용 데이터입니다. 출처: adamawolf/apple_device_identifiers (gist 3048717), DeviceKit·UFADE 보강 자료. 본 사이트는 Apple Inc.와 제휴 관계가 없습니다.
