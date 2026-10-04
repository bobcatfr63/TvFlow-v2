param(
    [string]$Root = "G:\DevServer",
    [string]$SourceRepository = "bobcatfr63/TVFlow",
    [string]$LegacyRepository = "bobcatfr63/iptv",
    [string]$LegacyReleasesRepository = "bobcatfr63/IPTV-Releases",
    [string]$TargetRepository = "bobcatfr63/TvFlow-v2"
)

$ErrorActionPreference = "Stop"

function Log([string]$Message) {
    Write-Host "[$(Get-Date -Format o)] $Message"
}

function Replace-Text([string]$Path, [string]$Old, [string]$New) {
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $content = Get-Content -Raw -LiteralPath $Path
    $updated = $content.Replace($Old, $New)
    if ($updated -ne $content) {
        Set-Content -Encoding UTF8 -LiteralPath $Path -Value $updated
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repoRoot
try {
    if (Test-Path ".tvflow-v2-consolidated") {
        Log "La consolidation complète est déjà marquée comme terminée."
        exit 0
    }

    Log "Import du TVFlow actif dans TvFlow-v2 sans écraser l'historique v2."
    git config user.name "TVFlow DevServer"
    git config user.email "devserver@local"

    if (-not (git remote | Select-String -SimpleMatch "source-tvflow")) {
        git remote add source-tvflow "https://github.com/$SourceRepository.git"
    }
    git fetch source-tvflow main
    if ($LASTEXITCODE -ne 0) { throw "Impossible de récupérer $SourceRepository" }

    git checkout source-tvflow/main -- .
    if ($LASTEXITCODE -ne 0) { throw "Impossible d'importer le snapshot TVFlow" }

    # Le workflow v2 existant est la référence. L'ancien workflow est retiré pour éviter
    # deux builds concurrents sur le même PC.
    if (Test-Path ".forgejo\workflows\local-build.yml") {
        Remove-Item -Force ".forgejo\workflows\local-build.yml"
    }

    Replace-Text "devserver.yml" "repository: bobcatfr63/TVFlow" "repository: bobcatfr63/TvFlow-v2"
    Replace-Text "devserver.yml" "repository_dir: G:\DevServer\repos\TVFlow" "repository_dir: G:\DevServer\repos\TvFlow-v2"

    Get-ChildItem -Path "app" -Recurse -File -Include *.kt,*.kts -ErrorAction SilentlyContinue | ForEach-Object {
        Replace-Text $_.FullName "github.com/bobcatfr63/IPTV-Releases/releases/download/tvflow-latest" "github.com/bobcatfr63/TvFlow-v2/releases/download/tvflow-latest"
    }

    New-Item -ItemType Directory -Force -Path "docs" | Out-Null
    @"
# Consolidation TVFlow

Dépôt canonique : `bobcatfr63/TvFlow-v2`

Sources réunies :
- `bobcatfr63/TVFlow` : code Android actif.
- `bobcatfr63/iptv` : historique legacy dans `archive/iptv-legacy`.
- `bobcatfr63/IPTV-Releases` : historique des manifests dans `archive/iptv-releases-manifests`.
- historique complet du dépôt TVFlow précédent dans `archive/tvflow-v1-history`.

Les APK sont distribués via les Releases de `TvFlow-v2`. Ils ne sont pas committés dans Git.
Chemin projet Windows canonique : `G:\DevServer\repos\TvFlow-v2`.
"@ | Set-Content -Encoding UTF8 "docs\MIGRATION.md"

    if (Get-Command gh -ErrorAction SilentlyContinue) {
        Log "Création de l'inventaire des anciennes releases."
        $legacyJson = gh api "/repos/$LegacyReleasesRepository/releases?per_page=100"
        if ($LASTEXITCODE -eq 0 -and $legacyJson) {
            Set-Content -Encoding UTF8 "docs\legacy-releases.json" $legacyJson
        }
    }

    git add -A
    git commit -m "chore: consolidate TVFlow into TvFlow-v2"
    if ($LASTEXITCODE -ne 0) { throw "Commit de consolidation impossible" }
    git push origin main
    if ($LASTEXITCODE -ne 0) { throw "Push de consolidation impossible" }

    Log "Préservation des historiques dans des branches d'archive."
    git push origin "source-tvflow/main:refs/heads/archive/tvflow-v1-history" --force

    if (-not (git remote | Select-String -SimpleMatch "legacy-iptv")) {
        git remote add legacy-iptv "https://github.com/$LegacyRepository.git"
    }
    git fetch legacy-iptv main
    if ($LASTEXITCODE -eq 0) {
        git push origin "legacy-iptv/main:refs/heads/archive/iptv-legacy" --force
    }

    if (-not (git remote | Select-String -SimpleMatch "legacy-releases")) {
        git remote add legacy-releases "https://github.com/$LegacyReleasesRepository.git"
    }
    git fetch legacy-releases main
    if ($LASTEXITCODE -eq 0) {
        git push origin "legacy-releases/main:refs/heads/archive/iptv-releases-manifests" --force
    }

    if (Get-Command gh -ErrorAction SilentlyContinue) {
        Log "Migration de l'APK stable courant et de ses métadonnées."
        $release = gh api "/repos/$LegacyReleasesRepository/releases/tags/tvflow-latest" | ConvertFrom-Json
        $latestApk = $release.assets | Where-Object { $_.name -like "*.apk" } | Sort-Object created_at -Descending | Select-Object -First 1
        $metadata = $release.assets | Where-Object { $_.name -in @("release.json","sha256.txt") }
        $assets = @($latestApk) + @($metadata) | Where-Object { $_ }

        gh release view tvflow-latest --repo $TargetRepository *> $null
        if ($LASTEXITCODE -ne 0) {
            gh release create tvflow-latest --repo $TargetRepository --title "TVFlow latest" --notes "Builds locaux validés de TVFlow v2."
        }

        $tempAssets = Join-Path $env:TEMP "tvflow-v2-assets"
        New-Item -ItemType Directory -Force $tempAssets | Out-Null
        foreach ($asset in $assets) {
            $dest = Join-Path $tempAssets $asset.name
            Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $dest -UseBasicParsing
            gh release upload tvflow-latest $dest --repo $TargetRepository --clobber
            Remove-Item -Force $dest
        }
        Remove-Item -Recurse -Force $tempAssets -ErrorAction SilentlyContinue
    }

    Log "Synchronisation du checkout canonique Windows."
    $targetDir = Join-Path $Root "repos\TvFlow-v2"
    if (Test-Path $targetDir) {
        Push-Location $targetDir
        try {
            git remote set-url origin "https://github.com/$TargetRepository.git"
            git fetch origin
            git checkout main
            git reset --hard origin/main
        } finally { Pop-Location }
    } elseif (Get-Command gh -ErrorAction SilentlyContinue) {
        gh repo clone $TargetRepository $targetDir
    }

    Set-Content -Encoding UTF8 ".tvflow-v2-consolidated" "Consolidation terminée $(Get-Date -Format o)"
    git add .tvflow-v2-consolidated
    git commit -m "chore: mark TvFlow-v2 consolidation complete"
    if ($LASTEXITCODE -eq 0) {
        git push origin main
    }
    Log "Consolidation terminée."
}
finally {
    Pop-Location
}
