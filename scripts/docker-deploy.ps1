param (
    [string]$ImageName = "housing-society-billing-portal",
    [string]$ImageTag = "latest",
    [int]$HostPort = 8084,
    [int]$ContainerPort = 8080,
    [string]$ContainerName = "housing-billing-cd"
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host " Housing Society Billing Portal - Docker CD Deployment   "
Write-Host " Image Name     : $ImageName"
Write-Host " Image Tag      : $ImageTag"
Write-Host " Container Name : $ContainerName"
Write-Host " Port Mapping   : Host $HostPort -> Container $ContainerPort"
Write-Host "=========================================================="

# 1. Stop and remove existing container if present
Write-Host "Checking for existing container named '$ContainerName'..."
$existing = docker ps -a --filter "name=^/${ContainerName}$" --format "{{.ID}}"
if ($existing) {
    Write-Host "Stopping and removing existing container $ContainerName ($existing)..."
    docker stop $ContainerName 2>$null | Out-Null
    docker rm $ContainerName 2>$null | Out-Null
    Write-Host "Previous container removed successfully."
}

# 2. Deploy fresh Docker container
$fullImage = "${ImageName}:${ImageTag}"
Write-Host "Starting fresh Docker container from image '$fullImage'..."

$dbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "host.docker.internal" }
$dbPort = if ($env:DB_PORT) { $env:DB_PORT } else { "3306" }
$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { "housing_billing" }
$dbUser = if ($env:DB_USER) { $env:DB_USER } else { "root" }
$dbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "Housing@2026" }

$containerId = docker run -d `
    --name $ContainerName `
    -p "${HostPort}:${ContainerPort}" `
    -e "DB_HOST=$dbHost" `
    -e "DB_PORT=$dbPort" `
    -e "DB_NAME=$dbName" `
    -e "DB_USER=$dbUser" `
    -e "DB_PASSWORD=$dbPassword" `
    $fullImage

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
    Write-Error "Failed to start Docker container '$ContainerName'."
    exit 1
}

Write-Host "Container started successfully with ID: $containerId"
Write-Host "Inspecting container status:"
docker ps --filter "name=^/${ContainerName}$"
exit 0
