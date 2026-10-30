package com.example

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.model.ResumeProfile
import com.example.ui.PdfExtractionLoadingScreen
import com.example.ui.StructureVerificationActivity
import com.example.ui.theme.ResumeShowcaseTheme
import com.example.viewmodel.ResumeViewModel
import com.example.viewmodel.UiState
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private val viewModel: ResumeViewModel by viewModels()

    private lateinit var coldStartContainer: View
    private lateinit var composeView: ComposeView
    private lateinit var overlayProgress: View
    private lateinit var tvProgressStep: TextView
    private lateinit var pbHorizontal: ProgressBar
    private lateinit var tvProgressPercent: TextView

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.processPdfResume(uri)
        }
    }

    private val verificationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val compiledJson = result.data?.getStringExtra(StructureVerificationActivity.EXTRA_COMPILED_PROFILE)
            if (!compiledJson.isNullOrBlank()) {
                val compiledProfile = ResumeProfile.fromJson(compiledJson)
                viewModel.onStructureCompiled(compiledProfile)
                startActivity(com.example.ui.ResumeVisualiserActivity.createIntent(this@MainActivity, compiledProfile))
            }
        } else {
            viewModel.resetToIdle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        coldStartContainer = findViewById(R.id.view_cold_start)
        composeView = findViewById(R.id.compose_view_complex_layout)
        overlayProgress = findViewById(R.id.overlay_progress)
        tvProgressStep = findViewById(R.id.tv_progress_step)
        pbHorizontal = findViewById(R.id.pb_horizontal)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)

        setupColdStart()
        observeViewModel()
    }

    private fun setupColdStart() {
        val btnSelectPdf = findViewById<Button>(R.id.btn_upload_pdf)
        val cardDropzone = findViewById<View>(R.id.card_dropzone)
        val btnDemoPdf = findViewById<Button>(R.id.btn_load_demo)

        btnSelectPdf?.setOnClickListener {
            filePickerLauncher.launch("application/pdf")
        }

        cardDropzone?.setOnClickListener {
            filePickerLauncher.launch("application/pdf")
        }

        btnDemoPdf?.setOnClickListener {
            loadBundledSamplePdf()
        }
    }

    private fun loadBundledSamplePdf() {
        lifecycleScope.launch {
            try {
                val assetNames = listOf(
                    "arpit_aswal_resume.pdf",
                    "sample_resume.pdf",
                    "ArpitAswal_Flutter_Experience_Resume_0fae348f.pdf"
                )
                var tempFile: File? = null
                for (name in assetNames) {
                    try {
                        val input = assets.open(name)
                        val outFile = File(cacheDir, "sample_resume.pdf")
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                        tempFile = outFile
                        break
                    } catch (_: Exception) {}
                }

                if (tempFile != null && tempFile.exists()) {
                    viewModel.processPdfResume(Uri.fromFile(tempFile))
                } else {
                    val sample = ResumeProfile.sampleProfile()
                    viewModel.onStructureCompiled(sample)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Could not load demo resume: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Idle -> {
                            coldStartContainer.visibility = View.VISIBLE
                            composeView.visibility = View.GONE
                            composeView.disposeComposition()
                            overlayProgress.visibility = View.GONE
                        }
                        is UiState.IngestionInProgress -> {
                            coldStartContainer.visibility = View.GONE
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
                            coldStartContainer.visibility = View.GONE
                            composeView.visibility = View.VISIBLE
                            overlayProgress.visibility = View.GONE
                            val intent = StructureVerificationActivity.createIntent(this@MainActivity, state.profile)
                            verificationLauncher.launch(intent)
                            viewModel.onVerificationLaunched(state.profile)
                        }
                        is UiState.VerificationInProgress -> {
                            coldStartContainer.visibility = View.GONE
                            composeView.visibility = View.VISIBLE
                            overlayProgress.visibility = View.GONE
                        }
                        is UiState.Success -> {
                            // Only ONE finalized screen appears: ResumeVisualizerActivity (launched upon compilation)
                            // Cleanly return to ready state without showing any duplicate screens or edit toolbars
                            coldStartContainer.visibility = View.VISIBLE
                            overlayProgress.visibility = View.GONE
                            composeView.visibility = View.GONE
                            composeView.disposeComposition()
                        }
                        is UiState.EditMode -> {
                            val intent = StructureVerificationActivity.createIntent(this@MainActivity, state.draftProfile)
                            verificationLauncher.launch(intent)
                        }
                        is UiState.Error -> {
                            overlayProgress.visibility = View.GONE
                            Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
