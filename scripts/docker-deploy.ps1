param (
    [string]$ImageName = "housing-society-billing-portal",
    [string]$ImageTag = "latest",
    [int]$HostPort = 8084,
    [int]$ContainerPort = 8080,
    [string]$ContainerName = "housing-billing-cd",
    [string]$DbHost = "host.docker.internal",
    [string]$DbPort = "3306",
    [string]$DbName = "housing_billing",
    [string]$DbUser = "root",
    [string]$DbPassword = "Housing@2026"
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host " Housing Society Billing Portal - Docker CD Deployment   "
Write-Host " Image Name     : $ImageName"
Write-Host " Image Tag      : $ImageTag"
Write-Host " Container Name : $ContainerName"
Write-Host " Port Mapping   : Host $HostPort -> Container $ContainerPort"
Write-Host " Target DB Host : $DbHost"
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

$containerId = docker run -d `
    --name $ContainerName `
    -p "${HostPort}:${ContainerPort}" `
    -e "DB_HOST=$DbHost" `
    -e "DB_PORT=$DbPort" `
    -e "DB_NAME=$DbName" `
    -e "DB_USER=$DbUser" `
    -e "DB_PASSWORD=$DbPassword" `
    $fullImage

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
    Write-Error "Failed to start Docker container '$ContainerName'."
    exit 1
}

Write-Host "Container started successfully with ID: $containerId"
Write-Host "Inspecting container status:"
docker ps --filter "name=^/${ContainerName}$"
exit 0
