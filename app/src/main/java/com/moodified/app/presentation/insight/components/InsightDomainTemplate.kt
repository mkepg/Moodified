package com.moodified.app.presentation.insight.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moodified.app.core.theme.MilkWhite

@Composable
fun InsightDomainTemplate(
    title: String,
    subtitle: String,
    isTracking: Boolean,
    onBack: (() -> Unit)? = null,
    status: (@Composable ColumnScope.() -> Unit)? = null,
    stats: (@Composable ColumnScope.() -> Unit)? = null,
    breakdown: (@Composable ColumnScope.() -> Unit)? = null,
    extras: (@Composable ColumnScope.() -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            InsightHeader(
                title = title,
                subtitle = subtitle,
                isTracking = isTracking,
                eyebrow = null,
                onBack = onBack,
            )
        }
        slotItem(status)
        slotItem(stats)
        slotItem(breakdown)
        slotItem(extras)
    }
}

private fun LazyListScope.slotItem(slot: (@Composable ColumnScope.() -> Unit)?) {
    if (slot != null) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { slot() }
        }
    }
}
