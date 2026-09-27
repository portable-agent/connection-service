$ErrorActionPreference = "Stop"

$requiredFiles = @(
    "README.md",
    "AGENTS.md",
    "SERVICE.md",
    "catalog-info.yaml",
    "mkdocs.yml",
    "docs/index.md",
    "docs/architecture.md",
    "docs/development.md",
    "docs/runbook.md",
    "src/main/openapi/connection-api.yaml"
)

$missingFiles = $requiredFiles | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) }
if ($missingFiles.Count -gt 0) {
    throw "Missing required files: $($missingFiles -join ', ')"
}

$catalogText = Get-Content -LiteralPath "catalog-info.yaml" -Raw
if ($catalogText -notmatch "backstage\.io/techdocs-ref:\s*dir:\.") {
    throw "catalog-info.yaml must contain backstage.io/techdocs-ref: dir:."
}

$mkdocsText = Get-Content -LiteralPath "mkdocs.yml" -Raw
if ($mkdocsText -notmatch "(?m)^docs_dir:\s*docs\s*$") {
    throw "mkdocs.yml must contain docs_dir: docs."
}

$contractText = Get-Content -LiteralPath "src/main/openapi/connection-api.yaml" -Raw
if ($contractText -notmatch "(?m)^  version: 2\.7\.0$") {
    throw "connection-api.yaml must use portable-agent/contracts version 2.7.0."
}

Write-Host "connection-service documentation matches the standard."
