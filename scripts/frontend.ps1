param([ValidateSet('dev','build','test')][string]$Task = 'dev')
$ErrorActionPreference = 'Stop'
$frontendRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend'
Push-Location $frontendRoot
try {
    if (!(Test-Path 'node_modules')) { throw '请先在 frontend 目录执行 npm ci 安装依赖。' }
    $npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
    $npxCommand = Get-Command npx.cmd -ErrorAction SilentlyContinue
    if ($Task -eq 'test' -and $npxCommand) { & $npxCommand.Source playwright test }
    elseif ($Task -ne 'test' -and $npmCommand) { & $npmCommand.Source run $Task }
    else {
        # Installed dependencies can run directly when npm is absent from PATH.
        $nodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
        $nodePath = if ($nodeCommand) { $nodeCommand.Source } else {
            Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
        }
        if (!(Test-Path -LiteralPath $nodePath)) { throw 'Node.js is unavailable. Install Node.js and reopen the terminal.' }
        switch ($Task) {
            'dev' { & $nodePath 'node_modules\vite\bin\vite.js' --host 127.0.0.1 }
            'build' {
                & $nodePath 'node_modules\vue-tsc\bin\vue-tsc.js' -b
                if ($LASTEXITCODE -ne 0) { throw "TypeScript check failed: $LASTEXITCODE" }
                & $nodePath 'node_modules\vite\bin\vite.js' build
            }
            'test' { & $nodePath 'node_modules\@playwright\test\cli.js' test }
        }
    }
    if ($LASTEXITCODE -ne 0) { throw "前端命令失败，退出码 $LASTEXITCODE" }
} finally { Pop-Location }
