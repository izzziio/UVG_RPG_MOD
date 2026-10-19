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
# Internal surface covers have no inventory item; the applicator is a standalone item.
$inventoryBlockIds = @($registeredBlockIds | Where-Object { $_ -notin @('texture_cover','cit_door_swing','short_glass_door','wide_glass_door') })
Test-ExactAssetSet $itemModelRoot '*.json' 'item model' ($inventoryBlockIds + @('texture_applicator'))
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

$requiredTranslationKeys = @('itemGroup.posted_signage', 'item.posted_signage.texture_applicator') + @(
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
    if ($blockId -notin @('texture_cover','cit_door_swing','short_glass_door','wide_glass_door')) {
        Require-File (Join-Path $itemModelRoot "$blockId.json") "Missing item model for $blockId"
    }
    Require-File (Join-Path $lootRoot "$blockId.json") "Missing loot table for $blockId"
    if ($blockId -eq 'trash_bin') {
        Require-File (Join-Path $blockModelRoot 'trash_bin.obj') 'Missing default tapered bin mesh'
        Require-File (Join-Path $blockModelRoot 'trash_bin_item.obj') 'Missing default tapered bin inventory mesh'
        Require-File (Join-Path $blockModelRoot 'trash_bin.mtl') 'Missing default tapered bin materials'
    }

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
        if ($blockId -in @('cit_short_door_test','cit_wide_door_test','clear_short_door_test','clear_wide_door_test','cit_glass_panel','cit_door_swing')) {
            if (@($blockstate.multipart).Count -ne 1 -or $blockstate.multipart[0].apply.model -ne "posted_signage:block/$blockId") {
                Add-ValidationError "Incorrect entity-rendered multipart model in $blockstatePath"
            }
        }
        elseif ($blockId -match '^(customizable_hanging_sign|(?:mens|womens|mixed)_restroom_sign)' -or
                $blockId -in @('right_arrow_sign','fire_extinguisher_sign','emergency_exit_sign','do_not_use_elevator_sign','emergency_stairs_sign')) {
            foreach ($facing in $expectedRotations.Keys) {
                foreach ($glass in @('false','true')) {
                    $suffix = if ($glass -eq 'true') { '_glass' } else { '' }
                    $expectedVariants["facing=$facing,glass_mounted=$glass"] = [ordered]@{
                        model = "posted_signage:block/${blockId}${suffix}"; rotation = $expectedRotations[$facing]
                    }
                }
            }
        }
        elseif ($blockId -eq 'texture_cover') {
            foreach ($facing in @('north', 'east', 'south', 'west', 'up', 'down')) {
                $expectedVariants["facing=$facing"] = [ordered]@{ model = 'posted_signage:block/texture_cover'; rotation = 0 }
            }
        }
        elseif ($blockId -eq 'drinking_fountain') {
            foreach ($facing in $expectedRotations.Keys) {
                foreach ($half in @('lower', 'upper')) {
                    foreach ($active in @('false', 'true')) {
                        $suffix = if ($half -eq 'lower' -and $active -eq 'true') { '_active' } else { '' }
                        $expectedVariants["active=$active,facing=$facing,half=$half"] = [ordered]@{
                            model = "posted_signage:block/drinking_fountain_$half$suffix"
                            rotation = $expectedRotations[$facing]
                        }
                    }
                }
            }
        }
        elseif ($blockId -eq 'sansevieria_planter') {
            $expectedVariants['connection=single'] = [ordered]@{
                model = 'posted_signage:block/sansevieria_planter'; rotation = 0 }
            foreach ($connection in $expectedRotations.Keys) {
                $expectedVariants["connection=$connection"] = [ordered]@{
                    model = 'posted_signage:block/sansevieria_planter_connected'
                    rotation = $expectedRotations[$connection]
                }
            }
        }
        elseif ($blockId -in @('trash_bin','wide_glass_door','short_glass_door')) {
            $dims = @{
                trash_bin = @(1,1)
                wide_glass_door = @(2,3)
                short_glass_door = @(1,2)
            }[$blockId]
            foreach ($facing in $expectedRotations.Keys) {
                for ($x = 0; $x -lt 2; $x++) {
                    for ($y = 0; $y -lt 3; $y++) {
                        $modelX = [Math]::Min($x, $dims[0] - 1)
                        $modelY = [Math]::Min($y, $dims[1] - 1)
                        $openStates = if ($blockId -eq 'trash_bin') { @('none') } else { @('false', 'true') }
                        foreach ($open in $openStates) {
                            $key = "facing=$facing,part_x=$x,part_y=$y"
                            $suffix = ''
                            if ($open -ne 'none') { $key = "facing=$facing,open=$open,part_x=$x,part_y=$y" }
                            if ($open -eq 'true') { $suffix = '_open' }
                            $rotation = $expectedRotations[$facing]
                            if ($blockId -eq 'wide_glass_door' -and $open -eq 'true') { $rotation = ($rotation + 270) % 360 }
                            $expectedVariants[$key] = [ordered]@{
                                model = "posted_signage:block/${blockId}_part_${modelX}_${modelY}${suffix}"
                                rotation = $rotation
                            }
                        }
                    }
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

        $actualVariantNames = if ($blockstate.PSObject.Properties['variants']) { @($blockstate.variants.PSObject.Properties.Name) } else { @() }
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
        if ($blockId -in @('texture_cover','cit_short_door_test','cit_wide_door_test','clear_short_door_test','clear_wide_door_test','cit_door_swing')) {
            if ($loot.type -ne 'minecraft:block' -or $pools.Count -ne 0) {
                Add-ValidationError "Internal texture covers must not drop items: $lootPath"
            }
        }
        elseif ($loot.type -ne 'minecraft:block' -or $pools.Count -ne 1) {
            Add-ValidationError "Loot table must contain exactly one block pool: $lootPath"
        }
        else {
            $pool = $pools[0]
            $entries = @($pool.entries)
            $dropId = switch ($blockId) {
                'short_glass_door' { 'clear_short_door_test' }
                'wide_glass_door' { 'clear_wide_door_test' }
                default { $blockId }
            }
            if ([int]$pool.rolls -ne 1 -or $entries.Count -ne 1 -or
                    $entries[0].type -ne 'minecraft:item' -or
                    $entries[0].name -ne "posted_signage:$dropId") {
                Add-ValidationError "Loot table must drop exactly one matching block item: $lootPath"
            }
            $conditions = @($pool.conditions | ForEach-Object condition)
            if ('minecraft:survives_explosion' -notin $conditions) {
                Add-ValidationError "Loot table is missing survives_explosion: $lootPath"
            }
            if ($blockId -in @('statue_relief', 'trash_bin', 'wide_glass_door', 'short_glass_door')) {
                $originConditions = @($pool.conditions | Where-Object condition -EQ 'minecraft:block_state_property')
                if ($originConditions.Count -ne 1) {
                    Add-ValidationError "Multiblock loot must be restricted to its origin: $lootPath"
                }
                else {
                    $originCondition = $originConditions[0]
                    $expectedProperties = if ($blockId -eq 'statue_relief') { @{ part = 'stem_bottom_0' } } else { @{ part_x = '0'; part_y = '0' } }
                    if ($originCondition.block -ne "posted_signage:$blockId") {
                        Add-ValidationError "Incorrect multiblock loot block: $lootPath"
                    }
                    foreach ($key in $expectedProperties.Keys) {
                        $property = $originCondition.properties.PSObject.Properties[$key]
                        if ($null -eq $property -or $property.Value -cne $expectedProperties[$key]) {
                            Add-ValidationError "Incorrect origin loot property $key`: $lootPath"
                        }
                    }
                }
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
