[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$resourceRoot = Join-Path $PSScriptRoot 'src/main/resources'
$assetRoot = Join-Path $resourceRoot 'assets/posted_signage'
$dataRoot = Join-Path $resourceRoot 'data/posted_signage'
$registrationSource = Join-Path $PSScriptRoot 'src/main/java/com/postedsignage/PostedSignage.java'
$errors = [System.Collections.Generic.List[string]]::new()

function Add-ValidationError([string]$Message) {
    $errors.Add($Message)
}

function Require-File([string]$Path, [string]$Reason) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-ValidationError "$Reason`: $Path"
    }
}

function Get-RegisteredBlockIds([string]$Path) {
    Require-File $Path 'Missing registration source'
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return @()
    }

    $source = Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
    $ids = [System.Collections.Generic.List[string]]::new()

    foreach ($match in [regex]::Matches($source, 'signBlock\("([a-z0-9_]+)"\)')) {
        $ids.Add($match.Groups[1].Value)
    }
    foreach ($match in [regex]::Matches(
            $source, 'BLOCKS\.register\(\s*"([a-z0-9_]+)"', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        $ids.Add($match.Groups[1].Value)
    }

    foreach ($listName in @('RESTROOM_SIGN_NAMES', 'CUSTOMIZABLE_HANGING_SIGN_NAMES')) {
        $pattern = "(?s)$listName\s*=\s*List\.of\((.*?)\);"
        $listMatch = [regex]::Match($source, $pattern)
        if (-not $listMatch.Success) {
            Add-ValidationError "Could not find $listName in $Path"
            continue
        }
        foreach ($idMatch in [regex]::Matches($listMatch.Groups[1].Value, '"([a-z0-9_]+)"')) {
            $ids.Add($idMatch.Groups[1].Value)
        }
    }

    $duplicates = @($ids | Group-Object | Where-Object Count -gt 1 | ForEach-Object Name)
    foreach ($duplicate in $duplicates) {
        Add-ValidationError "Duplicate registered block ID in $Path`: $duplicate"
    }

    return @($ids | Sort-Object -Unique)
}

function Test-ExactAssetSet(
        [string]$Root,
        [string]$Filter,
        [string]$Description,
        [string[]]$ExpectedIds) {
    if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
        Add-ValidationError "Missing $Description directory: $Root"
        return
    }

    $actualIds = @(Get-ChildItem -LiteralPath $Root -File -Filter $Filter |
            ForEach-Object BaseName | Sort-Object -Unique)
    foreach ($missing in @($ExpectedIds | Where-Object { $_ -notin $actualIds })) {
        Add-ValidationError "Missing $Description for registered block: $missing"
    }
    foreach ($extra in @($actualIds | Where-Object { $_ -notin $ExpectedIds })) {
        Add-ValidationError "Unregistered $Description found: $extra"
    }
}

$jsonFiles = @(Get-ChildItem -LiteralPath $resourceRoot -Recurse -File -Filter '*.json')
$parsedJson = @{}
foreach ($file in $jsonFiles) {
    try {
        $parsedJson[$file.FullName] = Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName | ConvertFrom-Json
    }
    catch {
        Add-ValidationError "Invalid JSON in $($file.FullName): $($_.Exception.Message)"
    }
}

$blockstateRoot = Join-Path $assetRoot 'blockstates'
$blockModelRoot = Join-Path $assetRoot 'models/block'
$itemModelRoot = Join-Path $assetRoot 'models/item'
$lootRoot = Join-Path $dataRoot 'loot_table/blocks'
$registeredBlockIds = @(Get-RegisteredBlockIds $registrationSource)
$blockIds = @(Get-ChildItem -LiteralPath $blockstateRoot -File -Filter '*.json' |
        Sort-Object BaseName | ForEach-Object BaseName)

Test-ExactAssetSet $blockstateRoot '*.json' 'blockstate' $registeredBlockIds
Test-ExactAssetSet $itemModelRoot '*.json' 'item model' $registeredBlockIds
Test-ExactAssetSet $lootRoot '*.json' 'loot table' $registeredBlockIds

# Every block needs a same-named base model for its inventory representation.
# State-specific models may append a suffix, such as statue_relief_top_left.
foreach ($blockId in $registeredBlockIds) {
    Require-File (Join-Path $blockModelRoot "$blockId.json") "Missing base block model for $blockId"
}
$blockModelIds = @(Get-ChildItem -LiteralPath $blockModelRoot -File -Filter '*.json' |
        ForEach-Object BaseName | Sort-Object -Unique)
foreach ($modelId in $blockModelIds) {
    $modelOwner = @($registeredBlockIds | Where-Object {
            $modelId -eq $_ -or $modelId.StartsWith($_ + '_', [System.StringComparison]::Ordinal)
        })
    if ($modelOwner.Count -eq 0) {
        Add-ValidationError "Unregistered block model found: $modelId"
    }
}

$languageFiles = @(
    Join-Path $assetRoot 'lang/en_us.json'
    Join-Path $assetRoot 'lang/es_mx.json'
    Join-Path $assetRoot 'lang/es_es.json'
)
$languageKeys = @{}
foreach ($languageFile in $languageFiles) {
    Require-File $languageFile 'Missing language file'
    if ($parsedJson.ContainsKey($languageFile)) {
        $languageKeys[$languageFile] = @($parsedJson[$languageFile].PSObject.Properties.Name)
    }
}

$requiredTranslationKeys = @('itemGroup.posted_signage') + @(
    $registeredBlockIds | ForEach-Object { "block.posted_signage.$_" })
foreach ($languageFile in $languageFiles) {
    if (-not $languageKeys.ContainsKey($languageFile)) {
        continue
    }
    foreach ($translationKey in $requiredTranslationKeys) {
        if ($translationKey -notin $languageKeys[$languageFile]) {
            Add-ValidationError "Missing $translationKey in $languageFile"
        }
    }
}

foreach ($blockId in $blockIds) {
    Require-File (Join-Path $blockModelRoot "$blockId.json") "Missing block model for $blockId"
    Require-File (Join-Path $itemModelRoot "$blockId.json") "Missing item model for $blockId"
    Require-File (Join-Path $lootRoot "$blockId.json") "Missing loot table for $blockId"

    $blockstatePath = Join-Path $blockstateRoot "$blockId.json"
    if ($parsedJson.ContainsKey($blockstatePath)) {
        $blockstate = $parsedJson[$blockstatePath]
        $expectedRotations = [ordered]@{
            north = 0
            east = 90
            south = 180
            west = 270
        }
        $expectedVariants = [ordered]@{}
        if ($blockId -eq 'sansevieria_planter') {
            $expectedVariants['connection=single'] = [ordered]@{
                model = 'posted_signage:block/sansevieria_planter'; rotation = 0 }
            foreach ($connection in $expectedRotations.Keys) {
                $expectedVariants["connection=$connection"] = [ordered]@{
                    model = 'posted_signage:block/sansevieria_planter_connected'
                    rotation = $expectedRotations[$connection]
                }
            }
        }
        elseif ($blockId -eq 'statue_relief') {
            $parts = @(
                0..3 | ForEach-Object { "stem_bottom_$_" }
            ) + @(
                0..3 | ForEach-Object { "stem_top_$_" }
            ) + @(
                0..9 | ForEach-Object { "bar_bottom_$_" }
            ) + @(
                0..9 | ForEach-Object { "bar_top_$_" }
            )
            foreach ($part in $parts) {
                foreach ($facing in $expectedRotations.Keys) {
                    $expectedVariants["facing=$facing,part=$part"] = [ordered]@{
                        model = "posted_signage:block/statue_relief_$part"
                        rotation = $expectedRotations[$facing]
                    }
                }
            }
        }
        else {
            foreach ($facing in $expectedRotations.Keys) {
                $expectedVariants["facing=$facing"] = [ordered]@{
                    model = "posted_signage:block/$blockId"
                    rotation = $expectedRotations[$facing]
                }
            }
        }

        $actualVariantNames = @($blockstate.variants.PSObject.Properties.Name)
        foreach ($variantKey in $expectedVariants.Keys) {
            $variantProperty = $blockstate.variants.PSObject.Properties[$variantKey]
            if ($null -eq $variantProperty) {
                Add-ValidationError "Missing $variantKey variant in $blockstatePath"
                continue
            }
            $variant = $variantProperty.Value
            if ($variant.model -ne $expectedVariants[$variantKey].model) {
                Add-ValidationError "Incorrect model for $variantKey in $blockstatePath"
            }
            $actualRotation = if ($null -eq $variant.PSObject.Properties['y']) {
                0
            }
            else {
                [int]$variant.y
            }
            if ($actualRotation -ne $expectedVariants[$variantKey].rotation) {
                Add-ValidationError "Incorrect Y rotation for $variantKey in $blockstatePath"
            }
        }
        foreach ($extraVariant in @($actualVariantNames | Where-Object { $_ -notin $expectedVariants.Keys })) {
            Add-ValidationError "Unexpected $extraVariant variant in $blockstatePath"
        }
    }

    $itemModelPath = Join-Path $itemModelRoot "$blockId.json"
    if ($parsedJson.ContainsKey($itemModelPath) -and
            $parsedJson[$itemModelPath].parent -ne "posted_signage:block/$blockId") {
        Add-ValidationError "Item model does not inherit its block model: $itemModelPath"
    }

    $lootPath = Join-Path $lootRoot "$blockId.json"
    if ($parsedJson.ContainsKey($lootPath)) {
        $loot = $parsedJson[$lootPath]
        $pools = @($loot.pools)
        if ($loot.type -ne 'minecraft:block' -or $pools.Count -ne 1) {
            Add-ValidationError "Loot table must contain exactly one block pool: $lootPath"
        }
        else {
            $pool = $pools[0]
            $entries = @($pool.entries)
            if ([int]$pool.rolls -ne 1 -or $entries.Count -ne 1 -or
                    $entries[0].type -ne 'minecraft:item' -or
                    $entries[0].name -ne "posted_signage:$blockId") {
                Add-ValidationError "Loot table must drop exactly one matching block item: $lootPath"
            }
            $conditions = @($pool.conditions | ForEach-Object condition)
            if ('minecraft:survives_explosion' -notin $conditions) {
                Add-ValidationError "Loot table is missing survives_explosion: $lootPath"
            }
        }
    }
}

# Verify local model and texture references. Minecraft namespace references are
# supplied by the game and intentionally do not map to files in this project.
foreach ($file in $jsonFiles | Where-Object FullName -Match '[\\/]assets[\\/]posted_signage[\\/](blockstates|models)[\\/]') {
    $raw = Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName
    foreach ($match in [regex]::Matches($raw, '"(?:model|parent)"\s*:\s*"posted_signage:(block|item)/([^"#]+)"')) {
        $modelPath = Join-Path $assetRoot "models/$($match.Groups[1].Value)/$($match.Groups[2].Value).json"
        Require-File $modelPath "Broken model reference in $($file.FullName)"
    }

    if ($parsedJson.ContainsKey($file.FullName)) {
        $textureProperty = $parsedJson[$file.FullName].PSObject.Properties['textures']
        if ($null -ne $textureProperty) {
            $textures = $textureProperty.Value
            foreach ($texture in $textures.PSObject.Properties.Value) {
                if ($texture -is [string] -and $texture -match '^posted_signage:(block|item)/(.+)$') {
                    $texturePath = Join-Path $assetRoot "textures/$($Matches[1])/$($Matches[2]).png"
                    Require-File $texturePath "Broken texture reference in $($file.FullName)"
                }
            }
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Error ("Asset validation failed with {0} error(s):`n- {1}" -f
            $errors.Count, ($errors -join "`n- "))
    exit 1
}

Write-Host "Asset validation passed: $($jsonFiles.Count) JSON files and $($registeredBlockIds.Count) registered block asset sets."
