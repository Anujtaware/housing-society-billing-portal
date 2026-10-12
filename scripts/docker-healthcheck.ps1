param (
    [int]$HostPort = 8084,
    [string]$ContainerName = "housing-billing-cd",
    [int]$MaxRetries = 30,
    [int]$DelaySeconds = 2
)

$ErrorActionPreference = "Continue"

Write-Host "=========================================================="
Write-Host " Docker Continuous Deployment - Health Check Verification "
Write-Host " Container    : $ContainerName"
Write-Host " Target URL   : http://localhost:$HostPort/"
Write-Host " Max Retries  : $MaxRetries (interval: ${DelaySeconds}s)"
Write-Host "=========================================================="

$url = "http://localhost:$HostPort/index.html"
$success = $false

for ($i = 1; $i -le $MaxRetries; $i++) {
    Write-Host "Attempt $i of ${MaxRetries}: Checking $url..."
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200 -and $response.Content -match "Housing Society Billing Portal") {
            Write-Host "✅ Health Check PASSED: Docker application is responding with HTTP 200 OK."
            $success = $true
            break
        } else {
            Write-Host "Received StatusCode $($response.StatusCode), waiting for expected content..."
        }
    } catch {
        Write-Host "Waiting for containerized application to become healthy: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds $DelaySeconds
}

if (-not $success) {
    Write-Error "❌ Docker Health Check FAILED after $MaxRetries attempts!"
    Write-Host "`nDiagnostic Container Logs ($ContainerName):"
    docker logs --tail 50 $ContainerName
    exit 1
}

# Verify API endpoints through Docker mapped port
Write-Host "`nVerifying REST API endpoints via Docker (port $HostPort)..."
try {
    $resResidents = Invoke-RestMethod -Uri "http://localhost:$HostPort/residents" -Method Get -TimeoutSec 5
    Write-Host "✅ GET /residents responded successfully. Count: $($resResidents.Count)"

    $resBills = Invoke-RestMethod -Uri "http://localhost:$HostPort/bills" -Method Get -TimeoutSec 5
    Write-Host "✅ GET /bills responded successfully. Count: $($resBills.Count)"
} catch {
    Write-Error "❌ Docker API verification failed: $($_.Exception.Message)"
    Write-Host "`nDiagnostic Container Logs ($ContainerName):"
    docker logs --tail 50 $ContainerName
    exit 1
}

Write-Host "`nRepresentative Container Logs ($ContainerName):"
docker logs --tail 20 $ContainerName

Write-Host "`n=========================================================="
Write-Host " Docker CD Deployment Verification Complete!             "
Write-Host " Status       : HEALTHY ✅"
Write-Host " Web Portal   : http://localhost:$HostPort/index.html"
Write-Host "=========================================================="
exit 0
