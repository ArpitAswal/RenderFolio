package com.example

import android.app.Activity
import android.content.Intent
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
import com.example.data.ResumeCloudRepository
import com.example.data.ResumePreferencesRepository
import com.example.model.ResumeProfile
import com.example.ui.PdfExtractionLoadingScreen
import com.example.ui.ResumeVisualiserActivity
import com.example.ui.ShowcaseQrBottomSheet
import com.example.ui.StructureVerificationActivity
import com.example.ui.TailoredShowcaseSelectorBottomSheet
import com.example.ui.theme.ResumeShowcaseTheme
import com.example.viewmodel.ResumeViewModel
import com.example.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private val viewModel: ResumeViewModel by viewModels()
    private lateinit var preferencesRepository: ResumePreferencesRepository
    private val cloudRepository by lazy { ResumeCloudRepository() }

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
        if (result.resultCode == Activity.RESULT_OK) {
            val compiledJson = result.data?.getStringExtra(StructureVerificationActivity.EXTRA_COMPILED_PROFILE)
            if (!compiledJson.isNullOrBlank()) {
                val compiledProfile = ResumeProfile.fromJson(compiledJson)
                viewModel.onStructureCompiled(compiledProfile)
                startActivity(ResumeVisualiserActivity.createIntent(this@MainActivity, compiledProfile))
            }
        } else {
            viewModel.resetToIdle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferencesRepository = ResumePreferencesRepository.getInstance(applicationContext)

        coldStartContainer = findViewById(R.id.view_cold_start)
        composeView = findViewById(R.id.compose_view_complex_layout)
        overlayProgress = findViewById(R.id.overlay_progress)
        tvProgressStep = findViewById(R.id.tv_progress_step)
        pbHorizontal = findViewById(R.id.pb_horizontal)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)

        setupColdStart()
        observeViewModel()
        observeCompiledProfile()

        // Handle Deep Link on initial launch
        handleDeepLinkIntent(intent)

        // Asynchronously purge expired resumes and reconcile local showcases with Cloud Firestore
        syncShowcasesWithCloud()
    }

    override fun onResume() {
        super.onResume()
        preferencesRepository.refresh()
        syncShowcasesWithCloud()
    }

    private fun syncShowcasesWithCloud() {
        lifecycleScope.launch(Dispatchers.IO) {
            cloudRepository.purgeExpiredResumes()
            val cloudShowcases = cloudRepository.fetchUserShowcases().getOrNull()
            if (cloudShowcases != null && cloudShowcases.isNotEmpty()) {
                preferencesRepository.syncFromCloud(cloudShowcases)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        // Support HTTPS: https://lightweight-firebase-projects.web.app/r/{shareId} or https://resumeshowcase.web.app/r/{shareId}
        val isHttpMatch = (uri.scheme == "https" || uri.scheme == "http") &&
                (uri.host == "lightweight-firebase-projects.web.app" || uri.host == "resumeshowcase.web.app") &&
                uri.path?.startsWith("/r/") == true

        // Support Custom Scheme: resumeshowcase://r/{shareId}
        val isCustomSchemeMatch = uri.scheme == "resumeshowcase" &&
                (uri.host == "r" || uri.path?.startsWith("/r/") == true)

        if (isHttpMatch || isCustomSchemeMatch) {
            val shareId = uri.lastPathSegment
            if (!shareId.isNullOrBlank()) {
                // Forward directly to ResumeVisualiserActivity to avoid cold-start flicker
                val visualiserIntent = Intent(Intent.ACTION_VIEW, uri, this, ResumeVisualiserActivity::class.java)
                startActivity(visualiserIntent)
            }
        }
    }

    private fun setupColdStart() {
        val btnSelectPdf = findViewById<Button>(R.id.btn_upload_pdf)
        val cardDropzone = findViewById<View>(R.id.card_dropzone)
        val btnDemoPdf = findViewById<Button>(R.id.btn_load_demo)
        val btnViewActive = findViewById<View>(R.id.btn_view_active_visualizer)
        val btnShowQr = findViewById<View>(R.id.btn_show_active_qr)

        btnSelectPdf?.setOnClickListener {
            filePickerLauncher.launch("application/pdf")
        }

        cardDropzone?.setOnClickListener {
            filePickerLauncher.launch("application/pdf")
        }

        btnDemoPdf?.setOnClickListener {
            // Directly launch the Visualizer with pre-defined demo showcase for first-time exploration
            val sample = ResumeProfile.sampleProfile()
            startActivity(ResumeVisualiserActivity.createIntent(this, sample))
        }

        // Visualizer Launch: Direct if 1 resume, dynamic selector sheet if multiple tailored resumes
        btnViewActive?.setOnClickListener {
            val showcases = preferencesRepository.getShowcases()
            when {
                showcases.isEmpty() -> {
                    Toast.makeText(this, "No compiled showcase found.", Toast.LENGTH_SHORT).show()
                }
                showcases.size == 1 -> {
                    // Single resume: directly open ResumeVisualiserActivity
                    startActivity(ResumeVisualiserActivity.createIntent(this, showcases.first().profile))
                }
                else -> {
                    // Multiple tailored resumes: open dynamic selector bottom sheet
                    val selector = TailoredShowcaseSelectorBottomSheet.newInstance(TailoredShowcaseSelectorBottomSheet.Mode.VISUALISE)
                    selector.onShowcaseSelected = { selectedShowcase ->
                        startActivity(ResumeVisualiserActivity.createIntent(this@MainActivity, selectedShowcase.profile))
                    }
                    selector.show(supportFragmentManager, TailoredShowcaseSelectorBottomSheet.TAG)
                }
            }
        }

        // Show QR Code: Direct if 1 resume, dynamic selector sheet if multiple tailored resumes
        btnShowQr?.setOnClickListener {
            val showcases = preferencesRepository.getShowcases()
            when {
                showcases.isEmpty() -> {
                    Toast.makeText(this, "No compiled showcase found.", Toast.LENGTH_SHORT).show()
                }
                showcases.size == 1 -> {
                    // Single resume: directly open ShowcaseQrBottomSheet
                    val item = showcases.first()
                    val sheet = ShowcaseQrBottomSheet.newInstance(
                        shareUrl = item.shareUrl,
                        candidateName = item.candidateName,
                        isCelebration = false
                    )
                    sheet.onViewVisualizer = {
                        startActivity(ResumeVisualiserActivity.createIntent(this, item.profile))
                    }
                    sheet.show(supportFragmentManager, ShowcaseQrBottomSheet.TAG)
                }
                else -> {
                    // Multiple tailored resumes: open selector bottom sheet first
                    val selector = TailoredShowcaseSelectorBottomSheet.newInstance(TailoredShowcaseSelectorBottomSheet.Mode.SHOW_QR)
                    selector.onShowcaseSelected = { selectedShowcase ->
                        val sheet = ShowcaseQrBottomSheet.newInstance(
                            shareUrl = selectedShowcase.shareUrl,
                            candidateName = selectedShowcase.candidateName,
                            isCelebration = false
                        )
                        sheet.onViewVisualizer = {
                            startActivity(ResumeVisualiserActivity.createIntent(this@MainActivity, selectedShowcase.profile))
                        }
                        sheet.show(supportFragmentManager, ShowcaseQrBottomSheet.TAG)
                    }
                    selector.show(supportFragmentManager, TailoredShowcaseSelectorBottomSheet.TAG)
                }
            }
        }
    }

    private fun observeCompiledProfile() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferencesRepository.showcasesFlow.collect { showcases ->
                    val btnDemo = findViewById<Button>(R.id.btn_load_demo)
                    val layoutActiveActions = findViewById<View>(R.id.layout_active_showcase_actions)

                    if (showcases.isNotEmpty()) {
                        btnDemo?.visibility = View.GONE
                        layoutActiveActions?.visibility = View.VISIBLE
                    } else {
                        btnDemo?.visibility = View.VISIBLE
                        layoutActiveActions?.visibility = View.GONE
                    }
                }
            }
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
                    viewModel.loadSampleProfile()
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
