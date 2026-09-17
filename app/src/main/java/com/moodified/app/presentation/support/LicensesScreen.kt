package com.moodified.app.presentation.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.presentation.support.data.License
import com.moodified.app.presentation.support.data.Licenses

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite)
                .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item { LicensesHeader(onBack = onBack) }
        itemsIndexed(Licenses.all) { index, license ->
            LicenseRow(
                license = license,
                isExpanded = expandedIndex == index,
                onToggle = { expandedIndex = if (expandedIndex == index) null else index },
            )
        }
    }
}

@Composable
private fun LicensesHeader(onBack: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MilkWhite)
                .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = DeepSage.copy(alpha = 0.08f)) {
                Text(
                    text = "LICENSES",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                            fontSize = 10.sp,
                        ),
                    color = DeepSage,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Open-source",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Moodified is built on the shoulders of these projects.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LicenseRow(
    license: License,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        color = SageSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = license.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                    )
                    Text(
                        text = "${license.version} · ${license.licenseType.name.replace('_', ' ').lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = Licenses.textFor(license.licenseType),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = license.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }
        }
    }
}
