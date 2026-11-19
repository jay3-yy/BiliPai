package com.android.purebilibili.core.util

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

data class AppHingeFeature(
    val orientation: AppHingeOrientation,
    val bounds: IntRect,
    val isSeparating: Boolean,
    val isOccluding: Boolean,
    val isFlat: Boolean,
)

enum class AppHingeSafePane {
    Largest,
    Start,
    End,
    Top,
    Bottom,
}

enum class AppHingeSafeContentPurpose {
    Media,
    Controls,
    Dialog,
    Sheet,
    Menu,
}

internal fun selectPrimaryHingeFeature(
    hinges: List<AppHingeFeature>,
): AppHingeFeature? {
    if (hinges.isEmpty()) return null
    return hinges
        .filter { hinge -> hinge.isSeparating || hinge.isOccluding || !hinge.isFlat }
        .maxByOrNull { hinge -> hinge.bounds.width * hinge.bounds.height }
        ?: hinges.first()
}

internal fun resolveHingeSafeContentRegions(
    containerWidthPx: Int,
    containerHeightPx: Int,
    hinges: List<AppHingeFeature>,
    containerOrigin: IntOffset = IntOffset.Zero,
    clearancePx: Int = 0,
): List<IntRect> {
    if (containerWidthPx <= 0 || containerHeightPx <= 0) return emptyList()
    val fullWindow = IntRect(0, 0, containerWidthPx, containerHeightPx)
    val obstructing = hinges.mapNotNull { hinge ->
        if (!hinge.isSeparating && !hinge.isOccluding && hinge.isFlat) return@mapNotNull null
        val local = IntRect(
            hinge.bounds.left - containerOrigin.x,
            hinge.bounds.top - containerOrigin.y,
            hinge.bounds.right - containerOrigin.x,
            hinge.bounds.bottom - containerOrigin.y,
        )
        val clearance = clearancePx.coerceAtLeast(0)
        val bounds = when (hinge.orientation) {
            AppHingeOrientation.Vertical -> {
                if (local.bottom <= 0 || local.top >= containerHeightPx ||
                    local.right < 0 || local.left > containerWidthPx
                ) return@mapNotNull null
                IntRect(
                    (local.left - clearance).coerceIn(0, containerWidthPx), 0,
                    (local.right + clearance).coerceIn(0, containerWidthPx), containerHeightPx,
                )
            }
            AppHingeOrientation.Horizontal -> {
                if (local.right <= 0 || local.left >= containerWidthPx ||
                    local.bottom < 0 || local.top > containerHeightPx
                ) return@mapNotNull null
                IntRect(
                    0, (local.top - clearance).coerceIn(0, containerHeightPx),
                    containerWidthPx, (local.bottom + clearance).coerceIn(0, containerHeightPx),
                )
            }
            AppHingeOrientation.None -> return@mapNotNull null
        }
        hinge.copy(bounds = bounds)
    }
    if (obstructing.isEmpty()) return listOf(fullWindow)

    val xEdges = sortedUniqueEdges(
        start = 0,
        end = containerWidthPx,
        spans = obstructing
            .filter { hinge -> hinge.orientation == AppHingeOrientation.Vertical }
            .map { hinge -> hinge.bounds.left to hinge.bounds.right },
    )
    val yEdges = sortedUniqueEdges(
        start = 0,
        end = containerHeightPx,
        spans = obstructing
            .filter { hinge -> hinge.orientation == AppHingeOrientation.Horizontal }
            .map { hinge -> hinge.bounds.top to hinge.bounds.bottom },
    )

    val regions = ArrayList<IntRect>()
    for (xIndex in 0 until xEdges.lastIndex) {
        val left = xEdges[xIndex]
        val right = xEdges[xIndex + 1]
        if (right <= left) continue
        val coveredByVerticalHinge = obstructing.any { hinge ->
            hinge.orientation == AppHingeOrientation.Vertical &&
                left >= hinge.bounds.left &&
                right <= hinge.bounds.right
        }
        if (coveredByVerticalHinge) continue
        for (yIndex in 0 until yEdges.lastIndex) {
            val top = yEdges[yIndex]
            val bottom = yEdges[yIndex + 1]
            if (bottom <= top) continue
            val coveredByHorizontalHinge = obstructing.any { hinge ->
                hinge.orientation == AppHingeOrientation.Horizontal &&
                    top >= hinge.bounds.top &&
                    bottom <= hinge.bounds.bottom
            }
            if (coveredByHorizontalHinge) continue
            regions += IntRect(left, top, right, bottom)
        }
    }
    // An entirely occluded container must not fall back to placing content under the hinge.
    return regions
}

internal fun AppFoldingFeatureInfo.layoutHinges(): List<AppHingeFeature> =
    hinges.ifEmpty {
        val bounds = hingeBounds ?: return@ifEmpty emptyList()
        listOf(
            AppHingeFeature(
                orientation = hingeOrientation,
                bounds = bounds,
                isSeparating = isSeparating,
                isOccluding = isOccluding,
                isFlat = posture == AppFoldPosture.Flat || posture == AppFoldPosture.None,
            )
        )
    }

internal fun resolvePreferredHingeSafePane(
    posture: AppFoldPosture,
    purpose: AppHingeSafeContentPurpose,
): AppHingeSafePane {
    return when (posture) {
        AppFoldPosture.Tabletop -> when (purpose) {
            AppHingeSafeContentPurpose.Media -> AppHingeSafePane.Top
            AppHingeSafeContentPurpose.Controls,
            AppHingeSafeContentPurpose.Dialog,
            AppHingeSafeContentPurpose.Sheet,
            AppHingeSafeContentPurpose.Menu -> AppHingeSafePane.Bottom
        }
        AppFoldPosture.Book -> when (purpose) {
            AppHingeSafeContentPurpose.Media -> AppHingeSafePane.Start
            AppHingeSafeContentPurpose.Controls,
            AppHingeSafeContentPurpose.Dialog,
            AppHingeSafeContentPurpose.Sheet,
            AppHingeSafeContentPurpose.Menu -> AppHingeSafePane.End
        }
        AppFoldPosture.None,
        AppFoldPosture.Flat -> AppHingeSafePane.Largest
    }
}

internal fun resolveHingeSafeRegion(
    regions: List<IntRect>,
    pane: AppHingeSafePane,
): IntRect? {
    if (regions.isEmpty()) return null
    return when (pane) {
        AppHingeSafePane.Largest -> regions.maxByOrNull { region -> region.width * region.height }
        AppHingeSafePane.Start -> regions.minByOrNull { region -> region.left }
        AppHingeSafePane.End -> regions.maxByOrNull { region -> region.right }
        AppHingeSafePane.Top -> regions.minByOrNull { region -> region.top }
        AppHingeSafePane.Bottom -> regions.maxByOrNull { region -> region.bottom }
    }
}

internal fun resolveOccludingHingeBounds(
    hinges: List<AppHingeFeature>,
    containerWidthPx: Int,
    containerHeightPx: Int,
): List<IntRect> {
    return hinges.mapNotNull { hinge ->
        if (!hinge.isOccluding) return@mapNotNull null
        val left = hinge.bounds.left.coerceIn(0, containerWidthPx)
        val top = hinge.bounds.top.coerceIn(0, containerHeightPx)
        val right = hinge.bounds.right.coerceIn(0, containerWidthPx)
        val bottom = hinge.bounds.bottom.coerceIn(0, containerHeightPx)
        if (right <= left || bottom <= top) null
        else IntRect(left, top, right, bottom)
    }
}

private fun sortedUniqueEdges(
    start: Int,
    end: Int,
    spans: List<Pair<Int, Int>>,
): List<Int> {
    val edges = ArrayList<Int>(spans.size * 2 + 2)
    edges += start
    edges += end
    spans.forEach { (spanStart, spanEnd) ->
        edges += spanStart.coerceIn(start, end)
        edges += spanEnd.coerceIn(start, end)
    }
    return edges.distinct().sorted()
}
