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
