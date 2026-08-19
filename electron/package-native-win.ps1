param(
    [string]$NativePath = $env:FM_AI_NATIVE_PATH
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($NativePath)) {
    $NativePath = Join-Path $root 'target\fm-ai-assistent.exe'
}

$NativePath = [System.IO.Path]::GetFullPath($NativePath)
if (-not (Test-Path -LiteralPath $NativePath -PathType Leaf)) {
    throw "Native executable not found: $NativePath. Build it with: mvn.cmd -Pnative -DskipTests native:compile"
}

$nativeOutput = Join-Path $root 'electron\resources\native'
New-Item -ItemType Directory -Path $nativeOutput -Force | Out-Null
Copy-Item -LiteralPath $NativePath -Destination (Join-Path $nativeOutput 'fm-ai-assistent.exe') -Force

$nativeDirectory = Split-Path -Parent $NativePath
Get-ChildItem -LiteralPath $nativeDirectory -Filter '*.dll' -File | Copy-Item -Destination $nativeOutput -Force

Push-Location $root
try {
    & npm.cmd run build-win
    if ($LASTEXITCODE -ne 0) {
        throw "Windows packaging failed (exit code $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}
