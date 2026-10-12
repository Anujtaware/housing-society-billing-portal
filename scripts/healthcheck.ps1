param (
    [int]$DeployPort = 8081,
    [int]$MaxRetries = 30,
    [int]$DelaySeconds = 2
)

$ErrorActionPreference = "Continue"

Write-Host "=========================================================="
Write-Host " Housing Society Billing Portal - Health Check Verification"
Write-Host " Target URL   : http://localhost:$DeployPort/"
Write-Host " Max Retries  : $MaxRetries (interval: ${DelaySeconds}s)"
Write-Host "=========================================================="

$url = "http://localhost:$DeployPort/index.html"
$success = $false

for ($i = 1; $i -le $MaxRetries; $i++) {
    Write-Host "Attempt $i of ${MaxRetries}: Checking $url..."
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200 -and $response.Content -match "Housing Society Billing Portal") {
            Write-Host "✅ Health Check PASSED: Application is responding with HTTP 200 OK."
            $success = $true
            break
        } else {
            Write-Host "Received StatusCode $($response.StatusCode), waiting for expected content..."
        }
    } catch {
        Write-Host "Waiting for application to become healthy: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds $DelaySeconds
}

if (-not $success) {
    Write-Error "❌ Health Check FAILED after $MaxRetries attempts!"
    exit 1
}

# Verify API endpoints through Nginx reverse proxy
Write-Host "`nVerifying REST API endpoints via Nginx (port $DeployPort)..."
try {
    $resResidents = Invoke-RestMethod -Uri "http://localhost:$DeployPort/residents" -Method Get -TimeoutSec 5
    Write-Host "✅ GET /residents responded successfully. Count: $($resResidents.Count)"

    $resBills = Invoke-RestMethod -Uri "http://localhost:$DeployPort/bills" -Method Get -TimeoutSec 5
    Write-Host "✅ GET /bills responded successfully. Count: $($resBills.Count)"
} catch {
    Write-Error "❌ API verification failed: $($_.Exception.Message)"
    exit 1
}

Write-Host "`n=========================================================="
Write-Host " Deployed Application Verification Complete!               "
Write-Host " Status       : HEALTHY ✅"
Write-Host " Web Portal   : http://localhost:$DeployPort/index.html"
Write-Host "=========================================================="
exit 0
