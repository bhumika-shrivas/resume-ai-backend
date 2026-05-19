$services = [ordered]@{
    "discovery-server"     = 8761
    "api-gateway"          = 8080
    "auth-service"         = 8081
    "resume-service"       = 8082
    "section-service"      = 8083
    "template-service"     = 8084
    "ai-service"           = 8085
    "export-service"       = 8086
    "jobmatch-service"     = 8087
    "notification-service" = 8088
    "resumeai-web"         = 8089
    "payment-service"      = 8091
}

# 1. Load Environment Variables from .env.example
$envFile = ".env.example"
if (Test-Path $envFile) {
    Write-Host "Loading environment variables from $envFile..."
    Get-Content $envFile | ForEach-Object {
        if ($_ -match "^(.*)=(.*)$") {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
        }
    }
} else {
    Write-Host "WARNING: .env.example not found. Make sure your environment variables are set globally!"
}

# 2. Create logs directory
if (!(Test-Path "logs")) { 
    New-Item -ItemType Directory -Path "logs" | Out-Null 
}

# 3. Stop existing services and Start new ones
foreach ($service in $services.Keys) {
    $port = $services[$service]
    Write-Host "`n--- Deploying $service (Port: $port) ---"

    # Find process ID using this port
    $processIds = (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Where-Object State -eq 'Listen').OwningProcess
    if ($processIds) {
        foreach ($pidToKill in $processIds) {
            Write-Host "Killing existing process ID $pidToKill listening on port $port..."
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 2
    }

    # Find the newly built .jar file
    $jarPath = Get-ChildItem -Path "$service\target" -Filter "*.jar" | Select-Object -First 1
    if ($jarPath) {
        Write-Host "Starting $($jarPath.Name) in background..."
        $logPath = (Join-Path (Get-Location) "logs\$service.log")
        $startArgs = "-jar `"$($jarPath.FullName)`""
        
        # Start-Process detaches it so Jenkins doesn't kill it or hang
        Start-Process -FilePath "java" -ArgumentList $startArgs -RedirectStandardOutput $logPath -RedirectStandardError $logPath -WindowStyle Hidden
    } else {
        Write-Host "ERROR: No .jar file found for $service! Did the build fail?"
    }
}

Write-Host "`n=== All services deployed successfully! ==="
