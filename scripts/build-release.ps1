. (Join-Path $PSScriptRoot 'enter-dev-shell.ps1')
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $ProjectRoot
try {
    $LocalGradle = Get-ChildItem (Join-Path $ProjectRoot '.tools\gradle') -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    if ($LocalGradle) {
        & $LocalGradle lintRelease testDebugUnitTest assembleRelease
    } else {
        & .\gradlew.bat lintRelease testDebugUnitTest assembleRelease
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
