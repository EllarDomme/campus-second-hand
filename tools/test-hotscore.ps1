# 一键跑热度分自检（不需要 mvn test，不需要联网）
#
# 用法：powershell -ExecutionPolicy Bypass -File .\tools\test-hotscore.ps1
#
# 为什么不用 mvn test：
#   本机离线 + 本地 Maven 仓库只读，surefire 的 junit-platform 插件下不下来，
#   所以这里用"编译一个 main 类 + 手写断言"的土办法，但结果一样可靠。

$ErrorActionPreference = 'Stop'
$base = Split-Path -Parent $PSScriptRoot
Set-Location $base

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk1.8.0_202' }
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'

# 1) 先编译（改了代码必须重新编译，否则跑的是旧的）
Write-Host '[1/4] 编译 campus-server...' -ForegroundColor Cyan
& mvn -B -o -q -f (Join-Path $base 'pom.xml') -pl campus-server -am compile -DskipTests 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host '  编译失败，先修编译错误' -ForegroundColor Red
    exit 1
}
Write-Host '  ✅ 编译通过' -ForegroundColor Green

# 2) 解出依赖 jar（跑 main 需要完整类路径）
$libs = Join-Path $base '..\.dsh-libs'
$libs = [System.IO.Path]::GetFullPath((Join-Path $base '..\.dsh-libs'))
if (-not (Test-Path $libs)) {
    Write-Host '[2/4] 解出依赖 jar...' -ForegroundColor Cyan
    $jar = Join-Path $base 'campus-server\target\campus-server-1.0-SNAPSHOT.jar'
    if (-not (Test-Path $jar)) {
        & mvn -B -o -q -f (Join-Path $base 'pom.xml') -pl campus-server -am -DskipTests package 2>&1 | Out-Null
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    New-Item -ItemType Directory -Path $libs -Force | Out-Null
    foreach ($e in $zip.Entries) {
        if ($e.FullName -like 'BOOT-INF/lib/*.jar') {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $libs ([System.IO.Path]::GetFileName($e.FullName))), $true)
        }
    }
    $zip.Dispose()
    Write-Host '  ✅ 依赖已解出' -ForegroundColor Green
} else {
    Write-Host '[2/4] 依赖已存在，跳过' -ForegroundColor DarkGray
}

# 3) 编译自检器
Write-Host '[3/4] 编译自检器...' -ForegroundColor Cyan
$out = Join-Path $base '..\.dsh-hotcheck'
$out = [System.IO.Path]::GetFullPath((Join-Path $base '..\.dsh-hotcheck'))
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out -Force | Out-Null
$src = Join-Path $base 'tools\HotScoreCheck.java'
& $javac -encoding UTF-8 -cp (Join-Path $base 'campus-server\target\classes') -d $out $src 2>&1 | ForEach-Object { '  ' + $_ }
if ($LASTEXITCODE -ne 0) {
    Write-Host '  自检器编译失败' -ForegroundColor Red
    exit 1
}

# 4) 运行
Write-Host '[4/4] 运行自检...' -ForegroundColor Cyan
Write-Host ''
$cp = (@(
    (Join-Path $base 'campus-server\target\classes'),
    $out,
    "$libs\*"
) -join ';')
$javaArgs = @('-Dfile.encoding=UTF-8', '-cp', $cp, 'HotScoreCheck')
& $java @javaArgs
$code = $LASTEXITCODE
Write-Host ''
if ($code -eq 0) {
    Write-Host '✅ 全部通过' -ForegroundColor Green
} else {
    Write-Host '❌ 有失败用例，看上面的 FAIL 行' -ForegroundColor Red
}
exit $code
