[CmdletBinding()]
param([string]$JdkRoot = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$launcher = Join-Path $PSScriptRoot 'run-story-debug.ps1'
$hostExe = (Get-Process -Id $PID).Path
$oldTemp = $env:TEMP
$oldTmp = $env:TMP
$oldCompilerOptions = $env:JDK_JAVAC_OPTIONS
$oldJavaOptions = $env:JAVA_TOOL_OPTIONS
$testDirectory = Join-Path ([IO.Path]::GetTempPath()) ('story debug test ' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDirectory | Out-Null

function Test-LauncherFailure([string]$Stage, [int]$ExpectedCode, [string[]]$ExpectedMessage,
        [string]$CompilerRoot = $JdkRoot, [switch]$OmitShip,
        [string]$Difficulty = 'EASY', [string]$Ship = 'STANDARD', [string[]]$Modifiers = @(),
        [switch]$OmitSelection) {
    $arguments = @('-NoProfile', '-NonInteractive', '-File', $launcher)
    if (-not $OmitSelection) {
        $arguments += @('-Route', 'TRUCE', '-Stage', $Stage, '-Difficulty', $Difficulty)
        if (-not $OmitShip) { $arguments += @('-Ship', $Ship) }
    }
    $arguments += $Modifiers
    if ($CompilerRoot) { $arguments += @('-JdkRoot', $CompilerRoot) }
    # Windows PowerShell wraps redirected native stderr in error records.
    $output = & {
        $ErrorActionPreference = 'Continue'
        & $hostExe @arguments 2>&1
    }
    $code = $LASTEXITCODE
    if ($code -ne $ExpectedCode) { throw "Expected exit $ExpectedCode, got $code. Output: $output" }
    foreach ($message in $ExpectedMessage) {
        if (($output | Out-String) -notmatch [regex]::Escape($message)) {
            throw "Missing '$message' for stage $Stage, difficulty $Difficulty, ship $Ship. Output: $output"
        }
    }
    if (Get-ChildItem -Force -LiteralPath $testDirectory -Filter 'invaders-story-debug-*') {
        throw "Launcher left its session in $testDirectory."
    }
}

try {
    $env:TEMP = $testDirectory
    $env:TMP = $testDirectory
    Test-LauncherFailure '7' 1 'Specify all four:' -OmitShip
    Test-LauncherFailure '7' 1 'Combat modifiers require all four selections.' -OmitSelection -Modifiers @('-Invincible', 'ON')
    Test-LauncherFailure '7' 1 'JDK compiler not found.' -CompilerRoot (Join-Path $testDirectory 'missing JDK')
    # An invalid compiler option makes javac deterministically return 2 without opening a window.
    $env:JDK_JAVAC_OPTIONS = '--invalid-story-debug-test-option'
    foreach ($difficulty in 'NORMAL', 'HARD') {
        Test-LauncherFailure '7' 1 'Difficulty must be EASY.' -Difficulty $difficulty
    }
    foreach ($ship in 'SPEEDSTER', 'TANK') {
        Test-LauncherFailure '7' 1 'Ship must be STANDARD.' -Ship $ship
    }
    Test-LauncherFailure '3.5' 1 'Stage must be an integer.'
    Test-LauncherFailure '7' 2 'javac failed (2).'
    $env:JDK_JAVAC_OPTIONS = $null
    Test-LauncherFailure '8' 1 'Stage must be between 1 and 7'
    $env:JAVA_TOOL_OPTIONS = '-Djava.awt.headless=true'
    Test-LauncherFailure '7' 1 @('Route TRUCE   |   Stage 7   |   EASY   |   STANDARD', 'HeadlessException') -Difficulty 'easy' -Ship 'standard'
    Test-LauncherFailure '7' 1 @('Invincible: ON   Starting lives: 15', 'Enemy HP: 2.0x   Enemy speed: 0.5x', 'HeadlessException') `
        -Modifiers @('-StartingLives', '15', '-EnemyHpMultiplier', '2.0', '-Invincible', 'ON', '-EnemySpeedMultiplier', '0.5')
    Test-LauncherFailure '7' 1 'Enemy HP multiplier must be between 0.25 and 4.0.' -Modifiers @('-EnemyHpMultiplier', '4.00000000000000001')
    Test-LauncherFailure '7' 1 'Enemy speed multiplier must be between 0.25 and 4.0.' -Modifiers @('-EnemySpeedMultiplier', '0.2499999999999999999')
} finally {
    $env:JDK_JAVAC_OPTIONS = $oldCompilerOptions
    $env:JAVA_TOOL_OPTIONS = $oldJavaOptions
    $env:TEMP = $oldTemp
    $env:TMP = $oldTmp
    if (-not (Get-ChildItem -Force -LiteralPath $testDirectory)) {
        Remove-Item -LiteralPath $testDirectory
    }
}

# Expected native failures must not leak into the GitHub Actions shell epilogue.
Write-Output 'PASS: selection and modifier validation, legacy and modified direct launch, missing JDK, javac/java exit codes, cleanup in a path with spaces.'
exit 0
