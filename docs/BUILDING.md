# Building and testing

Use **JDK 17, 21, or 25**, available through `JAVA_HOME` or `PATH`. Compilation, tests, and game launch use the JDK running Gradle; a separate JDK 17 installation is not required. The build targets Java 17 with `--release 17` and uses the included **Gradle 9.8.2 Wrapper**. Gradle, JUnit, JaCoCo, and Spotless are downloaded automatically; the first build needs internet access. No separate Gradle installation or IDE plugin is required.

## Build and run

From the repository root, run:

```powershell
# Windows PowerShell
.\gradlew.bat clean build
.\gradlew.bat run
```

```bash
# macOS / Linux
sh ./gradlew clean build
sh ./gradlew run
```

`build` compiles the game, runs the checks below, and creates the JAR and distributions under `build/`. Use `run` to open the game for manual testing.

## IntelliJ IDEA

1. Open the repository folder containing `settings.gradle` as a Gradle project.
2. Set **Project SDK** and **Gradle JVM** to the same JDK (17, 21, or 25), keeping the project language level at **17**. In **Settings → Build, Execution, Deployment → Build Tools → Gradle**, use the **Wrapper** and select **Gradle** for building and running tests.
3. Reload the Gradle project, then select **Invaders - Run** from the run configuration menu. The shared configurations in `.run/` use the same tasks as the command line.

Gradle configures `src/`, `res/`, and `test/`; manual source-root or classpath setup is unnecessary. If the shared configurations are not visible yet, run the corresponding task from the Gradle tool window after reloading.

## Tasks and reports

Run a task with `.\gradlew.bat <task>` on Windows or `sh ./gradlew <task>` on macOS/Linux.

| Task | IntelliJ configuration | Purpose |
| --- | --- | --- |
| `run` | Invaders - Run | Compile and launch the game |
| `build` | Invaders - Build | Compile, run `check`, and package the game |
| `check` | Invaders - Check | Run JUnit, standalone regression tests, Spotless, and combined coverage |
| `test` | Invaders - JUnit Tests | Run JUnit 5 tests |
| `smokeTest` | Invaders - Regression Tests | Run registered `main()` regression tests |
| `jacocoTestReport` | Invaders - Coverage | Run both test groups and generate JaCoCo reports |
| `spotlessCheck` | Invaders - Format Check | Check whitespace without changing files |
| `spotlessApply` | Invaders - Format | Apply whitespace cleanup |

- JUnit results: `build/reports/tests/test/index.html`.
- Combined JaCoCo coverage: `build/reports/jacoco/test/html/index.html` and `build/reports/jacoco/test/jacocoTestReport.xml`.
- Standalone regression results appear in the console and contribute to the combined coverage report.

Spotless currently handles trailing whitespace, final newlines, and LF line endings. It does not change Java indentation or import order. A broader Java formatting style remains open for course-wide discussion. Coverage is reported without enforcing a minimum percentage.

Test sources from `test/` and `tests/` are consolidated under `test/`, following their Java package directories. The build rejects Java files left under `tests/` so they cannot be silently skipped. JUnit 5 tests are discovered automatically. Standalone `main()` suites are listed in `smokeTestClasses` in `build.gradle`; register new standalone `*Test.java` suites there so `check` runs them.

To run one suite, use `test --tests "level.JsonLevelReaderTest"` for JUnit or `smokeLevelModelTest` for a standalone test. Use `tasks` to list all available tasks. Existing PowerShell scripts in `scripts/` also delegate to the Wrapper. Use these Gradle tasks instead of manual `javac` commands in older team documents.

## 개발 로그 형식

일반 게임의 콘솔과 `log` 파일, Gradle JUnit·스모크 테스트, 스토리 디버그
전투는 공통 로그 형식을 사용합니다. 머리말에는 현지 시간대 기준의 날짜와
24시간제 시각, 영문 로그 레벨, 로그 출처를 표시합니다.

```text
2026-11-23 18:34:05 [INFO] screen.GameScreen: Stage 7 failed.
```

로그 본문과 예외 스택 트레이스는 원래 내용을 표시합니다. 본문에 한글이 포함되면
출력하는 JVM과 이를 표시하는 콘솔이 같은 문자 인코딩을 사용해야 합니다.

Gradle JUnit·스모크 테스트 JVM이나 스토리 디버그 전투 JVM에
`-Djava.util.logging.config.file=<경로>`를 명시하면 해당 설정 파일이 우선합니다.
예를 들어 루트 로거와 콘솔 핸들러를 모두 `FINE`으로 설정하면 상세 로그가
표시됩니다. 설정 파일을 지정하지 않으면 공통 형식과 `INFO` 기준을 사용합니다.

### 한국어 Windows에서 검증

같은 JDK와 콘솔 설정을 유지한 채 변경 전후에 아래 명령을 실행합니다.

1. IntelliJ Gradle 실행 구성에서 `smokeBulletCleanupTest --console=plain`을 실행합니다.
   `Initializing ... ship formation` 로그의 날짜·로그 레벨을 확인합니다.
2. JUnit은 `test --tests "engine.StoryDebugBattleControlsTest" --info --console=plain`을
   실행하고 테스트의 표준 오류 출력에서 같은 형식인지 확인합니다.
3. `run`으로 일반 게임을 실행하고 콘솔과 프로젝트 폴더의 `log` 파일에서
   같은 형식인지 확인합니다.
4. PowerShell에서 아래 명령으로 스토리 디버그 전투를 실행합니다.
   `BossAttack`이나 `PlayerDamaged` 로그에서 같은 형식으로 표시되는지 확인합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-story-debug.ps1 `
  -Route HYBRID -Stage 7 -Difficulty EASY -Ship STANDARD `
  -EnemyHpMultiplier '4.0' -EnemySpeedMultiplier '0.25'
```

변경 전에는 기본 Java 로그 형식이 시스템 언어에 따라 `월`, `오후`, `정보`를
붙일 수 있습니다. 변경 후에는 위 실행 경로에 같은 형식이 표시되어야 합니다.
예외 로그가 나오면 메시지 아래에 스택 트레이스도 있는지 확인합니다.

### 검증 결과

- 기존 Windows PowerShell 실행에서 스토리 디버그 전투 로그의 한글 머리말이
  깨졌고, 콘솔을 UTF-8로 바꾸자 정상 표시됐습니다.
- 한국어 Windows에서 수정 버전 `5fc949c`의 스토리 디버그 전투, 일반 게임
  콘솔과 `log` 파일, JUnit 결과 XML의 표준 오류에 새 머리말이 표시되는 것을
  확인했습니다. 일반 게임의 파일 로그에서는 `FINE` 레벨도 확인했습니다.
- 한국어 Windows의 IntelliJ Gradle 실행 창에서 로그 머리말이 더 이상 깨지지
  않는 것을 확인했습니다. [IntelliJ 리뷰](https://github.com/LetsCubeSpin/Invaders-SDP-24592/pull/41#pullrequestreview-6020447639)
- Windows Java 17에서 수정 커밋 `d64186d`로 사용자 지정 설정을 적용했을 때
  `FINE` 로그와 지정한 포맷이 유지되고, 설정을 생략했을 때 공통 포맷이
  적용되는 것을 확인했습니다. [재검증 리뷰](https://github.com/LetsCubeSpin/Invaders-SDP-24592/pull/41#pullrequestreview-6022173943)
- Linux의 한국어 로캘에서는 Gradle 스모크 테스트에서 새 머리말과 예외 스택
  트레이스를 확인했습니다.

## CI and local files

[GitHub Actions](../.github/workflows/ci.yml) runs `clean build` with JDK 17 on Windows and Linux for pull requests and pushes to `main`. These two jobs verify the minimum supported Java version; JDK 21 and 25 are checked separately when changing JDK support or build tooling. Available test and coverage reports are kept as workflow artifacts for seven days, including on failed builds.

`clean` removes generated files under `build/`. Gradle `run` keeps local scores in the repository root; records and achievements use the existing `.space-invaders/` directory under the user's home. Build output, local saves, and personal IDE settings are excluded from Git.
