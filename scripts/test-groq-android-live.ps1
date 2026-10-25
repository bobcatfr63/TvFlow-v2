param([string]$Device = 'emulator-5554', [switch]$ConfigureHttps)
if ($Device -notmatch '^(emulator-555[46]|adb-R5CW50QQ6EM-f1VGl1\._adb-tls-connect\._tcp)$') { throw 'Unauthorized ADB target' }
$ErrorActionPreference = 'Stop'
$adb = 'G:\DevServer\android-sdk\platform-tools\adb.exe'
$secretDir = 'G:\DevServer\secrets\tvflow-groq'
$secure = Import-Clixml "$secretDir\proxy-token.xml"
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
$tempName = 'tvflow-proxy-' + [Guid]::NewGuid().ToString('N')
$tempPath = Join-Path $secretDir $tempName
$remotePath = "/data/local/tmp/$tempName"
try {
    & $adb -s $Device reverse tcp:18765 tcp:18765 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'ADB reverse failed' }
    [System.IO.File]::WriteAllText($tempPath, $token, (New-Object System.Text.UTF8Encoding($false)))
    & $adb -s $Device push $tempPath $remotePath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'ADB upload failed' }
    & $adb -s $Device shell run-as com.tvflow.app cp $remotePath files/groq_test_token | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Private storage copy failed' }
    $testClass = if ($ConfigureHttps) { 'com.tvflow.app.ui.search.GroqLiveDeviceTest#configurePrivateHttpsForPhone' } else { 'com.tvflow.app.ui.search.GroqLiveDeviceTest#realGroqRoundTrip' }
    & $adb -s $Device shell am instrument -w -e class $testClass com.tvflow.app.test/androidx.test.runner.AndroidJUnitRunner
    if ($LASTEXITCODE -ne 0) { throw 'Instrumentation failed' }
} finally {
    $token = $null
    Remove-Item $tempPath -Force -ErrorAction SilentlyContinue
    & $adb -s $Device shell rm -f $remotePath | Out-Null
    & $adb -s $Device shell run-as com.tvflow.app rm -f files/groq_test_token | Out-Null
}
