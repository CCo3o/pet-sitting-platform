param([ValidateRange(1024,65535)][int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($listener) { throw "端口 $Port 已被占用。请在原后端终端按 Ctrl+C，等退出后再运行。" }
$secret = Read-Host '请粘贴 DeepSeek API Key，然后按回车（输入不会显示）' -AsSecureString
$env:DEEPSEEK_API_KEY = [System.Net.NetworkCredential]::new('', $secret).Password.Trim()
if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) { throw '密钥为空，请重新运行本命令并输入密钥。' }
Remove-Variable secret
try { & (Join-Path $PSScriptRoot 'dev.ps1') run -Port $Port }
finally { Remove-Item Env:\DEEPSEEK_API_KEY -ErrorAction SilentlyContinue }
