param(
    [string]$MinecraftDir,
    [string]$Version = '1.6.5',
    [string]$Jdk,
    [string]$Out
)

# Portable build script for wss-cheat-menu.
# ASCII only on purpose: Windows PowerShell reads .ps1 with the system codepage.

$ErrorActionPreference = 'Continue'
$repo = $PSScriptRoot
if (-not $Out) { $Out = Join-Path $repo 'dist\wsscheat-1.0.0.jar' }

function Fail($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- locate game
function Find-Pack($root, $version) {
    if (-not $root) { return $null }
    $versions = Join-Path $root 'versions'
    if (-not (Test-Path -LiteralPath $versions)) { return $null }
    foreach ($d in (Get-ChildItem -LiteralPath $versions -Directory -ErrorAction SilentlyContinue)) {
        if ($version -and $d.Name -notlike "*$version*") { continue }
        $hit = Get-ChildItem -LiteralPath (Join-Path $d.FullName 'mods') -Filter 'wonder_shop_story_core*.jar' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($hit) { return [pscustomobject]@{ Root = $root; VersionDir = $d.FullName; CoreJar = $hit.FullName } }
    }
    return $null
}

$found = $null
if ($MinecraftDir) {
    $found = Find-Pack $MinecraftDir $Version
    if (-not $found) { Fail "no modpack with wonder_shop_story_core*.jar found under $MinecraftDir\versions" }
} else {
    $candidates = @(
        'D:\pcl mc\.minecraft',
        'C:\pcl mc\.minecraft',
        (Join-Path $env:APPDATA '.minecraft'),
        (Join-Path $env:USERPROFILE '.minecraft')
    )
    foreach ($c in $candidates) {
        $found = Find-Pack $c $Version
        if ($found) { break }
    }
    if (-not $found) {
        Fail 'modpack not found. Pass -MinecraftDir "D:\path\to\.minecraft" (the folder containing versions\).'
    }
}
$MinecraftDir = $found.Root
$versionDir = $found.VersionDir
$coreJar = $found.CoreJar
Write-Host "[1/5] minecraft: $MinecraftDir"
Write-Host "[2/5] version dir: $versionDir"
Write-Host "      core mod:   $(Split-Path -Leaf $coreJar)"

# ---------------------------------------------------------------- locate jdk
function Test-Jdk($dir) {
    if (-not $dir) { return $false }
    return (Test-Path -LiteralPath (Join-Path $dir 'bin\javac.exe'))
}

if (-not (Test-Jdk $Jdk)) {
    $candidates = @($env:JAVA_HOME)
    $candidates += (Get-ChildItem "$env:LOCALAPPDATA\Programs\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { $_.FullName })
    $candidates += (Get-ChildItem "$env:LOCALAPPDATA\Programs\Java" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { $_.FullName })
    $candidates += (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { $_.FullName })
    $candidates += (Get-ChildItem 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { $_.FullName })
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    if ($javac) { $candidates += (Split-Path (Split-Path $javac.Source)) }
    foreach ($c in $candidates) {
        if (Test-Jdk $c) { $Jdk = $c; break }
    }
}
if (-not (Test-Jdk $Jdk)) {
    Fail 'A JDK 21+ is required. Install one and/or pass -Jdk "C:\Program Files\Java\jdk-21".'
}
$jdkVersion = (& (Join-Path $Jdk 'bin\javac.exe') -version 2>&1) -join ' '
Write-Host "[3/5] jdk: $Jdk ($jdkVersion)"

# ---------------------------------------------------------------- classpath
$work = Join-Path $env:TEMP 'wss-cheat-menu-build'
New-Item -ItemType Directory -Force -Path $work, (Join-Path $work 'lib') | Out-Null
Copy-Item -LiteralPath $coreJar -Destination (Join-Path $work 'lib\core.jar') -Force

$libraries = Join-Path $MinecraftDir 'libraries'
if (-not (Test-Path -LiteralPath $libraries)) { Fail "missing $libraries" }

$jars = @(Get-ChildItem -LiteralPath $libraries -Recurse -Filter *.jar | Where-Object {
    $_.FullName -notmatch '\\net\\minecraft\\client\\' -and
    $_.FullName -notmatch '\\net\\minecraftforge\\forge\\' -and
    $_.FullName -notmatch 'neoforge\\21\.1\.221'
} | ForEach-Object { $_.FullName })

# NeoForge 1.21.1 ships the official-mapped client classes in the *-srg jar.
$mcJar = Get-ChildItem -LiteralPath (Join-Path $libraries 'net\minecraft\client') -Recurse -Filter 'client-1.21.1-*-srg.jar' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $mcJar) { Fail 'client-1.21.1-*-srg.jar not found in libraries. Run the pack once with NeoForge first.' }
$jars += $mcJar.FullName
$jars += (Join-Path $work 'lib\core.jar')

$cp = ($jars | Select-Object -Unique | ForEach-Object { $_.Replace('\', '/') }) -join ';'
Set-Content -LiteralPath (Join-Path $work 'args.txt') -Value ('-cp "' + $cp + '"') -Encoding ascii

# ---------------------------------------------------------------- compile
Write-Host '[4/5] javac'
$classOut = Join-Path $work 'classes'
Remove-Item -Recurse -Force $classOut -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classOut | Out-Null
$srcs = @(Get-ChildItem -LiteralPath (Join-Path $repo 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Write-Host "      sources: $($srcs.Count)"
& (Join-Path $Jdk 'bin\javac.exe') --release 21 -encoding UTF-8 -nowarn -d $classOut "@$work\args.txt" $srcs *> (Join-Path $work 'javac.log')
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath (Join-Path $work 'javac.log') -Encoding UTF8 | ForEach-Object { Write-Host $_ }
    Fail "javac failed: $LASTEXITCODE"
}
Copy-Item -Path (Join-Path $repo 'res\*') -Destination $classOut -Recurse -Force

# ---------------------------------------------------------------- package
Write-Host '[5/5] jar'
New-Item -ItemType Directory -Force -Path (Split-Path $Out) | Out-Null
Remove-Item -Force $Out -ErrorAction SilentlyContinue
& (Join-Path $Jdk 'bin\jar.exe') --create --file $Out -C $classOut . *> (Join-Path $work 'jar.log')
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath (Join-Path $work 'jar.log') -Encoding UTF8 | ForEach-Object { Write-Host $_ }
    Fail "jar failed: $LASTEXITCODE"
}
Write-Host ("OK -> {0} ({1} bytes)" -f $Out, (Get-Item $Out).Length) -ForegroundColor Green
