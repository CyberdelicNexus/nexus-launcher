# Builds the signed release APK with Unity's bundled JDK and Android SDK, then hands it to Quest Fleet.
# The same APK goes to the Meta alpha channel. Raise -VersionCode for every Meta upload.
param(
    [int]$VersionCode = 1,
    [string]$VersionName = '0.1.0',
    [string]$Signing = (Join-Path $HOME '.cyberdelic\nexus-launcher\signing.json'),
    [string]$FleetRoot = (Join-Path $PSScriptRoot '..\..\quest_fleet')
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $Signing)) { throw "Signing file not found: $Signing" }
$player = Get-ChildItem -LiteralPath 'C:/Program Files/Unity/Hub/Editor' -Directory |
    Sort-Object Name -Descending |
    ForEach-Object { Join-Path $_.FullName 'Editor/Data/PlaybackEngines/AndroidPlayer' } |
    Where-Object { Test-Path -LiteralPath "$_/SDK/build-tools/36.0.0" } | Select-Object -First 1
if (!$player) { throw 'No Unity Android module with build-tools 36.0.0 was found.' }

$key = Get-Content -LiteralPath $Signing -Raw | ConvertFrom-Json
$env:JAVA_HOME = "$player/OpenJDK"
$env:ANDROID_HOME = "$player/SDK"
$env:NEXUS_LAUNCHER_KEYSTORE_PATH = $key.path
$env:NEXUS_LAUNCHER_KEYSTORE_PASSWORD = $key.password
$env:NEXUS_LAUNCHER_KEY_ALIAS = $key.alias

# Lint is left out: AGP 8.11 lint crashes parsing the android-37.0 platform folder in Unity's SDK.
$repo = Split-Path $PSScriptRoot
Push-Location $repo
try {
    "sdk.dir=$($env:ANDROID_HOME -replace '\\', '/')" | Set-Content -LiteralPath 'local.properties' -Encoding ascii
    & .\gradlew.bat --no-daemon :nexus-launcher:testDebugUnitTest :nexus-launcher:assembleRelease "-PnexusVersionCode=$VersionCode" "-PnexusVersionName=$VersionName"
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }

$apk = Join-Path $PSScriptRoot 'build/outputs/apk/release/nexus-launcher-release.apk'
if (!(Test-Path -LiteralPath $apk)) { throw 'Release APK was not produced. Check the signing file.' }
$dist = Join-Path $FleetRoot 'android/nexus-launcher/dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $dist 'nexus-launcher.apk') -Force
Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Format-List Hash, Path
