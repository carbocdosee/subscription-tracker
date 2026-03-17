<#
.SYNOPSIS
    One-command API test runner for SaaS Subscription Tracker.

.DESCRIPTION
    1. Starts the Docker Compose stack
    2. Waits until the backend health endpoint responds with HTTP 200
    3. Runs the Jest API test suite and generates an HTML report in api-tests/reports/
    4. Opens the report in the default browser
    5. Shuts down the Docker Compose stack

.PARAMETER Build
    Force a Docker image rebuild before starting.

.PARAMETER KeepUp
    Leave the Docker stack running after tests finish.

.EXAMPLE
    .\run-tests.ps1
    .\run-tests.ps1 -Build
    .\run-tests.ps1 -KeepUp
#>

param(
    [switch]$Build,
    [switch]$KeepUp
)

# --- Resolve script root (safe under -File and dot-source) ---
if ($PSScriptRoot -and $PSScriptRoot -ne '') {
    $Root = $PSScriptRoot
} else {
    $Root = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if (-not $Root) { $Root = (Get-Location).Path }

$ApiTests   = Join-Path $Root 'api-tests'
$ReportsDir = Join-Path $ApiTests 'reports'
$ReportFile = Join-Path $ReportsDir 'test-report.html'
$TestExit   = 0

# --- Console helpers (plain ASCII for PS 5.1 compatibility) ---
function Write-Step { param($msg) Write-Host "`n>  $msg" -ForegroundColor Cyan }
function Write-Ok   { param($msg) Write-Host "[OK]   $msg" -ForegroundColor Green }
function Write-Warn { param($msg) Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Fail { param($msg) Write-Host "[FAIL] $msg" -ForegroundColor Red }

# --- Cleanup helper (called from finally) ---
function Invoke-Cleanup {
    Set-Location $Root
    if (-not $KeepUp) {
        Write-Step 'Shutting down Docker Compose stack...'
        & docker compose down 2>&1 | Out-Null
        Write-Ok 'Stack stopped.'
    } else {
        Write-Warn 'Stack left running (-KeepUp). Stop manually: docker compose down'
    }
    if (Test-Path $ReportFile) {
        Write-Step 'Opening HTML report in browser...'
        Start-Process $ReportFile
        Write-Ok "Report: $ReportFile"
    } else {
        Write-Warn "Report not found at: $ReportFile"
    }
}

# =============================================================================
try {

    # --- 1. Start Docker Compose ---------------------------------------------
    Write-Step 'Starting Docker Compose stack...'
    Set-Location $Root
    $upArgs = @('compose', 'up', '-d')
    if ($Build) { $upArgs += '--build' }
    & docker @upArgs
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed.' }

    # Always recreate the backend so it picks up the latest .env (e.g. RATE_LIMIT_RPM)
    Write-Host '   ...refreshing backend container...' -ForegroundColor DarkGray
    & docker compose up -d --force-recreate --no-deps backend 2>&1 | Out-Null

    # --- 2. Wait for backend health ------------------------------------------
    Write-Step 'Waiting for backend to become healthy...'
    $healthUrl = 'http://localhost:80/health'
    $maxWait   = 120
    $interval  = 5
    $elapsed   = 0
    $alive     = $false

    while (-not $alive) {
        Start-Sleep -Seconds $interval
        $elapsed += $interval

        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing `
                        -TimeoutSec 3 -ErrorAction SilentlyContinue
        if ($null -ne $response -and $response.StatusCode -eq 200) {
            $alive = $true
            Write-Ok "Backend is healthy after ${elapsed}s."
        } else {
            Write-Host "   ...waiting (${elapsed}/${maxWait}s)" -ForegroundColor DarkGray
        }

        if (-not $alive -and $elapsed -ge $maxWait) {
            throw "Backend did not become healthy within ${maxWait}s. Run: docker compose logs backend"
        }
    }

    # --- 3. Prepare api-tests ------------------------------------------------
    Write-Step 'Preparing api-tests...'
    Set-Location $ApiTests

    $envFile    = Join-Path $ApiTests '.env'
    $envExample = Join-Path $ApiTests '.env.test.example'
    if (-not (Test-Path $envFile)) {
        Copy-Item $envExample $envFile
        Write-Ok 'Created api-tests/.env from .env.test.example'
    }

    Write-Step 'Installing npm dependencies...'
    & npm.cmd install
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed.' }

    # --- 4. Run tests --------------------------------------------------------
    Write-Step 'Running API tests...'
    & npm.cmd run test:ci
    $TestExit = $LASTEXITCODE

} catch {
    Write-Fail "Error: $_"
    $TestExit = 1
} finally {
    Invoke-Cleanup
}

# --- Summary -----------------------------------------------------------------
Write-Host ''
if ($TestExit -eq 0) {
    Write-Ok 'All tests PASSED.'
} else {
    Write-Fail "Some tests FAILED (exit $TestExit). See the HTML report for details."
}

exit $TestExit
