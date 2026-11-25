package com.android.purebilibili.core.plugin.skin

import com.android.purebilibili.core.store.HomeSettings

fun resolveUiSkinState(
    selection: UiSkinSelection,
    installedSkins: List<InstalledUiSkinPackage>
): UiSkinState {
    val normalizedFanNumber = selection.customFanNumber
        .filter(Char::isDigit)
        .take(8)
        .ifBlank { "000088" }
        .padStart(6, '0')
    if (!selection.enabled) {
        return UiSkinState(
            enabled = false,
            disabledSurfaces = selection.disabledSurfaces,
            customFanNumber = normalizedFanNumber,
            tonePolicy = selection.tonePolicy,
        )
    }
    val selectedInstallId = selection.selectedInstallId
    val rawSkin = if (!selectedInstallId.isNullOrBlank()) {
        installedSkins.firstOrNull { it.installId == selectedInstallId }
    } else {
        val selectedSkinId = selection.selectedSkinId
        if (selectedSkinId.isNullOrBlank()) {
            null
        } else {
            installedSkins.firstOrNull { it.manifest.skinId == selectedSkinId }
        }
    }
        ?: return UiSkinState(
            enabled = false,
            disabledSurfaces = selection.disabledSurfaces,
            customFanNumber = normalizedFanNumber,
            tonePolicy = selection.tonePolicy,
        )

    val allSurfaces = rawSkin.manifest.surfaces
    val forcedColorMode = when (selection.tonePolicy) {
        UiSkinTonePolicy.AUTO -> rawSkin.manifest.colors.colorMode
        UiSkinTonePolicy.FORCE_LIGHT_BG -> "light"
        UiSkinTonePolicy.FORCE_DARK_BG -> "dark"
    }
    val effectiveManifest = rawSkin.manifest.copy(
        surfaces = if (selection.disabledSurfaces.isEmpty()) allSurfaces else (allSurfaces - selection.disabledSurfaces),
        colors = if (forcedColorMode != rawSkin.manifest.colors.colorMode) {
            rawSkin.manifest.colors.copy(colorMode = forcedColorMode)
        } else {
            rawSkin.manifest.colors
        }
    )
    val effectiveSkin = if (effectiveManifest == rawSkin.manifest) {
        rawSkin
    } else {
        rawSkin.copy(manifest = effectiveManifest)
    }
    return UiSkinState(
        enabled = true,
        activeSkin = effectiveSkin,
        availableSurfaces = allSurfaces,
        disabledSurfaces = selection.disabledSurfaces,
        customFanNumber = normalizedFanNumber,
        tonePolicy = selection.tonePolicy,
    )
}

fun resolveUiSkinHomeSettings(
    homeSettings: HomeSettings,
    uiSkinState: UiSkinState
): HomeSettings {
    return homeSettings
}
