package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled

/** For controls that draw their own feedback or hand off directly to a transition. */
object AppNoClickIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoClickIndicationNode()
    override fun equals(other: Any?) = other === this
    override fun hashCode() = javaClass.hashCode()
}

private class NoClickIndicationNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() = drawContent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvideAppElasticFeedback(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (enabled && LocalComponentMotionEnabled.current) {
        CompositionLocalProvider(
            LocalIndication provides AppNoClickIndication,
            LocalRippleConfiguration provides null,
            content = content,
        )
    } else {
        content()
    }
}
