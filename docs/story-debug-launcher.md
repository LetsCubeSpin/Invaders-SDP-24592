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

통합 기준 main은 `0412ff2f423d6c16079d7b8ebeb7a26f59e63c33`이다. VESPERA(BOSS_C)의 소환 전투는 구현되어 일반 Campaign Stage 7에서 사용된다. 그러나 `StoryRoute`는 여전히 HYBRID 최종 보스로 BOSS_A, TRUCE 최종 보스로 BOSS_B 및 탄종 MULTI를 반환한다. **BOSS_C 구현과 HYBRID 연결 완료는 별개다.** OBELISK와 PIERCE는 이 main에 없다. 런처는 별도의 대체표를 두지 않는다. HOMECOMING의 수류탄 지급 플래그와 미리보기 탄종은 **데이터 기준**이다. 전투 도중의 통상 아이템 드롭은 기존 게임 규칙을 따른다. 미병합 PR #9·#10·#13의 기능은 포함하지 않았다.

## 저장 격리와 연동 지점

* 런처는 테스트 소스에만 있으며, 전용 스크립트가 필요한 Java 소스를 임시 폴더에 컴파일한다. Gradle 또는 다운로드 없이 실행할 수 있다. `engine.Core`의 일반 진입점과 배포 JAR에는 런처가 들어가지 않는다.
* 한 번에 한 스테이지를 새 JVM에서 실행한다. 명시적 `-Dinvaders.storyDebug=true`와 실제 메모리 전용 preferences가 필요하다. Windows 레지스트리의 일반 난이도·키·오디오 설정을 사용하지 않는다. 작업 디렉터리, `user.home`, `invaders.storageDir`는 매번 고유한 임시 경로다. 스크립트는 종료 시 그 경로를 정리한다.
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

## 검증

```powershell
.\scripts\test-story-debug.ps1
.\gradlew.bat test --tests engine.StoryDebugLauncherTest
.\gradlew.bat test smokeTest spotlessCheck
```

252개 설정 조합에서 선택 경계, 보스, 기체·루트 탄종과 수류탄 조건을 확인한다. 격리 통합 테스트는 새 JVM마다 **4루트×7단계×3난이도×3기체 = 252개 실제 전투 화면**을 초기화하고 기체·난이도·초기 생명·스테이지 데이터를 확인한다. 보스 없는 단계는 모든 웨이브를 비우고 실제 진행 메서드로 마지막 웨이브에서만 클리어되는지 확인한다. 대표 최종 보스를 처치한 뒤 실제 루프의 클리어와 실행 종료도 검증한다. 하이스코어·재화·기록 파일에 미리 넣어 둔 표식 값과 파일 목록은 두 차례 실행 후 대조한다.

별도 격리 테스트는 main의 일반 Campaign Stage 7을 통해 BOSS_C를 생성한다. 대기 시계만 만료시켜 페이즈별 A/B/C 소환 요청이 실제 `GameScreen`의 적으로 변환되는지, 보스 처치 시 소환 적 정리와 스테이지 종료가 이어지는지 검사한다. 이는 HYBRID가 BOSS_C로 연결되었다는 증거가 아니다. 위 장비 연동 전까지는 이 문서의 미완료 상태를 유지한 채 해석한다.

PowerShell 회귀 테스트는 소수 스테이지 입력 거부, `javac` 종료 코드 2 전달, Java의 잘못된 스테이지 종료 코드 1 전달, 공백이 있는 경로에서 각 실패 후 임시 폴더 정리를 검사한다. Java 실패 시나리오에서는 실제 선택적 컴파일도 수행한다. PowerShell 프로세스 자체가 강제 종료되면 임시 폴더가 남을 수 있다. 다음 실행에서는 새 폴더를 사용한다.
