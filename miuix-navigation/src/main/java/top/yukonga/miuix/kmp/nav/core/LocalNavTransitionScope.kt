// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.core

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

/**
 * The live Miuix transition scope for the current entry.
 *
 * Use this local when entry content needs Miuix-specific navigation data such as relative depth,
 * gesture progress, settle state or the coarse [NavTransitionScope.isRunning] lifecycle signal.
 * Reads of per-frame values remain deferred when performed from a `graphicsLayer` block.
 *
 * Accessing this local outside a [NavDisplay] entry is an error.
 */
public val LocalNavTransitionScope: ProvidableCompositionLocal<NavTransitionScope> =
    compositionLocalOf {
        error(
            "Unexpected access to LocalNavTransitionScope. Access it only inside a " +
                "NavDisplay entry.",
        )
    }

/** This entry's transition, retained by the renderer through its exit. */
public val LocalNavEntryTransition: ProvidableCompositionLocal<NavTransition?> =
    compositionLocalOf { null }
