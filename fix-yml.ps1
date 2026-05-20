[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$utf8 = New-Object System.Text.UTF8Encoding $false
$file = "java-backend\src\main\resources\application.yml"
$content = [System.IO.File]::ReadAllText($file, $utf8)
$content = $content -replace 'url: http://[^/]+:18789', 'url: http://47.122.119.189:18789'
[System.IO.File]::WriteAllText($file, $content, $utf8)
Write-Host "✓ Updated application.yml (UTF-8 preserved)"
