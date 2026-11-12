<#
.SYNOPSIS
  Runs scoped Gradle tasks locally with the environment this repo needs and prints only what matters.

.DESCRIPTION
  Meant for local and agent sessions on Windows. Sets JAVA_HOME to a Temurin 25 JDK (from ~/.jdks) if it is not set,
  provides GitHub Packages credentials (GITHUB_ACTOR / GHCR_PAT) from the GitHub CLI if they are missing, runs the
  given Gradle tasks with plain console output and prints a short summary: on success one line, on failure only the
  failing tests, compiler errors and Gradle's failure section. The full log is kept in a temp file.

.EXAMPLE
  ./scripts/gradle-scoped.ps1 :domain-impl:test
  ./scripts/gradle-scoped.ps1 :adapter-in-web:test --tests '*ApiTokenAccessTests'
#>
param(
  [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
  [string[]] $GradleArgs
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
  $jdk = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
    Where-Object Name -like 'temurin-25*' | Sort-Object Name -Descending | Select-Object -First 1
  if (-not $jdk) { throw 'No Temurin 25 JDK found in ~/.jdks - set JAVA_HOME manually.' }
  $env:JAVA_HOME = $jdk.FullName
}

if (-not $env:GHCR_PAT) {
  $env:GHCR_PAT = (& gh auth token 2>$null)
  if (-not $env:GHCR_PAT) { throw 'GHCR_PAT is not set and `gh auth token` returned nothing - run `gh auth login` first.' }
}
if (-not $env:GITHUB_ACTOR) {
  $env:GITHUB_ACTOR = (& gh api user --jq .login 2>$null)
  if (-not $env:GITHUB_ACTOR) { $env:GITHUB_ACTOR = 'christiangroth' }
}

$log = Join-Path $env:TEMP ("gradle-scoped-{0}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$started = Get-Date
Push-Location $repoRoot
try {
  # Windows PowerShell 5.1 wraps native stderr lines in error records; with 'Stop' they would abort the script.
  $ErrorActionPreference = 'Continue'
  & (Join-Path $repoRoot 'gradlew.bat') @GradleArgs --console=plain 2>&1 | ForEach-Object { "$_" } | Where-Object { $_ -ne 'System.Management.Automation.RemoteException' } | Set-Content -Path $log -Encoding UTF8
  $exitCode = $LASTEXITCODE
  $ErrorActionPreference = 'Stop'
} finally {
  Pop-Location
}
$seconds = [int]((Get-Date) - $started).TotalSeconds

if ($exitCode -eq 0) {
  Write-Output ("OK: gradle {0} ({1}s), log: {2}" -f ($GradleArgs -join ' '), $seconds, $log)
  exit 0
}

Write-Output ("FAILED: gradle {0} ({1}s, exit {2}), full log: {3}" -f ($GradleArgs -join ' '), $seconds, $exitCode, $log)
$lines = Get-Content $log
# Failing tests, Kotlin compiler errors and detekt/ktlint findings.
$lines | Select-String -Pattern ' FAILED$', '^e: ', 'error:', '\[ktlint\]', 'detekt' | Select-Object -First 40 | ForEach-Object { $_.Line }
# Gradle's own failure section ("* What went wrong:" up to "* Try:").
$start = ($lines | Select-String -Pattern '^\* What went wrong:' | Select-Object -First 1).LineNumber
if ($start) {
  $end = ($lines | Select-String -Pattern '^\* Try:' | Where-Object LineNumber -gt $start | Select-Object -First 1).LineNumber
  if (-not $end) { $end = [Math]::Min($lines.Count, $start + 20) }
  $lines[($start - 1)..($end - 2)]
}
exit $exitCode
