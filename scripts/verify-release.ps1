# Issue 20 local release gates. PowerShell 5.1 compatible; only test/build commands.
# Writes reviewable evidence under ignored release-evidence/; does not load .env.
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmssZ')
$evidence = Join-Path $root (Join-Path 'release-evidence' $stamp)
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$results = New-Object System.Collections.Generic.List[object]

Write-Host "UniDoc Issue 20 - local automated release gates"
Write-Host "Evidence: $evidence"
Write-Host 'No database reset, live SMTP test, or browser UAT is performed by this script.'

function Invoke-Gate {
    param(
        [string]$Name,
        [string]$WorkingDirectory,
        [string]$Executable,
        [string[]]$Arguments,
        [string]$LogName,
        [string]$ExpectedArtifact = ''
    )
    $started = [DateTimeOffset]::UtcNow
    $logFile = Join-Path $evidence $LogName
    $status = 1
    $details = ''
    Write-Host "Running: $Name"
    try {
        Push-Location $WorkingDirectory
        try {
            # Use a local log rather than echoing verbose test traces to the terminal.
            # Mask the common JWT Authorization header and serialized token patterns.
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                & $Executable @Arguments 2>&1 | ForEach-Object {
                    ([string]$_) -replace '(?i)(Bearer\s+)eyJ[A-Za-z0-9._-]+', '$1[REDACTED]' `
                        -replace '(?i)("token"\s*:\s*")eyJ[A-Za-z0-9._-]+', '$1[REDACTED]'
                } | Out-File -LiteralPath $logFile -Encoding utf8
                $status = $LASTEXITCODE
                if ($null -eq $status) { $status = 1 }
            } finally {
                $ErrorActionPreference = $previousPreference
            }
        } finally {
            Pop-Location
        }
        if ($status -eq 0 -and $ExpectedArtifact -and -not (Test-Path -LiteralPath $ExpectedArtifact)) {
            $status = 1
            $details = 'Expected build artifact was not produced.'
        }
        if ($status -eq 0 -and ($Name -eq 'Backend clean test' -or $Name -eq 'Backend clean package')) {
            $counts = Get-BackendTestCounts
            # The last verified pre-release backend baseline was 253 executed tests;
            # this release adds at least one more. Reject misleading Maven success
            # where tests were disabled or only a tiny subset was selected.
            if ($counts.tests -lt 254 -or $counts.failures -gt 0 -or
                    $counts.errors -gt 0 -or $counts.skipped -gt 1) {
                $status = 1
                $details = ('Surefire evidence is incomplete or failed: tests={0}, failures={1}, errors={2}, skipped={3}.' -f `
                    $counts.tests, $counts.failures, $counts.errors, $counts.skipped)
            }
        }
        if ($status -eq 0 -and $Name -eq 'Frontend request tests') {
            $testText = Get-Content -LiteralPath $logFile -Raw
            $passedMatch = [regex]::Match($testText, '(?m)^# pass\s+(\d+)\s*$')
            $failureMatch = [regex]::Match($testText, '(?m)^# fail\s+(\d+)\s*$')
            if (-not $passedMatch.Success -or -not $failureMatch.Success -or
                    [int]$passedMatch.Groups[1].Value -lt 45 -or
                    [int]$failureMatch.Groups[1].Value -ne 0) {
                $status = 1
                $details = 'The Node test summary is missing, too small, or reports failures.'
            }
        }
        if ($status -eq 0 -and $Name -eq 'Frontend lint') {
            # Oxlint can return exit 0 while reporting warnings; issue 20 requires no unresolved warnings.
            $lintText = Get-Content -LiteralPath $logFile -Raw
            if ($lintText -match '(?i)Found\s+([1-9][0-9]*)\s+warnings?') {
                $status = 1
                $details = 'Oxlint reported warnings; see the local log.'
            }
        }
    } catch {
        $status = 1
        $details = $_.Exception.GetType().Name + ': command could not be started or completed.'
        if (-not (Test-Path -LiteralPath $logFile)) {
            Set-Content -LiteralPath $logFile -Value $details -Encoding UTF8
        }
    }
    $elapsed = [math]::Round(([DateTimeOffset]::UtcNow - $started).TotalSeconds, 2)
    $success = ($status -eq 0)
    $results.Add([pscustomobject]@{
        name = $Name
        command = [System.IO.Path]::GetFileName($Executable) + ' ' + ($Arguments -join ' ')
        utc = $started.ToString('o')
        durationSeconds = $elapsed
        exitCode = $status
        result = $(if ($success) { 'PASS' } else { 'FAIL' })
        artifact = $(if ($ExpectedArtifact) { [System.IO.Path]::GetFileName($ExpectedArtifact) } else { '' })
        log = $LogName
        note = $details
    }) | Out-Null
    Write-Host ("{0}: {1} ({2}s)" -f $(if ($success) { 'PASS' } else { 'FAIL' }), $Name, $elapsed)
}

function Get-BackendTestCounts {
    $reportPath = Join-Path $backend 'target\surefire-reports'
    $aggregate = [ordered]@{ tests = 0; passed = 0; failures = 0; errors = 0; skipped = 0; files = 0 }
    if (-not (Test-Path -LiteralPath $reportPath)) { return $aggregate }
    foreach ($item in (Get-ChildItem -LiteralPath $reportPath -Filter 'TEST-*.xml' -File)) {
        try {
            [xml]$xml = Get-Content -LiteralPath $item.FullName -Raw
            $suite = $xml.testsuite
            if ($null -eq $suite) { continue }
            $aggregate.tests += [int]$suite.tests
            $aggregate.failures += [int]$suite.failures
            $aggregate.errors += [int]$suite.errors
            $aggregate.skipped += [int]$suite.skipped
            $aggregate.files += 1
        } catch {
            Write-Warning ('Unreadable Surefire test summary: ' + $item.Name)
        }
    }
    $aggregate.passed = $aggregate.tests - $aggregate.failures - $aggregate.errors - $aggregate.skipped
    return $aggregate
}

$maven = Join-Path $backend 'mvnw.cmd'
$npmCommand = Get-Command 'npm.cmd' -ErrorAction SilentlyContinue
if ($null -eq $npmCommand) { $npmCommand = Get-Command 'npm' -ErrorAction SilentlyContinue }
$npmExe = if ($null -ne $npmCommand) { $npmCommand.Source } else { 'npm.cmd' }

Invoke-Gate -Name 'Backend clean test' -WorkingDirectory $backend -Executable $maven `
    -Arguments @('clean', 'test') -LogName '01-backend-test.log'
$backendTests = Get-BackendTestCounts
Invoke-Gate -Name 'Backend clean package' -WorkingDirectory $backend -Executable $maven `
    -Arguments @('clean', 'package') -LogName '02-backend-package.log' `
    -ExpectedArtifact (Join-Path $backend 'target\unidoc-backend-0.0.1-SNAPSHOT.jar')
$backendPackageTests = Get-BackendTestCounts
Invoke-Gate -Name 'Frontend npm ci' -WorkingDirectory $frontend -Executable $npmExe `
    -Arguments @('ci') -LogName '03-frontend-ci.log'
Invoke-Gate -Name 'Frontend request tests' -WorkingDirectory $frontend -Executable $npmExe `
    -Arguments @('run', 'test:requests') -LogName '04-frontend-tests.log'
Invoke-Gate -Name 'Frontend lint' -WorkingDirectory $frontend -Executable $npmExe `
    -Arguments @('run', 'lint') -LogName '05-frontend-lint.log'
Invoke-Gate -Name 'Frontend production build' -WorkingDirectory $frontend -Executable $npmExe `
    -Arguments @('run', 'build') -LogName '06-frontend-build.log' `
    -ExpectedArtifact (Join-Path $frontend 'dist\index.html')

$passed = @($results | Where-Object { $_.result -eq 'PASS' }).Count
$allPassed = ($passed -eq $results.Count)
$summary = [ordered]@{
    createdUtc = ([DateTimeOffset]::UtcNow).ToString('o')
    scope = 'Local static/automated gates only; no live MySQL, SMTP, UAT, reset or browser checks.'
    outcome = $(if ($allPassed) { 'PASS - automated gates only' } else { 'FAIL - automated gates' })
    backendCleanTest = $backendTests
    backendPackageTests = $backendPackageTests
    gates = @($results.ToArray())
}
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $evidence 'gate-summary.json') -Encoding UTF8
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# UniDoc - Issue 20 automated gate record')
$lines.Add('')
$lines.Add('Generated (UTC): ' + $summary.createdUtc)
$lines.Add('Outcome: **' + $summary.outcome + '**')
$lines.Add('')
$lines.Add('| Gate | Outcome | Duration (s) | Local log |')
$lines.Add('| --- | --- | ---: | --- |')
foreach ($gate in $results) {
    $lines.Add('| ' + $gate.name + ' | ' + $gate.result + ' | ' + $gate.durationSeconds + ' | ' + $gate.log + ' |')
}
$lines.Add('')
$lines.Add('Backend clean-test: tests=' + $backendTests.tests + ', passed=' + $backendTests.passed +
    ', failed=' + $backendTests.failures + ', errors=' + $backendTests.errors + ', skipped=' + $backendTests.skipped)
$lines.Add('Backend clean-package: tests=' + $backendPackageTests.tests + ', passed=' + $backendPackageTests.passed +
    ', failed=' + $backendPackageTests.failures + ', errors=' + $backendPackageTests.errors + ', skipped=' + $backendPackageTests.skipped)
$lines.Add('')
$lines.Add('Manual MySQL/SMTP, E2E-01..10, performance, Chrome, Edge and reset checks remain independent release gates.')
$lines | Set-Content -LiteralPath (Join-Path $evidence 'gate-summary.md') -Encoding UTF8
Write-Host ('Summary: ' + (Join-Path $evidence 'gate-summary.md'))
if ($allPassed) { exit 0 }
exit 1
