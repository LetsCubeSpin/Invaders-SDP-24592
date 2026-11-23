# Records & Achievements: Development and Verification

The local implementation covers team requirements 3.1–3.7. Its initial integration baseline combined course main `f476f5b` (2026-10-15) with team main `2d5ce71`, preserving Campaign mode, multiple waves, items, Infinite Mode, Settings, BGM, and audio caching. The formal acceptance criteria remain defined in [`teams/Chinese_can_fly.md`](../teams/Chinese_can_fly.md).

## Usage

- On the title screen, use Up/Down to select **Records & Achievements**, then press Space to open it. Records uses screen code 9, preserving the Infinite Mode shortcut I and screen code 8.
- Use Left/Right to switch between **Run Records**, **Run Details** and **Achievements**. On **Run Details**, Up/Down selects one of the top ten runs and shows its mode and play statistics; Infinite runs show "-" for the stage and waves, and runs saved by version 1 profiles say their details were not recorded. On **Achievements**, Up/Down scrolls when the list does not fit. Press Escape or Space to return to the menu.
- **Achievements** shows each entry with an icon: the bronze, silver or gold medal of the highest unlocked tier (`RankBadge.forTier`), a trophy for an unlocked single-tier achievement, or a dimmed trophy while locked. A bar under the name fills towards the next tier, with the progress at its right (for example `13/20`); a complete achievement shows when it was finished instead, and a hidden one shows neither until it unlocks. Up/Down scrolls when the list does not fit.
- When an achievement unlocks during gameplay, a one-line notice slides in from the right on the status strip, the 28-pixel band between the HUD separator (y 40) and the bonus ship's path (y 70) where no gameplay object goes. To make room, the bonus ship flies at y 70 instead of 60 and bosses appear at y 86 instead of 78, so a passing bonus ship does not overlap the boss and the boss stays above the enemy formation, which starts at y 100 or lower. Grenades and active buffs are shown as icons at the left of the same strip, with two players' icons separated by `|`, and the notice uses the room right of them. It is a green box with the tier's medal or a trophy, the name and tier, and `+N` while more notices wait, and its border flashes for a moment once it has slid in; there is no room for the condition, and a long name is cut short. Notices come one at a time in the order they unlocked and stay 3 seconds, 2 while one more waits and 1.4 while two or more wait, with the achievement sound as each starts. They are shared by every game screen of the run (`hud.AchievementToast`) and only count time while a game screen is shown, so a stage change does not cut one short; when the run ends, any notice not yet shown is left to the results screen. The results screen lists the achievements unlocked during that run, up to six rows; when more were unlocked, the last row reads `and N more`. A tiered achievement shows its tier in both places, for example `Exterminator (Bronze)`.
- The profile is stored at `.space-invaders/records-achievements.properties` under the user's home directory. Run records and achievement unlock times are saved together; personal bests are recalculated from the complete record history.

## Local Data Protection

The profile uses a versioned UTF-8 Properties file. Writes run on a separate thread: the system writes a temporary file, reads it back for validation, preserves the previous valid backup, and replaces the primary profile. When loading a corrupt profile, it preserves a copy of the corrupt data and attempts to restore a backup. If neither the primary profile nor its backup can be read successfully, the program reports the problem and disables automatic writes so the original data is not overwritten with an empty profile.

Abandoning a run does not create a completed-run record; achievements already unlocked are queued for saving immediately. Completed runs are saved asynchronously without blocking the results screen. Exiting through the menu, closing the window, or pressing Alt+F4 waits up to ten seconds for the background save queue. Timeouts or failures are reported. Forced process termination or power loss can still lose progress that has not yet been written.

After backup recovery, the page and log report the recovery and warn that recent progress may have been lost. Backup updates also use a validated temporary file before replacement. If the backup cannot be updated, the current write is aborted and the existing valid primary profile is preserved.

## Run Details (Profile Version 2)

Each run record also keeps its game mode and play statistics:

| Field | Source |
| --- | --- |
| `mode` | `CAMPAIGN`, `INFINITE` or `STORY`, set when Core starts the run. Custom levels and Time Attack leave no record. |
| `stage` | Highest stage started. Always 0 in Infinite Mode, which has no stages. |
| `waves` | Waves started over the whole run, across every stage. Always 0 in Infinite Mode. |
| `shots` | Sum of the bullet counts of `ShotFiredEvent`. |
| `hits` | Number of `BulletHitEvent`s: player bullets that hit an enemy ship (formation enemy, bonus ship, summoned minion or boss), whether or not the hit defeats it. Grenades are not bullets and send no hit. Accuracy is hits ÷ shots; a run without shots has 0% accuracy. |
| `timesHit` | Number of `PlayerHitEvent`s. |
| `bosses` | Number of `BossDefeatedEvent`s. |
| `items` | Number of `ItemUsedEvent`s. |

`RunSession.listenTo` subscribes the session to each screen's event bus and drops repeated deliveries of the same event ID. The counts can be read during play through `RunSession.getStats()` and `getAccuracy()`, so achievements such as an accuracy goal on a stage clear can use the same counts the record keeps.

Profiles are written as version 2. A version 1 profile still loads without losing records, achievements or endings; its records get the `UNKNOWN` mode and 0 for every statistic. The version 1 file stays valid, so the first version 2 write backs it up to `.bak` instead of quarantining it, and a version 1 backup can still restore a corrupt version 2 profile. During development, note that every branch shares the profile in the home directory, and a branch without version 2 support cannot read a version 2 profile. If the profile was saved only once as version 2, that branch restores the version 1 `.bak`, so runs and achievements recorded since then are not shown there. After a second version 2 save the `.bak` is version 2 as well, so that branch shows an empty profile and blocks writes. In both cases no file is overwritten, the profile reads normally again on a branch with version 2 support, and each start on the older branch adds `.corrupt-<id>` copies next to the profile. This goes away once every branch includes version 2.

## Achievements

Every achievement is defined in `records.Achievement`, and adding one means adding one constant there; `RecordsService` checks, saves and lists it without other changes. For example:

```java
public static final Achievement SHARPSHOOTER = add(define("sharpshooter",
        "Sharpshooter", "Finish a run with {target}% accuracy (50+ shots).")
        .run(Stat.ACCURACY_PERCENT, 50)
        .atLeast(Stat.SHOTS_FIRED, 50));
```

A definition has:

| Part | Meaning |
| --- | --- |
| ID | Saved in the profile. Never change the ID of an existing achievement. |
| Name and condition | `{target}` in the condition is replaced by the target of the tier shown. |
| Scope | `stage(...)`: one cleared Campaign or Story stage must reach the target, counting only that stage's values. `stageTotal(...)`: the values of every counted stage add up over the whole game. `run(...)`: one completed run must reach the target. `total(...)`: the values of every counted run add up over the whole game. Abandoned runs never count towards either total. |
| Stat | The value compared with the targets (`records.Stat`): enemies, active seconds, score, high scores beaten, stage, waves, shots, bullet hits, accuracy percent, times hit, bosses, items, completed runs, stages cleared, or endings seen. Stage number and accuracy cannot be added up, so they are not for `total` or `stageTotal`; score, completed runs and high scores are not counted per stage; stages cleared is for stage scopes only; endings seen is for `total` without requirements. A new kind of value is added there. |
| Targets | One to three increasing targets. With more than one they are the Bronze, Silver and Gold tiers, each unlocked and timed separately. |
| Requirements | `atLeast(stat, n)` and `atMost(stat, n)` limit which stages or runs count, for conditions such as "without losing a life" (`atMost(Stat.TIMES_HIT, 0)`) or "with at least 50 shots". Runs saved by version 1 profiles have no play statistics, so they never meet a requirement on them. |
| Hidden | `.hidden()`: the name and condition show as `???`, without progress, until the first tier unlocks. |

When `GameScreen` receives `LevelCompletedEvent`, `RunSession.stageCleared` creates a `StageClear` with the stage's shots, hits, lives lost, bosses, items, enemies, waves and active seconds, counted from the moment the stage started, and keeps it with the run. A `stage` achievement is checked against it at once (`RecordsService.stageCleared`), so it unlocks even if the run is abandoned later. The stage clears are saved with the run record only when the run is completed, so a `stageTotal` achievement, checked when a run is completed, never counts the stages of an abandoned run. Progress for both comes from the saved stage clears. A `run` achievement whose stat and requirements only use enemies defeated and active seconds is also checked during play, as Ace Pilot is; any other `run` or `total` achievement is checked when a run is completed.

Four examples show the parts: **Exterminator** has tiers for 100, 500 and 1000 enemies over every completed run, **Sharpshooter** needs 50% accuracy in a run with at least 50 shots, the hidden **Untouchable** needs a stage cleared without losing a life, and **Clean Sweep** has tiers for 10, 25 and 50 stages cleared without losing a life over every completed run.

The records page shows a `run` achievement's progress from the best counted run, and a `total` one's from every counted run added up. Both are rebuilt from the saved runs, so progress is not stored separately. When a run is completed, `run` achievements are checked against the best counted run, so a newly added achievement that an earlier run already reached unlocks then.

The existing six achievements (First Flight, First Contact, Ace Pilot, Still Flying, Top That and Every Ending) are single-tier definitions with their original IDs and conditions, so their unlock states and times load unchanged. Each saved achievement entry is one tier: an entry without a `tier` field is the first tier, so profiles saved before tiers existed load as they are, and single-tier achievements are still saved without one. Achievement IDs the build does not define are kept and saved again instead of rejecting the profile, so a profile written by a branch with more achievements still loads. A branch without this achievement system cannot do the same: it rejects achievement IDs it does not define, repeated IDs (as the tiers of one achievement are saved) and more achievement entries than it defines. Once the profile holds an unlock of a newer achievement or of a second tier, such a branch treats it as corrupt, as described for version 2 above: it restores the `.bak` if that is still readable, otherwise it shows an empty profile and blocks writes. No file is overwritten, and the profile reads normally again on a branch with this achievement system. A version 2 record can also store its stage clears as `record.N.stages` and `record.N.stage.M` (the nine `StageClear` values separated by commas); builds without them ignore these keys, and records without them have no saved stages, so the profile stays version 2.

## Gameplay Integration and Shared Interfaces

Campaign mode covers normal and special enemies defeated by bullets, Orbital Annihilator beams, or Chameleon Hull contact. Infinite Mode covers Zigzag enemies defeated by bullets. All of these paths connect to requirement 3.5 through `EnemyDefeatedEvent` and the shared `GameplayEventPublisher`. Each enemy retains a stable event ID, and the service ignores duplicate events. Shield absorption, escaped enemies, and player collisions in Infinite Mode do not count as enemy defeats. A defeated boss sends `EnemyDefeatedEvent` as well as `BossDefeatedEvent`, so it counts as an enemy defeat and as a boss; minions a boss summons do not count as enemy defeats. Future valid defeat paths must also use this interface.

The Records page can be opened from the title menu and returns to it, preserving Settings navigation and the Infinite Mode shortcut. Both modes display HUD unlock notifications and a summary of achievements unlocked during the run. Campaign victory titles, Infinite Mode replay navigation, existing item rendering, and BGM transitions are preserved. The achievement proposed by Octopus for completing a Campaign stage for the first time is not in the current achievement set; with stage achievements it can be added as one `stage` definition (see [Achievements](#achievements)).

## Verification

Run the following commands from the game repository root:

```powershell
powershell -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\build.ps1
powershell -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\test-records.ps1
.\gradlew.bat test --tests "records.*"
```

`test-records.ps1` runs the standalone records, gameplay integration and course wave-list checks. They cover field validation, run modes and statistics collected from the gameplay events, version 2 persistence across restarts, loading and converting version 1 profiles without losing data, version 1 backups, active-time accounting, duplicate completion handling, boundary conditions for the existing achievements, scrolling the achievement list, ranking order and the ten-entry limit, recovery after restart, backup-recovery warnings, preservation of original files when recovery is unavailable, protection of the valid primary profile when backup writes fail, and event handling after a run has closed. The achievement definitions are tested by JUnit, which `test-records.ps1` does not run: `AchievementDefinitionTest` covers achievement progress, tiers, hidden entries, requirements, stage achievements, the example achievements and kept unlock times, and `AllEndingsAchievementTest` and `EndingSaveTest` cover Every Ending and saved endings.

Verification on 2026-11-23: `testRecords`, `smokeTest` and `test` passed with 158 Java source files, with 431 records checks, 49 gameplay integration checks, the course wave-list checks and 615 JUnit tests, 42 of them in `AchievementDefinitionTest`. `ItemStatusTest` covers the status strip icons and `AchievementToastTest` the notices: three unlocks at once shown in order with shorter times while others wait, a stage change not cutting one short, and pixel checks that a notice never draws outside the strip or over the item icons; `TopLaneLayoutTest` checks that the strip, the bonus ship, the boss and the formation do not overlap. `RunBrowsingTest` covers the sorted and filtered run lists and the cumulative stats, and the records checks press every records page key through the screen and draw every tab, order and filter with 0, 1 and 12 runs.

The initial verification on 2026-10-15 ran with 60 Java source files, 104 records checks and 35 gameplay integration checks. Its checks call the actual collision handlers and cover normal and special enemies across the three defeat paths, repeated calls, shield absorption, Infinite Mode defeats/escapes/collisions, exclusion of countdowns and ending animations, abandonment, the 60-second achievement trigger, natural-death records, the I shortcut, and replay in both modes.

The tests also cover recovery of 80 queued records after a separate process exits, achievement-threshold rechecks on the final frame, and profile protection. Offscreen renders produced by the actual drawing code were inspected for rankings, achievements, failure results, Infinite Mode results, and a Campaign victory summary with the five achievements of that time. Outputs are stored in the ignored `out/` directory. These checks do not establish that every stage has been manually played through, and the gameplay difficulty of the achievement targets, from the 20-defeat and 60-second thresholds to the tiers of Exterminator and Clean Sweep and Sharpshooter's 50% accuracy, has not been evaluated.

## Compatibility Boundaries and Team Workflow

The implementation preserves Campaign victory/failure detection, the multi-wave constructor, difficulty settings, configured controls, audio caching, BGM, items, player assets, and Settings. A complete Campaign run and an Infinite Mode run ending in natural death share the same record history and rankings. Pressing Escape to abandon Infinite Mode preserves the upstream results screen and already unlocked achievements, but does not create a completed-run record. Both modes exclude startup waiting and ending animations from active play duration. The current course main branch has no gameplay pause; future pause support must call `RunSession.stopActiveSegment` and `RunSession.startActiveSegment`.

Main-branch synchronization was handled in a separate internal team PR. The seven requirement PRs were developed as a dependency chain from 3.1 through 3.7; later PRs included prerequisite commits until the earlier PRs were merged. All internal PRs targeted `clover0409/Invaders-SDP-24592:main`, using the course repository as a read-only upstream source for synchronization.
