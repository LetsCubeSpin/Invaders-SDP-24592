# Space Invaders

> Course: Software Development Practices (CSE2024) · 2026-2 · Section 24592

This IC-PBL course project extends and improves the Java [Space Invaders](https://en.wikipedia.org/wiki/Space_Invaders) clone from [RobertoIA/Invaders](https://github.com/RobertoIA/Invaders). Students apply software engineering practices through requirements engineering, team development, code review, and pull-request-based integration into a shared codebase.

### Members

| Student ID | Name | GitHub |
| --- | --- | --- |
| 2024098695 | Donghyun Kim | [331leo](https://github.com/331leo) |
| 2022039807 | Donghyeok Kang | [hye6k](https://github.com/hye6k) |
| 2023096417 | Doyu Lee | [ddy105](https://github.com/ddy105) |
| 2023088277 | Sehwan Cheon | [iamsehwan](https://github.com/iamsehwan) |
| 2024042333 | Hanheum Lee | [snowinsummer1](https://github.com/snowinsummer1) |
| 2025039570 | Seongmin Lee | [LetsCubeSpin](https://github.com/LetsCubeSpin) |
| 2022074220 | Seongmin Lee | [coldfarmer10](https://github.com/coldfarmer10) |
| 2023092542 | KyungJun Park | [rudwnssla123-ux](https://github.com/rudwnssla123-ux) |
| 2022055250 | Hyunjin Hwang | [taeyanggye88-sys](https://github.com/taeyanggye88-sys) |

### Overall Workflow

| Step | Process | Description |
|------|---------|-------------|
| 1 | Create Feature Branch | Create a separate branch for each feature and assign team members as needed. |
| 2 | Develop Feature | Implement the assigned feature on the feature branch. |
| 3 | Commit Changes | Commit changes in small, functional units with clear commit messages. |
| 4 | Open Pull Request | Open a Pull Request to merge the feature branch into `main`. Direct pushes to `main` are not allowed. |
| 5 | Code Review | 1–2 team members review the Pull Request. |
| 6 | Resolve Conflicts | If a merge conflict occurs, resolve it before merging. Use GitHub Issues for issue tracking and Slack for communication. |
| 7 | Merge | After approval, merge the Pull Request into `main` using the standard Merge strategy. |
| 8 | Delete Feature Branch | After the feature is completed and merged, delete the feature branch after team consultation. |

## Jira Link
https://letscubespin.atlassian.net/jira/software/projects/KAN/boards/1?filter=&groupBy=none

## Screenshots
| Title Screen | Game Screen | Score Screen |
| :---: | :---: | :---: |
| ![Space Invaders title screen](https://user-images.githubusercontent.com/69495129/136980139-7ad6adab-3f11-4711-b0a6-341080aa3361.png) | ![Space Invaders game screen](https://user-images.githubusercontent.com/69495129/136980236-c5d9ef85-f09a-47a7-b9d9-948f7b624002.png) | ![Space Invaders score screen](https://user-images.githubusercontent.com/69495129/136980681-93dcadaf-08cb-48d8-90c9-68c651a115c9.png) |

## Development
- Recommended IDE: IntelliJ IDEA
- Runtime requirement: Java 17 or later. Development JDKs: 17, 21, or 25; keep the language level at Java 17.
- Before implementing a feature, build and run the baseline game and analyze the relevant source code.

To run the baseline in IntelliJ IDEA, mark `src` as **Sources Root** and `res` as **Resources Root**, then run `engine.Core`. The resource directory must be on the runtime classpath: the game loads `graphics`, `scores`, and `font.ttf` from its root. Installing the font in your operating system does not make it available to the game.

### Story debug launcher (developer tool)

Launch any of the 28 Story combinations directly (4 routes × 7 stages, all on EASY with the STANDARD ship). The script compiles the developer tool with your JDK.

On **Windows**, install **PowerShell 7 (`pwsh`)** and **JDK 17 or later**, open a terminal in the repository root, and run:

```powershell
pwsh -NoProfile -File .\scripts\run-story-debug.ps1
```

The script uses `JAVA_HOME`, or `javac` on PATH. To select a JDK explicitly, add `-JdkRoot 'C:\Program Files\Java\jdk-17'`, replacing that example with your installation directory (the parent of `bin`). A desktop session is required.

1. Select a **Story route** and **Stage (1–7)**. Set **Invincible** (default OFF), **Starting lives** (1–99, default 4), **Enemy HP multiplier** and **Enemy speed multiplier** (defaults 1.0×). Both multiplier menus offer 0.25×, 0.5×, 1.0×, 2.0× and 4.0×. Click **Start stage**.
2. To stop a developer-tool battle, press **Esc** and select **개발자 도구로 복귀**, or press **Ctrl+Shift+D** in the battle window. The picker reopens with your previous settings. This works with invincibility on or off, including boss fights. An interrupted battle awards no clear or defeat and saves no campaign progress or score. Normal games have no launcher-return controls.
3. After a clear, defeat or battle-window close, **Replay same settings** restarts the same stage with all four modifiers. **Change selection** reopens all six controls at their previous values.
4. **Exit tool**, **Cancel** or closing the selection/result dialog exits the tool.

The tool compiles once per launch. Each battle starts in a fresh JVM with the selected starting lives, zero score, fresh enemies, memory-only preferences and its own temporary save directory. Left/Right moves, Space fires and G throws a grenade when available.

For a single battle, supply the route, stage, `EASY` and `STANDARD`. A clear, defeat or window close ends this one-shot run. Explicitly returning to the picker switches to repeat testing. Omitted modifiers use their defaults. This example starts TRUCE stage 7 with invincibility, 15 lives, double enemy HP and half enemy speed.

```powershell
pwsh -NoProfile -File .\scripts\run-story-debug.ps1 -Route TRUCE -Stage 7 -Difficulty EASY -Ship STANDARD -Invincible ON -StartingLives 15 -EnemyHpMultiplier 2 -EnemySpeedMultiplier 0.5
```

On Linux/macOS, run `sh ./scripts/run-story-debug.sh` for the picker. The equivalent single battle uses the same values.

```sh
sh ./scripts/run-story-debug.sh --route TRUCE --stage 7 --difficulty EASY --ship STANDARD \
  --invincible ON --starting-lives 15 --enemy-hp-multiplier 2 --enemy-speed-multiplier 0.5
```

Controls, CLI limits, movement measurements, save isolation and starting-equipment details are in the [Story debug guide](docs/story-debug-launcher.md).

## Attribution and Licensing

This course project is based on [RobertoIA/Invaders](https://github.com/RobertoIA/Invaders). The upstream repository does not currently declare an open-source license. This repository therefore does not assert a new open-source license over the upstream code or third-party assets. Any permission or licensing terms confirmed by the relevant rights holders should be documented here before redistribution beyond the permissions provided through GitHub.

The bundled `res/font.ttf` is **Space Invaders** by [kylemaoin](https://fontstruct.com/fontstructions/show/282751/space_invaders_9) (© 2010), shared under [Creative Commons Attribution-ShareAlike 3.0](https://creativecommons.org/licenses/by-sa/3.0/). It came from the [official FontStruct download](https://fontstruct.com/fontstructions/download/282751); its font data is unchanged, and its filename was changed from `space-invaders.ttf` to `font.ttf` to match the game's resource lookup. The accompanying [license](res/font-license/license.txt) and [readme](res/font-license/readme.txt) are included as required by that download. This license applies to the font, not to the upstream game code or other assets. Verify those assets' licenses separately before redistributing them.
