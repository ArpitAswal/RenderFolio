package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.constant.AppConstants
import com.example.data.PdfExtractionRepository
import com.example.data.ResumePreferencesRepository
import com.example.model.ResumeProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Idle : UiState
    data class IngestionInProgress(
        val step: String,
        val progress: Float,
        val activeIndex: Int = 0,
        val sections: List<String> = emptyList()
    ) : UiState
    data class ExtractedNeedsVerification(val profile: ResumeProfile) : UiState
    data class VerificationInProgress(val profile: ResumeProfile) : UiState
    data class Success(val profile: ResumeProfile) : UiState
    data class EditMode(val draftProfile: ResumeProfile) : UiState
    data class Error(val message: String) : UiState
}

class ResumeViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "ResumeViewModel"
    private val pdfRepository = PdfExtractionRepository(application.applicationContext)
    private val preferencesRepository = ResumePreferencesRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var lastSuccessProfile: ResumeProfile? = null

    init {
        // App starts clean at UiState.Idle to always show the cold-start PDF attach view first
        viewModelScope.launch {
            try {
                val savedProfile = preferencesRepository.profileFlow.firstOrNull()
                if (savedProfile != null) {
                    lastSuccessProfile = savedProfile
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to load cached profile: ${e.message}")
            }
        }
    }

    fun processPdfResume(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileSize = getFileSize(uri)
                if (fileSize > AppConstants.MAX_FILE_SIZE_BYTES) {
                    _uiState.value = UiState.Error(
                        "File size (${fileSize / (1024 * 1024)}MB) exceeds the maximum allowed limit of 10MB."
                    )
                    return@launch
                }

                _uiState.value = UiState.IngestionInProgress(
                    step = "Initializing document scanner...",
                    progress = 0.05f,
                    activeIndex = 0,
                    sections = emptyList()
                )

                val result = pdfRepository.extractAndParseResume(uri) { step, progress, index, sectionList ->
                    _uiState.value = UiState.IngestionInProgress(
                        step = step,
                        progress = progress,
                        activeIndex = index,
                        sections = sectionList
                    )
                }

                val profile = result.profile
                lastSuccessProfile = profile
                preferencesRepository.saveProfile(profile)
                _uiState.value = UiState.ExtractedNeedsVerification(profile)
            } catch (e: Exception) {
                Log.e(tag, "Resume ingestion failed: ${e.message}", e)
                _uiState.value = UiState.Error(
                    "Failed to process PDF: ${e.localizedMessage ?: "Unknown parsing error"}. Please ensure the PDF is valid."
                )
            }
        }
    }

    fun loadSampleProfile() {
        viewModelScope.launch {
            val sampleSections = listOf(
                "Personal Info & Contact",
                "Summary / Profile",
                "Work Experience",
                "Featured Projects",
                "Technical Skills",
                "Education & Academics"
            )

            // Step 1: Personal Info & Contact (no previous item above)
            _uiState.value = UiState.IngestionInProgress("Personal Info & Contact", 0.08f, 0, sampleSections)
            kotlinx.coroutines.delay(200)
            _uiState.value = UiState.IngestionInProgress("Personal Info & Contact", 0.18f, 0, sampleSections)
            kotlinx.coroutines.delay(600)

            // Step 2: Summary / Profile
            _uiState.value = UiState.IngestionInProgress("Summary / Profile", 0.25f, 1, sampleSections)
            kotlinx.coroutines.delay(250)
            _uiState.value = UiState.IngestionInProgress("Summary / Profile", 0.38f, 1, sampleSections)
            kotlinx.coroutines.delay(650)

            // Step 3: Work Experience
            _uiState.value = UiState.IngestionInProgress("Work Experience", 0.48f, 2, sampleSections)
            kotlinx.coroutines.delay(250)
            _uiState.value = UiState.IngestionInProgress("Work Experience", 0.65f, 2, sampleSections)
            kotlinx.coroutines.delay(700)

            // Step 4: Featured Projects
            _uiState.value = UiState.IngestionInProgress("Featured Projects", 0.72f, 3, sampleSections)
            kotlinx.coroutines.delay(250)
            _uiState.value = UiState.IngestionInProgress("Featured Projects", 0.80f, 3, sampleSections)
            kotlinx.coroutines.delay(650)

            // Step 5: Technical Skills
            _uiState.value = UiState.IngestionInProgress("Technical Skills", 0.86f, 4, sampleSections)
            kotlinx.coroutines.delay(250)
            _uiState.value = UiState.IngestionInProgress("Technical Skills", 0.92f, 4, sampleSections)
            kotlinx.coroutines.delay(650)

            // Step 6: Education & Academics (no next item below)
            _uiState.value = UiState.IngestionInProgress("Education & Academics", 0.96f, 5, sampleSections)
            kotlinx.coroutines.delay(300)
            _uiState.value = UiState.IngestionInProgress("Education & Academics", 1.0f, 5, sampleSections)
            kotlinx.coroutines.delay(500)

            val sample = ResumeProfile.sampleProfile()
            lastSuccessProfile = sample
            preferencesRepository.saveProfile(sample)
            _uiState.value = UiState.ExtractedNeedsVerification(sample)
        }
    }

    fun onVerificationLaunched(profile: ResumeProfile) {
        _uiState.value = UiState.VerificationInProgress(profile)
    }

    fun onStructureCompiled(compiledProfile: ResumeProfile) {
        lastSuccessProfile = compiledProfile
        _uiState.value = UiState.Success(compiledProfile)
        viewModelScope.launch {
            preferencesRepository.saveProfile(compiledProfile)
        }
    }

    fun resetToIdle() {
        _uiState.value = UiState.Idle
    }

    fun enterEditMode() {
        val current = (_uiState.value as? UiState.Success)?.profile ?: lastSuccessProfile
        if (current != null) {
            _uiState.value = UiState.EditMode(current)
        }
    }

    fun cancelEditMode() {
        lastSuccessProfile?.let {
            _uiState.value = UiState.Success(it)
        } ?: run {
            _uiState.value = UiState.Idle
        }
    }

    fun updateProfile(editedProfile: ResumeProfile) {
        lastSuccessProfile = editedProfile
        _uiState.value = UiState.Success(editedProfile)
        viewModelScope.launch {
            preferencesRepository.saveProfile(editedProfile)
        }
    }

    fun clearProfile() {
        viewModelScope.launch {
            lastSuccessProfile = null
            preferencesRepository.clearProfile()
            _uiState.value = UiState.Idle
        }
    }

    fun resetError() {
        _uiState.value = lastSuccessProfile?.let { UiState.Success(it) } ?: UiState.Idle
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        return it.getLong(sizeIndex)
                    }
                }
            }
            getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                it.available().toLong()
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
