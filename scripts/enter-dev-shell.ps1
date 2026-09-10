$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ToolsRoot = Join-Path $ProjectRoot '.tools'
$JdkContainer = Join-Path $ToolsRoot 'microsoft-jdk17'
$JdkRoot = Get-ChildItem $JdkContainer -Directory -ErrorAction SilentlyContinue |
    Where-Object {
        (Test-Path (Join-Path $_.FullName 'bin\java.exe')) -and
        (Test-Path (Join-Path $_.FullName 'bin\jlink.exe'))
    } |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $JdkRoot) {
    $ConfiguredJdk = $env:JAVA_HOME
    if (
        $ConfiguredJdk -and
        (Test-Path (Join-Path $ConfiguredJdk 'bin\java.exe')) -and
        (Test-Path (Join-Path $ConfiguredJdk 'bin\jlink.exe'))
    ) {
        $JdkRoot = $ConfiguredJdk
    } else {
        throw 'JDK 17 is not ready under .tools\microsoft-jdk17 and JAVA_HOME is unavailable.'
    }
}

$env:JAVA_HOME = $JdkRoot
$ProjectAndroidSdk = Join-Path $ToolsRoot 'android-sdk'
if (Test-Path $ProjectAndroidSdk) {
    $env:ANDROID_SDK_ROOT = $ProjectAndroidSdk
} elseif (-not $env:ANDROID_SDK_ROOT -and $env:ANDROID_HOME) {
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
}
if (-not $env:ANDROID_SDK_ROOT) {
    throw 'Android SDK is not ready under .tools\android-sdk and ANDROID_SDK_ROOT is unavailable.'
}
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:ANDROID_USER_HOME = Join-Path $ToolsRoot 'android-home'
$env:GRADLE_USER_HOME = Join-Path $ToolsRoot 'gradle-home'
$LocalUserHome = Join-Path $ToolsRoot 'user-home'
$LocalTemp = Join-Path $ToolsRoot 'tmp'
$null = New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME, $env:GRADLE_USER_HOME, $LocalUserHome, $LocalTemp
$env:GRADLE_OPTS = "-Duser.home=$LocalUserHome -Djava.io.tmpdir=$LocalTemp $env:GRADLE_OPTS".Trim()
$env:Path = "$(Join-Path $JdkRoot 'bin');$(Join-Path $env:ANDROID_SDK_ROOT 'platform-tools');$env:Path"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_SDK_ROOT=$env:ANDROID_SDK_ROOT"
Write-Host "ANDROID_USER_HOME=$env:ANDROID_USER_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
