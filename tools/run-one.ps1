# ============================================================
# 只跑【一个模块】的回归（快速验证用，约 20 秒）
#
# 和 run-regression.ps1 的区别：
#   run-regression.ps1  跑全部 9 个模块（约 3 分钟，提交前用）
#   run-one.ps1         只跑一个模块（开发时用，快 10 倍）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\run-one.ps1 -Module adminuser
#
# 注意：如果改了 Java 代码，先编译再跑（本脚本会先检查 class 是否比源码旧并提醒）
# ============================================================
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('order', 'bargain', 'flash', 'message', 'delivery', 'credit', 'complaint', 'adminuser')]
    [string]$Module
)

# 注意：这里【不能】设 $ErrorActionPreference = 'Stop'。
# mysql.exe 会把 "Using a password on the command line" 这种警告写到 stderr，
# 一旦设成 Stop，这条警告就会被当成致命错误，脚本直接退出（踩过）。
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot

# 模块 → (重置脚本, 测试脚本, 中文名)
$modules = @{
    order     = @{ Sql = 'test-reset.sql';           Py = 'order-flow-test.py';   Name = '订单模块' }
    bargain   = @{ Sql = 'test-reset-bargain.sql';   Py = 'bargain-test.py';      Name = '议价模块' }
    flash     = @{ Sql = 'test-reset-flashsale.sql'; Py = 'flashsale-test.py';    Name = '捡漏抢购' }
    message   = @{ Sql = 'test-reset-message.sql';   Py = 'message-test.py';      Name = '私信模块' }
    delivery  = @{ Sql = 'test-reset-delivery.sql';  Py = 'delivery-test.py';     Name = '校园跑腿' }
    credit    = @{ Sql = 'test-reset-credit.sql';    Py = 'credit-test.py';       Name = '评价+信用分' }
    complaint = @{ Sql = 'test-reset-complaint.sql';  Py = 'complaint-test.py';   Name = '投诉工单' }
    adminuser = @{ Sql = 'test-reset-admin-user.sql'; Py = 'admin-user-test.py';  Name = '用户管理' }
}
$m = $modules[$Module]

# ---------- 1. 找 Python ----------
# 优先用 py 启动器（必须带 -3；不带的话 py.exe 会把自己的二进制当脚本读，报 '\x90' 错）
$pyExe = $null
$g = Get-Command 'py' -ErrorAction SilentlyContinue
if ($g) { $pyExe = @($g.Source, '-3') }
if (-not $pyExe) {
    $g2 = Get-Command 'python' -ErrorAction SilentlyContinue
    if ($g2) { $pyExe = @($g2.Source) }
}
if (-not $pyExe) {
    $bundled = Get-ChildItem "$env:USERPROFILE\.dsh\dsh-runtimes" -Recurse -Filter 'python.exe' -ErrorAction SilentlyContinue |
               Select-Object -First 1
    if ($bundled) { $pyExe = @($bundled.FullName) }
}
if (-not $pyExe) { Write-Host "❌ 找不到 Python" -ForegroundColor Red; exit 1 }

# 参数切片：只有 1 个元素时不能写 [1..0]（PowerShell 会生成一个倒序范围，混进 null）
if ($pyExe.Count -gt 1) { $pyArgs = $pyExe[1..($pyExe.Count - 1)] } else { $pyArgs = @() }

# ---------- 2. 数据库与 Redis 配置（从 gitignore 的 application-dev.yml 读）----------
$yml = Join-Path $root 'sky-server\src\main\resources\application-dev.yml'
$dbHost = 'localhost'; $dbPort = '3306'; $dbName = 'campus_second_hand'; $dbUser = 'root'; $dbPass = ''
if (Test-Path -LiteralPath $yml) {
    $raw = Get-Content -LiteralPath $yml -Raw
    if ($raw -match '(?m)^\s*host:\s*(\S+)')     { $dbHost = $Matches[1] }
    if ($raw -match '(?m)^\s*port:\s*(\S+)')     { $dbPort = $Matches[1] }
    if ($raw -match '(?m)^\s*database:\s*(\S+)') { $dbName = $Matches[1] }
    if ($raw -match '(?m)^\s*username:\s*(\S+)') { $dbUser = $Matches[1] }
    if ($raw -match '(?m)^\s*password:\s*(\S+)') { $dbPass = $Matches[1] }
}
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

# ---------- 3. 提醒：class 是否比源码旧（这是最容易踩的坑）----------
$srcNewest = Get-ChildItem (Join-Path $root 'sky-server\src') -Recurse -File -Include *.java, *.xml |
             Sort-Object LastWriteTime -Descending | Select-Object -First 1
$clsNewest = Get-ChildItem (Join-Path $root 'sky-server\target\classes') -Recurse -File -ErrorAction SilentlyContinue |
             Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($srcNewest -and $clsNewest -and $srcNewest.LastWriteTime -gt $clsNewest.LastWriteTime) {
    Write-Host "⚠️  源码比编译产物新！请先执行 mvn compile，否则跑的是旧代码" -ForegroundColor Yellow
    Write-Host "    最新源码: $($srcNewest.Name)  $($srcNewest.LastWriteTime)" -ForegroundColor Yellow
    Write-Host ""
}

# ---------- 4. 服务是否就绪 ----------
$ready = $false
foreach ($i in 1..10) {
    try {
        $r = Invoke-RestMethod 'http://localhost:8080/user/shop/status' -TimeoutSec 3
        if ($r.code -eq 1) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
}
if (-not $ready) {
    Write-Host "❌ 服务没启动，请先跑: powershell -ExecutionPolicy Bypass -File .\run-dev.ps1" -ForegroundColor Red
    exit 1
}

# ---------- 5. 重置数据 + 跑测试 ----------
Write-Host "===== 快速验证：$($m.Name) =====" -ForegroundColor Cyan
$sqlFile = Join-Path $root "sql\$($m.Sql)"
& $mysqlExe "-h$dbHost" "-P$dbPort" "-u$dbUser" "-p$dbPass" "--default-character-set=utf8mb4" `
    -e "source $($sqlFile -replace '\\','/')" 2>&1 |
    Where-Object { $_ -notmatch 'password on the command line' } | Out-Null
Write-Host "  ✅ 数据已重置（$($m.Sql)）" -ForegroundColor Green

$testFile = Join-Path $root "tools\$($m.Py)"
$out = & $pyExe[0] @pyArgs $testFile 2>&1
$out | Where-Object { $_ -match '^\s*(✅|❌|期望包含|实际返回|RESULT|=====)' } | ForEach-Object { Write-Host $_ }

$match = [regex]::Match(($out -join "`n"), 'RESULT pass=(\d+) fail=(\d+) total=(\d+)')
if ($match.Success) {
    $p = [int]$match.Groups[1].Value; $f = [int]$match.Groups[2].Value; $t = [int]$match.Groups[3].Value
    Write-Host ""
    if ($f -eq 0) {
        Write-Host "🎉 $($m.Name)：$p / $t 全过" -ForegroundColor Green
        exit 0
    } else {
        Write-Host "❌ $($m.Name)：通过 $p / $t，失败 $f" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "⚠️ 没拿到 RESULT 行（脚本可能异常退出）" -ForegroundColor Yellow
    $out | Select-Object -Last 15 | ForEach-Object { Write-Host "  $_" }
    exit 1
}
