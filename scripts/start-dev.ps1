# Local UniDoc launcher. Never deletes data or stops a process it did not create.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envPath = Join-Path $root '.env'
$examplePath = Join-Path $root '.env.example'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Import-UniDocEnvironment {
    param([string]$Path)
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $key = $matches[1]
            $value = $matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
}

function Assert-AvailablePort {
    param([int]$Port)
    $used = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($used.Count -gt 0) {
        $pidOwner = $used[0].OwningProcess
        $name = (Get-Process -Id $pidOwner -ErrorAction SilentlyContinue).ProcessName
        throw "Port $Port is already listening (PID $pidOwner; process: $name). Inspect this process manually; UniDoc will not stop it."
    }
}

try {
    Set-Location $root
    foreach ($executable in @('java.exe', 'node.exe', 'npm.cmd')) {
        if (-not (Get-Command $executable -ErrorAction SilentlyContinue)) {
            throw "Missing $executable. Install Java JDK 21 and a supported Node.js/npm release before starting."
        }
    }
    $javaVersion = (& cmd.exe /c 'java -version 2>&1' | Select-Object -First 1) -join ''
    if ($javaVersion -notmatch 'version "21([\."-])') {
        throw "Java 21 is required. Detected: $javaVersion"
    }
    $nodeVersion = (& node.exe --version).TrimStart('v')
    $nodeSegments = @($nodeVersion.Split('.'))
    $nodeMajor = [int]$nodeSegments[0]
    $nodeMinor = [int]$nodeSegments[1]
    if ($nodeMajor -lt 20 -or ($nodeMajor -eq 20 -and $nodeMinor -lt 19) -or
        ($nodeMajor -eq 22 -and $nodeMinor -lt 12) -or $nodeMajor -eq 21) {
        throw "Unsupported Node.js version $nodeVersion. Use Node.js 22.12+ or 20.19+."
    }
    if (-not (Test-Path $envPath)) {
        [System.IO.File]::Copy($examplePath, $envPath)
        Write-Host 'Created .env from .env.example. Database credentials must be configured.'
    }
    Import-UniDocEnvironment $envPath
    if ([string]::IsNullOrWhiteSpace($env:UNIDOC_JWT_SECRET) -or
        $env:UNIDOC_JWT_SECRET.StartsWith('replace_')) {
        $bytes = New-Object byte[] 32
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
        $secret = [Convert]::ToBase64String($bytes)
        $lines = [System.IO.File]::ReadAllLines($envPath)
        $present = $false
        $updated = New-Object 'System.Collections.Generic.List[string]'
        foreach ($line in $lines) {
            if ($line -match '^\s*UNIDOC_JWT_SECRET\s*=') {
                $updated.Add("UNIDOC_JWT_SECRET=$secret")
                $present = $true
            } else {
                $updated.Add($line)
            }
        }
        if (-not $present) { $updated.Add("UNIDOC_JWT_SECRET=$secret") }
        [System.IO.File]::WriteAllLines($envPath, $updated.ToArray(), $utf8NoBom)
        $env:UNIDOC_JWT_SECRET = $secret
        Write-Host 'Generated and saved a private Base64 JWT secret in .env.'
    }
    try {
        if ([Convert]::FromBase64String($env:UNIDOC_JWT_SECRET).Length -lt 32) {
            throw 'JWT secret is shorter than 32 bytes.'
        }
    } catch { throw 'UNIDOC_JWT_SECRET must decode to at least 32 bytes.' }
    if ([string]::IsNullOrWhiteSpace($env:UNIDOC_DB_USERNAME) -or
        [string]::IsNullOrWhiteSpace($env:UNIDOC_DB_PASSWORD) -or
        $env:UNIDOC_DB_PASSWORD -eq 'replace_me') {
        throw 'Edit the .env MySQL application username/password before starting. Nothing was launched.'
    }
    if ($env:UNIDOC_DB_URL -notmatch '^jdbc:mysql://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]{1,5})?/unidoc(\?.*)?$') {
        throw 'UNIDOC_DB_URL must target the local unidoc MySQL schema.'
    }
    $dbHostName = $matches[1].TrimStart('[').TrimEnd(']')
    $dbPort = 3306
    if ($matches[2]) { $dbPort = [int]$matches[2].TrimStart(':') }
    if ($env:UNIDOC_DB_URL -match '(?i)[?&](user|password)=') {
        throw 'Keep credentials outside the JDBC URL in separate .env variables.'
    }
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connection = $client.BeginConnect($dbHostName, $dbPort, $null, $null)
        if (-not $connection.AsyncWaitHandle.WaitOne(3000)) {
            throw "Cannot connect to MySQL at $dbHostName`:$dbPort. Start MySQL and verify the port."
        }
        $client.EndConnect($connection)
    } finally { $client.Dispose() }
    Assert-AvailablePort 8080
    Assert-AvailablePort 5173
    if ($env:UNIDOC_RESET_DEMO -eq 'true') {
        throw 'Reset mode is enabled in .env; disable it and use reset-demo-data.cmd for guarded reset.'
    }

    $publicUrl = $env:VITE_API_BASE_URL
    if ([string]::IsNullOrWhiteSpace($publicUrl)) { $publicUrl = 'http://localhost:8080' }
    if ($publicUrl -ne 'http://localhost:8080') {
        Write-Warning "VITE_API_BASE_URL is $publicUrl; ensure CORS/API settings match the actual backend."
    }
    [System.IO.File]::WriteAllText((Join-Path $root 'frontend\.env.local'),
            "VITE_API_BASE_URL=$publicUrl`n", $utf8NoBom)
    $uploadRoot = $env:UNIDOC_STORAGE_ROOT
    if ([string]::IsNullOrWhiteSpace($uploadRoot)) { $uploadRoot = './data/uploads' }
    if (-not [System.IO.Path]::IsPathRooted($uploadRoot)) { $uploadRoot = Join-Path $root $uploadRoot }
    $uploadRoot = [System.IO.Path]::GetFullPath($uploadRoot)
    if ($uploadRoot -match '[\\/]frontend[\\/]public([\\/]|$)' -or
        $uploadRoot -match '[\\/]backend[\\/]src[\\/]main[\\/]resources[\\/]static([\\/]|$)') {
        throw 'Storage must not be configured inside a public directory.'
    }
    New-Item -ItemType Directory -Path (Join-Path $uploadRoot '.tmp') -Force | Out-Null
    if (-not (Test-Path (Join-Path $root 'backend\mvnw.cmd'))) { throw 'Maven Wrapper is missing.' }
    $modules = Join-Path $root 'frontend\node_modules'
    $installedLock = Join-Path $modules '.package-lock.json'
    $sourceLock = Join-Path $root 'frontend\package-lock.json'
    if (-not (Test-Path $installedLock) -or
        (Get-Item $installedLock).LastWriteTimeUtc -lt (Get-Item $sourceLock).LastWriteTimeUtc) {
        Write-Host 'Frontend dependencies missing or stale; running npm ci...'
        Push-Location (Join-Path $root 'frontend')
        try {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed (exit $LASTEXITCODE)." }
        } finally { Pop-Location }
    }

    Write-Host "Starting API on port 8080 (local MySQL $dbHostName`:$dbPort, schema unidoc)."
    Write-Host 'Waiting up to 180 seconds for GET http://localhost:8080/api/health.'
    $backendCommand = '& .\backend\mvnw.cmd -f backend\pom.xml spring-boot:run'
    Start-Process -FilePath 'powershell.exe' -WorkingDirectory $root -ArgumentList @(
        '-NoProfile', '-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $backendCommand) | Out-Null
    $deadline = (Get-Date).AddSeconds(180)
    $healthy = $false
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri 'http://localhost:8080/api/health' -TimeoutSec 2
            if ($health.status -eq 'ok') { $healthy = $true; break }
        } catch { } # API not listening yet; keep waiting until the displayed deadline
        Start-Sleep -Seconds 2
    }
    if (-not $healthy) {
        throw 'API was not healthy within 180 seconds. Read the backend terminal for MySQL/JWT/storage errors.'
    }
    Write-Host 'Backend health check succeeded. Starting Vite on port 5173.'
    Start-Process -FilePath 'powershell.exe' -WorkingDirectory $root -ArgumentList @(
        '-NoProfile', '-NoExit', '-ExecutionPolicy', 'Bypass', '-Command',
        'npm.cmd --prefix frontend run dev -- --host localhost --port 5173 --strictPort') | Out-Null
    Start-Sleep -Seconds 2
    Start-Process 'http://localhost:5173'
    Write-Host 'UniDoc is starting at http://localhost:5173. Backend and frontend run in separate terminals.'
    exit 0
} catch {
    Write-Error "UniDoc startup failed: $($_.Exception.Message)"
    exit 1
}
