param (
    [string]$EnvName = "DEV",
    [int]$DeployPort = 8081,
    [int]$AppPort = 8082,
    [string]$JarPath = "application/target/billing-portal-0.0.1-SNAPSHOT.jar",
    [string]$NginxDir = "C:\DevTools\nginx",
    [string]$DeployBaseDir = "C:\DevTools\deployments"
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host " Housing Society Billing Portal - Deployment Automation   "
Write-Host " Environment  : $EnvName"
Write-Host " Nginx Port   : $DeployPort"
Write-Host " App Port     : $AppPort"
Write-Host " Source JAR   : $JarPath"
Write-Host "=========================================================="

if (-not (Test-Path $JarPath)) {
    throw "Source JAR not found at: $JarPath"
}

if (-not (Test-Path $NginxDir)) {
    throw "Nginx directory not found at: $NginxDir"
}

$targetDir = Join-Path $DeployBaseDir $EnvName
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

$targetJar = Join-Path $targetDir "billing-portal.jar"
$logFile   = Join-Path $targetDir "app.log"
$errFile   = Join-Path $targetDir "app_err.log"
$pidFile   = Join-Path $targetDir "billing-portal.pid"

# 1. Stop any existing application on $AppPort
Write-Host "Checking for existing application listening on port $AppPort..."
$existingConn = Get-NetTCPConnection -LocalPort $AppPort -State Listen -ErrorAction SilentlyContinue
if ($existingConn) {
    $existingPid = $existingConn.OwningProcess
    Write-Host "Found existing process $existingPid on port $AppPort. Terminating..."
    Stop-Process -Id $existingPid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

if (Test-Path $pidFile) {
    $savedPid = Get-Content $pidFile -ErrorAction SilentlyContinue
    if ($savedPid) {
        $p = Get-Process -Id $savedPid -ErrorAction SilentlyContinue
        if ($p) {
            Write-Host "Terminating saved PID $savedPid..."
            Stop-Process -Id $savedPid -Force -ErrorAction SilentlyContinue
        }
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

# 2. Deploy new JAR
Write-Host "Deploying new artifact to: $targetJar"
Copy-Item -Path $JarPath -Destination $targetJar -Force

# 3. Start Spring Boot application detached
Write-Host "Starting Spring Boot application (Environment: $EnvName, Port: $AppPort)..."
$javaExe = (Get-Command java).Source
$javaArgs = "-Dspring.profiles.active=$($EnvName.ToLower()) -jar `"$targetJar`" --server.port=$AppPort"
$cmdLine = "`"$javaExe`" $javaArgs"

# Create detached process using Win32_Process
$procResult = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
    CommandLine = $cmdLine
    CurrentDirectory = $targetDir
}

if ($procResult.ReturnValue -ne 0) {
    throw "Failed to start Spring Boot process. ReturnValue: $($procResult.ReturnValue)"
}

$newPid = $procResult.ProcessId
Write-Host "Spring Boot application started with PID: $newPid"
Set-Content -Path $pidFile -Value $newPid

# 4. Configure Nginx Reverse Proxy
Write-Host "Configuring Nginx reverse proxy on port $DeployPort -> 127.0.0.1:$AppPort..."
$nginxConfPath = Join-Path $NginxDir "conf\nginx.conf"

$nginxConfig = @"
worker_processes  1;

events {
    worker_connections  1024;
}

http {
    include       mime.types;
    default_type  application/octet-stream;
    sendfile        on;
    keepalive_timeout  65;

    upstream billing_backend {
        server 127.0.0.1:$AppPort;
    }

    server {
        listen       $DeployPort;
        server_name  localhost;

        location / {
            proxy_pass http://billing_backend;
            proxy_set_header Host `$host;
            proxy_set_header X-Real-IP `$remote_addr;
            proxy_set_header X-Forwarded-For `$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto `$scheme;
            proxy_set_header X-Environment $EnvName;
        }
    }
}
"@

# Write UTF-8 without BOM
[System.IO.File]::WriteAllText($nginxConfPath, $nginxConfig, (New-Object System.Text.UTF8Encoding($false)))

# Test Nginx config
Write-Host "Testing Nginx configuration syntax..."
$nginxExe = Join-Path $NginxDir "nginx.exe"
& $nginxExe -t -p $NginxDir
if ($LASTEXITCODE -ne 0) {
    throw "Nginx configuration test failed!"
}

# 5. Start or reload Nginx
$nginxRunning = Get-Process -Name nginx -ErrorAction SilentlyContinue
if ($nginxRunning) {
    Write-Host "Nginx is already running. Reloading configuration..."
    & $nginxExe -p $NginxDir -s reload
    Start-Sleep -Seconds 1
} else {
    Write-Host "Starting Nginx web server..."
    $nginxResult = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
        CommandLine = "`"$nginxExe`""
        CurrentDirectory = $NginxDir
    }
    if ($nginxResult.ReturnValue -ne 0) {
        throw "Failed to start Nginx! ReturnValue: $($nginxResult.ReturnValue)"
    }
    Start-Sleep -Seconds 2
}

Write-Host "Deployment stage completed successfully."
