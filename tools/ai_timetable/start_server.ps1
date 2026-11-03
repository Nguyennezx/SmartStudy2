param([string]$BindAddress = '127.0.0.1', [int]$Port = 8765)
$ErrorActionPreference = 'Stop'
$pythonPath = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$configPath = Join-Path $PSScriptRoot 'connection.local.json'
if (!(Test-Path -LiteralPath $configPath)) {
    & $pythonPath -c "import json,secrets,pathlib,sys; pathlib.Path(sys.argv[1]).write_text(json.dumps({'token':secrets.token_urlsafe(32)},indent=2),encoding='utf-8')" $configPath
    if ($LASTEXITCODE -ne 0) { throw 'Khong tao duoc ma ket noi.' }
}
$connection = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$env:AI_IMPORT_TOKEN = $connection.token
if (!$env:GEMINI_API_KEY) { $env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY', 'User') }
& $pythonPath (Join-Path $PSScriptRoot 'server.py') --host $BindAddress --port $Port --model gemini-3.5-flash-lite
