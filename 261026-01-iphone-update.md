**WhatDevice 최신 iPhone 업데이트 결과 — 2026-10-26**

`https://github.com/gayulz/WhatDevice`를 `/Users/focusone/workspace/WhatDevice`에 복제하고, 확인일 기준 출시된 iPhone 중 기존 데이터에 없는 3개 모델의 식별자 4개를 반영했다. 전체 기기 식별자는 232개에서 236개, iPhone 식별자는 63개에서 67개로 늘었다.

iPhone 16e, iPhone 17, iPhone 17 Pro, iPhone 17 Pro Max, iPhone Air, iPhone 17e는 기존 미러에 이미 포함되어 있어 그대로 유지했다. 이번 작업의 대상은 이 사이트가 제공하는 식별자·기종명 매핑과 한글 검색이며, 하드웨어 사양 데이터는 사이트의 기존 제공 범위에 없다.

| 추가 식별자 | 기종명 | 확인한 구분 |
|---|---|---|
| `iPhone19,2` | iPhone 18 Pro | 기본 모델 |
| `iPhone19,3` | iPhone 18 Pro Max | 미국형 |
| `iPhone19,7` | iPhone 18 Pro Max | 글로벌형 |
| `iPhone19,4` | iPhone Duo | 폴더블 모델 |

제품명과 출시는 [Apple iPhone 목록](https://www.apple.com/kr/iphone/), [iPhone 18 Pro 제품 사양](https://www.apple.com/kr/iphone-18-pro/specs/), [iPhone Duo 출시 발표](https://www.apple.com/newsroom/2026/09/apple-unveils-iphone-duo/)를 확인했다. Duo의 출시일은 2026-10-23이다.

머신 식별자 매핑은 [DeviceKit의 기기 정의](https://github.com/devicekit/DeviceKit/blob/master/Source/Device.generated.swift)와 [UFADE의 기기 정의](https://github.com/prosch88/UFADE/blob/main/ufade/irecv_devices.py)를 대조했다. Apple 제품 페이지는 제품명·출시의 근거이며, 머신 식별자의 근거는 두 공개 프로젝트의 기기 정의다.

기존 `data/devices.txt`는 수기 수정하지 않았다. 원본 gist를 직접 내려받아 비교한 결과 기존 파일과 바이트가 같았고, 신규 식별자는 아직 없었다. 해당 232개 식별자 미러의 SHA-256은 `6e27b629890c7ed299483bf612c5ffa2ba43848344f25baf96beff26d74a7c67`이다.

보강 데이터는 `data/devices-supplement.txt`에 출처와 확인일을 함께 기록했다. 빌드 시 원본 미러를 먼저 읽고 없는 식별자만 보강한다. 원본에 동일 식별자가 추가되면 원본 값을 우선하고, 상세 페이지와 검색 결과는 중복 생성하지 않는다. CI의 원본 동기화는 보강 파일을 덮어쓰지 않는다.

| 변경 파일 | 반영 내용 |
|---|---|
| `data/devices-supplement.txt` | 신규 식별자 4개와 근거 |
| `src/main/java/com/whatdevice/BuildSite.java` | 미러·보강 병합, Duo 한글 표기, 출처 안내 |
| `src/test/java/com/whatdevice/DeviceDataTest.java` | 최신 식별자 10개·동일 모델의 지역별 식별자·한글 표기 회귀 검사 |
| `build.gradle` | 추가 라이브러리 없이 `check`에서 회귀 검사 실행 |
| `.github/workflows/build.yml` | 배포 전 `check` 실행, 미러·보강의 고유 식별자 수로 산출물 검증 |
| `README.md`, `AGENTS.md` | 보강 경로, JDK 17, 검증 명령과 데이터 관리 규칙 |
| `content/policy-privacy.html` | 보강 자료 출처 안내 |

| 수행한 검증 | 결과 |
|---|---|
| 수정 전 회귀 검사 | 신규 `iPhone19,7` 부재로 실패하는 것을 확인 |
| `gradle check run --no-daemon` | JDK 17·Gradle 8.8에서 성공, 기기 페이지 236개 생성 |
| 원본 미러 보존 | `git diff --exit-code -- data/devices.txt` 통과 |
| CI의 실제 페이지 수 검증 구문 | 고유 식별자 236개와 상세 페이지 236개 일치 |
| 전체 정적 산출물 | HTML 248개, 사이트맵의 중복 없는 URL 247개, 내부 링크 6,337개 검사 통과 |
| 신규 기기의 노출 경로 | 홈·iPhone 카테고리·상세 페이지·검색 데이터·사이트맵 반영 확인 |
| 원본이 신규 식별자를 모두 포함하는 임시 환경 | 원본 값 우선, 중복 페이지 없음, CI 검증 통과 |
| 보강 파일 누락 | 빌드가 실패하여 불완전한 데이터 생성을 중단하는지 확인 |
| 데스크톱 브라우저 | 1280px에서 식별자·영문·한글·기존 기기·검색 결과 없음 등 검색 21개 사례 통과 |
| 모바일 브라우저 | 390px에서 한글 URL 검색, Pro Max 식별자 2개 검색, 검색어 삭제 후 목록 복구, 가로 넘침 없음 확인 |
| 상세 페이지 이동 | Duo 식별자·기종명·한글명·같은 계열 링크 확인 |
| `git diff --check` | 통과 |

브라우저 검증에는 `ego-browser`를 사용했다. 임시 테스트 데이터는 별도 임시 디렉터리에서 만들었으며, 원본 미러와 사용자 데이터는 변경하지 않았다. 검증용 브라우저 작업 공간은 종료했다. 생성 결과는 `dist/`이며 Git 추적 대상에 넣지 않는다.

Autopus의 `auto sync verify`는 상위 작업공간을 Git 저장소가 아닌 메타 루트로 인식하여 `unsupported topology` 오류로 실행하지 못했다. 프로젝트의 실제 Git 루트와 변경 파일을 Git으로 직접 확인했다. 이를 해결하기 위한 상위 작업공간 설정 변경은 수행하지 않았다.

원격 Git push, GitHub Actions 실행, GitHub Pages 배포와 운영 사이트 반영 확인은 수행하지 않았다. 로컬 실행에 시스템 Gradle을 사용했으며, 누락된 wrapper JAR를 생성하거나 도구 버전을 바꾸지 않았다. Gradle 9 관련 기존 폐기 예정 기능 경고는 이번 변경 범위에서 다루지 않았다.

재검증은 저장소 루트에서 다음 명령으로 수행한다.

```bash
gradle check run --no-daemon
```
