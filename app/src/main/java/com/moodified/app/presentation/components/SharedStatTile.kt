package com.moodified.app.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary

/**
 * Phase 3: replaced the untyped [value: Any] parameter with a sealed
 * [TileValue] type. The old API silently rendered nothing for unrecognised
 * types; callers now get a compile-time error on invalid values.
 *
 * Migration:
 *   value = "42"               →  value = TileValue.Text("42")
 *   value = Icons.Rounded.Foo  →  value = TileValue.Icon(Icons.Rounded.Foo)
 */
sealed interface TileValue {
    @JvmInline value class Text(val text: String)    : TileValue
    @JvmInline value class Icon(val icon: ImageVector) : TileValue
}

@Composable
fun SharedStatTile(
    modifier:    Modifier,
    label:       String,
    value:       TileValue,
    subLabel:    String,
    accentColor: Color,
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(18.dp),
        color    = accentColor.copy(alpha = 0.10f)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedContent(
                targetState    = value,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label          = "statValue"
            ) { v ->
                when (v) {
                    is TileValue.Text -> Text(
                        text  = v.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp
                        ),
                        color = TextPrimary
                    )
                    is TileValue.Icon -> Icon(
                        imageVector        = v.icon,
                        contentDescription = null,
                        tint               = accentColor,
                        modifier           = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight    = FontWeight.SemiBold,
                    fontSize      = 10.sp,
                    letterSpacing = 0.8.sp
                ),
                color = TextSecondary
            )
            Text(
                text  = subLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSecondary
            )
        }
    }
}
