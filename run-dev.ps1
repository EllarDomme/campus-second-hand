# 本地启动脚本（校园二手物品交易平台 / 由苍穹外卖改造而来）
#
# 为什么不用 mvn spring-boot:run？
#   1) 本机 Maven 本地仓库（mvn_repo）不可写，无法 mvn install 反应堆模块；
#   2) 从 campus-server 目录直接 run 会从仓库取「旧版」campus-common / campus-pojo jar，
#      而这两个模块的改动不会生效（例如 MessageConstant 常量改名会抛 NoSuchFieldError）。
# 因此本脚本把三个模块的 target/classes 放在 classpath 最前面，保证拿到的是最新编译结果。
#
# 用法： .\run-dev.ps1          启动
#        .\run-dev.ps1 -Rebuild 先 mvn clean compile 再启动

param([switch]$Rebuild)

$ErrorActionPreference = 'Stop'
$base = $PSScriptRoot
$env:JAVA_HOME = 'C:\Program Files\Java\jdk1.8.0_202'   # 工程用 Lombok 1.18.20，必须 JDK 8
$env:MAVEN_HOME = 'D:\java_web_develop\apache-maven-3.9.4-bin\apache-maven-3.9.4'
$cpFile = Join-Path $base '.dsh-cp-server.txt'

if ($Rebuild) {
    & mvn -B -o -f (Join-Path $base 'pom.xml') -pl campus-server -am -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { throw "编译失败" }
}

# ---------------------------------------------------------------------------
# 启动前先清端口：8080 被占用时 Spring 会报 "Port 8080 was already in use" 然后退出，
# 而最常见的原因就是【上一次的服务没关干净】（自己调试时也常中招）。
# 这里先找出占用者，确认是 java 才杀，避免误杀别的程序。
#
# 为什么用 netstat 而不是 Get-NetTCPConnection：
#   Get-NetTCPConnection 依赖 CIM 服务，在某些环境会返回空结果（实测踩过），
#   而 netstat -ano 是系统自带、一定能拿到 PID。
# ---------------------------------------------------------------------------
$port = 8080
function Get-PortOwnerPids([int]$p) {
    $pids = @()
    netstat -ano | Select-String ":$p\s" | ForEach-Object {
        $parts = ($_.Line -replace '\s+', ' ').Trim() -split ' '
        if ($parts.Length -ge 5 -and $parts[-2] -eq 'LISTENING') {
            $v = 0
            if ([int]::TryParse($parts[-1], [ref]$v)) { $pids += $v }
        }
    }
    return ($pids | Select-Object -Unique)
}

foreach ($ownerPid in (Get-PortOwnerPids $port)) {
    $p = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
    if ($p -and $p.ProcessName -eq 'java') {
        Write-Host "端口 $port 被上次残留的 java 进程占用（PID $($p.Id)），正在清理..." -ForegroundColor Yellow
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Milliseconds 300
            if (-not (Get-PortOwnerPids $port)) { break }
        }
    }
    else {
        # 拿不到进程信息（别的会话/管理员权限起的）—— 常见于"IDEA 里跑着一个旧实例"。
        # 以前这里直接报错退出，结果用户每次都得自己找 IDEA 停掉，很烦。
        # 现在先尝试按 PID 强杀（admin 权限下能成功），不行再提示手动处理。
        Write-Host "端口 $port 被一个看不到的进程占用（PID $ownerPid）—— 可能是 IDEA 里跑的旧实例" -ForegroundColor Yellow
        Write-Host "  正在尝试结束它..." -ForegroundColor Yellow
        & taskkill /F /PID $ownerPid 2>&1 | Out-Null
        Start-Sleep -Seconds 2
        if (Get-PortOwnerPids $port) {
            Write-Host "  ✗ 结束失败（权限不足）。请用【管理员权限】的 PowerShell 执行：" -ForegroundColor Red
            Write-Host "      taskkill /F /PID $ownerPid" -ForegroundColor Red
            Write-Host "    或者到 IDEA 里点红色停止按钮。" -ForegroundColor Red
            exit 1
        }
        Write-Host "  ✅ 已结束旧实例" -ForegroundColor Green
    }
}

if (-not (Test-Path -LiteralPath $cpFile)) {
    Write-Host 'classpath 缺失，正在生成...'
    & mvn -B -o -q -f (Join-Path $base 'campus-server\pom.xml') dependency:build-classpath `
        "-Dmdep.outputFile=$cpFile" '-DincludeScope=runtime'
}

# 用 fat jar 启动（classpath 方案在模块改名后不可用了：
# 依赖解析拿不到 campus-common/campus-pojo，因为本地仓库是只读的、没法 install）
$jar = Join-Path $base 'campus-server\target\campus-server-1.0-SNAPSHOT.jar'
# 源码比 jar 新 → 必须重新打包，否则跑的是旧代码（这个坑踩过很多次）
$needBuild = -not (Test-Path -LiteralPath $jar)
if (-not $needBuild) {
    $newestSrc = Get-ChildItem (Join-Path $base 'campus-common\src'), (Join-Path $base 'campus-pojo\src'), (Join-Path $base 'campus-server\src') -Recurse -File -ErrorAction SilentlyContinue |
                 Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($newestSrc -and $newestSrc.LastWriteTime -gt (Get-Item -LiteralPath $jar).LastWriteTime) {
        Write-Host "源码比 jar 新（$($newestSrc.Name)），重新打包..." -ForegroundColor Yellow
        $needBuild = $true
    }
}
if ($needBuild) {
    Write-Host 'jar 不存在，先打包...' -ForegroundColor Yellow
    & mvn -B -o -f (Join-Path $base 'pom.xml') -pl campus-server -am -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw '打包失败' }
}

Write-Host "启动 campus-server（JDK: $env:JAVA_HOME）..."
# 用参数数组传递，避免 PowerShell 把 -Dkey=value 拆成两个参数
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$javaArgs = @('-Dfile.encoding=UTF-8', '-jar', $jar)
& "$env:JAVA_HOME\bin\java.exe" @javaArgs
