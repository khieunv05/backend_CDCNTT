# Nạp .env vào phiên PowerShell hiện tại rồi suy ra các biến mà Spring Boot đọc.
#
# Dùng (chú ý dấu chấm đầu dòng để biến tồn tại trong phiên hiện tại):
#   . .\scripts\dev-env.ps1
#   .\mvnw.cmd spring-boot:run
#
# Không in giá trị secret ra màn hình.

param(
    [string]$Path = (Join-Path $PSScriptRoot '..\.env')
)

if (-not (Test-Path -LiteralPath $Path)) {
    throw "Khong tim thay $Path. Hay sao chep .env.example thanh .env va dien gia tri."
}

$loaded = @{}
Get-Content -LiteralPath $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith('#')) { return }
    $idx = $line.IndexOf('=')
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    if ($value.Length -ge 2 -and (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    $loaded[$key] = $value
}

# application.yml đọc DB_*; .env lại đặt tên APP_DB_*/MYSQL_HOST_PORT.
$hostPort = if ($loaded.ContainsKey('MYSQL_HOST_PORT')) { $loaded['MYSQL_HOST_PORT'] } else { '3306' }
$appDb = if ($loaded.ContainsKey('APP_DB_NAME')) { $loaded['APP_DB_NAME'] } else { 'lenglish' }
$testDb = if ($loaded.ContainsKey('TEST_DB_NAME')) { $loaded['TEST_DB_NAME'] } else { 'lenglish_test' }

# Bắt buộc dùng ${...} quanh tên biến: PowerShell gom cả "?connectionTimeZone"
# vào tên biến khi viết $appDb?..., khiến chuỗi URL bị cắt mất phần database.
$env:DB_URL = "jdbc:mysql://localhost:${hostPort}/${appDb}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
$env:DB_USERNAME = $loaded['APP_DB_USERNAME']
$env:DB_PASSWORD = $loaded['APP_DB_PASSWORD']

# MySqlTestConfiguration so khớp tuyệt đối URL này, nên cổng luôn là 3306.
$env:TEST_DB_URL = "jdbc:mysql://localhost:3306/${testDb}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
$env:TEST_DB_USERNAME = $loaded['TEST_DB_USERNAME']
$env:TEST_DB_PASSWORD = $loaded['TEST_DB_PASSWORD']

Write-Host "Da nap $($loaded.Count) bien tu $Path"
Write-Host ("  DB_URL       = {0}" -f $env:DB_URL)
Write-Host ("  DB_USERNAME  = {0}" -f $env:DB_USERNAME)
Write-Host ("  TEST_DB_URL  = {0}" -f $env:TEST_DB_URL)
Write-Host ("  TEST_DB_USER = {0}" -f $env:TEST_DB_USERNAME)
Write-Host "  DB_PASSWORD / TEST_DB_PASSWORD / JWT_SECRET / AI_API_KEY = [da nap, khong in]"

if ($hostPort -ne '3306') {
    Write-Warning "MYSQL_HOST_PORT=$hostPort nhung integration test bat buoc dung localhost:3306 - ./mvnw verify se bi guard tu choi."
}
