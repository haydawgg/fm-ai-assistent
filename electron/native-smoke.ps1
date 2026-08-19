param(
    [string]$NativePath = $env:FM_AI_NATIVE_PATH
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($NativePath)) {
    $NativePath = Join-Path (Get-Location) 'target\fm-ai-assistent.exe'
}

$NativePath = [System.IO.Path]::GetFullPath($NativePath)
if (-not (Test-Path -LiteralPath $NativePath -PathType Leaf)) {
    throw "Native executable not found: $NativePath"
}

$port = 18080
$baseUrl = "http://127.0.0.1:$port"
$process = $null

try {
    $process = Start-Process -FilePath $NativePath `
        -ArgumentList "--server.port=$port" `
        -PassThru `
        -WindowStyle Hidden

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Seconds 1
        if ($process.HasExited) {
            throw "Native executable exited before becoming ready (code $($process.ExitCode))."
        }
        try {
            $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') {
                $ready = $true
                break
            }
        } catch {
            # Spring Boot may still be binding the port.
        }
    }

    if (-not $ready) {
        throw "Native executable did not become healthy within 60 seconds."
    }

    $previousSmokeUrl = $env:FM_AI_SMOKE_URL
    $env:FM_AI_SMOKE_URL = $baseUrl
    try {
        & node (Join-Path (Get-Location) 'electron\desktop-smoke.mjs')
        if ($LASTEXITCODE -ne 0) {
            throw "Desktop smoke checks failed for the native executable (exit code $LASTEXITCODE)."
        }
    } finally {
        $env:FM_AI_SMOKE_URL = $previousSmokeUrl
    }
} finally {
    if ($null -ne $process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F | Out-Null
    }
}
