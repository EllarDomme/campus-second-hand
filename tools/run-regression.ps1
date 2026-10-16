<#
一键回归：冒烟测试 + 订单模块 + 议价模块

用法（在项目根目录）：
    powershell -ExecutionPolicy Bypass -File tools\run-regression.ps1

它会做四件事：
    1. 检查服务是否已启动（不会帮你启动，避免误以为是脚本起的）
    2. 跑 tools\smoke-test.ps1             —— 接口存活 / 已删除接口必须 404（18 项）
    3. 重置订单数据 → 跑 tools\order-flow-test.py  —— 订单全流程（19 项）
    4. 重置议价数据 → 跑 tools\bargain-test.py     —— 议价（12 项）

数据库连接信息从 sky-server\src\main\resources\application-dev.yml 读取
（该文件已被 .gitignore 排除，所以密码不会进仓库）。

退出码：0 = 全部通过；1 = 有失败
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$WaitSeconds = 30
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$passed = 0
$failed = 0
$summary = @()

function Write-Head($text) {
    Write-Host ''
    Write-Host ('=' * 74)
    Write-Host " $text"
    Write-Host ('=' * 74)
}

# ---------- 1. 找 Python 解释器 ----------
# ⚠️ Windows 上 python.exe 可能是"应用商店别名"假 stub（跑起来没任何输出），
#    所以优先用 py launcher，并实测一次确认真能跑。
$pyExe = $null
foreach ($cand in @(@('py', '-3'), @('python'), @('python3'))) {
    $exe = $cand[0]
    if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) { continue }
    $args = @(); if ($cand.Count -gt 1) { $args = $cand[1..($cand.Count - 1)] }
    try {
        $out = & $exe @args -c "print('PYOK')" 2>&1
        if ($out -match 'PYOK') { $pyExe = @($exe) + $args; break }
    } catch { }
}
if (-not $pyExe) {
    Write-Host '⚠️ 没找到可用的 Python 解释器，将跳过两个 Python 测试脚本' -ForegroundColor Yellow
    Write-Host '   （Python 是运行 tools\*.py 测试脚本所需的；项目本身不需要 Python）'
}

# ---------- 2. 数据库连接信息（从 application-dev.yml 读，避免把密码写进脚本）----------
$yml = Join-Path $root 'sky-server\src\main\resources\application-dev.yml'
$mysqlExe = $null
foreach ($cand in @('mysql', 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe')) {
    if (Get-Command $cand -ErrorAction SilentlyContinue) { $mysqlExe = (Get-Command $cand).Source; break }
}
$dbHost = 'localhost'; $dbPort = '3306'; $dbName = 'campus_second_hand'
$dbUser = 'root'; $dbPass = ''; $redisPass = ''
if (Test-Path -LiteralPath $yml) {
    $raw = Get-Content -LiteralPath $yml -Raw
    if ($raw -match '(?m)^\s*host:\s*(\S+)')     { $dbHost = $Matches[1] }
    if ($raw -match '(?m)^\s*port:\s*(\S+)')     { $dbPort = $Matches[1] }
    if ($raw -match '(?m)^\s*database:\s*(\S+)') { $dbName = $Matches[1] }
    if ($raw -match '(?m)^\s*username:\s*(\S+)') { $dbUser = $Matches[1] }
    if ($raw -match '(?m)^\s*password:\s*(\S+)') { $dbPass = $Matches[1] }
    # ⚠️ yml 里 datasource 和 redis 各有一个 password: 字段，
    #    上面那个匹配到的是 datasource 的；Redis 的要从 redis: 这一段里单独取
    if ($raw -match '(?s)redis:(.*)') {
        if ($Matches[1] -match '(?m)^\s*password:\s*(\S+)') { $redisPass = $Matches[1] }
    }
}
# redis-cli 的认证参数（没配密码就不加 -a，否则会拿空字符串当密码去认证而报错）
$redisAuth = @(); if ($redisPass) { $redisAuth = @('-a', $redisPass) }
$canReset = ($mysqlExe -and $dbPass)

function Invoke-ResetSql($sqlFile) {
    if (-not $canReset) {
        Write-Host "  ⚠️ 跳过数据重置（找不到 mysql 客户端或读不到数据库配置）" -ForegroundColor Yellow
        return $false
    }
    $path = Join-Path $root "sql\$sqlFile"
    # 把脚本里的库名替换成配置里的库名，避免两边不一致
    $sql = (Get-Content -LiteralPath $path -Raw) -replace 'campus_second_hand', $dbName
    $tmp = Join-Path $env:TEMP ("dsh-reset-" + [guid]::NewGuid().ToString('N') + '.sql')
    Set-Content -LiteralPath $tmp -Value $sql -Encoding UTF8
    & $mysqlExe "-h$dbHost" "-P$dbPort" "-u$dbUser" "-p$dbPass" --default-character-set=utf8mb4 -e "source $($tmp -replace '\\','/')" 2>&1 |
        Where-Object { $_ -notmatch 'password on the command line' } | Out-Null
    Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    Write-Host "  ✅ 已重置数据（$sqlFile）" -ForegroundColor Green
    return $true
}

# ---------- 3. 等服务就绪 ----------
Write-Head "一键回归  ->  $BaseUrl"
$ready = $false
for ($i = 1; $i -le $WaitSeconds; $i++) {
    try {
        $r = Invoke-WebRequest "$BaseUrl/user/shop/status" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { Start-Sleep -Seconds 1 }
}
if (-not $ready) {
    Write-Host "❌ 服务没起来（等了 $WaitSeconds 秒）。请先启动项目再跑回归。" -ForegroundColor Red
    Write-Host "   启动方式：powershell -ExecutionPolicy Bypass -File .\run-dev.ps1"
    exit 1
}
Write-Host "  ✅ 服务已就绪" -ForegroundColor Green

# ---------- 4. 冒烟测试 ----------
Write-Head "① 冒烟测试（接口存活 / 已删除接口必须 404）"
$smokeOut = & powershell -ExecutionPolicy Bypass -File (Join-Path $root 'tools\smoke-test.ps1') 2>&1
$smokeOut | Select-Object -Last 4 | ForEach-Object { Write-Host "  $_" }
$m = [regex]::Match(($smokeOut -join "`n"), '合计 (\d+) 项：通过 (\d+)，失败 (\d+)')
if ($m.Success) {
    $passed += [int]$m.Groups[2].Value
    $failed += [int]$m.Groups[3].Value
    $summary += "冒烟测试            : 通过 $($m.Groups[2].Value) / $($m.Groups[1].Value)"
}

# ---------- 5. 订单模块 ----------
Write-Head "② 订单模块回归（下单/支付/确认/拒单/收货/取消）"
if ($pyExe) {
    Invoke-ResetSql 'test-reset.sql' | Out-Null
    $out = & $pyExe[0] @($pyExe[1..($pyExe.Count - 1)]) (Join-Path $root 'tools\order-flow-test.py') 2>&1
    $out | Where-Object { $_ -match '^\s*(✅|❌|期望包含|实际返回|订单模块回归|RESULT|=====)' } | ForEach-Object { Write-Host "  $_" }
    $m = [regex]::Match(($out -join "`n"), 'RESULT pass=(\d+) fail=(\d+) total=(\d+)')
    if ($m.Success) {
        $passed += [int]$m.Groups[1].Value
        $failed += [int]$m.Groups[2].Value
        $summary += "订单模块回归        : 通过 $($m.Groups[1].Value) / $($m.Groups[3].Value)"
    }
} else {
    Write-Host "  ⚠️ 跳过（没有可用的 Python）" -ForegroundColor Yellow
}

# ---------- 6. 议价模块 ----------
Write-Head "③ 议价模块回归（发起出价 / 列表数据隔离）"
if ($pyExe) {
    Invoke-ResetSql 'test-reset-bargain.sql' | Out-Null
    $out = & $pyExe[0] @($pyExe[1..($pyExe.Count - 1)]) (Join-Path $root 'tools\bargain-test.py') 2>&1
    $out | Where-Object { $_ -match '^\s*(✅|❌|期望包含|实际返回|议价模块回归|RESULT|=====)' } | ForEach-Object { Write-Host "  $_" }
    $m = [regex]::Match(($out -join "`n"), 'RESULT pass=(\d+) fail=(\d+) total=(\d+)')
    if ($m.Success) {
        $passed += [int]$m.Groups[1].Value
        $failed += [int]$m.Groups[2].Value
        $summary += "议价模块回归        : 通过 $($m.Groups[1].Value) / $($m.Groups[3].Value)"
    }
} else {
    Write-Host "  ⚠️ 跳过（没有可用的 Python）" -ForegroundColor Yellow
}

# ---------- 7. 捡漏抢购 ----------
Write-Head "④ 限时捡漏抢购（Redis 预扣 + 数据库兜底 + 并发压测）"
if ($pyExe) {
    Invoke-ResetSql 'test-reset-flashsale.sql' | Out-Null
    # 说明：Redis 里的库存 key 由测试脚本自己调 POST /admin/flashSale/resetStock/{id} 清理。
    #      不用 redis-cli 的原因：Windows 版 redis-cli 是 Cygwin 编译的，
    #      在受限环境里会因创建命名对象失败而报 0xC0000022，根本跑不起来。

    $out = & $pyExe[0] @($pyExe[1..($pyExe.Count - 1)]) (Join-Path $root 'tools\flashsale-test.py') 2>&1
    $out | Where-Object { $_ -match '^\s*(✅|❌|期望包含|实际返回|捡漏抢购回归|RESULT|=====|【准备】|成功：|失败：|列表返回|  完成)' } |
        ForEach-Object { Write-Host "  $_" }
    $m = [regex]::Match(($out -join "`n"), 'RESULT pass=(\d+) fail=(\d+) total=(\d+)')
    if ($m.Success) {
        $passed += [int]$m.Groups[1].Value
        $failed += [int]$m.Groups[2].Value
        $summary += "捡漏抢购回归        : 通过 $($m.Groups[1].Value) / $($m.Groups[3].Value)"
    }

    # 数据层证据：MySQL 里放出去 1 件、Redis 库存扣到 0（两边一致）
    if ($canReset) {
        Write-Host "  --- 数据层核对（并发抢的那件：商品2）---"
        $sqlCheck = @"
SELECT CONCAT('  MySQL 抢购成功记录 = ', COUNT(*)) FROM $dbName.flash_sale_record WHERE product_id = 2 AND is_success = 1;
SELECT CONCAT('  MySQL 捡漏订单     = ', COUNT(*)) FROM $dbName.trade_order WHERE product_id = 2 AND order_source = 3;
SELECT CONCAT('  MySQL 商品2 状态   = ', status, '（1=交易中，说明已被锁走）') FROM $dbName.product WHERE id = 2;
"@
        & $mysqlExe "-h$dbHost" "-P$dbPort" "-u$dbUser" "-p$dbPass" -N -B -e $sqlCheck 2>&1 |
            Where-Object { $_ -notmatch 'password on the command line' } | ForEach-Object { Write-Host $_ }
        try {
            $adminToken = (Invoke-RestMethod "$BaseUrl/admin/employee/login" -Method POST -TimeoutSec 10 `
                -ContentType 'application/json; charset=utf-8' `
                -Body ([System.Text.Encoding]::UTF8.GetBytes('{"username":"zhangsan","password":"123456"}'))).data.token
            $stock = (Invoke-RestMethod "$BaseUrl/admin/flashSale/stock/2" -Headers @{ token = $adminToken } -TimeoutSec 10).data
            Write-Host "  Redis 商品2 剩余库存 = '$stock'（0 或空=已扣光；空表示 key 不存在）"
        } catch {
            Write-Host "  （查 Redis 库存失败：$($_.Exception.Message)）"
        }
    }
} else {
    Write-Host "  ⚠️ 跳过（没有可用的 Python）" -ForegroundColor Yellow
}

# ---------- 8. 私信（含 WebSocket 实时推送）----------
Write-Head "⑤ 私信（会话 / 消息 / 搜索 / WebSocket 实时推送）"
if ($pyExe) {
    Invoke-ResetSql 'test-reset-message.sql' | Out-Null

    $out = & $pyExe[0] @($pyExe[1..($pyExe.Count - 1)]) (Join-Path $root 'tools\message-test.py') 2>&1
    $out | Where-Object { $_ -match '^\s*(✅|❌|期望包含|实际返回|私信模块回归|RESULT|=====|会话id|学姐的 WebSocket)' } |
        ForEach-Object { Write-Host "  $_" }
    $m = [regex]::Match(($out -join "`n"), 'RESULT pass=(\d+) fail=(\d+) total=(\d+)')
    if ($m.Success) {
        $passed += [int]$m.Groups[1].Value
        $failed += [int]$m.Groups[2].Value
        $summary += "私信模块回归        : 通过 $($m.Groups[1].Value) / $($m.Groups[3].Value)"
    }

    if ($canReset) {
        Write-Host "  --- 数据层核对 ---"
        $sqlCheck = @"
SELECT CONCAT('  会话数 = ', COUNT(*)) FROM $dbName.conversation;
SELECT CONCAT('  消息数 = ', COUNT(*)) FROM $dbName.message;
SELECT CONCAT('  未读消息数 = ', COUNT(*)) FROM $dbName.message WHERE is_read = 0;
"@
        & $mysqlExe "-h$dbHost" "-P$dbPort" "-u$dbUser" "-p$dbPass" -N -B -e $sqlCheck 2>&1 |
            Where-Object { $_ -notmatch 'password on the command line' } | ForEach-Object { Write-Host $_ }
    }
} else {
    Write-Host "  ⚠️ 跳过（没有可用的 Python）" -ForegroundColor Yellow
}

# ---------- 9. 校园跑腿 ----------
Write-Head "⑥ 校园跑腿（生成跑腿单 / 并发抢单 / 状态机 / 主订单联动）"
if ($pyExe) {
    Invoke-ResetSql 'test-reset-delivery.sql' | Out-Null

    $out = & $pyExe[0] @($pyExe[1..($pyExe.Count - 1)]) (Join-Path $root 'tools\delivery-test.py') 2>&1
    $out | Where-Object { $_ -match '^\s*(✅|❌|期望包含|实际返回|校园跑腿回归|RESULT|=====|成功：|失败：|交易订单id)' } |
        ForEach-Object { Write-Host "  $_" }
    $m = [regex]::Match(($out -join "`n"), 'RESULT pass=(\d+) fail=(\d+) total=(\d+)')
    if ($m.Success) {
        $passed += [int]$m.Groups[1].Value
        $failed += [int]$m.Groups[2].Value
        $summary += "校园跑腿回归        : 通过 $($m.Groups[1].Value) / $($m.Groups[3].Value)"
    }

    if ($canReset) {
        Write-Host "  --- 数据层核对 ---"
        $sqlCheck = @"
SELECT CONCAT('  跑腿单状态分布: 待接单=', SUM(status=0), ' 已接单=', SUM(status=1), ' 取货中=', SUM(status=2), ' 已送达=', SUM(status=3), ' 已取消=', SUM(status=4)) FROM $dbName.delivery_order;
SELECT CONCAT('  完成单的送达时间: ', IFNULL(MAX(finish_time), 'NULL')) FROM $dbName.delivery_order WHERE status = 3;
"@
        & $mysqlExe "-h$dbHost" "-P$dbPort" "-u$dbUser" "-p$dbPass" -N -B -e $sqlCheck 2>&1 |
            Where-Object { $_ -notmatch 'password on the command line' } | ForEach-Object { Write-Host $_ }
    }
} else {
    Write-Host "  ⚠️ 跳过（没有可用的 Python）" -ForegroundColor Yellow
}

# ---------- 10. 评价 + 信用分 ----------
Write-Head "⑦ 评价 + 信用分（一单一评 / 好评差评联动 / 每日加分上限 / 超时爽约扣分）"
if ($pyExe) {
    Invoke-ResetSql 'test-reset-credit.sql' | Out-Null

    $out = & $pyExe[0] @($pyExe[1..($pyExe.Count - 1)]) (Join-Path $root 'tools\credit-test.py') 2>&1
    $out | Where-Object { $_ -match '^\s*(✅|❌|期望包含|实际返回|评价\+信用分回归|RESULT|=====|      第|  订单912)' } |
        ForEach-Object { Write-Host "  $_" }
    $m = [regex]::Match(($out -join "`n"), 'RESULT pass=(\d+) fail=(\d+) total=(\d+)')
    if ($m.Success) {
        $passed += [int]$m.Groups[1].Value
        $failed += [int]$m.Groups[2].Value
        $summary += "评价+信用分回归     : 通过 $($m.Groups[1].Value) / $($m.Groups[3].Value)"
    }

    if ($canReset) {
        Write-Host "  --- 数据层核对 ---"
        $sqlCheck = @"
SELECT CONCAT('  评价数 = ', COUNT(*)) FROM $dbName.review;
SELECT CONCAT('  信用分流水 = ', COUNT(*), ' 条') FROM $dbName.credit_log;
SELECT CONCAT('  ', u.nickname, ' 信用分=', u.credit_score) FROM $dbName.user u WHERE u.id IN (1,2,3) ORDER BY u.id;
"@
        & $mysqlExe "-h$dbHost" "-P$dbPort" "-u$dbUser" "-p$dbPass" -N -B -e $sqlCheck 2>&1 |
            Where-Object { $_ -notmatch 'password on the command line' } | ForEach-Object { Write-Host $_ }
    }
} else {
    Write-Host "  ⚠️ 跳过（没有可用的 Python）" -ForegroundColor Yellow
}

# ---------- 11. 投诉工单 ----------
Write-Head "⑧ 投诉工单（商品举报 / 交易纠纷 / 管理端裁决 / 信用分处罚只扣一次）"
if ($pyExe) {
    Invoke-ResetSql 'test-reset-complaint.sql' | Out-Null

    $out = & $pyExe[0] @($pyExe[1..($pyExe.Count - 1)]) (Join-Path $root 'tools\complaint-test.py') 2>&1
    $out | Where-Object { $_ -match '^\s*(✅|❌|期望包含|实际返回|投诉工单回归|RESULT|=====|      商品举报工单id|      交易纠纷工单id)' } |
        ForEach-Object { Write-Host "  $_" }
    $m = [regex]::Match(($out -join "`n"), 'RESULT pass=(\d+) fail=(\d+) total=(\d+)')
    if ($m.Success) {
        $passed += [int]$m.Groups[1].Value
        $failed += [int]$m.Groups[2].Value
        $summary += "投诉工单回归        : 通过 $($m.Groups[1].Value) / $($m.Groups[3].Value)"
    }

    if ($canReset) {
        Write-Host "  --- 数据层核对 ---"
        $sqlCheck = @"
SELECT CONCAT('  工单数 = ', COUNT(*), '（待处理=', SUM(handle_status=0), ' 已完结=', SUM(handle_status=2), '）') FROM $dbName.complaint;
SELECT CONCAT('  已完结工单: 成立=', SUM(handle_result=1), ' 不成立=', SUM(handle_result=2)) FROM $dbName.complaint WHERE handle_status = 2;
SELECT CONCAT('  投诉导致的信用分流水 = ', COUNT(*), ' 条') FROM $dbName.credit_log WHERE reason_type = 6;
SELECT CONCAT('  ', u.nickname, ' 信用分=', u.credit_score) FROM $dbName.user u WHERE u.id IN (1,2,3) ORDER BY u.id;
"@
        & $mysqlExe "-h$dbHost" "-P$dbPort" "-u$dbUser" "-p$dbPass" -N -B -e $sqlCheck 2>&1 |
            Where-Object { $_ -notmatch 'password on the command line' } | ForEach-Object { Write-Host $_ }
    }
} else {
    Write-Host "  ⚠️ 跳过（没有可用的 Python）" -ForegroundColor Yellow
}

# ---------- 12. 汇总 ----------
Write-Head "回归汇总"
$summary | ForEach-Object { Write-Host "  $_" }
Write-Host ''
Write-Host ("  合计：通过 {0}，失败 {1}" -f $passed, $failed) -ForegroundColor $(if ($failed -gt 0) { 'Red' } else { 'Green' })
Write-Host ''
exit ([int]($failed -gt 0))
