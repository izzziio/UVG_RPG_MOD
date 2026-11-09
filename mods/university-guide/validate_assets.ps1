[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$resourceRoot = Join-Path $PSScriptRoot 'src/main/resources'
$assetRoot = Join-Path $resourceRoot 'assets/universityguide'
$errors = [System.Collections.Generic.List[string]]::new()

function Add-ValidationError([string]$Message) {
    $errors.Add($Message)
}

function Require-File([string]$Path, [string]$Reason) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-ValidationError "$Reason`: $Path"
    }
}

$requiredFiles = @(
    (Join-Path $assetRoot 'blockstates/guide_barrier.json'),
    (Join-Path $assetRoot 'models/block/guide_barrier.json'),
    (Join-Path $assetRoot 'models/item/guide_barrier.json'),
    (Join-Path $assetRoot 'textures/block/guide_barrier.png'),
    (Join-Path $assetRoot 'blockstates/tour_stop.json'),
    (Join-Path $assetRoot 'models/block/tour_stop.json'),
    (Join-Path $assetRoot 'models/item/tour_stop.json'),
    (Join-Path $assetRoot 'lang/en_us.json'),
    (Join-Path $assetRoot 'lang/es_es.json'),
    (Join-Path $resourceRoot 'data/universityguide/loot_table/entities/guide.json')
)
foreach ($requiredFile in $requiredFiles) {
    Require-File $requiredFile 'Missing required resource'
}

$jsonFiles = @(Get-ChildItem -LiteralPath $resourceRoot -Recurse -File -Filter '*.json')
$parsedJson = @{}
foreach ($file in $jsonFiles) {
    try {
        $rawJson = Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName
        # Windows PowerShell 5.1 rechaza los nombres de propiedad vacíos, que Minecraft
        # usa para la variante predeterminada del blockstate.
        $normalizedJson = $rawJson -replace '""\s*:', '"__default__":'
        $parsedJson[$file.FullName] = $normalizedJson | ConvertFrom-Json
    }
    catch {
        Add-ValidationError "JSON inválido en $($file.FullName): $($_.Exception.Message)"
    }
}

$blockstatePath = Join-Path $assetRoot 'blockstates/tour_stop.json'
$blockModelPath = Join-Path $assetRoot 'models/block/tour_stop.json'
$itemModelPath = Join-Path $assetRoot 'models/item/tour_stop.json'
if ($parsedJson.ContainsKey($blockstatePath)) {
    $defaultVariant = $parsedJson[$blockstatePath].variants.PSObject.Properties['__default__']
    if ($null -eq $defaultVariant -or
            $defaultVariant.Value.model -ne 'universityguide:block/tour_stop') {
        Add-ValidationError "El blockstate tour_stop debe referenciar universityguide:block/tour_stop"
    }
}
if ($parsedJson.ContainsKey($itemModelPath) -and
        $parsedJson[$itemModelPath].parent -ne 'universityguide:block/tour_stop') {
    Add-ValidationError 'tour_stop item model must inherit universityguide:block/tour_stop'
}

foreach ($file in $jsonFiles | Where-Object FullName -Match '[\\/]assets[\\/]universityguide[\\/](blockstates|models)[\\/]') {
    $raw = Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName
    foreach ($match in [regex]::Matches($raw, '"(?:model|parent)"\s*:\s*"universityguide:(block|item)/([^"#]+)"')) {
        $modelPath = Join-Path $assetRoot "models/$($match.Groups[1].Value)/$($match.Groups[2].Value).json"
        Require-File $modelPath "Referencia de modelo rota en $($file.FullName)"
    }
}

$languagePaths = @(
    (Join-Path $assetRoot 'lang/en_us.json'),
    (Join-Path $assetRoot 'lang/es_es.json')
)
$languageKeys = @{}
foreach ($languagePath in $languagePaths) {
    if ($parsedJson.ContainsKey($languagePath)) {
        $languageKeys[$languagePath] = @($parsedJson[$languagePath].PSObject.Properties.Name | Sort-Object)
    }
}
if ($languageKeys.Count -eq $languagePaths.Count) {
    $referenceKeys = $languageKeys[$languagePaths[0]]
    foreach ($languagePath in $languagePaths[1..($languagePaths.Count - 1)]) {
        foreach ($missing in @($referenceKeys | Where-Object { $_ -notin $languageKeys[$languagePath] })) {
            Add-ValidationError "Falta la clave de traducción $missing en $languagePath"
        }
        foreach ($extra in @($languageKeys[$languagePath] | Where-Object { $_ -notin $referenceKeys })) {
            Add-ValidationError "La clave de traducción $extra no está en $($languagePaths[0])"
        }
    }
}

$requiredTranslations = @(
    'block.universityguide.guide_barrier',
    'command.universityguide.barrier_given',
    'block.universityguide.tour_stop',
    'entity.universityguide.guide',
    'screen.universityguide.destinations',
    'message.universityguide.stop_saved',
    'command.universityguide.spawned'
)
foreach ($languagePath in $languagePaths) {
    if (-not $languageKeys.ContainsKey($languagePath)) {
        continue
    }
    foreach ($translation in $requiredTranslations) {
        if ($translation -notin $languageKeys[$languagePath]) {
            Add-ValidationError "Falta la traducción obligatoria $translation en $languagePath"
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Error ("La validación de assets falló con {0} error(es):`n- {1}" -f
            $errors.Count, ($errors -join "`n- "))
    exit 1
}

Write-Host "Validación de assets correcta: $($jsonFiles.Count) archivos JSON y traducciones coincidentes."
