#!/bin/sh
# Development-only launcher. Uses the same Java entry point as run-story-debug.ps1.
set -eu

usage() {
    printf '%s\n' 'Usage: sh scripts/run-story-debug.sh [--jdk-root JDK_HOME]' \
        '       [--route ROUTE --stage 1..7 --difficulty DIFFICULTY --ship SHIP]' \
        'Omit all four selections to open the picker. Requires JDK 17+ and a desktop session.'
}
fail() { printf '%s\n' "$*" >&2; exit 2; }
newline='
'
case "$0${PATH:-}" in *"$newline"*) fail 'Newlines in script or PATH names are not supported.' ;; esac

jdk_root=${JAVA_HOME:-}
route= stage= difficulty= ship= seen= selections=0
while [ "$#" -gt 0 ]; do
    option=$1
    case "$option" in
        --help|-h) usage; exit 0 ;;
        --jdk-root|--route|--stage|--difficulty|--ship)
            [ "$#" -ge 2 ] && [ -n "$2" ] || fail "Missing value for $option."
            case " $seen " in *" $option "*) fail "Duplicate option: $option." ;; esac
            seen="$seen $option"
            case "$option" in
                --jdk-root) jdk_root=$2 ;;
                --route) route=$2; selections=$((selections + 1)) ;;
                --stage) stage=$2; selections=$((selections + 1)) ;;
                --difficulty) difficulty=$2; selections=$((selections + 1)) ;;
                --ship) ship=$2; selections=$((selections + 1)) ;;
            esac
            shift 2 ;;
        *) fail "Unknown option: $option. Use --help for usage." ;;
    esac
done
if [ "$selections" -ne 0 ]; then
    [ "$selections" -eq 4 ] || fail 'Specify all four selections, or omit all to open the picker.'
    route=$(printf '%s' "$route" | LC_ALL=C tr '[:lower:]' '[:upper:]')
    difficulty=$(printf '%s' "$difficulty" | LC_ALL=C tr '[:lower:]' '[:upper:]')
    ship=$(printf '%s' "$ship" | LC_ALL=C tr '[:lower:]' '[:upper:]')
    case "$route" in HYBRID|HUMAN_VICTORY|HOMECOMING|TRUCE) ;; *) fail 'Invalid route.' ;; esac
    case "$stage" in [1-7]) ;; *) fail 'Stage must be an integer from 1 to 7.' ;; esac
    case "$difficulty" in EASY) ;; *) fail 'Invalid difficulty.' ;; esac
    case "$ship" in STANDARD) ;; *) fail 'Invalid ship.' ;; esac
fi

# Prefix relative paths before cd so a name beginning with '-' is never an option.
physical_dir() {
    case "$1" in *"$newline"*) printf '%s\n' 'Newlines in directory names are not supported.' >&2; return 2 ;; esac
    case "$1" in /*) directory=$1 ;; *) directory=./$1 ;; esac
    (CDPATH= cd -P "$directory" && pwd -P)
}
script_dir=$(physical_dir "$(dirname "$0")") || fail 'Cannot locate the launcher directory.'
repo=$(physical_dir "$script_dir/..") || fail 'Cannot locate the project directory.'
if [ -n "$jdk_root" ]; then
    jdk_root=$(physical_dir "$jdk_root") || fail 'JDK directory not found. Set JAVA_HOME or --jdk-root.'
    compiler=$jdk_root/bin/javac
else
    compiler=$(command -v javac) || fail 'JDK compiler not found. Set JAVA_HOME or --jdk-root.'
fi

# Resolve PATH alternatives/symlinks without GNU-only readlink -f (macOS has readlink).
links=0
while :; do
    compiler_dir=$(physical_dir "$(dirname "$compiler")") || fail 'JDK compiler directory not found.'
    compiler=$compiler_dir/$(basename "$compiler")
    [ -L "$compiler" ] || break
    links=$((links + 1))
    [ "$links" -le 40 ] || fail 'Too many JDK compiler symlinks.'
    target=$(readlink "$compiler") || fail 'Cannot resolve the JDK compiler.'
    case "$target" in /*) compiler=$target ;; *) compiler=$compiler_dir/$target ;; esac
done
java=$compiler_dir/java
[ -f "$compiler" ] && [ -x "$compiler" ] || fail 'JDK compiler not found or not executable.'
[ -f "$java" ] && [ -x "$java" ] || fail 'JDK runtime not found beside javac.'
if version=$("$compiler" -version 2>&1); then :; else
    status=$?; printf '%s\n' "$version" "javac version check failed ($status)." >&2; exit "$status"
fi
major=$(printf '%s\n' "$version" | sed -n 's/^javac \([0-9][0-9]*\).*/\1/p')
case "$major" in ''|*[!0-9]*) fail 'Cannot determine the JDK version.' ;; esac
[ "$major" -ge 17 ] || fail 'JDK 17 or later is required.'
[ -f "$repo/test/engine/StoryDebugLauncher.java" ] && [ -d "$repo/res" ] || fail 'Incomplete project checkout.'

temp_root=$(physical_dir "${TMPDIR:-/tmp}") || fail 'TMPDIR must name an existing writable directory.'
session=$(mktemp -d "${temp_root%/}/invaders-story-debug.XXXXXXXXXX") || exit 1
cleanup() {
    status=$?
    trap - 0 HUP INT TERM
    # Delete only this invocation's owned, direct child of the canonical temp root.
    if [ -e "$session" ] || [ -L "$session" ]; then
        case "${session##*/}" in invaders-story-debug.??????????) valid_name=yes ;; *) valid_name=no ;; esac
        if [ "$valid_name" = yes ] && [ "${session%/*}" = "${temp_root%/}" ] \
                && [ ! -L "$session" ] && [ -f "$session/.invaders-session" ] \
                && [ "$(cat "$session/.invaders-session")" = "$session" ]; then
            if rm -rf "$session"; then :; else
                printf '%s\n' "Could not remove debug session: $session" >&2
                [ "$status" -ne 0 ] || status=1
            fi
        else
            printf '%s\n' "Refusing to remove an unrecognized debug session: $session" >&2
            [ "$status" -ne 0 ] || status=1
        fi
    fi
    exit "$status"
}
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
printf '%s\n' "$session" > "$session/.invaders-session"
mkdir "$session/classes"
# Relative class/source paths also support ':' in the checkout or TMPDIR name.
# rm removes this symlink, never the project's resource directory.
ln -s "$repo/res" "$session/resources"
if (cd "$repo" && "$compiler" --release 17 -encoding UTF-8 -d "$session/classes" \
        -sourcepath 'src:test' test/engine/StoryDebugLauncher.java); then :; else
    status=$?; printf '%s\n' "javac failed ($status)." >&2; exit "$status"
fi
set --
if [ "$selections" -eq 4 ]; then set -- "$route" "$stage" "$difficulty" "$ship"; fi
if (cd "$session" && "$java" '-Dinvaders.storyDebug=true' \
        '-Djava.util.prefs.PreferencesFactory=shop.PreviewPreferencesFactory' \
        "-Duser.home=$session" "-Dinvaders.storageDir=$session" \
        -Dfile.encoding=UTF-8 -cp 'classes:resources' engine.StoryDebugLauncher "$@"); then
    exit 0
else
    status=$?; printf '%s\n' "Story debug failed ($status)." >&2; exit "$status"
fi
