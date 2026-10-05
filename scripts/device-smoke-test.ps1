param(
    [string]$ApkPath = "",
    [switch]$RequireDevice
)

$ErrorActionPreference = "Stop"
$package = "com.tvflow.app"
$activity = "com.tvflow.app/.MainActivity"
$root = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $root "local-output"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    foreach ($candidate in @(
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
        "G:\DevServer\android-sdk\platform-tools\adb.exe",
        "D:\DevServer\android-sdk\platform-tools\adb.exe"
    )) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    throw "ADB introuvable. Vérifier ANDROID_HOME/ANDROID_SDK_ROOT."
}

function Resolve-Apk([string]$requested) {
    if ($requested) {
        $resolved = Resolve-Path $requested -ErrorAction Stop
        return $resolved.Path
    }

    $candidates = @()
    if (Test-Path $outputDir) {
        $candidates += Get-ChildItem $outputDir -Filter *.apk -File -ErrorAction SilentlyContinue
    }
    $apkTree = Join-Path $root "app\build\outputs\apk"
    if (Test-Path $apkTree) {
        $candidates += Get-ChildItem $apkTree -Filter *.apk -File -Recurse -ErrorAction SilentlyContinue
    }
    $selected = $candidates | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (-not $selected) { throw "Aucun APK TVFlow trouvé." }
    return $selected.FullName
}

$adb = Resolve-Adb
$apk = Resolve-Apk $ApkPath
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $outputDir "device-smoke-$stamp.txt"
$summary = New-Object System.Collections.Generic.List[string]
$failures = 0

$summary.Add("TVFlow device smoke test")
$summary.Add("Date: $(Get-Date -Format o)")
$summary.Add("APK: $apk")
$summary.Add("ADB: $adb")
$summary.Add("")

& $adb start-server | Out-Null
$devices = @(& $adb devices | Select-Object -Skip 1 | ForEach-Object {
    $parts = ($_ -split "\s+")
    if ($parts.Count -ge 2 -and $parts[1] -eq "device") { $parts[0] }
} | Where-Object { $_ })

if ($devices.Count -eq 0) {
    $summary.Add("NO_DEVICE: aucun appareil Android autorisé détecté.")
    $summary | Set-Content -Encoding UTF8 $summaryPath
    if ($RequireDevice) {
        Write-Error "Aucun appareil ADB détecté."
        exit 20
    }
    Write-Warning "Aucun appareil ADB détecté. Build non validé sur appareil."
    exit 0
}

foreach ($serial in $devices) {
    $safeSerial = ($serial -replace "[^A-Za-z0-9_.-]", "_")
    $logPath = Join-Path $outputDir "device-smoke-$safeSerial-$stamp-logcat.txt"
    $model = (& $adb -s $serial shell getprop ro.product.model 2>$null).Trim()
    $sdk = (& $adb -s $serial shell getprop ro.build.version.sdk 2>$null).Trim()

    $summary.Add("DEVICE $serial | model=$model | sdk=$sdk")

    try {
        & $adb -s $serial logcat -c | Out-Null

        $installOutput = (& $adb -s $serial install -r $apk 2>&1 | Out-String).Trim()
        $summary.Add("INSTALL: $installOutput")
        if ($LASTEXITCODE -ne 0 -or $installOutput -notmatch "Success") {
            throw "Installation ADB échouée."
        }

        & $adb -s $serial shell am force-stop $package | Out-Null
        Start-Sleep -Milliseconds 800

        $startOutput = (& $adb -s $serial shell am start -W -n $activity 2>&1 | Out-String).Trim()
        $summary.Add("START: " + ($startOutput -replace "\r?\n", " | "))
        if ($LASTEXITCODE -ne 0 -or $startOutput -match "Error|Exception") {
            throw "Démarrage de MainActivity échoué."
        }

        Start-Sleep -Seconds 8

        $appPid = (& $adb -s $serial shell pidof $package 2>$null | Out-String).Trim()
        if (-not $appPid) { throw "Le processus TVFlow n'est plus actif après le lancement." }
        $summary.Add("PROCESS: alive pid=$appPid")

        $activityDump = (& $adb -s $serial shell dumpsys activity activities 2>$null | Out-String)
        $hasActivity = $activityDump -match [regex]::Escape($package)
        $summary.Add("ACTIVITY: " + $(if ($hasActivity) { "visible/running" } else { "process only" }))

        $logcat = (& $adb -s $serial logcat -d -v threadtime 2>&1 | Out-String)
        $logcat | Set-Content -Encoding UTF8 $logPath

        $fatalPatterns = @(
            "FATAL EXCEPTION",
            "Process:\s+com\.tvflow\.app",
            "ANR in com\.tvflow\.app",
            "Fatal signal .*com\.tvflow\.app"
        )
        $fatal = $false
        foreach ($pattern in $fatalPatterns) {
            if ($logcat -match $pattern) { $fatal = $true; break }
        }
        if ($fatal) { throw "Crash/ANR détecté dans Logcat." }

        $summary.Add("LOGCAT: PASS")
        $summary.Add("RESULT: PASS")
    }
    catch {
        $failures++
        $summary.Add("RESULT: FAIL - $($_.Exception.Message)")
        try {
            (& $adb -s $serial logcat -d -v threadtime 2>&1 | Out-String) |
                Set-Content -Encoding UTF8 $logPath
        } catch {}
    }

    $summary.Add("")
}

$summary | Set-Content -Encoding UTF8 $summaryPath
$summary | ForEach-Object { Write-Host $_ }

if ($failures -gt 0) {
    Write-Error "$failures appareil(s) ont échoué au smoke-test TVFlow."
    exit 30
}

Write-Host "TVFlow validé sur $($devices.Count) appareil(s) ADB."
exit 0
