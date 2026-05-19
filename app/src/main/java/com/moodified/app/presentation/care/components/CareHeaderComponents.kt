package com.moodified.app.presentation.care.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.*
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.intervention.WellBeingDomain
import com.moodified.app.presentation.insight.InsightDomainReadiness

@Composable
fun CareHeader(mood: InferredMoodState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = DeepSage.copy(alpha = 0.08f)) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DeepSage))
                Text(
                    text = "ADAPTIVE CARE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = DeepSage
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = mood.interpretationLabel,
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = DmSerifDisplay, fontSize = 34.sp),
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = mood.explainabilityString,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun WellBeingDomainRow(activeDomain: WellBeingDomain, onSelect: (WellBeingDomain) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(WellBeingDomain.entries.toTypedArray()) { domain ->
            val isSelected = domain == activeDomain
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSelect(domain) },
                color = if (isSelected) DeepSage else MilkDeep,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = domain.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                    color = if (isSelected) MilkWhite else TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun CareOnboardingState(readiness: InsightDomainReadiness, isIntradayComplete: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = SageSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.SelfImprovement, null, tint = DeepSage, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("Cultivating Care", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DmSerifDisplay), color = TextPrimary)
            Spacer(Modifier.height(12.dp))

            val message = if (!isIntradayComplete && readiness.anyReady) {
                "We need a bit more data today to provide personalized guidance. Keep tracking and check back later."
            } else {
                "We are silently learning your rhythms to provide guidance tailored to you. Insights will bloom soon."
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            if (!readiness.anyReady) {
                Spacer(Modifier.height(32.dp))
                val progress = (readiness.activity.progressFraction + readiness.mood.progressFraction) / 2f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(0.6f).height(6.dp).clip(CircleShape),
                    color = DeepSage,
                    trackColor = SageDim
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = DmSerifDisplay, fontSize = 20.sp),
        color = TextPrimary,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}