# ============================================================
# 一键重灌「演示数据」
#
# 什么时候需要跑它：
#   · 跑过 run-regression.ps1 之后（测试夹具会清掉一部分演示数据）
#   · 想重置成"看起来正在运行"的状态
#   · 给别人演示之前
#
# 用法：powershell -ExecutionPolicy Bypass -File tools\reseed-demo.ps1
# ============================================================
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot

# 0. 如果有源码改动，先生成最新的 SQL
$gen = Join-Path $root 'tools\gen-demo-data.py'
$sql = Join-Path $root 'sql\seed-demo-data.sql'
if (Test-Path $gen) {
    $py = $null
    $g = Get-Command 'py' -ErrorAction SilentlyContinue
    if ($g) { $py = @($g.Source, '-3') }
    if (-not $py) {
        $bundled = Get-ChildItem "$env:USERPROFILE\.dsh\dsh-runtimes" -Recurse -Filter 'python.exe' -ErrorAction SilentlyContinue |
                   Select-Object -First 1
        if ($bundled) { $py = @($bundled.FullName) }
    }
    if ($py) {
        if ($py.Count -gt 1) { $pyArgs = $py[1..($py.Count - 1)] } else { $pyArgs = @() }
        & $py[0] @pyArgs $gen | Out-Null
    }
}

# 1. 从 gitignored 的 application-dev.yml 读数据库配置
$yml = Join-Path $root 'campus-server\src\main\resources\application-dev.yml'
$dbHost = 'localhost'; $dbPort = '3306'; $dbUser = 'root'; $dbPass = ''
if (Test-Path -LiteralPath $yml) {
    $raw = Get-Content -LiteralPath $yml -Raw
    if ($raw -match '(?m)^\s*host:\s*(\S+)')     { $dbHost = $Matches[1] }
    if ($raw -match '(?m)^\s*port:\s*(\S+)')     { $dbPort = $Matches[1] }
    if ($raw -match '(?m)^\s*username:\s*(\S+)') { $dbUser = $Matches[1] }
    if ($raw -match '(?m)^\s*password:\s*(\S+)') { $dbPass = $Matches[1] }
}
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

Write-Host "===== 重灌演示数据 =====" -ForegroundColor Cyan
Write-Host "  数据库: ${dbHost}:${dbPort}  用户: $dbUser"
$mysqlPath = $sql -replace '\\', '/'
& $mysqlExe "-h$dbHost" "-P$dbPort" "-u$dbUser" "-p$dbPass" '--default-character-set=utf8mb4' -e "source $mysqlPath" 2>&1 |
    Where-Object { $_ -notmatch 'password on the command line' } | ForEach-Object { Write-Host $_ }

Write-Host ""
Write-Host "===== 现在打开网页看 =====" -ForegroundColor Green
Write-Host "  http://localhost:8080/ui/index.html" -ForegroundColor Green
Write-Host ""
Write-Host "  管理后台: zhangsan / 123456" -ForegroundColor Gray
Write-Host "  用户端  : 点页面上的快捷登录（林小满 / 陈知行 / 韩风）" -ForegroundColor Gray
