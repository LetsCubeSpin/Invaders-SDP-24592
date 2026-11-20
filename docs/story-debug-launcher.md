# KAN-56 · 개발자용 Story 스테이지 런처

Windows의 **PowerShell 7 (`pwsh`)**에서 2차 프로젝트 루트로 이동한 뒤 실행한다. JDK 17 이상과 GUI를 표시할 수 있는 데스크톱 세션이 필요하다. `JAVA_HOME` 또는 PATH의 `javac`를 사용하며, 여러 버전을 설치했다면 `-JdkRoot`로 선택한다. 아래 설치 경로는 실제 JDK 위치에 맞춘다.

```powershell
pwsh -NoProfile -File .\scripts\run-story-debug.ps1 -JdkRoot 'C:\Program Files\Java\jdk-17'
pwsh -NoProfile -File .\scripts\run-story-debug.ps1 -JdkRoot 'C:\Program Files\Java\jdk-17' -Route TRUCE -Stage 7 -Difficulty EASY -Ship STANDARD
```

첫 명령은 **반복 테스트 도구**의 선택 창을 연다. Tab과 Shift+Tab으로 항목을 이동하고 방향키로 값을 고른다. 미리보기에서 현재 데이터의 보스, 웨이브 수, 스토리가 요구하는 장비를 확인한 뒤 **Start stage** 버튼에서 Space를 누른다. 기본값은 HYBRID / 1 / NORMAL / STANDARD다. 전투에서는 좌우 방향키로 이동하고 Space로 발사한다. G는 보유한 수류탄을 쓰고 M은 음소거를 전환한다.

전투가 끝나면 **Replay same settings / Change selection / Exit tool** 창이 나타난다. 같은 설정으로 바로 다시 실행하거나, 마지막 선택값이 유지된 선택 창에서 다른 루트·스테이지·난이도·기체를 고를 수 있다. 이전 전투의 생명·점수·아이템·설정은 다음 전투로 이월하지 않는다. 마지막 선택값은 관리 프로세스의 메모리에만 남으며 도구를 완전히 종료하면 초기화된다.

| 동작 | 반복 테스트 모드의 결과 |
| --- | --- |
| 클리어 또는 패배 | 현재 전투와 임시 저장 폴더를 정리하고 다음 동작 선택 창 표시 |
| 전투 창의 × | 해당 전투 JVM만 종료하고 다음 동작 선택 창 표시 |
| Replay same settings | 같은 네 선택값으로 새 JVM·새 임시 저장 폴더에서 시작 |
| Change selection | 마지막 네 선택값을 표시한 선택 창으로 돌아감 |
| 선택 창의 Cancel / Esc / × | 새 전투를 실행하지 않고 도구 전체 종료 |
| Exit tool 또는 다음 동작 창의 × / Esc | 도구 전체 종료 |
| 전투 JVM 실패 또는 시작·정리 오류 | 오류를 표시하고 사용자가 재실행·설정 변경·종료를 선택. 자동 재시도하지 않음 |

전투 중 Esc로 선택 창에 돌아오는 기능은 없다. 전투를 중단하려면 전투 창의 ×를 사용한다. 일반 게임으로 돌아가려면 도구를 종료하고 `engine.Core` 또는 `gradlew.bat run`을 별도로 실행한다.

**네 선택 인수를 모두 지정한 두 번째 명령은 기존의 일회성 실행을 유지한다.** 선택 창과 재실행 창 없이 한 전투만 실행하고 종료 코드를 전달한다. 일부 인수만 지정할 수는 없다. 사람이 사용하는 파일은 `run-story-debug.*`이며 `test-story-debug.*`는 오류 처리와 격리를 검증하는 자동 테스트다.

컴파일은 도구를 켤 때 한 번만 수행한다. `Compiling Story debug launcher...` 다음 `Starting Story debug launcher...`가 표시된다. 반복 중에는 컴파일 결과와 리소스만 재사용한다. 소스를 변경한 뒤 새 코드를 테스트하려면 도구를 종료하고 다시 켠다.

`JDK compiler not found` 또는 `JDK runtime not found`가 나오면 `-JdkRoot`가 `bin`의 부모인 완전한 JDK 설치 폴더인지 확인한다. 이 검사는 임시 세션 생성 전에 수행된다. 스크립트는 실행 정책을 변경하지 않는다. `AllSigned` 환경에서 서명 오류가 발생하면 해당 환경의 승인된 스크립트 서명·실행 절차가 필요하다. Windows PowerShell 5.1은 구문 검사만 통과했으며, 현재 검증 환경의 서명 정책 때문에 실제 실행 호환성은 미확인이다.

| 항목 | 선택 |
| --- | --- |
| 루트 | HYBRID (1.1.1), HUMAN_VICTORY (1.1.2), HOMECOMING (1.2.1), TRUCE (1.2.2) |
| 스테이지 | 1~7. 3과 7은 해당 보스 및 본래 스토리 적 편성과 함께 시작 |
| 난이도 | EASY (현재 유일한 난이도) |
| 기체 | STANDARD (현재 유일한 기체). 기체 기본 최대 생명으로 시작 |

## 시작 상태와 현재 장비 제한

매번 새 `StoryProgress`와 점수 0의 `GameState`를 만든다. 1에서는 선택을 적용하지 않고 2~3에서는 첫 번째 답만, 4~7에서는 두 답을 적용한다. `GameScreen.forStoryStage`가 이 진행 상태와 레벨에서 `StoryStageData`의 웨이브·적 행·소행성대·보스를 읽는다. `Core.applyDifficulty`와 `GameplayConfig`가 일반 Story와 같은 방식으로 난이도를 적용한다.

| 시점 | 스토리 데이터 | 현재 전투 |
| --- | --- | --- |
| Stage 1~3 | 선택 기체의 기본 탄종. Stage 3은 첫 선택에 따라 ARGUS 또는 NOX | 기체와 보스 적용. 모든 기체의 시작 탄종은 NORMAL |
| Stage 4~7 | 루트 탄종 및 수류탄 지급 여부. Stage 7은 루트의 최종 보스 | 현재 등록된 보스와 웨이브 적용. 시작 탄종 NORMAL, 시작 수류탄 0 |

이번 반복 실행 개선의 기준 main은 `d0895201e4e47efcbe75fa0d070b2864586e68c7`이다. VESPERA(BOSS_C)의 소환 전투는 구현되어 일반 Campaign Stage 7에서 사용된다. 그러나 `StoryRoute`는 여전히 HYBRID 최종 보스로 BOSS_A, TRUCE 최종 보스로 BOSS_B 및 탄종 MULTI를 반환한다. **BOSS_C 구현과 HYBRID 연결 완료는 별개다.** OBELISK 전투와 PIERCE는 이 main에 없다. 런처는 별도의 대체표를 두지 않는다. HOMECOMING의 수류탄 지급 플래그와 미리보기 탄종은 **데이터 기준**이다. 전투 도중의 통상 아이템 드롭은 기존 게임 규칙을 따른다.

기준 main에는 PR #9의 BOSS_D 처치 분류·최종 보스 보상과 엔딩 수집·저장·BGM·업적 API가 포함되어 있다. 엔딩 API의 본 게임 연결은 해당 담당자의 미완료 항목이다. 디버그 실행은 엔딩 API나 기록·보상 구독을 생성하지 않는다. 반복 실행 도구는 기준 main의 코드를 재사용하며 다른 브랜치의 미병합 변경을 가져오지 않는다.

## 저장 격리와 연동 지점

* 런처는 테스트 소스에만 있으며, 전용 스크립트가 필요한 Java 소스를 임시 폴더에 컴파일한다. Gradle 또는 다운로드 없이 실행할 수 있다. `engine.Core`의 일반 진입점과 배포 JAR에는 런처가 들어가지 않는다.
* 무인수 실행에서는 `StoryDebugController`가 선택과 재실행만 관리하며 직접 전투를 실행하지 않는다. `StoryDebugSession`이 매번 별도 JVM의 기존 `StoryDebugLauncher`를 네 인수로 실행한다. 전투 로직은 기존 `GameScreen.forStoryStage`를 그대로 사용한다. 네 인수 실행은 관리 루프 없이 기존 런처에 위임한다.
* 명시적 `-Dinvaders.storyDebug=true`와 실제 메모리 전용 preferences가 필요하다. Windows 레지스트리의 일반 난이도·키·오디오 설정을 사용하지 않는다. 반복 모드의 각 전투는 작업 디렉터리, `user.home`, `invaders.storageDir`, `java.io.tmpdir`가 같은 새 `stage-*` 폴더를 사용한다. 각 전투 종료 후 해당 폴더를 지우고, 도구 종료 시 스크립트가 컴파일 결과를 포함한 전체 임시 세션을 정리한다.
* 관리 프로세스가 종료 신호를 받으면 shutdown hook이 실행 중인 자식 JVM을 중단하고 해당 폴더를 정리한다. 전투 시작 실패·0이 아닌 종료·대기 중 인터럽트에도 정리를 수행한다. 파일 삭제 실패는 오류로 표시한다. OS 강제 종료나 전원 차단처럼 종료 훅과 `finally`가 실행되지 않는 상황까지 정리를 보장하지는 않는다. 이 경우 임시 파일이 남을 수 있으며, 다음 실행은 새 경로를 사용한다.
* 정상 전투 종료 후 전체 종료하면 코드 0이다. 전투 실패 직후 전체 종료하거나 설정 변경을 취소하면 해당 실패 코드를 유지한다. 사용자가 명시적으로 재시도해 성공한 후 종료하면 코드 0이며, 관리 루프의 대기가 인터럽트되면 코드 130이다.
* `StoryDebugLauncher.createScreen`은 활성 기록 서비스가 있는 JVM을 거부한다. `RunSession`은 null이며, 보상 구독·정산, 기록 서비스 생성·종료 훅, 점수 화면, 엔딩 기록을 호출하지 않는다. 디버그 전투의 일시적인 점수와 클리어 이벤트는 게임 화면 안에서 정상 발생한다.
* 일반 실행의 `-Dstory.route=TRUCE`는 `PlaceholderStoryScenes`가 질문에 답할 루트만 정한다. 디버그 선택 창도 이 경로를 재사용해 초기 루트를 읽는다. 기존 `-Dinvaders.debugKeys=true`는 전투 중 F2 방패 테스트 옵션이다.
* **박경준 및 무기 담당자**가 변경할 최소 지점은 `GameScreen.forStoryStage`와 `GameScreen.initialize()`다. 팩토리에서 `progress.weaponForStage(stageNumber, shipType.getBulletType())`와 `progress.givesGrenadesOnStage(stageNumber)`를 보관하고, 스토리 기체 생성 직후에 탄종·시작 수류탄을 한 번만 적용한다. 수류탄 개수는 담당자의 게임 규칙이 필요하다. 장비 적용 후 기존 `StoryGameScreenTest.everyShipUsesTheNormalWeapon`을 새 게임 규칙으로 갱신하고 런처의 현재 장비 안내도 갱신한다. 보스·무기 담당자가 새 타입과 루트 데이터를 연결하면 런처는 같은 팩토리와 데이터를 사용한다.
* 별도 전투 창에는 `Core.java` 변경이 필요 없다. 최신 main의 일반 `GameModeScreen`에는 Story 메뉴가 추가되었고, `Core`의 분기 15로 정상 Story를 시작한다. `Core.playStory`는 1단계에서 시작해 기록·보상을 준비하므로 개발자 즉시 진입에 재사용하지 않는다. 일반 Story 메뉴와 개발자 선택 창은 서로 다른 진입점이다. 메뉴의 우클릭/Esc 뒤로 가기와 BGM 연속 재생 변경은 main 그대로 사용한다.

### 담당자 연동 계약

| 담당 영역 | 입력과 출력 | 기대 동작 및 필요한 검증 |
| --- | --- | --- |
| 박경준: `GameScreen.forStoryStage` / `initialize` | 스테이지 번호, 선행 선택이 복원된 `StoryProgress`, `ShipType` → 실제 기체의 시작 장비 | `weaponForStage(stage, ship.getBulletType())` 및 `givesGrenadesOnStage(stage)`를 사용한다. Stage 1~3/4~7 경계, 4루트×7단계×3기체, 일반 Story/Debug 동등성을 검사한다. |
| 박경준 및 무기 담당자: 수류탄/루트 무기 | 지급 플래그와 합의된 지급 개수 → `Ship.setBulletType` / `addGrenades` 결과 | 수류탄 지급 개수와 스테이지 간 이월·상한 규칙을 먼저 합의한다. 초기화 때 한 번만 적용하고 다음 웨이브에서 재지급하지 않는다. PIERCE를 임의 탄종으로 치환하지 않는다. |
| 박경준·이성민: `StoryRoute` / 보스 연결 | HYBRID 최종 보스 데이터 → BOSS_C 실제 생성 | 일반 Campaign에서 검증한 BOSS_C를 Story에 연결한 뒤 HYBRID Stage 7 소환·처치·종료를 재검증한다. TRUCE의 OBELISK는 별도 구현·병합 후 검증한다. |
| KAN-56 | 선택 검증, 선행 선택 복원, 격리 JVM → 공용 Story 팩토리 | 테스트·설명 갱신은 자체 처리한다. 담당자의 소스나 시작 장비를 디버그 전용 하드코딩으로 대체하지 않는다. |

장비 지급 및 HYBRID 연결이 남아 있으므로 Jira 완료 조건 전체가 충족된 상태는 아니다. 일반 Story와 Debug가 같은 미완료 초기화를 사용한다는 사실을 장비 요구사항 충족으로 해석하지 않는다.

## Linux/macOS 실행

POSIX `sh`와 JDK 17 이상으로 실행한다. Gradle이나 추가 라이브러리 설치는 필요 없다. 기존 Windows PowerShell 스크립트와 **동일한 `engine.StoryDebugLauncher` 및 `GameScreen.forStoryStage`**를 사용한다.

```sh
sh ./scripts/run-story-debug.sh
sh ./scripts/run-story-debug.sh --route TRUCE --stage 7 --difficulty EASY --ship STANDARD
sh ./scripts/run-story-debug.sh --jdk-root '/path with spaces/to/jdk-17'
```

첫 명령은 Windows와 같은 반복 테스트 도구를 열고, 네 선택값을 모두 전달하면 일회성 전투에 바로 진입한다. 선택 창과 직접 실행 모두 게임 창을 표시할 **GUI 데스크톱 세션**이 필요하다. SSH·컨테이너·헤드리스 CI에서 실제 전투 화면을 띄우는 용도로 사용하지 않는다. 셸 입력 검증은 부분 선택, 누락/중복/알 수 없는 옵션과 잘못된 값을 거부한다. 루트·난이도·기체의 영문 대소문자는 구분하지 않으며, 스테이지는 `1`~`7` 한 자리 정수로 지정한다.

JDK 탐색 순서는 `--jdk-root` → `JAVA_HOME` → PATH의 `javac`이다. 명시한 JDK 경로가 잘못되면 다른 설치로 조용히 대체하지 않고 오류를 낸다. PATH의 `javac` 심볼릭 링크를 해석한 뒤 같은 `bin` 디렉터리의 `java`를 사용한다. macOS에서는 예를 들어 `/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`을 JDK 설치 경로로 지정할 수 있다. `javac -version`으로 17 이상인지 먼저 확인한다.

프로젝트·JDK·`TMPDIR` 경로는 따옴표로 전달한다. 공백, 따옴표, `$`, 세미콜론, 대괄호, 콜론이 포함된 경로를 문자열 명령 실행 없이 처리한다. 컴파일의 소스 경로는 `src:test`, 실행 클래스패스는 임시 세션 기준 `classes:resources`로 지정하므로 경로 자체의 콜론이 구분자로 해석되지 않는다. 리소스는 원본 `res`를 가리키는 심볼릭 링크로 읽는다. 줄바꿈이 포함된 디렉터리·스크립트·PATH 값은 지원하지 않는다.

Unix에서도 매 실행을 새 JVM으로 시작하며, `invaders.storyDebug=true`, 메모리 전용 `PreviewPreferencesFactory`, 독립된 `user.home`·`invaders.storageDir`·작업 디렉터리를 사용한다. 일반 파일 기반 Preferences와 게임 저장 데이터를 사용하지 않는다. `TMPDIR`(기본 `/tmp`) 아래 `mktemp -d`로 전용 세션을 만들고, 성공·컴파일 실패·실행 실패 후 해당 세션만 정리한다. 정리는 부모 경로·생성 이름·소유 마커를 확인하고, 세션이 심볼릭 링크로 교체되었으면 삭제를 거부한다. 프로젝트 리소스 링크의 대상은 삭제하지 않는다. `SIGKILL`이나 전원 종료처럼 셸 종료 처리가 실행되지 않는 경우에는 임시 폴더가 남을 수 있으며, 다음 실행은 별도의 새 폴더를 사용한다.

입력·JDK 검증 오류는 종료 코드 2, 컴파일·Java 실행 실패는 해당 프로세스의 종료 코드를 전달한다. 정상 종료는 0이다. 이 개선은 플랫폼 실행 방식만 다루며 기존 시작 무기·수류탄·보스·엔딩 연동의 완료를 의미하지 않는다. 개발 런처는 계속 `test/`에 있고 일반 실행 메뉴와 배포 JAR에는 포함되지 않는다.

## 검증

`StoryDebugControllerTest`는 재실행·설정 변경·마지막 선택 유지·취소·오류 후 명시적 재시도·인터럽트를 검증한다. 실제 자식 JVM에서 프로세스 ID, 작업/저장/임시 경로, 메모리 Preferences의 초기 상태를 확인하고 정상 종료·실패·관리 프로세스 종료 훅에서 자식 종료와 폴더 정리를 검사한다. GUI를 표시하지 않는 이 테스트와 사람이 선택 창·전투를 조작하는 검증은 구분한다.

```powershell
pwsh -NoProfile -File .\scripts\test-story-debug.ps1 -JdkRoot 'C:\Program Files\Java\jdk-17'
.\gradlew.bat test --tests engine.StoryDebugLauncherTest
.\gradlew.bat test --tests engine.StoryDebugControllerTest
.\gradlew.bat test smokeTest spotlessCheck
```

Linux/macOS에서는 다음 명령을 사용한다.

```sh
sh ./scripts/test-story-debug.sh
sh ./gradlew test --tests engine.StoryDebugLauncherTest
sh ./gradlew test smokeTest spotlessCheck
```

Unix 스크립트 검증은 가짜 JDK로 성공·인수 경계·예외 종료 코드·JDK 탐색·정리 보호를 검사하고, 실제 JDK로 컴파일 실패 및 헤드리스 Java 실패 경로를 검사한다. 실제 소스와 리소스를 공백·특수문자가 있는 경로에 복사해 컴파일·실행한다. GUI 성공은 가짜 실행기로만 검사하며, 실제 Java 실행에는 `java.awt.headless=true`를 적용해 창을 열지 않는다. 기존 Java 저장 데이터 격리 테스트는 그대로 유지한다. CI의 Ubuntu 작업은 Unix 스크립트 검증을, Windows 작업은 기존 PowerShell 검증을 실행하며 이후 전체 Gradle build를 수행한다. macOS 실환경 실행은 별도 확인이 필요하다.

252개 설정 조합에서 선택 경계, 보스, 기체·루트 탄종과 수류탄 조건을 확인한다. 격리 통합 테스트는 새 JVM마다 **4루트×7단계×3난이도×3기체 = 252개 실제 전투 화면**을 초기화하고 기체·난이도·초기 생명·스테이지 데이터를 확인한다. 보스 없는 단계는 모든 웨이브를 비우고 실제 진행 메서드로 마지막 웨이브에서만 클리어되는지 확인한다. 대표 최종 보스를 처치한 뒤 실제 루프의 클리어와 실행 종료도 검증한다. 하이스코어·재화·기록 파일에 미리 넣어 둔 표식 값과 파일 목록은 두 차례 실행 후 대조한다.

추가로 **4루트×7단계×3기체 = 84개**에서 일반 Story 팩토리와 Debug의 시작 탄종·수류탄 수를 비교한다. 이는 두 경로의 동등성 검사이며, NORMAL·수류탄 0을 올바른 게임 규칙으로 고정하는 검사가 아니다. 실제 시작 장비가 스토리 데이터의 요구를 충족하는지는 위 미완료 항목으로 별도 관리한다. 격리 자식 JVM에는 Gradle이 제공하는 JaCoCo 에이전트 인수를 전달한다.

별도 격리 테스트는 main의 일반 Campaign Stage 7을 통해 BOSS_C를 생성한다. 대기 시계만 만료시켜 페이즈별 A/B/C 소환 요청이 실제 `GameScreen`의 적으로 변환되는지, 보스 처치 시 소환 적 정리와 스테이지 종료가 이어지는지 검사한다. 이는 HYBRID가 BOSS_C로 연결되었다는 증거가 아니다. 위 장비 연동 전까지는 이 문서의 미완료 상태를 유지한 채 해석한다.

PowerShell 회귀 테스트는 불완전한 선택 인수, 잘못된 JDK 경로, 소수 스테이지 입력 거부, `javac` 종료 코드 2 전달, Java의 잘못된 스테이지 종료 코드 1 전달과 각 실패 후 임시 폴더 정리를 검사한다. 임시 경로에는 공백이 있으며 Java 실패 시나리오에서는 실제 선택적 컴파일도 수행한다. PowerShell 프로세스 자체가 강제 종료되면 임시 폴더가 남을 수 있다. 다음 실행에서는 새 폴더를 사용한다.
