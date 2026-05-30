# DECISIONS — 자율 판단 기록

> Cowork가 `Cowork-build-spec.md`에 따라 자율 개발하며 내린 결정들을 기록한 문서.
> 명세에 명시되지 않았거나, 명세의 "막혔을 때 행동 지침"(단순·표준·되돌리기 쉬운 선택)을 적용한 부분을 남긴다.
> 작성일 기준: 2026-05-30.

## A. 명세 기본값 그대로 확정한 항목 (지시서 2번)

- 사이트명 **WhatDevice**, UI 한국어, 범위 **iPhone + iPad**, 다크모드 **지원**, **Java 21**, 문의 이메일 **gayulz@kakao.com**.
- 위 항목은 별도 지시가 없어 명세 기본값을 그대로 사용함.

## B. 보강 데이터(overrides.json)와 출처

clo4 원본(`devices.json`)은 iPhone 15 시리즈(iPhone16,2)·iPad 12.9 6세대(iPad14,6)까지만 있고
최신 기기가 누락되어 있었다. 아래 값을 `overrides.json`에 보강했다.

**1차 출처:** adamawolf의 표준 식별자 목록 gist (명세가 신뢰 소스로 명시) —
`https://gist.github.com/adamawolf/3048717`
**교차 확인:** everymac.com, appledb.dev 검색 결과.

### iPhone (명세 확정값 + 조사값)

| 기종명 | 식별자 | 근거 |
|---|---|---|
| iPhone 16 / 16 Plus / 16 Pro / 16 Pro Max | iPhone17,3 / 17,4 / 17,1 / 17,2 | 명세 4.3 확정값 |
| iPhone 16e | iPhone17,5 | adamawolf gist |
| iPhone 17 | iPhone18,3 | adamawolf gist |
| iPhone 17 Pro | iPhone18,1 | adamawolf gist |
| iPhone 17 Pro Max | iPhone18,2 | adamawolf gist |
| iPhone Air | iPhone18,4 | adamawolf gist |

### iPad (조사값, Wi-Fi/Cellular 변형 포함)

| 기종명 | 식별자 |
|---|---|
| iPad mini (A17 Pro, 2024) | iPad16,1 / iPad16,2 |
| iPad Air 11" (M2, 2024) | iPad14,8 / iPad14,9 |
| iPad Air 13" (M2, 2024) | iPad14,10 / iPad14,11 |
| iPad Pro 11" (M4, 2024) | iPad16,3 / iPad16,4 |
| iPad Pro 13" (M4, 2024) | iPad16,5 / iPad16,6 |
| iPad (A16, 11세대, 2025) | iPad15,7 / iPad15,8 |
| iPad Air 11" (M3, 2025) | iPad15,3 / iPad15,4 |
| iPad Air 13" (M3, 2025) | iPad15,5 / iPad15,6 |

- **정책:** 명세의 "확신 없으면 비워두라"에 따라, adamawolf 표준 목록에서 명확히 확인되는 값만 넣었다.
  불확실한 추측값은 추가하지 않음. 최종 사실관계는 사람이 검수하는 것을 전제로 함.

## C. 원본 데이터 1건 정정

- clo4 원본은 `"iPad (5th generation) Wi-Fi"`와 `"... Wi-Fi + Cellular"`를 **둘 다 `iPad6,12`**로 두는 오류가 있었다.
- adamawolf 목록 기준 iPad 2017(5세대)는 `iPad6,11`(Wi-Fi)와 `iPad6,12`(Cellular)로 나뉜다.
- 동봉한 `devices.json`에서 Wi-Fi를 `iPad6,11`로 정정. (식별자→기종 역방향 충돌 제거 효과도 있음)

## D. 설계·구현 판단

- **D-01 슬러그**: 식별자 기준으로 생성(`iPhone15,2`→`iphone15-2`). 범위 내 식별자 160개가 모두 유일해 페이지 충돌 없음.
- **D-02 관련 기기 묶음**: 마케팅 세대(이름) 대신 **식별자 major 계열**(예: `iPhone15,x`)로 묶음.
  이름 파싱은 X/XS/SE 등 예외가 많아 오분류 위험이 있으나, 식별자 계열은 하드웨어 기준이라 항상 정확하다.
  화면에는 "같은 식별자 계열 (iPhone15,x)"로 정직하게 표기.
- **D-03 한글 검색**: 기종명을 한글로 자동 변환한 별칭(`koreanName`)을 만들어 검색·표시에 사용
  (예: iPhone→아이폰, Pro→프로). 토큰 치환 방식의 **best-effort**이며 완벽한 번역은 아님. "참고" 표기로 노출.
- **D-04 레이아웃 단일화**: 모든 페이지를 `templates/layout.html` 하나로 감싸 헤더/푸터/메타/다크모드를 공유.
  서브경로 배포 대비 `{{BASE}}` 상대경로 토큰 사용(루트=`""`, 하위폴더=`"../"`).
- **D-05 다크모드**: 시스템 설정(`prefers-color-scheme`)을 기본으로 따르고 토글로 덮어씀.
  선택값은 `localStorage`에 저장, 렌더 전 인라인 스크립트로 깜빡임 방지.
- **D-06 분석/광고/Search Console**: 명세대로 **실제 ID 없이 주석 placeholder**만 삽입(layout.html).
- **D-07 배포 URL**: `BuildSite.java`의 `SITE_URL`을 `https://YOUR-USERNAME.github.io/whatdevice` placeholder로 둠.
  실제 배포 시 한 줄만 교체. canonical·sitemap·OG에 사용.
- **D-08 Gradle Wrapper jar 미포함**: 개발 환경(샌드박스)에서 `services.gradle.org`·Maven 접근이 차단되어
  바이너리 `gradle-wrapper.jar`를 받아 동봉할 수 없었다. 표준 래퍼 스크립트/설정은 포함했고,
  사용자가 `gradle wrapper`(1회) 또는 `gradle run`으로 빌드하도록 README에 안내. **되돌리기 쉬운** 선택.
- **D-09 의존성 버전 고정**: Jackson `2.17.2`, Gradle `8.10.2`로 핀. 재현성 확보용. 필요 시 상향 가능.
- **D-10 dist gitignore**: 결과물은 소스에서 항상 재생성되므로 커밋하지 않음(.gitignore). 미리보기용으로 로컬엔 생성됨.

## E. 빌드 검증 방법 (중요)

- 개발 환경에 **JRE 11만 있고 JDK(javac)가 없으며**, Gradle/Maven 호스트가 네트워크 차단되어
  이 환경에서는 `./gradlew run`을 직접 실행할 수 없었다.
- 대신 **동일한 `data/`·`templates/`·`content/`·`static/`을 읽어 `dist/`를 생성하는 충실한 레퍼런스 포팅(Python)**으로
  빌드 산출물을 생성하고 다음을 검증했다. 변환 규칙(필터/슬러그/한글/계열/정렬/치환)은 `BuildSite.java`와 1:1로 맞춤.
  - 생성물 166개 HTML(기기 160 + 메인 1 + 가이드 2 + 정책 3), sitemap URL 166개 일치.
  - 전 페이지 HTML 파서 통과(구문 오류 0).
  - `search.js`를 Node로 구동해 **양방향 검색**(식별자/영문명/한글명) 9개 케이스 모두 통과.
  - 미치환 플레이스홀더 0, 다크모드 배선/이메일 치환/favicon 복사 확인.
- **남은 검증(사용자 권장):** 실제 Mac에서 `./gradlew run`이 동일 산출물을 만드는지 1회 확인,
  그리고 브라우저에서 다크/라이트·모바일 반응형 육안 점검.

- **D-11 git 커밋**: 초기엔 개발 환경 파일시스템의 삭제 제약으로 git 실행이 막혔으나, 삭제 권한을
  얻은 뒤 **의미 단위 한국어 커밋 7개**를 직접 생성했다(chore→data→feat(build)→feat(ui)→content→ci→docs).
  `dist/`는 `.gitignore`로 추적 제외. 원격 연결·푸시는 사용자가 수행:
  `git remote add origin <URL> && git push -u origin main`.

## F. 데이터 정확성 일반 정책

- 부정확한 추측 데이터는 신뢰를 깎으므로 넣지 않는다(명세 9). 모든 데이터는 사람 검수 전제, 면책 문구 상시 노출.
