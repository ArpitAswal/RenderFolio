package com.example.ui.tablet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.constant.AppConstants
import kotlin.math.abs

/**
 * Screen 2 (Tablet): PDF Extraction Loading Screen for Tablet Viewports (sw >= 600dp).
 *
 * Implements Stitch Design: "ResumeShowcase - Extraction Loading (Tablet Light)"
 * (Screen ID: de7143b2754f4a9ca4b4266867128b3b).
 *
 * Architecture & Separation of Concerns:
 * - Isolated into tablet package, completely decoupled from mobile implementation.
 * - Max-width 500dp centered container with generous tablet gutters.
 * - Calibrated 260dp 3-zone cylindrical wheel with tablet-specific typography scale.
 * - Continuous progress bar with zero Material 3 stop dots or indicator gaps.
 */
@Composable
fun PdfExtractionLoadingScreenTablet(
    modifier: Modifier = Modifier,
    step: String,
    progress: Float,
    activeIndex: Int = 0,
    sections: List<String> = emptyList(),
) {
    val effectiveSections = remember(sections) {
        sections.ifEmpty {
            listOf(
                "Personal Info & Contact",
                "Summary / Profile",
                "Work Experience",
                "Featured Projects",
                "Technical Skills",
                "Education & Academics"
            )
        }
    }

    val currentActiveIndex = activeIndex.coerceIn(0, effectiveSections.lastIndex)
    val activeSectionTitle = effectiveSections.getOrElse(currentActiveIndex) { step }

    val animatedIndexAnimatable = remember { Animatable(0f) }
    LaunchedEffect(currentActiveIndex) {
        if (currentActiveIndex == 0) {
            animatedIndexAnimatable.snapTo(0f)
        } else {
            animatedIndexAnimatable.animateTo(
                targetValue = currentActiveIndex.toFloat(),
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        }
    }
    val animatedIndex = animatedIndexAnimatable.value

    val animatedProgressAnimatable = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped <= 0.06f) {
            animatedProgressAnimatable.snapTo(clamped)
        } else {
            animatedProgressAnimatable.animateTo(
                targetValue = clamped,
                animationSpec = tween(durationMillis = 400, easing = LinearEasing)
            )
        }
    }
    val animatedProgress = animatedProgressAnimatable.value

    // Tablet-calibrated pulsing glowing ring transition
    val infiniteTransition = rememberInfiniteTransition(label = "TabletNodeGlowTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TabletPulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TabletPulseAlpha"
    )

    val density = LocalDensity.current

    // Root Canvas: fills entire viewport, centering the 500dp container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(AppConstants.TAG_PROGRESS_INDICATOR),
        contentAlignment = Alignment.Center
    ) {
        // Centered Content Column: strictly bounded to 500dp matching Stitch tablet spec
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Section
            Text(
                text = stringResource(R.string.extraction_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.extraction_subtitle),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Tablet-calibrated wheel spacing
            Spacer(modifier = Modifier.height(28.dp))

            // 3-Zone Cylindrical Extraction Wheel Area (Height: 250dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                contentAlignment = Alignment.Center
            ) {
                effectiveSections.forEachIndexed { index, title ->
                    val d = index - animatedIndex

                    if (abs(d) < 1.45f) {
                        val slotHeightPx = with(density) { 76.dp.toPx() }
                        val translationY = d * slotHeightPx
                        val scale = (1.0f - (abs(d) * 0.14f)).coerceIn(0.78f, 1.0f)
                        val rotationX = (-d * 20f).coerceIn(-28f, 28f)

                        // Zone opacity interpolation:
                        // Top completed: 40% (Stitch spec: style="opacity: 0.4;")
                        // Center active: 100%
                        // Bottom upcoming: 20% (Stitch spec: style="opacity: 0.2;")
                        val itemAlpha = when {
                            d < 0f -> {
                                val t = (1f + d).coerceIn(0f, 1f)
                                0.40f + (0.60f * t)
                            }
                            d > 0f -> {
                                val t = (1f - d).coerceIn(0f, 1f)
                                0.20f + (0.80f * t)
                            }
                            else -> 1.0f
                        }.coerceIn(0f, 1.0f)

                        val isCenterActive = abs(d) < 0.45f
                        val isCompleted = d < -0.45f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .graphicsLayer {
                                    this.translationY = translationY
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.rotationX = rotationX
                                    this.cameraDistance = 14f * density.density
                                    this.alpha = itemAlpha
                                }
                                .then(
                                    if (isCenterActive) {
                                        Modifier.semantics {
                                            liveRegion = LiveRegionMode.Polite
                                            contentDescription =
                                                "Extracting $activeSectionTitle, ${(animatedProgress * 100).toInt()}% complete"
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Indicator Node
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isCenterActive -> {
                                            // Glowing Active Node (26dp pulsing ring + 14dp solid center)
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp * pulseScale)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                        isCompleted -> {
                                            // Completed Solid Node (12dp solid circle)
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                        else -> {
                                            // Upcoming Node (10dp hollow ring with 1.5dp outline)
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                                        shape = CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(18.dp))

                                // Step Content Area
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = if (isCenterActive) 19.sp else 16.sp,
                                            fontWeight = if (isCenterActive) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isCenterActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (isCenterActive) {
                                            val percentText = "${(animatedProgress * 100).toInt().coerceIn(0, 100)}%"
                                            Text(
                                                text = percentText,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (isCenterActive) {
                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Status Badge: "Active Extraction"
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.extraction_active_badge),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Continuous Capsule Progress Bar
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(2.5.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .testTag("pdf_extraction_progress_bar")
                                        ) {
                                            if (animatedProgress > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                                        .clip(RoundedCornerShape(2.5.dp))
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bottom Component Group: Privacy Pill + Dynamic Subtext
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.cold_start_privacy_note),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.extraction_status_format, activeSectionTitle),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

