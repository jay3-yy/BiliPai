package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

@Composable
fun AppLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        modifier = if (userScrollEnabled) {
            modifier.appScrollBoundaryHaptics(state, reverseLayout, Orientation.Horizontal)
        } else modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = overscrollEffect,
        content = content,
    )
}

/** The scrollbar shares the list state; dragging never creates a second scroll model. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AppLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier, propagateMinConstraints = true) {
        CompositionLocalProvider(
            LocalListAuxiliaryWorkAllowed provides
                (LocalListAuxiliaryWorkAllowed.current && !state.isScrollInProgress),
        ) {
            LazyColumn(
                modifier = if (userScrollEnabled) {
                    Modifier.appScrollBoundaryHaptics(state, reverseLayout)
                } else Modifier,
                state = state, contentPadding = contentPadding, reverseLayout = reverseLayout,
                verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment,
                flingBehavior = flingBehavior, userScrollEnabled = userScrollEnabled,
                overscrollEffect = overscrollEffect, content = content,
            )
        }
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX && userScrollEnabled) {
            Box(Modifier.matchParentSize()) {
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(state),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    trackPadding = contentPadding, reverseLayout = reverseLayout,
                )
            }
        }
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AppLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: LazyGridScope.() -> Unit,
) {
    Box(modifier, propagateMinConstraints = true) {
        CompositionLocalProvider(
            LocalListAuxiliaryWorkAllowed provides
                (LocalListAuxiliaryWorkAllowed.current && !state.isScrollInProgress),
        ) {
            LazyVerticalGrid(
                modifier = if (userScrollEnabled) {
                    Modifier.appScrollBoundaryHaptics(state, reverseLayout)
                } else Modifier,
                columns = columns, state = state, contentPadding = contentPadding,
                reverseLayout = reverseLayout, verticalArrangement = verticalArrangement,
                horizontalArrangement = horizontalArrangement, flingBehavior = flingBehavior,
                userScrollEnabled = userScrollEnabled, overscrollEffect = overscrollEffect, content = content,
            )
        }
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX && userScrollEnabled) {
            Box(Modifier.matchParentSize()) {
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(state),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    trackPadding = contentPadding, reverseLayout = reverseLayout,
                )
            }
        }
    }
}
