package com.karamay.app.presentation.intervention

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karamay.app.core.theme.DmSerifDisplay
import com.karamay.app.core.theme.MilkWhite
import com.karamay.app.core.theme.TextPrimary
import com.karamay.app.core.theme.TextTertiary

@Composable
fun InterventionScreen() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MilkWhite)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✦", fontSize = 32.sp, color = TextTertiary)
            Spacer(Modifier.height(12.dp))
            Text(
                text  = "Care",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = "Interventions will arrive once we learn your patterns.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
    }
}
