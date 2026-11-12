# KAN-56 · 개발자용 Story 스테이지 런처

Windows PowerShell에서 2차 프로젝트 루트로 이동한 뒤 실행한다. JDK 17 이상이 필요하다. `JAVA_HOME` 또는 PATH의 `javac`를 사용하며, 여러 버전을 설치했다면 `-JdkRoot 'C:\Program Files\Java\jdk-17'`로 선택할 수 있다.

```powershell
.\scripts\run-story-debug.ps1
.\scripts\run-story-debug.ps1 -Route TRUCE -Stage 7 -Difficulty HARD -Ship SPEEDSTER
```

첫 명령은 선택 창을 연다. Tab과 Shift+Tab으로 항목을 이동하고 방향키로 값을 고른다. 미리보기에서 현재 데이터의 보스, 웨이브 수, 스토리가 요구하는 장비를 확인한 뒤 **Start stage** 버튼에서 Space를 누른다. Cancel 또는 Esc로 취소한다. 네 인수를 모두 지정하면 같은 설정을 선택 창 없이 바로 재현할 수 있다. 실행 중에는 좌우 방향키로 이동하고 Space로 발사한다. G는 보유한 수류탄을 쓴다. 창을 닫거나 한 스테이지를 클리어하거나 패배하면 디버그 실행이 끝난다.

| 항목 | 선택 |
| --- | --- |
| 루트 | HYBRID (1.1.1), HUMAN_VICTORY (1.1.2), HOMECOMING (1.2.1), TRUCE (1.2.2) |
| 스테이지 | 1~7. 3과 7은 해당 보스 및 본래 스토리 적 편성과 함께 시작 |
| 난이도 | EASY, NORMAL, HARD |
| 기체 | STANDARD, SPEEDSTER, TANK. 영구 업그레이드 없이 기체 기본 최대 생명으로 시작 |

## 시작 상태와 현재 장비 제한

매번 새 `StoryProgress`와 점수 0의 `GameState`를 만든다. 1에서는 선택을 적용하지 않고 2~3에서는 첫 번째 답만, 4~7에서는 두 답을 적용한다. `GameScreen.forStoryStage`가 이 진행 상태와 레벨에서 `StoryStageData`의 웨이브·적 행·소행성대·보스를 읽는다. `Core.applyDifficulty`와 `GameplayConfig`가 일반 Story와 같은 방식으로 난이도를 적용한다.

| 시점 | 스토리 데이터 | 현재 전투 |
| --- | --- | --- |
| Stage 1~3 | 선택 기체의 기본 탄종. Stage 3은 첫 선택에 따라 ARGUS 또는 NOX | 기체와 보스 적용. 모든 기체의 시작 탄종은 NORMAL |
| Stage 4~7 | 루트 탄종 및 수류탄 지급 여부. Stage 7은 루트의 최종 보스 | 현재 등록된 보스와 웨이브 적용. 시작 탄종 NORMAL, 시작 수류탄 0 |

기준 커밋 `0be8ba8`의 `StoryRoute`는 HYBRID 최종 보스로 BOSS_A, TRUCE 최종 보스로 BOSS_B 및 탄종 MULTI를 반환한다. VESPERA, OBELISK, PIERCE는 이 기준의 구현에 없다. 런처는 별도의 대체표를 두지 않는다. HOMECOMING의 수류탄 지급 플래그와 미리보기 탄종은 **데이터 기준**이다. 전투 도중의 통상 아이템 드롭은 기존 게임 규칙을 따른다.

## 저장 격리와 연동 지점

* 런처는 테스트 소스에만 있으며, 전용 스크립트가 필요한 Java 소스를 임시 폴더에 컴파일한다. Gradle 또는 다운로드 없이 실행할 수 있다. `engine.Core`의 일반 진입점과 배포 JAR에는 런처가 들어가지 않는다.
* 한 번에 한 스테이지를 새 JVM에서 실행한다. 명시적 `-Dinvaders.storyDebug=true`와 실제 메모리 전용 preferences가 필요하다. Windows 레지스트리의 일반 난이도·키·오디오 설정을 사용하지 않는다. 작업 디렉터리, `user.home`, `invaders.storageDir`는 매번 고유한 임시 경로다. 스크립트는 종료 시 그 경로를 정리한다.
* `StoryDebugLauncher.createScreen`은 활성 기록 서비스가 있는 JVM을 거부한다. `RunSession`은 null이며, 보상 구독·정산, 기록 서비스 생성·종료 훅, 점수 화면, 엔딩 기록을 호출하지 않는다. 디버그 전투의 일시적인 점수와 클리어 이벤트는 게임 화면 안에서 정상 발생한다.
* 일반 실행의 `-Dstory.route=TRUCE`는 `PlaceholderStoryScenes`가 질문에 답할 루트만 정한다. 디버그 선택 창도 이 경로를 재사용해 초기 루트를 읽는다. 기존 `-Dinvaders.debugKeys=true`는 전투 중 F2 방패 테스트 옵션이다.
* **박경준 및 무기 담당자**가 변경할 최소 지점은 `GameScreen.forStoryStage`와 `GameScreen.initialize()`다. 팩토리에서 `progress.weaponForStage(stageNumber, shipType.getBulletType())`와 `progress.givesGrenadesOnStage(stageNumber)`를 보관하고, 스토리 기체 생성 직후에 탄종·시작 수류탄을 한 번만 적용한다. 수류탄 개수는 담당자의 게임 규칙이 필요하다. 장비 적용 후 기존 `StoryGameScreenTest.everyShipUsesTheNormalWeapon`을 새 게임 규칙으로 갱신하고 런처의 현재 장비 안내도 갱신한다. 보스·무기 담당자가 새 타입과 루트 데이터를 연결하면 런처는 같은 팩토리와 데이터를 사용한다.
* 별도 전투 창에는 `Core.java` 변경이 필요 없다. 정식 메뉴 연동이 필요해지면 `Core.playStory`는 1단계에서 시작해 기록·보상을 준비한다는 점에 유의한다. 현재 `GameModeScreen`의 모드 선택은 Infinite/Campaign/Back이고 `Core`에는 Story용 분기 15가 따로 있다. 정식 화면에서 개발 도구를 호출하려면 담당자가 별도 JVM 실행 경계를 유지해야 한다. `feature/story_data`와 `feature/story_mode_play`의 확인된 커밋은 기준 main에 포함돼 있다.

## 검증

```powershell
.\scripts\test-story-debug.ps1
.\gradlew.bat test --tests engine.StoryDebugLauncherTest
.\gradlew.bat test smokeTest spotlessCheck
```

252개 조합에서 선택 경계, 보스, 기체·루트 탄종과 수류탄 조건을 확인한다. 격리 통합 테스트는 매번 새 JVM을 띄워 네 루트의 Stage 1·3·7에 실제 전투 화면을 생성하고, 기체와 난이도, 생성된 보스, 스테이지 데이터를 확인한다. 대표 최종 보스를 처치한 뒤 게임 루프가 클리어로 끝날 때까지 돌린다. 하이스코어·재화·기록 파일에 미리 넣어 둔 값과 파일 목록을 두 번의 실행 후 대조한다. 수동 장비 검증 시에는 위 표의 게임 구현 상태를 기준으로 해석한다.

PowerShell 회귀 테스트는 소수 스테이지 입력 거부, 컴파일러 종료 코드 전달, 공백이 있는 경로에서 실패 후 임시 세션 정리를 검사한다. PowerShell 프로세스 자체가 강제 종료되면 임시 폴더가 남을 수 있다. 다음 실행에서는 새 폴더를 사용한다.
