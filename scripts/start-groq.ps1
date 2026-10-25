param([switch]$Verify)
$ErrorActionPreference = 'Stop'
$secretDir = 'G:\DevServer\secrets\tvflow-groq'
function Read-LocalSecret($path) {
    $secure = Import-Clixml $path
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
try {
    $env:GROQ_API_KEY = Read-LocalSecret "$secretDir\groq-key.xml"
    $env:TVFLOW_PROXY_TOKEN = Read-LocalSecret "$secretDir\proxy-token.xml"
    $env:TVFLOW_GROQ_STATE = 'G:\DevServer\data\tvflow-groq\budget.sqlite3'
    $env:TVFLOW_GROQ_DAILY_CAP = '50'
    if ($Verify) { & python "$PSScriptRoot\test_groq_live.py" }
    else { & python "$PSScriptRoot\groq_proxy.py" }
    if ($LASTEXITCODE -ne 0) { throw "Groq verification or gateway failed (code $LASTEXITCODE)." }
} finally {
    Remove-Item Env:GROQ_API_KEY,Env:TVFLOW_PROXY_TOKEN -ErrorAction SilentlyContinue
}
