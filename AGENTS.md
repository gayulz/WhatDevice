# WhatDevice 작업 지침

## 프로젝트 목적

Apple 기기 식별자와 기종명을 양방향으로 검색하는 정적 한국어 사이트다. Java와 Gradle은 서버가 아니라 `dist/` 정적 산출물을 생성하는 빌드 도구로만 사용한다.

## 정본 문서와 데이터

- 프로젝트 설명과 실행 방법은 `README.md`를 먼저 읽는다.
- 개발 판단 기록은 `DECISIONS.md`를 확인한다.
- `data/devices.txt`는 외부 원본의 미러이므로 수동으로 항목을 추가·수정하지 않는다.
- 원본에 없는 출시 기기는 `data/devices-supplement.txt`에 출처·확인일과 함께 보강한다. 같은 식별자는 원본 미러 값을 우선하며, CI 동기화는 보강 파일을 덮어쓰지 않는다.
- 템플릿은 `templates/`, 정적 리소스는 `static/`, 빌드 진입점은 `src/main/java/com/whatdevice/BuildSite.java`다.

## 실행과 검증

- `build.gradle`과 `README.md`의 요구 환경은 JDK 17이다. 검증은 `gradle check run --no-daemon`으로 수행한다.
- wrapper JAR가 없는 상태라는 기존 기록이 있으므로 일반 빌드는 우선 시스템 Gradle `gradle run`을 검토한다. wrapper를 새로 생성하는 경우에는 사용자 승인과 `DECISIONS.md` 기록을 확인한다.
- 미리보기: `cd dist` 후 `python3 -m http.server 8000` (기존 메모리에는 8765도 기록되어 있어 다음 작업에서 포트를 하나로 정리한다.)
- CI는 데이터 동기화·빌드·페이지 수 검증·GitHub Pages 배포를 함께 수행한다. 데이터 갱신은 사용자가 명시적으로 요청한 경우에만 다룬다.
- 빌드 결과물 `dist/`는 생성 산출물이며 직접 편집하지 않는다.

## 금지사항

- 데이터 원본 미러를 수기로 고치지 않는다.
- 템플릿·빌드 로직과 무관한 대규모 리팩터링을 하지 않는다.
- 광고·분석 식별자와 배포 설정값을 답변이나 새 문서에 복사하지 않는다.
- 사용자 요청 없이 외부 배포·Git push·CI 수동 실행을 하지 않는다.

## 현재 상태

- `main` 브랜치의 운영 중인 정적 사이트다.
- 최근 변경은 불필요한 JSON 데이터와 Jackson 의존성을 제거하고 JDK 표준 라이브러리만 사용하는 방향이다.
