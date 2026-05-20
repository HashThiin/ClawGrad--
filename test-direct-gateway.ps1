Write-Host "=== Testing Backend Status ===" -ForegroundColor Cyan

# Test 1: Backend models endpoint
Write-Host "`n[Test 1] GET http://localhost:8080/api/v1/grading/models"
try {
    $models = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/models" -TimeoutSec 5
    Write-Host "  ✓ Backend running, Models: $($models.Count)" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Backend not accessible: $_" -ForegroundColor Red
    Write-Host "`nPlease restart backend manually:" -ForegroundColor Yellow
    Write-Host "  docker compose restart java-backend" -ForegroundColor Gray
    exit 1
}

# Test 2: Submit a simple task
Write-Host "`n[Test 2] Submit grading task"
$body = @{
    question = "计算 1+1=?"
    answer = "2"
    maxScore = 10
} | ConvertTo-Json -Compress

try {
    $resp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/ai-grade" `
        -Method Post -Body $body -ContentType "application/json" -TimeoutSec 5
    $taskId = $resp.taskId
    Write-Host "  ✓ Task submitted: $taskId" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Submit failed: $_" -ForegroundColor Red
    exit 1
}

# Test 3: Poll result
Write-Host "`n[Test 3] Polling result..."
for ($i = 0; $i -lt 30; $i++) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/ai-tasks/$taskId" -TimeoutSec 5
        
        $stageInfo = ($r.stages | ForEach-Object { "$($_.name):$($_.status)" }) -join " | "
        Write-Host "  [$i] $($r.status) - [$stageInfo]"
        
        if ($r.status -eq "COMPLETED") {
            Write-Host "`n  ✓ SUCCESS!" -ForegroundColor Green
            Write-Host "  Score: $($r.result.totalScore)/$($r.result.maxScore)" -ForegroundColor Green
            Write-Host "  Feedback: $($r.result.feedback)" -ForegroundColor Gray
            break
        }
        if ($r.status -eq "FAILED") {
            Write-Host "`n  ✗ FAILED: $($r.error)" -ForegroundColor Red
            break
        }
        Start-Sleep -Seconds 2
    } catch {
        Write-Host "  [poll error] $_"
        Start-Sleep -Seconds 2
    }
}
