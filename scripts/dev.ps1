param(
    [ValidateSet('test','run','package')][string]$Task = 'run',
    [ValidateRange(1024,65535)][int]$Port = 8080,
    [string]$DatabaseUrl = '',
    [string]$Profiles = 'dev',
    [ValidateSet('127.0.0.1','0.0.0.0')][string]$BindAddress = '127.0.0.1'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdk = Get-ChildItem (Join-Path $projectRoot '.tools') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName; $env:PATH = "$($jdk.FullName)\bin;$env:PATH" }
$maven = Join-Path $projectRoot '.tools\apache-maven-3.9.9\bin\mvn.cmd'
if (!(Test-Path $maven)) { $maven = 'mvn' }
Push-Location $projectRoot
try {
    if ($Task -eq 'run') {
        $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($listener) {
            $processIds = ($listener.OwningProcess | Sort-Object -Unique) -join ', '
            throw "端口 $Port 已被进程 $processIds 占用。请在原后端终端按 Ctrl+C，等待服务退出后再运行本命令；不要同时启动两个使用同一数据库的后端。"
        }
        $appArguments = "--server.port=$Port --server.address=$BindAddress"
        if ($DatabaseUrl) { $appArguments += " --spring.datasource.url=$DatabaseUrl" }
        & $maven spring-boot:run "-Dspring-boot.run.profiles=$Profiles" "-Dspring-boot.run.arguments=$appArguments"
    }
    else { & $maven $Task }
    if ($LASTEXITCODE -ne 0) { throw "Maven 执行失败，退出码 $LASTEXITCODE" }
} finally { Pop-Location }
