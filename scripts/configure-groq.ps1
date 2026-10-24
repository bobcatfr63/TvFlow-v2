# Run interactively on the PC. No secret in command arguments or repository.
$ErrorActionPreference = 'Stop'
$secretDir = 'G:\DevServer\secrets\tvflow-groq'
New-Item -ItemType Directory -Force $secretDir | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $secretDir /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
Write-Host 'Use a NEW Groq Free API key. Do not enable billing.'
$key = Read-Host 'Groq key (hidden)' -AsSecureString
if ($key.Length -lt 20) { throw 'Key missing; nothing saved.' }
$key | Export-Clixml "$secretDir\groq-key.xml"
if (!(Test-Path "$secretDir\proxy-token.xml")) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $token = [Convert]::ToBase64String($bytes)
    # Avoid ConvertTo-SecureString: some locked-down Windows installations cannot
    # auto-load Microsoft.PowerShell.Security even though DPAPI-backed CLIXML works.
    $secureToken = New-Object System.Security.SecureString
    foreach ($character in $token.ToCharArray()) { $secureToken.AppendChar($character) }
    $secureToken.MakeReadOnly()
    $secureToken | Export-Clixml "$secretDir\proxy-token.xml"
    $secureToken.Dispose()
    $token = $null
}
Write-Host 'Saved with Windows DPAPI for this Windows account. No API call made.'
