# 冒烟测试脚本（每完成一个批次/里程碑后跑一次）
#
# 用途：一秒钟确认「该活的活着、该死的死了」——避免删除或改造误伤既有功能。
# 用法： powershell -ExecutionPolicy Bypass -File tools\smoke-test.ps1
#        powershell -ExecutionPolicy Bypass -File tools\smoke-test.ps1 -BaseUrl http://localhost:8080
#
# 新增用例：往 $cases 数组里加一条即可。字段说明：
#   Name          用例名
#   Method        GET / POST
#   Path          以 / 开头的路径（可带查询串）
#   Body          请求体（可选，JSON 字符串）
#   NeedToken     $true 表示需要管理端 token
#   Expect        期望的 HTTP 状态码
#   Contains      期望响应里包含的字符串（可选）

param(
    [string]$BaseUrl = 'http://localhost:8080',
    # 注意：新库 campus_second_hand 的管理员是 zhangsan/lisi（admin 只存在于旧库 sky_take_out）
    [string]$AdminUser = 'zhangsan',
    [string]$AdminPass = '123456'
)

$ErrorActionPreference = 'Continue'

$cases = @(
    # ---------- 基线：必须一直活着 ----------
    @{ Name='公开接口-店铺状态';      Method='GET';  Path='/user/shop/status';                  Expect=200; Contains='"code":1' }
    @{ Name='接口文档-knife4j';       Method='GET';  Path='/doc.html';                          Expect=200 }
    @{ Name='分类分页(保留功能)';      Method='GET';  Path='/admin/category/page?page=1&pageSize=5'; NeedToken=$true; Expect=200; Contains='"code":1' }
    @{ Name='员工分页(保留功能)';      Method='GET';  Path='/admin/employee/page?page=1&pageSize=5'; NeedToken=$true; Expect=200 }
    # 注意：商品模块 T3-1 只改了类名，SQL 还在查 dish 表（T3-2 才改），
    # 所以这里只断言 HTTP 200，暂不断言 code=1
    @{ Name='商品分页(待T3-2打通)';    Method='GET';  Path='/admin/product/page?page=1&pageSize=5';  NeedToken=$true; Expect=200 }
    # ---------- T3-3：套餐/口味模块已删除，必须 404 ----------
    @{ Name='T3-3-套餐模块已删除';     Method='GET';  Path='/admin/setmeal/page?page=1&pageSize=5';  NeedToken=$true; Expect=404 }
    @{ Name='未登录应被拦截';         Method='GET';  Path='/admin/category/page?page=1&pageSize=5'; NeedToken=$false; Expect=401 }

    # ---------- M2.5 收藏（只测只读接口，写操作由专门的验收脚本覆盖）----------
    @{ Name='M2.5-我的收藏列表';       Method='GET';  Path='/user/favorite/list';                 NeedToken='user'; Expect=200; Contains='"code":1' }

    # ---------- 批次 A：已删除，必须 404 ----------
    @{ Name='批次A-工作台已移除';      Method='GET';  Path='/admin/workspace/businessData';       NeedToken=$true; Expect=404 }
    @{ Name='批次A-营业额报表已移除';   Method='GET';  Path='/admin/report/turnoverStatistics?begin=2026-01-01&end=2026-12-31'; NeedToken=$true; Expect=404 }
    @{ Name='批次A-用户报表已移除';     Method='GET';  Path='/admin/report/userStatistics?begin=2026-01-01&end=2026-12-31';     NeedToken=$true; Expect=404 }

    # ---------- S3-5b：购物车/地址簿/旧订单模块已删除，必须 404 ----------
    @{ Name='S3-5b-购物车已删除';      Method='GET';  Path='/user/shoppingCart/list';              NeedToken='user'; Expect=404 }
    @{ Name='S3-5b-地址簿已删除';      Method='GET';  Path='/user/addressBook/list';               NeedToken='user'; Expect=404 }
    @{ Name='S3-5b-旧订单查询已删除';   Method='GET';  Path='/admin/order/conditionSearch?page=1&pageSize=5'; NeedToken=$true; Expect=404 }
    @{ Name='S3-5b-旧订单统计已删除';   Method='GET';  Path='/admin/order/statistics';              NeedToken=$true; Expect=404 }

    # ---------- S3-4c/S3-5a：新订单模块必须活着 ----------
    @{ Name='S3-4c-我的订单列表';      Method='GET';  Path='/user/order/page?page=1&pageSize=5&view=buyer'; NeedToken='user'; Expect=200; Contains='"code":1' }
)

function Send-Case {
    param($Case, $AdminToken, $UserToken)
    $headers = @{}
    # NeedToken: $true = 管理端（请求头 token）；'user' = 用户端（请求头 authentication）
    # 注意：必须先用 -is [string] 判断类型。PowerShell 里 $true -eq 'user' 会返回 true
    #（非空字符串被强制转成布尔真值），不加这一步管理端用例会走错分支、拿不到 token。
    $need = $Case['NeedToken']
    if (($need -is [string]) -and ($need -eq 'user')) {
        if ($UserToken) { $headers['authentication'] = $UserToken }
    } elseif ($need -and $AdminToken) {
        $headers['token'] = $AdminToken
    }
    $p = @{
        Uri = "$BaseUrl$($Case['Path'])"; Method = $Case['Method']
        UseBasicParsing = $true; TimeoutSec = 15; Headers = $headers
    }
    if ($Case['Body']) { $p['ContentType'] = 'application/json'; $p['Body'] = $Case['Body'] }
    try {
        $r = Invoke-WebRequest @p
        return @{ Status = [int]$r.StatusCode; Content = [string]$r.Content; Err = $null }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            return @{ Status = [int]$resp.StatusCode; Content = ''; Err = $null }
        }
        return @{ Status = -1; Content = ''; Err = $_.Exception.Message }
    }
}

Write-Host ''
Write-Host ('=' * 74)
Write-Host " 冒烟测试  ->  $BaseUrl"
Write-Host ('=' * 74)

# 先取管理端 token
$token = $null
$login = Send-Case @{ Method='POST'; Path='/admin/employee/login'; NeedToken=$false
                      Body = (@{ username=$AdminUser; password=$AdminPass } | ConvertTo-Json -Compress) } $null $null
if ($login.Status -eq 200) {
    try { $token = ($login.Content | ConvertFrom-Json).data.token } catch { }
}
if ($token) {
    Write-Host " [准备] 管理端登录成功，已获取 token" -ForegroundColor Green
} else {
    Write-Host " [准备] 管理端登录失败（HTTP $($login.Status)），需要 token 的用例将失败" -ForegroundColor Red
}

# 再取用户端 token（走测试登录接口，不依赖微信；用户端请求头名是 authentication）
$userToken = $null
try {
    $u = Invoke-RestMethod "$BaseUrl/user/user/testLogin?openid=test_openid_buyer_001" -TimeoutSec 15
    $userToken = $u.data.token
} catch { }
if ($userToken) {
    Write-Host " [准备] 用户端测试登录成功，已获取 userToken" -ForegroundColor Green
} else {
    Write-Host " [准备] 用户端测试登录失败，NeedToken='user' 的用例将失败" -ForegroundColor Red
}

$pass = 0; $fail = 0; $failNames = @()
foreach ($c in $cases) {
    $res = Send-Case $c $token $userToken
    # 注意：必须用 $c['Key'] 索引器，不能用 $c.Contains —— Hashtable 自带 Contains 方法会覆盖同名 key
    $ok = ($res.Status -eq $c['Expect'])
    $detail = "HTTP $($res.Status) (期望 $($c['Expect']))"
    $expectText = $c['Contains']
    if ($ok -and $expectText) {
        if ($res.Content -notlike "*$expectText*") {
            $ok = $false; $detail += " 响应缺少 '$expectText'"
        }
    }
    if ($ok) {
        $pass++
        Write-Host ("  [PASS] {0,-26} {1}" -f $c['Name'], $detail) -ForegroundColor Green
    } else {
        $fail++; $failNames += $c['Name']
        $extra = if ($res.Err) { "  $($res.Err)" } else { '' }
        Write-Host ("  [FAIL] {0,-26} {1}{2}" -f $c['Name'], $detail, $extra) -ForegroundColor Red
    }
}

Write-Host ('-' * 74)
Write-Host " 合计 $($cases.Count) 项：通过 $pass，失败 $fail"
if ($fail -gt 0) {
    Write-Host " 失败用例：$($failNames -join '、')"
    Write-Host " 提示：若失败项是「已移除的接口」，说明它又回来了；若是「保留功能」，说明改造误伤了既有代码。"
}
Write-Host ''
exit ([int]($fail -gt 0))
