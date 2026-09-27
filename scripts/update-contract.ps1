param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version
)

$ErrorActionPreference = 'Stop'
$repoPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$targetDirectory = [System.IO.Path]::GetFullPath((Join-Path $repoPath 'src/main/openapi'))
$targetPath = Join-Path $targetDirectory 'connection-api.yaml'
$fileId = [guid]::NewGuid()
$stagePath = Join-Path $targetDirectory ".connection-api-$fileId.yaml"
$backupPath = Join-Path $targetDirectory ".connection-api-$fileId.backup"
$tempPath = Join-Path ([System.IO.Path]::GetTempPath()) ("portable-agent-contracts-" + [guid]::NewGuid())
$archiveName = "portable-agent-contracts-$Version.tgz"
$archivePath = Join-Path $tempPath $archiveName
$checksumPath = Join-Path $tempPath 'SHA256SUMS'
$releaseUrl = "https://github.com/portable-agent/contracts/releases/download/v$Version"

try {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw 'GitHub CLI is required to verify the contract attestation.'
    }
    New-Item -ItemType Directory -Path $tempPath | Out-Null
    New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
    Invoke-WebRequest -Uri "$releaseUrl/$archiveName" -OutFile $archivePath
    Invoke-WebRequest -Uri "$releaseUrl/SHA256SUMS" -OutFile $checksumPath

    $escapedArchiveName = [regex]::Escape($archiveName)
    $checksumLines = @(Get-Content -LiteralPath $checksumPath | Where-Object {
        $_ -match "^(?<hash>[a-fA-F0-9]{64})\s+\*?$escapedArchiveName$"
    })
    if ($checksumLines.Count -ne 1) {
        throw 'Checksum file does not contain exactly one entry for the contract bundle.'
    }
    $null = $checksumLines[0] -match '^(?<hash>[a-fA-F0-9]{64})'
    $expectedHash = $Matches.hash.ToUpperInvariant()
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash
    if ($actualHash -ne $expectedHash) {
        throw 'Checksum contract bundle does not match the release.'
    }

    & gh attestation verify $archivePath --repo portable-agent/contracts
    if ($LASTEXITCODE -ne 0) {
        throw 'Cannot verify the GitHub attestation for the contract bundle.'
    }

    & tar -xzf $archivePath -C $tempPath 'package/openapi/connection-api.yaml'
    if ($LASTEXITCODE -ne 0) {
        throw 'Cannot unpack contract bundle.'
    }

    $sourcePath = Join-Path $tempPath 'package/openapi/connection-api.yaml'
    $sourceText = Get-Content -Raw -LiteralPath $sourcePath
    if ($sourceText -notmatch "(?m)^  version: $([regex]::Escape($Version))$") {
        throw 'Connection API version does not match the requested release.'
    }
    Copy-Item -LiteralPath $sourcePath -Destination $stagePath

    if (Test-Path -LiteralPath $targetPath) {
        [System.IO.File]::Replace($stagePath, $targetPath, $backupPath, $true)
    } else {
        Move-Item -LiteralPath $stagePath -Destination $targetPath
    }

    Write-Output "Connection API updated to version $Version."
} finally {
    foreach ($localPath in @($stagePath, $backupPath)) {
        if (Test-Path -LiteralPath $localPath) {
            Remove-Item -LiteralPath $localPath -Force
        }
    }
    $resolvedTempPath = [System.IO.Path]::GetFullPath($tempPath)
    $systemTempPath = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTempPath.StartsWith($systemTempPath, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $resolvedTempPath)) {
        Remove-Item -LiteralPath $resolvedTempPath -Recurse -Force
    }
}
