[CmdletBinding()]
param(
    [ValidateSet('todos', 'posted-signage', 'university-guide')]
    [string]$Mod = 'todos',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$modules = if ($Mod -eq 'todos') { @('posted-signage', 'university-guide') } else { @($Mod) }
foreach ($moduleName in $modules) {
    Push-Location (Join-Path $repoPath "mods/$moduleName")
    try {
        if (Test-Path -LiteralPath './validate_assets.ps1' -PathType Leaf) {
            & ./validate_assets.ps1
        }
        $gradleArguments = @('--no-daemon', 'build')
        if ($Offline) { $gradleArguments += '--offline' }
        & ./gradlew.bat @gradleArguments
        if ($LASTEXITCODE -ne 0) { throw "No se pudo compilar $moduleName (codigo $LASTEXITCODE)." }
    }
    finally { Pop-Location }
}
