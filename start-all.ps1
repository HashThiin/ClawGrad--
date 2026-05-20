Write-Host "=== Waiting for Docker Desktop ===" -ForegroundColor Cyan
for ($i = 0; $i -lt 60; $i++) {
    try {
        docker compose ps 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Docker Desktop is ready!" -ForegroundColor Green
            break
        }
    } catch {}
    Start-Sleep -Seconds 2
    if ($i % 10 -eq 0 -and $i -gt 0) { Write-Host "  Waiting... ($i seconds)" }
}

Write-Host "`n=== Starting backend dependencies ===" -ForegroundColor Cyan
docker compose up -d mysql redis rabbitmq

Write-Host "`n=== Starting Java Backend ===" -ForegroundColor Cyan
docker compose up -d java-backend

Write-Host "`n=== Waiting for Backend to be ready ===" -ForegroundColor Cyan
for ($i = 0; $i -lt 40; $i++) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/grading/models" -TimeoutSec 3
        Write-Host "Backend is ready! Models: $($r.Count)" -ForegroundColor Green
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}

Write-Host "`n=== Starting Frontend ===" -ForegroundColor Cyan
Set-Location frontend
if (-not (Test-Path "node_modules")) {
    Write-Host "Installing dependencies..." -ForegroundColor Yellow
    npm install
}
npm run dev
