package com.example.ui

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
 * Screen 2: PDF Extraction Loading View with Flutter ListWheelScrollView 3D Cylindrical Wheel Animation.
 *
 * Dynamic features:
 * 1. Shows actual resume section titles detected from the uploaded document (e.g. Summary vs Profile,
 *    Experience vs Employment History, etc.).
 * 2. Deliberately calibrated timing so the user's eye can comfortably see the live percentage and progress bar.
 * 3. 3-Zone 3D ListWheelScrollView wheel scrolling from bottom to top:
 *    - For the first section, NO previous section title is shown above.
 *    - For the last section, NO upcoming section title is shown below.
 *    - Items roll smoothly from bottom to top with 3D rotation, scaling, and opacity interpolation.
 * 4. Harmonized Spacing Rhythm:
 *    - Completely centered on screen without any artificial card background.
 *    - Gap 1 (Header -> Wheel): 24dp.
 *    - Gap 2 (Wheel -> Status): 20dp.
 *    - 220dp wheel container with 68dp slots to eliminate empty voids.
 */
@Composable
fun PdfExtractionLoadingScreen(
    modifier: Modifier = Modifier,
    step: String,
    progress: Float,
    activeIndex: Int = 0,
    sections: List<String> = emptyList(),
) {
    // Pipeline sections: uses actual detected resume section titles, or fallback standard headings
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
            // When starting a new extraction, snap directly to 0 with no backward roll animation
            animatedIndexAnimatable.snapTo(0f)
        } else {
            animatedIndexAnimatable.animateTo(
                targetValue = currentActiveIndex.toFloat(),
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
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

    // Pulsing glowing indicator for the active center node
    val infiniteTransition = rememberInfiniteTransition(label = "NodeGlowTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val density = LocalDensity.current

    // Root Canvas: fills entire viewport, centering the unified extraction layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(AppConstants.TAG_PROGRESS_INDICATOR),
        contentAlignment = Alignment.Center
    ) {
        // Centered Content Column: bounded width, zero card background, unified vertical rhythm
        Column(
            modifier = Modifier
                .widthIn(max = AppConstants.EXTRACTION_CONTENT_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top Header Component (Static Text Group)
            Text(
                text = stringResource(R.string.extraction_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.extraction_subtitle),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Spacing above middle scrolling wheel: consistent 16dp
            Spacer(modifier = Modifier.height(AppConstants.EXTRACTION_SCROLLING_SPACING_DP.dp))

            // Middle Component: 3D Cylindrical Wheel Animation (height 220dp, seamless canvas background)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppConstants.EXTRACTION_WHEEL_HEIGHT_DP.dp),
                contentAlignment = Alignment.Center
            ) {
                // Render wheel items with 3D cylindrical transform
                effectiveSections.forEachIndexed { index, title ->
                    val d = index - animatedIndex

                    // Only render items currently visible in the 3-zone cylindrical wheel
                    if (abs(d) < 1.45f) {
                        val slotHeightPx = with(density) { AppConstants.EXTRACTION_SLOT_HEIGHT_DP.dp.toPx() }
                        val translationY = d * slotHeightPx
                        val scale = (1.0f - (abs(d) * 0.16f)).coerceIn(0.75f, 1.0f)
                        val rotationX = (-d * 22f).coerceIn(-30f, 30f)

                        // Zone opacity interpolation:
                        // Center (d == 0): 100% bright
                        // Top completed zone (d < 0): 30% faded
                        // Bottom upcoming zone (d > 0): 12% ghost
                        val itemAlpha = when {
                            d < 0f -> {
                                val t = (1f + d).coerceIn(0f, 1f)
                                0.30f + (0.70f * t)
                            }
                            d > 0f -> {
                                val t = (1f - d).coerceIn(0f, 1f)
                                0.12f + (0.88f * t)
                            }
                            else -> 1.0f
                        }.coerceIn(0f, 1.0f)

                        val isCenterActive = abs(d) < 0.45f
                        val isCompleted = d < -0.45f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .graphicsLayer {
                                    this.translationY = translationY
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.rotationX = rotationX
                                    this.cameraDistance = 12f * density.density
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
                                // Step Indicator Node on the vertical timeline
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isCenterActive -> {
                                            // Active Glowing Indicator Node
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp * pulseScale)
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
                                            // Solid Completed Indicator Node
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                        else -> {
                                            // Dim Inactive Indicator Node
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

                                Spacer(modifier = Modifier.width(16.dp))

                                // Step Content Area
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = if (isCenterActive) 18.sp else 15.sp,
                                            fontWeight = if (isCenterActive) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isCenterActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (isCenterActive) {
                                            val percentText = "${(animatedProgress * 100).toInt().coerceIn(0, 100)}%"
                                            Text(
                                                text = percentText,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (isCenterActive) {
                                        Spacer(modifier = Modifier.height(5.dp))

                                        // Status Badge Label: "Active Extraction"
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.extraction_active_badge),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Progress Bar: Unified continuous bar without Material 3 gap or stop dot
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

            // Spacing below middle scrolling wheel: consistent 16dp
            Spacer(modifier = Modifier.height(AppConstants.EXTRACTION_SCROLLING_SPACING_DP.dp))

            // Bottom Component Group: Privacy Trust Pill + Dynamic Section Subtext
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.cold_start_privacy_note),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Universal Dynamic Subtext: dynamically reflects active section title
            Text(
                text = stringResource(R.string.extraction_status_format, activeSectionTitle),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
