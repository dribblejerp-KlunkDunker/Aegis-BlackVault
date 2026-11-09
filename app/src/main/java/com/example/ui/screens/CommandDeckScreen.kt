package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ForensicMathEngine
import com.example.model.SubjectProfile
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.min

@Composable
fun CommandDeckScreen(
    activeSubject: SubjectProfile,
    allSubjects: List<SubjectProfile>,
    selectedSpectrumToken: String,
    containmentOverride: Boolean,
    onSelectSubject: (String) -> Unit,
    onSelectSpectrumToken: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spectrumBars = remember(activeSubject) {
        ForensicMathEngine.computeSpectrumBars(activeSubject)
    }
    val selectedBar = spectrumBars.find { it.token == selectedSpectrumToken } ?: spectrumBars.first()

    val avgThreat = activeSubject.threatMetrics.compositeScore
    val defconLabel = when {
        containmentOverride -> "DEFCON 3 (CONTAINED)"
        avgThreat >= 85 -> "DEFCON 1 (IMMINENT)"
        avgThreat >= 70 -> "DEFCON 2 (CRITICAL)"
        else -> "DEFCON 4 (NOMINAL)"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Active Target Dossier Switcher + Archetype Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE TARGET DOSSIER // ${activeSubject.codeId}",
                        color = PhosphorGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${activeSubject.scrapedArtifacts.size} TIMESTAMPED LOGS",
                        color = AmberSignal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allSubjects, key = { it.id }) { subj ->
                        val isSel = subj.id == activeSubject.id
                        Box(
                            modifier = Modifier
                                .testTag("subject_chip_${subj.handle}")
                                .background(
                                    if (isSel) PhosphorGreen else ObsidianBg,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSel) PhosphorGreen else PhosphorGreenDim,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectSubject(subj.id) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = subj.handle,
                                color = if (isSel) ObsidianBg else PhosphorGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Quick Summary Metrics
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurface, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "ARCHETYPE: ${activeSubject.linguisticProfile.archetypeTitle}",
                        color = AmberSignal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ALIAS: ${activeSubject.alias}",
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "CHRONO-LOC: ${activeSubject.loc}",
                        color = CyanTelemetry,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "BURROWS' Δ: ${activeSubject.burrowsDelta} (${activeSubject.styloMatch}% MATCH)",
                            color = PhosphorGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "LIWC ATI: ${activeSubject.linguisticProfile.analyticalThinkingIndex}/100 | CLOUT: ${activeSubject.linguisticProfile.cloutDominanceIndex}/100",
                            color = VioletHf,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Navigation Shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigateToTab(2) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberSignal)
                    ) {
                        Text("WHAT MAKES THEM TICK & PLAYBOOK", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onNavigateToTab(1) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanTelemetry)
                    ) {
                        Text("OPEN 3D BRAIN / MIND MAP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Threat Matrix & Behavioral Risk Telemetry
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, AlarmRed.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Threat Matrix",
                            tint = AlarmRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "THREAT & INFLUENCE MATRIX",
                            color = AlarmRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = defconLabel,
                        color = if (containmentOverride) AmberSignal else AlarmRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                ThreatMetricBar(if (activeSubject.restrictedOutputsUnlocked) "Pathway to Escalation / Hostility" else "Pathway to Escalation (WITHHELD: consent required)", activeSubject.threatMetrics.pathwayToViolence, AlarmRed)
                ThreatMetricBar("Memetic Virality & Reach", activeSubject.threatMetrics.memeticVirality, AmberSignal)
                ThreatMetricBar("Front/Back-Stage Context Collapse", activeSubject.threatMetrics.contextCollapse, PhosphorGreen)
                ThreatMetricBar("GraphRAG Network Centrality", activeSubject.threatMetrics.networkCentrality, CyanTelemetry)
            }
        }

        // 3. 30-Token Calibrated Burrows' Delta Stylometric Spectrum
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "30-TOKEN CALIBRATED STYLO SPECTRUM",
                        color = PhosphorGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "TOKEN \"${selectedBar.token.uppercase(Locale.US)}\": Z=${if (selectedBar.zScore >= 0) "+${selectedBar.zScore}" else selectedBar.zScore}σ (${selectedBar.freqPct}%)",
                        color = AmberSignal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(ObsidianBg, RoundedCornerShape(4.dp))
                        .border(1.dp, PhosphorGreenDim.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    spectrumBars.forEach { item ->
                        val isSel = item.token == selectedSpectrumToken
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(item.barHeightPct / 100f)
                                .background(
                                    if (isSel) AmberSignal else PhosphorGreen.copy(alpha = 0.55f),
                                    RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                )
                                .clickable { onSelectSpectrumToken(item.token) }
                        )
                    }
                }
                Text(
                    text = "Tap any function-word bar above to inspect its empirical Z-score deviation against the 50-word population baseline.",
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }
        }

        // 4. Equirectangular Live Radar Tracking + 24-Hour Diurnal Chronolocation Histogram
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar",
                            tint = PhosphorGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "EQUIRECTANGULAR RADAR & DIURNAL CHRONOLOCATION",
                            color = PhosphorGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "UTC${if (activeSubject.utcOffset >= 0) "+${activeSubject.utcOffset}" else activeSubject.utcOffset}",
                        color = AmberSignal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Radar Canvas
                    RadarMapWidget(
                        activeSubject = activeSubject,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                            .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                    )

                    // 24-Hour UTC Activity Histogram
                    DiurnalHistogramWidget(
                        hourlyBins = activeSubject.hourlyBins,
                        troughLabel = activeSubject.troughWindowLabel,
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                            .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreatMetricBar(
    label: String,
    value: Int,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = TextPrimary, fontSize = 11.sp)
            Text(text = "$value%", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(ObsidianBg, RoundedCornerShape(2.dp))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((value / 100f).coerceIn(0.02f, 1f))
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun RadarMapWidget(
    activeSubject: SubjectProfile,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_deg"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(size.width, size.height) * 0.40f

            drawCircle(PhosphorGreen.copy(alpha = 0.25f), radius = radius, center = Offset(cx, cy), style = Stroke(1.2f))
            drawCircle(PhosphorGreen.copy(alpha = 0.16f), radius = radius * 0.6f, center = Offset(cx, cy), style = Stroke(1f))
            drawLine(PhosphorGreen.copy(alpha = 0.18f), Offset(cx - radius, cy), Offset(cx + radius, cy), 1f)
            drawLine(PhosphorGreen.copy(alpha = 0.18f), Offset(cx, cy - radius), Offset(cx, cy + radius), 1f)

            rotate(degrees = sweepAngle, pivot = Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.85f to PhosphorGreen.copy(alpha = 0.05f),
                        1f to PhosphorGreen.copy(alpha = 0.38f),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 0f,
                    sweepAngle = 60f,
                    useCenter = true,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }

            // Equirectangular coordinate projection
            val px = cx + ((activeSubject.lon.toFloat() / 180f) * (radius * 0.85f))
            val py = cy - ((activeSubject.lat.toFloat() / 90f) * (radius * 0.85f))

            drawCircle(AlarmRed.copy(alpha = 0.35f), radius = 11f, center = Offset(px, py))
            drawCircle(AlarmRed, radius = 4.8f, center = Offset(px, py))
        }

        Text(
            text = String.format(Locale.US, "%.2fN, %.2fE", activeSubject.lat, activeSubject.lon),
            color = AmberSignal,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        )
    }
}

@Composable
private fun DiurnalHistogramWidget(
    hourlyBins: List<Int>,
    troughLabel: String,
    modifier: Modifier = Modifier
) {
    val maxVal = (hourlyBins.maxOrNull() ?: 1).coerceAtLeast(1)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Diurnal",
                tint = CyanTelemetry,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "24H UTC ACTIVITY HISTOGRAM",
                color = CyanTelemetry,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (hr in 0 until 24) {
                val count = hourlyBins.getOrElse(hr) { 0 }
                val ratio = (count.toFloat() / maxVal).coerceIn(0.08f, 1f)
                val isLow = count == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(ratio)
                        .background(
                            if (isLow) AlarmRed.copy(alpha = 0.65f) else PhosphorGreen.copy(alpha = 0.75f),
                            RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                        )
                )
            }
        }

        Text(
            text = troughLabel,
            color = AmberSignal,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
