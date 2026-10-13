# This script intentionally deletes database records only after human confirmation.
# It NEVER deletes or cleans data/uploads (or a configured upload root).
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envPath = Join-Path $root '.env'

function Import-UniDocEnvironment {
    foreach ($line in [System.IO.File]::ReadAllLines($envPath)) {
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

try {
    Set-Location $root
    if (-not (Test-Path $envPath)) {
        throw 'Missing .env. Configure your local MySQL credentials before attempting a reset.'
    }
    Import-UniDocEnvironment
    if ($env:UNIDOC_DB_URL -notmatch '^jdbc:mysql://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]{1,5})?/unidoc(\?.*)?$') {
        throw 'Reset refused: the JDBC URL must target local MySQL schema unidoc.'
    }
    $displayHost = $matches[1]
    $port = if ($matches[2]) { $matches[2].TrimStart(':') } else { '3306' }
    if ($env:UNIDOC_DB_URL -match '(?i)[?&](user|password)=') {
        throw 'Reset refused: credentials must not be embedded in the JDBC URL.'
    }
    if ([string]::IsNullOrWhiteSpace($env:UNIDOC_DB_USERNAME) -or
        [string]::IsNullOrWhiteSpace($env:UNIDOC_DB_PASSWORD) -or
        $env:UNIDOC_DB_PASSWORD -eq 'replace_me') {
        throw 'Reset refused: invalid/missing database application credentials.'
    }

    Write-Host ''
    Write-Host '========================================================'
    Write-Host '          UniDoc LOCAL DATABASE RESET'
    Write-Host '========================================================'
    Write-Host "Target database: jdbc:mysql://$displayHost`:$port/unidoc"
    Write-Host "Database user: $env:UNIDOC_DB_USERNAME"
    Write-Warning 'ALL records in the unidoc database tables will be permanently deleted.'
    Write-Warning 'Uploaded files will be PRESERVED, which may leave orphan files.'
    Write-Warning 'Stop the normal UniDoc backend before continuing.'
    Write-Host 'This operation will never stop another process or remove files.'

    $backendListeners = @(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)
    if ($backendListeners.Count -gt 0) {
        $ownerPid = $backendListeners[0].OwningProcess
        $ownerProcess = (Get-Process -Id $ownerPid -ErrorAction SilentlyContinue).ProcessName
        throw "Port 8080 is occupied by PID $ownerPid ($ownerProcess). Stop the backend manually before reset."
    }
    $typed = Read-Host 'To delete all UniDoc database records, type RESET exactly (anything else cancels)'
    if ($typed -cne 'RESET') {
        Write-Host 'Reset canceled; no database operation was started.'
        exit 0
    }
    $reseedAnswer = Read-Host 'Reseed all demo fixtures immediately afterwards? [Y/n]'
    $reseed = ($reseedAnswer -eq '' -or $reseedAnswer -match '^[Yy]$')
    if (-not $reseed -and $reseedAnswer -notmatch '^[Nn]$') {
        Write-Host 'Invalid reseed choice; reset canceled.'
        exit 0
    }
    if (-not (Test-Path (Join-Path $root 'backend\mvnw.cmd'))) {
        throw 'Maven Wrapper not found.'
    }
    $env:UNIDOC_RESET_DEMO = 'true'
    $env:UNIDOC_RESET_CONFIRMATION = 'RESET-UNIDOC-DEMO'
    $env:SERVER_PORT = '0'
    $env:UNIDOC_SEED_DEMO = if ($reseed) { 'true' } else { 'false' }
    $env:UNIDOC_EMAIL_ENABLED = 'false'
    Write-Host 'Backend reset guards enabled. Running non-web reset runner...'
    & (Join-Path $root 'backend\mvnw.cmd') -f (Join-Path $root 'backend\pom.xml') spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "Backend guarded reset failed (exit code $LASTEXITCODE); review backend output."
    }
    if ($reseed) {
        Write-Host 'Database reset and deterministic demo reseed completed.'
    } else {
        Write-Host 'Database reset completed without reseeding. Run start-dev.cmd with UNIDOC_SEED_DEMO=true to seed.'
    }
    Write-Warning 'Previously uploaded files remain on disk. Review any orphan files manually after confirming the root.'
    exit 0
} catch {
    Write-Error "UniDoc reset was not completed successfully: $($_.Exception.Message)"
    exit 1
}
