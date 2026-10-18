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
    & mvn -B -o -f (Join-Path $base 'pom.xml') clean compile -DskipTests
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
    elseif ($p) {
        Write-Host "端口 $port 被【$($p.ProcessName)】(PID $($p.Id)) 占用，不是本项目的进程，请手动处理。" -ForegroundColor Red
        exit 1
    }
}

if (-not (Test-Path -LiteralPath $cpFile)) {
    Write-Host 'classpath 缺失，正在生成...'
    & mvn -B -o -q -f (Join-Path $base 'campus-server\pom.xml') dependency:build-classpath `
        "-Dmdep.outputFile=$cpFile" '-DincludeScope=runtime'
}

$deps = (Get-Content -LiteralPath $cpFile -Raw).Trim()
$modules = @('campus-common', 'campus-pojo', 'campus-server') |
    ForEach-Object { Join-Path $base "$_\target\classes" } |
    Where-Object { Test-Path -LiteralPath $_ }
$classpath = (($modules + $deps) -join ';')

Write-Host "启动 campus-server（JDK: $env:JAVA_HOME）..."
# 用参数数组传递，避免 PowerShell 把 -Dkey=value 拆成两个参数
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$javaArgs = @('-Dfile.encoding=UTF-8', '-cp', $classpath, 'com.campus.CampusApplication')
& "$env:JAVA_HOME\bin\java.exe" @javaArgs
