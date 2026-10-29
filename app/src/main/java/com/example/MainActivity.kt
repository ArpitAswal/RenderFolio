package com.example

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.PdfExtractionLoadingScreen
import com.example.ui.ResumeEditScreen
import com.example.ui.ResumeShowcaseScreen
import com.example.ui.StructureVerificationActivity
import com.example.ui.theme.ResumeShowcaseTheme
import com.example.viewmodel.ResumeViewModel
import com.example.viewmodel.UiState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ResumeViewModel by viewModels()

    private lateinit var viewColdStart: View
    private lateinit var composeView: ComposeView
    private lateinit var overlayProgress: FrameLayout
    private lateinit var tvProgressStep: TextView
    private lateinit var pbHorizontal: ProgressBar
    private lateinit var tvProgressPercent: TextView

    // Document Picker launcher for "application/pdf"
    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processPdfResume(uri)
        }
    }

    // Native Runtime Permission launcher with graceful handling
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // User Allowed -> Toast feedback and immediately launch PDF picker!
            Toast.makeText(this, R.string.permission_granted_toast, Toast.LENGTH_SHORT).show()
            launchPdfPicker()
        } else {
            // User Denied -> Graceful explanation dialog with retry, settings, or demo options
            showPermissionDeniedDialog()
        }
    }

    private val structureVerificationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val json = result.data?.getStringExtra(com.example.ui.StructureVerificationActivity.EXTRA_COMPILED_PROFILE)
            if (!json.isNullOrBlank()) {
                val compiled = com.example.model.ResumeProfile.fromJson(json)
                viewModel.onStructureCompiled(compiled)
            } else {
                viewModel.resetToIdle()
            }
        } else {
            // User canceled or pressed back from structure verification -> return cleanly to cold start upload
            viewModel.resetToIdle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        observeViewModel()
    }

    private fun initViews() {
        viewColdStart = findViewById(R.id.view_cold_start)
        composeView = findViewById(R.id.compose_view_complex_layout)
        overlayProgress = findViewById(R.id.overlay_progress)
        tvProgressStep = findViewById(R.id.tv_progress_step)
        pbHorizontal = findViewById(R.id.pb_horizontal)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)
    }

    private fun setupListeners() {
        // PDF Action Button: triggers permission check before launching picker
        viewColdStart.findViewById<Button>(R.id.btn_upload_pdf)?.setOnClickListener {
            checkAndRequestPermission()
        }

        // Upload Dropzone Card: triggers permission check before launching picker
        viewColdStart.findViewById<View>(R.id.card_dropzone)?.setOnClickListener {
            checkAndRequestPermission()
        }

        // Explore Pre-loaded Demo Showcase: loads demo directly without needing permissions
        viewColdStart.findViewById<Button>(R.id.btn_load_demo)?.setOnClickListener {
            viewModel.loadSampleProfile()
        }
    }

    /**
     * Checks if storage permission is already granted.
     * If granted -> launches PDF picker directly.
     * If not granted -> requests runtime permission with user rationale handling.
     */
    private fun checkAndRequestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted -> launch document picker immediately
                launchPdfPicker()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // User previously denied -> show rationale dialog before asking again
                showPermissionRationaleDialog(permission)
            }
            else -> {
                // First-time ask -> trigger native system permission dialog
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun launchPdfPicker() {
        try {
            pdfPickerLauncher.launch("application/pdf")
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open document picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Rationale Dialog shown when explaining why permission is required.
     */
    private fun showPermissionRationaleDialog(permission: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_title)
            .setMessage(R.string.permission_rationale_message)
            .setPositiveButton(R.string.permission_grant_button) { dialog, _ ->
                dialog.dismiss()
                requestPermissionLauncher.launch(permission)
            }
            .setNegativeButton(R.string.permission_demo_button) { dialog, _ ->
                dialog.dismiss()
                viewModel.loadSampleProfile()
            }
            .show()
    }

    /**
     * Graceful fallback dialog when the user clicks 'Deny' on the system permission request.
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_title)
            .setMessage(R.string.permission_dialog_message)
            .setPositiveButton(R.string.permission_settings_button) { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNeutralButton(R.string.permission_demo_button) { dialog, _ ->
                dialog.dismiss()
                viewModel.loadSampleProfile()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, R.string.permission_denied_toast, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: UiState) {
        when (state) {
            is UiState.Idle -> {
                viewColdStart.visibility = View.VISIBLE
                overlayProgress.visibility = View.GONE
                composeView.visibility = View.GONE
            }

            is UiState.IngestionInProgress -> {
                viewColdStart.visibility = View.GONE
                overlayProgress.visibility = View.GONE
                composeView.visibility = View.VISIBLE

                composeView.setContent {
                    ResumeShowcaseTheme {
                        PdfExtractionLoadingScreen(
                            step = state.step,
                            progress = state.progress,
                            activeIndex = state.activeIndex,
                            sections = state.sections
                        )
                    }
                }
            }

            is UiState.ExtractedNeedsVerification -> {
                // PDF extraction is done -> Do NOT display the final resume with info & edit button!
                // Directly launch the Structure Verification screen
                viewColdStart.visibility = View.GONE
                composeView.visibility = View.VISIBLE
                overlayProgress.visibility = View.GONE
                val intent = StructureVerificationActivity.createIntent(this@MainActivity, state.profile)
                structureVerificationLauncher.launch(intent)
                viewModel.onVerificationLaunched(state.profile)
            }

            is UiState.VerificationInProgress -> {
                // Verification is currently active in StructureVerificationActivity
                viewColdStart.visibility = View.GONE
                composeView.visibility = View.VISIBLE
                overlayProgress.visibility = View.GONE
            }

            is UiState.Success -> {
                viewColdStart.visibility = View.GONE
                overlayProgress.visibility = View.GONE
                composeView.visibility = View.VISIBLE

                composeView.setContent {
                    ResumeShowcaseTheme {
                        ResumeShowcaseScreen(
                            profile = state.profile,
                            onEditClicked = {
                                viewModel.onVerificationLaunched(state.profile)
                                structureVerificationLauncher.launch(
                                    com.example.ui.StructureVerificationActivity.createIntent(this@MainActivity, state.profile)
                                )
                            },
                            onUploadAnotherClicked = { viewModel.clearProfile() },
                            onClearClicked = { viewModel.clearProfile() }
                        )
                    }
                }
            }

            is UiState.EditMode -> {
                viewColdStart.visibility = View.GONE
                overlayProgress.visibility = View.GONE
                composeView.visibility = View.VISIBLE

                composeView.setContent {
                    ResumeShowcaseTheme {
                        ResumeEditScreen(
                            initialProfile = state.draftProfile,
                            onSaveClicked = { updated -> viewModel.updateProfile(updated) },
                            onCancelClicked = { viewModel.cancelEditMode() }
                        )
                    }
                }
            }

            is UiState.Error -> {
                overlayProgress.visibility = View.GONE
                showErrorDialog(state.message)
            }
        }
    }

    private fun showErrorDialog(errorMessage: String) {
        AlertDialog.Builder(this)
            .setTitle("Extraction Notice")
            .setMessage(errorMessage)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                viewModel.resetError()
            }
            .setNeutralButton("Load Sample Instead") { dialog, _ ->
                dialog.dismiss()
                viewModel.loadSampleProfile()
            }
            .show()
    }
}
