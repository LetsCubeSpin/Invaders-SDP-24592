#!/bin/sh
# Headless launcher regression tests; no downloaded dependencies or GUI windows.
set -eu
repo=$(CDPATH= cd -P "$(dirname "$0")/.." && pwd -P)
original_java_home=${JAVA_HOME:-}
original_path=$PATH
temp_root=$(CDPATH= cd -P "${TMPDIR:-/tmp}" && pwd -P)
work=$(mktemp -d "${temp_root%/}/invaders-debug-test.XXXXXXXXXX")
cleanup() {
    status=$?
    trap - 0 HUP INT TERM
    case "${work##*/}" in invaders-debug-test.??????????) valid=yes ;; *) valid=no ;; esac
    if [ "$valid" = yes ] && [ "${work%/*}" = "${temp_root%/}" ] && [ ! -L "$work" ]; then
        rm -rf "$work" || { [ "$status" -ne 0 ] || status=1; }
    fi
    exit "$status"
}
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
die() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# Include shell metacharacters and the Unix classpath separator in real paths.
project="$work/project space 'quote; \$dollar [x]:colon"
jdk="$work/JDK space 'quote; \$dollar [x]"
sessions="$work/sessions space 'quote; \$dollar [x]:colon"
logs=$work/logs
mkdir -p "$project/scripts" "$project/src" "$project/res" "$project/test/engine" "$jdk/bin" "$sessions" "$logs"
cp "$repo/scripts/run-story-debug.sh" "$project/scripts/"
cp "$repo/test/engine/StoryDebugLauncher.java" "$project/test/engine/"
cp "$repo/test/engine/StoryDebugController.java" "$project/test/engine/"
printf 'keep project resources\n' > "$project/res/sentinel"
resource_hash=$(cksum < "$project/res/sentinel")
launcher=$project/scripts/run-story-debug.sh
export TEST_LOG_DIR="$logs" TEST_PROJECT="$project" TMPDIR="$sessions"

cat > "$jdk/bin/javac" <<'COMPILER'
#!/bin/sh
set -eu
if [ "${1:-}" = -version ]; then printf 'javac %s.0.1\n' "${MOCK_VERSION:-17}"; exit 0; fi
[ "$(pwd -P)" = "$TEST_PROJECT" ] || exit 91
printf '%s\n' "$@" > "$TEST_LOG_DIR/compiler.args"
exit "${MOCK_COMPILE_STATUS:-0}"
COMPILER
cat > "$jdk/bin/java" <<'RUNTIME'
#!/bin/sh
set -eu
session=$(pwd -P)
printf '%s\n' "$@" > "$TEST_LOG_DIR/java.args"
printf '%s\n' "$session" > "$TEST_LOG_DIR/session"
[ "$1" = '-Dinvaders.storyDebug=true' ] || exit 92
[ "$2" = '-Djava.util.prefs.PreferencesFactory=shop.PreviewPreferencesFactory' ] || exit 93
[ "$3" = "-Duser.home=$session" ] || exit 94
[ "$4" = "-Dinvaders.storageDir=$session" ] || exit 95
[ "$6" = -cp ] && [ "$7" = 'classes:resources' ] || exit 96
[ "$8" = engine.StoryDebugController ] || exit 97
[ -d classes ] && [ -L resources ] && [ -f resources/sentinel ] || exit 98
if [ "${MOCK_REPLACE_SESSION:-0}" = 1 ]; then
    mv "$session" "$session.saved"
    ln -s "$TEST_PROJECT" "$session"
fi
exit "${MOCK_JAVA_STATUS:-0}"
RUNTIME
chmod +x "$jdk/bin/javac" "$jdk/bin/java"
export JAVA_HOME="$jdk" MOCK_VERSION=17 MOCK_COMPILE_STATUS=0 MOCK_JAVA_STATUS=0 MOCK_REPLACE_SESSION=0
cases=0
assert_clean() {
    for entry in "$sessions"/invaders-story-debug.*; do
        [ ! -e "$entry" ] && [ ! -L "$entry" ] || die "session was not cleaned: $entry"
    done
    [ "$(cksum < "$project/res/sentinel")" = "$resource_hash" ] || die 'project resources changed'
}
run_case() {
    label=$1 expected=$2; shift 2
    if sh "$launcher" "$@" > "$logs/output" 2>&1; then actual=0; else actual=$?; fi
    if [ "$actual" -ne "$expected" ]; then cat "$logs/output" >&2; die "$label: exit $actual, expected $expected"; fi
    assert_clean
    cases=$((cases + 1)); printf 'PASS: %s\n' "$label"
}
run_case 'picker, JAVA_HOME, success cleanup' 0
[ "$(wc -l < "$logs/java.args" | tr -d ' ')" = 8 ] || die 'picker received selection arguments'
run_case 'four selections and explicit JDK' 0 --jdk-root "$jdk" --route truce --stage 7 --difficulty easy --ship standard
printf 'TRUCE\n7\nEASY\nSTANDARD\n' > "$logs/expected"
tail -n 4 "$logs/java.args" > "$logs/selection"
cmp "$logs/expected" "$logs/selection" || die 'selection argument boundaries changed'
run_case 'explicit modifier arguments' 0 --route truce --stage 7 --difficulty easy --ship standard \
    --enemy-speed-multiplier 0.5 --starting-lives 15 --invincible ON --enemy-hp-multiplier 2.0
printf '%s\n' TRUCE 7 EASY STANDARD --invincible ON --starting-lives 15 --enemy-hp-multiplier 2.0 --enemy-speed-multiplier 0.5 > "$logs/expected"
tail -n +9 "$logs/java.args" > "$logs/selection"
cmp "$logs/expected" "$logs/selection" || die 'wrong modifier values or argument boundaries'
grep -Fx 'src:test' "$logs/compiler.args" >/dev/null || die 'wrong Unix source path'
grep -Fx 'test/engine/StoryDebugController.java' "$logs/compiler.args" >/dev/null || die 'wrong shared entry point'
run_case 'partial selections' 2 --route TRUCE
run_case 'missing option value' 2 --jdk-root
run_case 'empty selection value' 2 --route ''
run_case 'unknown flag' 2 --unsupported
run_case 'duplicate selection' 2 --route TRUCE --route TRUCE
run_case 'modifier without selections' 2 --invincible ON
run_case 'duplicate modifier' 2 --starting-lives 15 --starting-lives 1
run_case 'missing modifier value' 2 --enemy-hp-multiplier
run_case 'invalid route' 2 --route 'TRUCE; touch injected' --stage 7 --difficulty EASY --ship STANDARD
run_case 'invalid difficulty' 2 --route TRUCE --stage 7 --difficulty IMPOSSIBLE --ship STANDARD
run_case 'invalid ship' 2 --route TRUCE --stage 7 --difficulty EASY --ship UNKNOWN
for difficulty in NORMAL HARD; do
    run_case "retired difficulty $difficulty" 2 --route TRUCE --stage 7 --difficulty "$difficulty" --ship STANDARD
    grep -Fx 'Difficulty must be EASY.' "$logs/output" >/dev/null || die 'missing supported difficulty'
done
for ship in SPEEDSTER TANK; do
    run_case "retired ship $ship" 2 --route TRUCE --stage 7 --difficulty EASY --ship "$ship"
    grep -Fx 'Ship must be STANDARD.' "$logs/output" >/dev/null || die 'missing supported ship'
done
for stage in 0 8 3.5 -1 '1; exit 0'; do
    run_case "invalid stage $stage" 2 --route TRUCE --stage "$stage" --difficulty EASY --ship STANDARD
done
run_case 'missing JDK' 2 --jdk-root "$work/missing JDK"
mkdir -p "$work/compiler only/bin"
cp "$jdk/bin/javac" "$work/compiler only/bin/javac"
run_case 'missing java' 2 --jdk-root "$work/compiler only"
MOCK_VERSION=11; run_case 'JDK older than 17' 2; MOCK_VERSION=17
MOCK_COMPILE_STATUS=42; run_case 'javac status and cleanup' 42; MOCK_COMPILE_STATUS=0
MOCK_JAVA_STATUS=37; run_case 'java status and cleanup' 37; MOCK_JAVA_STATUS=0
JAVA_HOME="$work/invalid JAVA_HOME"
run_case 'explicit JDK overrides JAVA_HOME' 0 --jdk-root "$jdk"
# Exercise PATH symlink resolution and pairing java with the resolved javac.
mkdir "$work/path-bin"
ln -s "$jdk/bin/javac" "$work/path-bin/javac"
JAVA_HOME=; PATH="$work/path-bin:$original_path"
run_case 'PATH compiler symlink' 0
PATH=$original_path; JAVA_HOME=$jdk

# A replaced session symlink must never make cleanup delete the project.
MOCK_REPLACE_SESSION=1
if sh "$launcher" > "$logs/output" 2>&1; then actual=0; else actual=$?; fi
[ "$actual" -eq 1 ] || die 'unowned-session cleanup was not refused'
grep -F 'Refusing to remove' "$logs/output" >/dev/null || die 'missing cleanup refusal'
session=$(cat "$logs/session")
[ -L "$session" ] && [ -d "$session.saved" ] || die 'unexpected tampering fixture'
rm "$session"
rm -rf "$session.saved"
MOCK_REPLACE_SESSION=0
assert_clean; cases=$((cases + 1)); printf 'PASS: cleanup refuses replaced symlink\n'

# Real JDK checks. GUI creation is deliberately rejected by headless mode.
cp -R "$repo/src/." "$project/src"
cp -R "$repo/test/." "$project/test"
cp -R "$repo/res/." "$project/res"
JAVA_HOME=$original_java_home
export JAVA_TOOL_OPTIONS='-Djava.awt.headless=true'
unset JDK_JAVAC_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
launcher=$project/scripts/run-story-debug.sh
run_case 'real JDK direct mode fails headlessly and cleans' 1 --route TRUCE --stage 7 --difficulty EASY --ship STANDARD
grep -F 'HeadlessException' "$logs/output" >/dev/null || die 'runtime failed before the GUI boundary'
grep -F 'Route TRUCE   |   Stage 7   |   EASY   |   STANDARD' "$logs/output" >/dev/null || die 'shared Java selection did not run'
run_case 'real JDK modified direct mode reaches GUI' 1 --route TRUCE --stage 7 --difficulty EASY --ship STANDARD \
    --starting-lives 15 --enemy-hp-multiplier 2.0 --invincible ON --enemy-speed-multiplier 0.5
grep -F 'HeadlessException' "$logs/output" >/dev/null || die 'modified launch failed before GUI'
grep -F 'Invincible: ON   Starting lives: 15' "$logs/output" >/dev/null || die 'wrong player modifiers'
grep -F 'Enemy HP: 2.0x   Enemy speed: 0.5x' "$logs/output" >/dev/null || die 'wrong enemy modifiers'
run_case 'real JDK rejects out-of-range multiplier' 1 --route TRUCE --stage 7 --difficulty EASY --ship STANDARD --enemy-speed-multiplier 4.01
grep -F 'Enemy speed multiplier must be between 0.25 and 4.0.' "$logs/output" >/dev/null || die 'modifier was not rejected during parsing'
run_case 'real JDK picker fails headlessly and cleans' 1
grep -F 'HeadlessException' "$logs/output" >/dev/null || die 'picker did not reach the GUI boundary'
launcher=$project/scripts/run-story-debug.sh
printf 'deliberately invalid Java\n' > "$project/test/engine/StoryDebugLauncher.java"
run_case 'real javac compile failure and cleanup' 1
grep -F 'javac failed (1).' "$logs/output" >/dev/null || die 'did not exercise compilation failure'
printf 'PASS: %s Unix launcher cases; no GUI opened.\n' "$cases"
