[CmdletBinding()]
param([string]$JdkRoot = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$launcher = Join-Path $PSScriptRoot 'run-story-debug.ps1'
$hostExe = (Get-Process -Id $PID).Path
$oldTemp = $env:TEMP
$oldTmp = $env:TMP
$oldCompilerOptions = $env:JDK_JAVAC_OPTIONS
$testDirectory = Join-Path ([IO.Path]::GetTempPath()) ('story debug test ' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDirectory | Out-Null

function Test-LauncherFailure([string]$Stage, [int]$ExpectedCode, [string]$ExpectedMessage,
        [string]$CompilerRoot = $JdkRoot, [switch]$OmitShip) {
    $arguments = @('-NoProfile', '-NonInteractive', '-File', $launcher, '-Route', 'TRUCE',
        '-Stage', $Stage, '-Difficulty', 'HARD')
    if (-not $OmitShip) { $arguments += @('-Ship', 'TANK') }
    if ($CompilerRoot) { $arguments += @('-JdkRoot', $CompilerRoot) }
    # Windows PowerShell wraps redirected native stderr in error records.
    $output = & {
        $ErrorActionPreference = 'Continue'
        & $hostExe @arguments 2>&1
    }
    $code = $LASTEXITCODE
    if ($code -ne $ExpectedCode -or ($output | Out-String) -notmatch [regex]::Escape($ExpectedMessage)) {
        throw "Stage $Stage returned $code (expected $ExpectedCode and '$ExpectedMessage'). Output: $output"
    }
    if (Get-ChildItem -Force -LiteralPath $testDirectory -Filter 'invaders-story-debug-*') {
        throw "Launcher left its session in $testDirectory."
    }
}

try {
    $env:TEMP = $testDirectory
    $env:TMP = $testDirectory
    Test-LauncherFailure '7' 1 'Specify all four:' -OmitShip
    Test-LauncherFailure '7' 1 'JDK compiler not found.' -CompilerRoot (Join-Path $testDirectory 'missing JDK')
    # An invalid compiler option makes javac deterministically return 2 without opening a window.
    $env:JDK_JAVAC_OPTIONS = '--invalid-story-debug-test-option'
    Test-LauncherFailure '3.5' 1 'Stage must be an integer.'
    Test-LauncherFailure '7' 2 'javac failed (2).'
    $env:JDK_JAVAC_OPTIONS = $null
    Test-LauncherFailure '8' 1 'Stage must be between 1 and 7'
} finally {
    $env:JDK_JAVAC_OPTIONS = $oldCompilerOptions
    $env:TEMP = $oldTemp
    $env:TMP = $oldTmp
    if (-not (Get-ChildItem -Force -LiteralPath $testDirectory)) {
        Remove-Item -LiteralPath $testDirectory
    }
}

# Expected native failures must not leak into the GitHub Actions shell epilogue.
Write-Output 'PASS: incomplete selection, missing JDK, fractional stage, javac/java exit codes, cleanup in a path with spaces.'
exit 0
