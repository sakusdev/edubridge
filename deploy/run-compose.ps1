param(
    [switch] $SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envFile = Join-Path $PSScriptRoot ".env"
$gradle = Join-Path $repoRoot ".gradle-local\gradle-8.14\bin\gradle.bat"

function Fail($message) {
    Write-Error $message
    exit 1
}

if (-not (Test-Path $envFile)) {
    Fail "Missing deploy\.env. Copy deploy\.env.example to deploy\.env and fill in real values."
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($null -eq $docker) {
    Fail "Docker CLI is not installed or not on PATH. Install Docker Desktop, then rerun this script."
}

if (-not $SkipBuild) {
    if (-not (Test-Path $gradle)) {
        Fail "Gradle runtime not found at $gradle"
    }
    Push-Location $repoRoot
    try {
        & $gradle build ":auth-service:installDist"
    } finally {
        Pop-Location
    }
}

Push-Location $PSScriptRoot
try {
    docker compose up -d --build

    Write-Host "Waiting for auth-service readiness..."
    $deadline = (Get-Date).AddMinutes(2)
    do {
        Start-Sleep -Seconds 3
        $containerId = docker compose ps -q auth-service
        if ([string]::IsNullOrWhiteSpace($containerId)) {
            continue
        }
        $health = docker inspect -f "{{.State.Health.Status}}" $containerId 2>$null
        if ($LASTEXITCODE -eq 0 -and $health -eq "healthy") {
            Write-Host "auth-service is ready."
            exit 0
        }
        if ($LASTEXITCODE -eq 0 -and $health -eq "unhealthy") {
            docker compose logs --tail=100 auth-service
            Fail "auth-service reported unhealthy."
        }
    } while ((Get-Date) -lt $deadline)

    docker compose logs --tail=100 auth-service
    Fail "auth-service did not become ready within 2 minutes."
} finally {
    Pop-Location
}
