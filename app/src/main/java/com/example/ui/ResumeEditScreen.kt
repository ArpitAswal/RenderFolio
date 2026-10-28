@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.constant.AppConstants
import com.example.model.*
import com.example.ui.theme.LocalBorderColor

@Composable
fun ResumeEditScreen(
    initialProfile: ResumeProfile,
    onSaveClicked: (ResumeProfile) -> Unit,
    onCancelClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onCancelClicked()
    }

    var fullName by remember { mutableStateOf(initialProfile.hero.fullName) }
    var headlineTitle by remember { mutableStateOf(initialProfile.hero.headlineTitle) }
    var location by remember { mutableStateOf(initialProfile.hero.location) }
    var email by remember { mutableStateOf(initialProfile.hero.contactInfo.email) }
    var phone by remember { mutableStateOf(initialProfile.hero.contactInfo.phone) }
    var summary by remember { mutableStateOf(initialProfile.coreSections.profileSummary) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Edit Resume Profile", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onCancelClicked,
                        modifier = Modifier.testTag(AppConstants.TAG_CANCEL_EDIT_BUTTON)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Edit")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val updatedProfile = initialProfile.copy(
                                hero = initialProfile.hero.copy(
                                    fullName = fullName,
                                    headlineTitle = headlineTitle,
                                    location = location,
                                    contactInfo = initialProfile.hero.contactInfo.copy(
                                        email = email,
                                        phone = phone
                                    )
                                ),
                                coreSections = initialProfile.coreSections.copy(
                                    profileSummary = summary
                                )
                            )
                            onSaveClicked(updatedProfile)
                        },
                        modifier = Modifier
                            .height(38.dp)
                            .testTag(AppConstants.TAG_SAVE_EDIT_BUTTON),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, LocalBorderColor.current)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Candidate Identity", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = headlineTitle,
                        onValueChange = { headlineTitle = it },
                        label = { Text("Headline Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, LocalBorderColor.current)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Contact Details", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, LocalBorderColor.current)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Executive Summary", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = summary,
                        onValueChange = { summary = it },
                        label = { Text("Summary") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )
                }
            }
        }
    }
}
