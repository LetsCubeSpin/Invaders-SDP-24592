[CmdletBinding()]
param(
    [string]$JdkRoot = $env:JAVA_HOME,
    [string]$Route,
    [string]$Stage,
    [string]$Difficulty,
    [string]$Ship,
    [string]$Invincible,
    [string]$StartingLives,
    [string]$EnemyHpMultiplier,
    [string]$EnemySpeedMultiplier
)

$ErrorActionPreference = 'Stop'
# Handle native tool exit codes explicitly, including when the caller enabled native error actions.
$PSNativeCommandUseErrorActionPreference = $false
$selection = @()
$fields = @('Route', 'Stage', 'Difficulty', 'Ship' | Where-Object { $PSBoundParameters.ContainsKey($_) })
if ($fields.Count -gt 0) {
    if ($fields.Count -ne 4) { throw 'Specify all four: -Route -Stage -Difficulty -Ship, or omit all to open the picker.' }
    $stageNumber = 0
    if (-not [int]::TryParse($Stage, [ref]$stageNumber)) { throw 'Stage must be an integer.' }
    if ($Difficulty.Trim() -ine 'EASY') { throw 'Difficulty must be EASY.' }
    if ($Ship.Trim() -ine 'STANDARD') { throw 'Ship must be STANDARD.' }
    $selection = @($Route, [string]$stageNumber, 'EASY', 'STANDARD')
}
$modifierOptions = [ordered]@{
    Invincible = '--invincible'
    StartingLives = '--starting-lives'
    EnemyHpMultiplier = '--enemy-hp-multiplier'
    EnemySpeedMultiplier = '--enemy-speed-multiplier'
}
foreach ($name in $modifierOptions.Keys) {
    if ($PSBoundParameters.ContainsKey($name)) {
        if ($fields.Count -ne 4) { throw 'Combat modifiers require all four selections.' }
        $selection += @($modifierOptions[$name], $PSBoundParameters[$name])
    }
}

$repo = Split-Path -Parent $PSScriptRoot
$compiler = if ($JdkRoot) {
    Join-Path $JdkRoot 'bin/javac.exe'
} else {
    $command = Get-Command javac -CommandType Application -ErrorAction SilentlyContinue
    if ($command) { $command.Source }
}
if (-not $compiler -or -not (Test-Path -LiteralPath $compiler -PathType Leaf)) {
    throw 'JDK compiler not found. Install JDK 17 or later and set JAVA_HOME or pass -JdkRoot to its installation directory.'
}
$java = Join-Path (Split-Path -Parent $compiler) 'java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
    throw "JDK runtime not found at '$java'. Pass -JdkRoot to a complete JDK 17 or later installation."
}
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$session = Join-Path $tempRoot ('invaders-story-debug-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $session 'classes'
New-Item -ItemType Directory -Path $classes -ErrorAction Stop | Out-Null
Push-Location -LiteralPath $session
try {
    # Compile once for the entire tool session, including all subsequent battle JVMs.
    Write-Host 'Compiling Story debug launcher...'
    & $compiler --release 17 -encoding UTF-8 -d $classes -sourcepath "$repo\src;$repo\test" "$repo\test\engine\StoryDebugController.java"
    if ($LASTEXITCODE -ne 0) {
        [Console]::Error.WriteLine("javac failed ($LASTEXITCODE).")
        exit $LASTEXITCODE
    }
    Write-Host 'Starting Story debug launcher...'
    & $java '-Dinvaders.storyDebug=true' '-Djava.util.prefs.PreferencesFactory=shop.PreviewPreferencesFactory' `
        "-Duser.home=$session" "-Dinvaders.storageDir=$session" -cp "$classes;$repo\res" engine.StoryDebugController @selection
    if ($LASTEXITCODE -ne 0) {
        [Console]::Error.WriteLine("Story debug failed ($LASTEXITCODE).")
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
    $resolved = (Resolve-Path -LiteralPath $session).Path
    if ([IO.Path]::GetDirectoryName($resolved) -eq $tempRoot.TrimEnd('\') -and
        [IO.Path]::GetFileName($resolved) -match '^invaders-story-debug-[0-9a-f]{32}$') {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
