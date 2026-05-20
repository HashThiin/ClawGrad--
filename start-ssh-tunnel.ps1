Write-Host "=== Starting SSH Tunnel ===" -ForegroundColor Cyan
# 启动 SSH 隧道：本地 18789 → 云服务器 18789
ssh -f -N -L 18789:localhost:18789 root@47.122.119.189
if ($LASTEXITCODE -eq 0) {
    Write-Host "SSH tunnel started successfully" -ForegroundColor Green
} else {
    Write-Host "Failed to start SSH tunnel. Please check SSH connection." -ForegroundColor Red
    exit 1
}

Write-Host "`n=== Restarting Backend ===" -ForegroundColor Cyan
docker compose restart java-backend

Write-Host "`n=== Waiting for Backend ===" -ForegroundColor Cyan
for ($i = 0; $i -lt 40; $i++) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/models" -TimeoutSec 3
        Write-Host "Backend ready! Models: $($r.Count)" -ForegroundColor Green
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}

Write-Host "`n=== Testing Gateway from Backend ===" -ForegroundColor Cyan
$body = @{
    question = "1+1=?"
    answer = "2"
    maxScore = 10
} | ConvertTo-Json -Compress

$resp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/ai-grade" `
    -Method Post -Body $body -ContentType "application/json"
$taskId = $resp.taskId
Write-Host "Task submitted: $taskId"

for ($i = 0; $i -lt 30; $i++) {
    $r = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/ai-tasks/$taskId"
    Write-Host "[$i] $($r.status) - $($r.currentStage)"
    
    if ($r.status -eq "COMPLETED") {
        Write-Host "Score: $($r.result.totalScore)/$($r.result.maxScore)" -ForegroundColor Green
        break
    }
    if ($r.status -eq "FAILED") {
        Write-Host "Failed: $($r.error)" -ForegroundColor Red
        break
    }
    Start-Sleep -Seconds 2
}
