Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendPom = Join-Path $repositoryRoot "vidyut-backend\pom.xml"
$agentDirectory = Join-Path $repositoryRoot "vidyut-ai\agent"
$agentVenvPython = Join-Path $agentDirectory ".venv\Scripts\python.exe"
$webDirectory = Join-Path $repositoryRoot "vidyut-web"

Write-Host "[1/3] Running Spring Boot tests"
& mvn -f $backendPom test
if ($LASTEXITCODE -ne 0) { throw "Spring Boot tests failed" }

Write-Host "[2/3] Running role-scoped agent tests"
Push-Location $agentDirectory
try {
    if (Test-Path -LiteralPath $agentVenvPython) {
        & $agentVenvPython -m unittest discover -s tests -v
    } elseif (Get-Command py -ErrorAction SilentlyContinue) {
        & py -m unittest discover -s tests -v
    } else {
        & python -m unittest discover -s tests -v
    }
    if ($LASTEXITCODE -ne 0) { throw "Agent tests failed" }
} finally {
    Pop-Location
}

Write-Host "[3/3] Running web lint and production build"
& npm --prefix $webDirectory run lint
if ($LASTEXITCODE -ne 0) { throw "Web lint failed" }
& npm --prefix $webDirectory run build
if ($LASTEXITCODE -ne 0) { throw "Web build failed" }

Write-Host "All Vidyut verification checks passed."
