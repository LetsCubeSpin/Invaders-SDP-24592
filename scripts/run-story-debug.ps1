[CmdletBinding()]
param(
    [string]$JdkRoot = $env:JAVA_HOME,
    [string]$Route,
    [int]$Stage,
    [string]$Difficulty,
    [string]$Ship
)

$ErrorActionPreference = 'Stop'
$selection = @()
$fields = @('Route', 'Stage', 'Difficulty', 'Ship' | Where-Object { $PSBoundParameters.ContainsKey($_) })
if ($fields.Count -gt 0) {
    if ($fields.Count -ne 4) { throw 'Specify all four: -Route -Stage -Difficulty -Ship, or omit all to open the picker.' }
    $selection = @($Route, [string]$Stage, $Difficulty, $Ship)
}

$repo = Split-Path -Parent $PSScriptRoot
$compiler = if ($JdkRoot) { Join-Path $JdkRoot 'bin/javac.exe' } else { (Get-Command javac).Source }
$java = Join-Path (Split-Path -Parent $compiler) 'java.exe'
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$session = Join-Path $tempRoot ('invaders-story-debug-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $session 'classes'
New-Item -ItemType Directory -Path $classes -ErrorAction Stop | Out-Null
Push-Location -LiteralPath $session
try {
    # Compile the development entry point and its transitive sources; the release jar stays unchanged.
    & $compiler --release 17 -encoding UTF-8 -d $classes -sourcepath "$repo\src;$repo\test" "$repo\test\engine\StoryDebugLauncher.java"
    if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)." }
    & $java '-Dinvaders.storyDebug=true' '-Djava.util.prefs.PreferencesFactory=shop.PreviewPreferencesFactory' `
        "-Duser.home=$session" "-Dinvaders.storageDir=$session" -cp "$classes;$repo\res" engine.StoryDebugLauncher @selection
    if ($LASTEXITCODE -ne 0) { throw "Story debug failed ($LASTEXITCODE)." }
} finally {
    Pop-Location
    $resolved = (Resolve-Path -LiteralPath $session).Path
    if ([IO.Path]::GetDirectoryName($resolved) -eq $tempRoot.TrimEnd('\') -and
        [IO.Path]::GetFileName($resolved) -match '^invaders-story-debug-[0-9a-f]{32}$') {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
