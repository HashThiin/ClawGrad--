Write-Host "=== Waiting for Docker Desktop ===" -ForegroundColor Cyan
for ($i = 0; $i -lt 60; $i++) {
    try {
        $null = docker info 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Docker Desktop is ready!" -ForegroundColor Green
            break
        }
    } catch {}
    Start-Sleep -Seconds 2
    if ($i % 10 -eq 0 -and $i -gt 0) { Write-Host "  Waiting... ($i seconds)" }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Docker Desktop failed to start" -ForegroundColor Red
    exit 1
}

Write-Host "`n=== Modifying application.yml to use host.docker.internal ===" -ForegroundColor Cyan
$ymlPath = "java-backend\src\main\resources\application.yml"
$yml = Get-Content $ymlPath -Raw
if ($yml -match "url: http://47\.122\.119\.189:18789") {
    $yml = $yml -replace "url: http://47\.122\.119\.189:18789", "url: http://host.docker.internal:18789"
    Set-Content $ymlPath $yml -NoNewline
    Write-Host "✓ Updated to host.docker.internal" -ForegroundColor Green
}

Write-Host "`n=== Starting SSH Tunnel ===" -ForegroundColor Cyan
# Check if tunnel already exists
$tunnel = Get-NetTCPConnection -LocalPort 18789 -ErrorAction SilentlyContinue
if ($tunnel) {
    Write-Host "SSH tunnel already running on port 18789" -ForegroundColor Green
} else {
    Write-Host "Starting SSH tunnel: localhost:18789 -> cloud:18789"
    # Start in background to avoid interactive prompt
    Start-Process "ssh" -ArgumentList "-f", "-N", "-L", "18789:localhost:18789", "root@47.122.119.189" -NoNewWindow
    Start-Sleep -Seconds 3
    
    # Verify tunnel
    $tunnel = Get-NetTCPConnection -LocalPort 18789 -ErrorAction SilentlyContinue
    if ($tunnel) {
        Write-Host "✓ SSH tunnel started" -ForegroundColor Green
    } else {
        Write-Host "⚠ SSH tunnel may need manual setup. Try:" -ForegroundColor Yellow
        Write-Host "  ssh -f -N -L 18789:localhost:18789 root@47.122.119.189" -ForegroundColor Gray
    }
}

Write-Host "`n=== Restarting Backend ===" -ForegroundColor Cyan
docker compose restart java-backend

Write-Host "`n=== Waiting for Backend ===" -ForegroundColor Cyan
for ($i = 0; $i -lt 50; $i++) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/models" -TimeoutSec 3
        Write-Host "✓ Backend ready! Models: $($r.Count)" -ForegroundColor Green
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}

Write-Host "`n=== Quick Test ===" -ForegroundColor Cyan
$body = @{
    question = "1+1=?"
    answer = "2"
    maxScore = 10
} | ConvertTo-Json -Compress

$resp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/ai-grade" `
    -Method Post -Body $body -ContentType "application/json"
$taskId = $resp.taskId
Write-Host "Task: $taskId"

for ($i = 0; $i -lt 25; $i++) {
    $r = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/ai-tasks/$taskId"
    Write-Host "[$i] $($r.status) - $($r.currentStage)"
    
    if ($r.status -eq "COMPLETED") {
        Write-Host "✓ Score: $($r.result.totalScore)/$($r.result.maxScore)" -ForegroundColor Green
        exit 0
    }
    if ($r.status -eq "FAILED") {
        Write-Host "✗ $($r.error)" -ForegroundColor Red
        exit 1
    }
    Start-Sleep -Seconds 2
}
Write-Host "TIMEOUT"
exit 1
