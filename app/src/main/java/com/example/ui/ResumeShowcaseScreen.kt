@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.constant.AppConstants
import com.example.model.*
import com.example.ui.theme.LocalBorderColor

@Composable
fun ResumeShowcaseScreen(
    profile: ResumeProfile,
    onEditClicked: () -> Unit,
    onUploadAnotherClicked: () -> Unit,
    onClearClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    BackHandler {
        onUploadAnotherClicked()
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 720

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(AppConstants.TAG_SUCCESS_SHOWCASE),
        containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = R.string.app_name.toString(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        FilledTonalButton(
                            onClick = onEditClicked,
                            modifier = Modifier
                                .height(38.dp)
                                .testTag(AppConstants.TAG_EDIT_BUTTON),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit", fontSize = 13.sp)
                        }

                        IconButton(onClick = {
                            shareProfileJson(context, profile)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Profile JSON",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .padding(horizontal = if (isWideScreen) 32.dp else 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item { HeroCard(profile.hero) }
                    item { SummaryCard(profile.coreSections.profileSummary) }
                    item { SkillsSection(profile.coreSections.skills) }
                    item { ExperienceSection(profile.coreSections.workExperience) }
                    item { ProjectsSection(profile.coreSections.projects) }
                    item { EducationSection(profile.coreSections.education) }
                    if (profile.dynamicSections.isNotEmpty()) {
                        items(profile.dynamicSections) { dynamicSection ->
                            DynamicSectionCard(dynamicSection)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("Resume Options") },
                    text = { Text("Would you like to upload a new resume or clear the current profile?") },
                    confirmButton = {
                        Button(onClick = {
                            showClearDialog = false
                            onUploadAnotherClicked()
                        }) {
                            Text("Upload Another PDF")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showClearDialog = false
                            onClearClicked()
                        }) {
                            Text("Clear Profile", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }
    }

@Composable
private fun HeroCard(hero: ProfileHero) {
    val context = LocalContext.current
    val borderColor = LocalBorderColor.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = hero.fullName
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .joinToString("")
                        .ifBlank { "RS" }

                    Text(
                        text = initials,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hero.fullName.ifBlank { "Professional Candidate" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = hero.headlineTitle.ifBlank { "Specialist" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (hero.location.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hero.location,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = borderColor)
            Spacer(modifier = Modifier.height(14.dp))

            val contact = hero.contactInfo
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (contact.email.isNotBlank()) {
                    item {
                        ContactChip(
                            icon = Icons.Default.Email,
                            label = contact.email,
                            onClick = { openExternalIntent(context, "mailto:${contact.email}") }
                        )
                    }
                }
                if (contact.phone.isNotBlank()) {
                    item {
                        ContactChip(
                            icon = Icons.Default.Phone,
                            label = contact.phone,
                            onClick = { openExternalIntent(context, "tel:${contact.phone}") }
                        )
                    }
                }
                if (contact.linkedinUrl.isNotBlank()) {
                    item {
                        ContactChip(
                            icon = Icons.Default.Link,
                            label = "LinkedIn",
                            onClick = { openExternalIntent(context, contact.linkedinUrl) }
                        )
                    }
                }
                if (contact.githubUrl.isNotBlank()) {
                    item {
                        ContactChip(
                            icon = Icons.Default.Code,
                            label = "GitHub",
                            onClick = { openExternalIntent(context, contact.githubUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, LocalBorderColor.current),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: String) {
    if (summary.isBlank()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LocalBorderColor.current)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Professional Summary", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = summary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExperienceSection(experiences: List<WorkExperienceItem>) {
    if (experiences.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LocalBorderColor.current)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Work Experience", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(14.dp))

            experiences.forEachIndexed { index, exp ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = exp.roleTitle.ifBlank { "Role" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = exp.companyName.ifBlank { "Organization" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    if (exp.startDate.isNotBlank()) {
                        Text(text = exp.startDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (exp.bulletPoints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        exp.bulletPoints.forEach { bullet ->
                            Text(text = "• $bullet", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (index < experiences.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = LocalBorderColor.current)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ProjectsSection(projects: List<ProjectItem>) {
    if (projects.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LocalBorderColor.current)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Featured Projects", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(14.dp))

            projects.forEachIndexed { index, proj ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = proj.projectTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (proj.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = proj.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (index < projects.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = LocalBorderColor.current)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SkillsSection(skills: SkillCategories) {
    if (skills.totalCount == 0) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LocalBorderColor.current)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Technical Competencies (${skills.totalCount})", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))

            if (skills.languages.isNotEmpty()) {
                SkillCategoryRow("Languages", skills.languages)
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (skills.frameworks.isNotEmpty()) {
                SkillCategoryRow("Frameworks & SDKs", skills.frameworks)
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (skills.cloudDevOps.isNotEmpty()) {
                SkillCategoryRow("Cloud & DevOps", skills.cloudDevOps)
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (skills.toolsMethodologies.isNotEmpty()) {
                SkillCategoryRow("Tools & Practices", skills.toolsMethodologies)
            }
        }
    }
}

@Composable
private fun SkillCategoryRow(categoryTitle: String, tags: List<String>) {
    Column {
        Text(text = categoryTitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tags.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EducationSection(educationList: List<EducationItem>) {
    if (educationList.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LocalBorderColor.current)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Education & Academics", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))

            educationList.forEach { edu ->
                Text(text = edu.institution, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = edu.degree, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DynamicSectionCard(section: DynamicSection) {
    if (section.items.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LocalBorderColor.current)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(section.sectionTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(10.dp))
            section.items.forEach { item ->
                Text(text = item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                if (item.description.isNotBlank()) {
                    Text(text = item.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun openExternalIntent(context: Context, rawUrl: String) {
    try {
        val targetUri = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://") && !rawUrl.startsWith("mailto:") && !rawUrl.startsWith("tel:")) {
            Uri.parse("https://$rawUrl")
        } else {
            Uri.parse(rawUrl)
        }
        val intent = Intent(Intent.ACTION_VIEW, targetUri)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot open: $rawUrl", Toast.LENGTH_SHORT).show()
    }
}

private fun shareProfileJson(context: Context, profile: ResumeProfile) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, profile.toJson())
            type = "application/json"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export Resume JSON"))
    } catch (_: Exception) {}
}
