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
# 清掉商品列表缓存（演示数据变了但缓存还是旧的，页面会显示老图片/老价格）
try {
    $redisCfg = (Get-Content -LiteralPath $yml -Raw) -split 'redis:' | Select-Object -Last 1
    $rPass = if ($redisCfg -match 'password:\s*(\S+)') { $Matches[1] } else { '' }
    $cli = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 6379)
    $st = $cli.GetStream()
    $wr = New-Object System.IO.StreamWriter($st); $wr.NewLine = "`r`n"; $wr.AutoFlush = $true
    if ($rPass) { $wr.Write("*2`r`n`$4`r`nAUTH`r`n`$$($rPass.Length)`r`n$rPass`r`n") }
    $wr.Write("*2`r`n`$6`r`nSELECT`r`n`$1`r`n1`r`n")
    $delKeys = @('product_list_null') + (1..20 | ForEach-Object { "product_list$_" })
    $wr.Write("*" + ($delKeys.Count + 1) + "`r`n`$3`r`nDEL`r`n" + (($delKeys | ForEach-Object { "`$" + $_.Length + "`r`n$_`r`n" }) -join ''))
    Start-Sleep -Milliseconds 300
    $cli.Close()
    Write-Host "  ✅ 已清商品列表缓存（product_list_null）" -ForegroundColor Green
} catch { Write-Host "  ⚠️ 清缓存失败（不影响数据）：$($_.Exception.Message)" -ForegroundColor Yellow }
