package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.ResumeCloudRepository
import com.example.data.ResumePreferencesRepository
import com.example.databinding.ActivityStructureVerificationBinding
import com.example.model.ResumeProfile
import com.example.ui.adapter.PaneTarget
import com.example.ui.adapter.SectionType
import com.example.ui.adapter.StructureSectionsAdapter
import com.example.ui.dialog.AddCustomSectionBottomSheet
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class StructureVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStructureVerificationBinding
    private lateinit var adapter: StructureSectionsAdapter
    private var rightAdapter: StructureSectionsAdapter? = null
    private lateinit var preferencesRepository: ResumePreferencesRepository
    private var itemTouchHelper: ItemTouchHelper? = null
    private var rightItemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStructureVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesRepository = ResumePreferencesRepository.getInstance(applicationContext)

        setupToolbar()
        setupKeyboardAndInsetsHandling()
        loadProfileAndSetupRecyclerView()
    }

    private fun setupKeyboardAndInsetsHandling() {
        val density = resources.displayMetrics.density
        val basePaddingBottom = (96 * density).toInt()

        binding.rvSections?.let { rv ->
            ViewCompat.setOnApplyWindowInsetsListener(rv) { v, insets ->
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                v.updatePadding(bottom = basePaddingBottom + maxOf(imeBottom, navBottom))
                insets
            }
        }

        binding.nestedScrollSections?.let { scroll ->
            ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                v.updatePadding(bottom = basePaddingBottom + maxOf(imeBottom, navBottom))
                insets
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Setup the Compile button in the action layout
        val menu = binding.toolbar.menu
        val compileItem = menu.findItem(R.id.action_compile)
        val actionView = compileItem?.actionView
        val btnCompile = actionView?.findViewById<Button>(R.id.btnMenuCompile)

        btnCompile?.setOnClickListener {
            compileAndSave()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_compile) {
                compileAndSave()
                true
            } else {
                false
            }
        }
    }

    private fun loadProfileAndSetupRecyclerView() {
        val jsonProfile = intent.getStringExtra(EXTRA_PROFILE_JSON)

        lifecycleScope.launch {
            val baseProfile = if (!jsonProfile.isNullOrBlank()) {
                try {
                    ResumeProfile.fromJson(jsonProfile)
                } catch (_: Exception) {
                    preferencesRepository.profileFlow.firstOrNull() ?: ResumeProfile.sampleProfile()
                }
            } else {
                preferencesRepository.profileFlow.firstOrNull() ?: ResumeProfile.sampleProfile()
            }

            setupRecyclerView(baseProfile)
        }
    }

    private fun setupRecyclerView(profile: ResumeProfile) {
        val rvLeft = binding.rvSectionsLeft
        val rvRight = binding.rvSectionsRight
        val isDualPane = rvLeft != null && rvRight != null

        if (isDualPane) {
            // Tablet Dual-Pane Architecture (Foundations & Credentials in Left, Experience & Portfolio in Right)
            adapter = StructureSectionsAdapter(
                profile = profile,
                onStartDrag = { viewHolder ->
                    itemTouchHelper?.startDrag(viewHolder)
                },
                onAddCustomSectionClicked = {},
                paneTarget = PaneTarget.LEFT
            )

            val targetRight = StructureSectionsAdapter(
                profile = profile,
                onStartDrag = { viewHolder ->
                    rightItemTouchHelper?.startDrag(viewHolder)
                },
                onAddCustomSectionClicked = {},
                paneTarget = PaneTarget.RIGHT
            )
            rightAdapter = targetRight

            // Wire shared full-width Add Custom Section button across both panes
            binding.btnAddCustomSection?.setOnClickListener {
                val sheet = AddCustomSectionBottomSheet { title ->
                    val leftCount = adapter.sections.count { it.type == SectionType.CUSTOM }
                    val rightCount = rightAdapter?.sections?.count { it.type == SectionType.CUSTOM } ?: 0

                    if (leftCount <= rightCount) {
                        adapter.addCustomSection(title)
                        binding.rvSectionsLeft?.smoothScrollToPosition(adapter.itemCount - 1)
                    } else {
                        rightAdapter?.addCustomSection(title)
                        val rightPos = (rightAdapter?.itemCount ?: 1) - 1
                        if (rightPos >= 0) {
                            binding.rvSectionsRight?.smoothScrollToPosition(rightPos)
                        }
                    }
                    binding.nestedScrollSections?.post {
                        binding.nestedScrollSections?.fullScroll(View.FOCUS_DOWN)
                    }
                }
                sheet.show(supportFragmentManager, AddCustomSectionBottomSheet.TAG)
            }

            // Setup Left Column RecyclerView
            rvLeft.layoutManager = LinearLayoutManager(this)
            rvLeft.isNestedScrollingEnabled = false
            val leftCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled(): Boolean = false
            }
            itemTouchHelper = ItemTouchHelper(leftCallback).also {
                it.attachToRecyclerView(rvLeft)
            }
            rvLeft.adapter = adapter

            // Setup Right Column RecyclerView
            rvRight.layoutManager = LinearLayoutManager(this)
            rvRight.isNestedScrollingEnabled = false
            val rightCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return targetRight.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled(): Boolean = false
            }
            rightItemTouchHelper = ItemTouchHelper(rightCallback).also {
                it.attachToRecyclerView(rvRight)
            }
            rvRight.adapter = targetRight
        } else {
            // Mobile Single-Column Setup
            rightAdapter = null
            rightItemTouchHelper = null

            adapter = StructureSectionsAdapter(
                profile = profile,
                onStartDrag = { viewHolder ->
                    itemTouchHelper?.startDrag(viewHolder)
                },
                onAddCustomSectionClicked = {
                    val sheet = AddCustomSectionBottomSheet { title ->
                        adapter.addCustomSection(title)
                        binding.rvSections?.smoothScrollToPosition(adapter.itemCount - 2)
                    }
                    sheet.show(supportFragmentManager, AddCustomSectionBottomSheet.TAG)
                },
                paneTarget = PaneTarget.ALL
            )

            binding.rvSections?.layoutManager = LinearLayoutManager(this)
            val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled(): Boolean = false
            }
            itemTouchHelper = ItemTouchHelper(callback).also {
                binding.rvSections?.let { rv -> it.attachToRecyclerView(rv) }
            }
            binding.rvSections?.adapter = adapter
        }
    }

    private fun compileAndSave() {
        val hero = adapter.currentHero
        if (hero.fullName.isBlank()) {
            Toast.makeText(this, "Please provide your Full Name.", Toast.LENGTH_SHORT).show()
            return
        }
        if (hero.headlineTitle.isBlank()) {
            Toast.makeText(this, "Please provide your Professional Headline / Title.", Toast.LENGTH_SHORT).show()
            return
        }
        if (hero.targetCompany.isBlank()) {
            Toast.makeText(this, getString(R.string.error_target_company_required), Toast.LENGTH_SHORT).show()
            return
        }

        // Strict Phone Validation (rejects standalone country codes like +91 without subscriber digits)
        val digitsOnly = hero.contactInfo.phone.filter { it.isDigit() }
        if (digitsOnly.length < 10) {
            Toast.makeText(this, "Please provide a valid Phone Number (at least 10 digits).", Toast.LENGTH_SHORT).show()
            return
        }

        // Strict Email Validation (rejects malformed domains like abc@.com)
        val email = hero.contactInfo.email.trim()
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
        if (email.isBlank() || !emailRegex.matches(email)) {
            Toast.makeText(this, "Please provide a valid Email Address (e.g. name@domain.com).", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (hero.location.isBlank()) {
            Toast.makeText(this, "Please provide your Location / Country.", Toast.LENGTH_SHORT).show()
            return
        }

        // Profile Links Validation
        for ((i, link) in adapter.profileLinks.withIndex()) {
            if (link.isNotBlank() && !isValidUrl(link)) {
                Toast.makeText(this, "Profile Link #${i + 1}: Please enter a valid URL.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Summary Character Limit Validation (allows up to 600 characters)
        val summary = adapter.currentSummary
        if (summary.trim().length > 600) {
            Toast.makeText(
                this,
                "Professional Summary cannot exceed 600 characters (currently ${summary.trim().length}).",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val careerAdapter = rightAdapter ?: adapter

        // Work Experience Validation (Mandatory for Experienced, Optional for Fresher)
        if (careerAdapter.isExperienced) {
            if (careerAdapter.currentExperiences.isEmpty()) {
                Toast.makeText(
                    this,
                    "Experienced candidates must have at least one Work Experience position (or switch to Fresher).",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            for ((i, exp) in careerAdapter.currentExperiences.withIndex()) {
                if (exp.companyName.isBlank()) {
                    Toast.makeText(this, "Position #${i + 1}: Company Name is required.", Toast.LENGTH_SHORT).show()
                    return
                }
                if (exp.roleTitle.isBlank()) {
                    Toast.makeText(this, "Position #${i + 1}: Role / Position Title is required.", Toast.LENGTH_SHORT)
                        .show()
                    return
                }
                if (!validateDuration(exp.startDate, exp.endDate)) {
                    Toast.makeText(
                        this,
                        "Position #${i + 1}: Please enter valid start and end dates for Duration (e.g. 2020 - 2024 or Aug 2023 - Present).",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                if (exp.bulletPoints.isEmpty() || exp.bulletPoints.all { it.isBlank() }) {
                    Toast.makeText(
                        this,
                        "Position #${i + 1}: Role & Responsibilities are required.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            }
        }

        // Projects Validation (Project Title, Mandatory Repository / Deployed URL, and Highlights)
        for ((i, proj) in careerAdapter.currentProjects.withIndex()) {
            if (proj.projectTitle.isBlank()) {
                Toast.makeText(this, "Project #${i + 1}: Project Title is required.", Toast.LENGTH_SHORT).show()
                return
            }
            val projectUrl = proj.demoUrl.ifBlank { proj.codeUrl }
            if (projectUrl.isBlank()) {
                Toast.makeText(this, "Project #${i + 1}: Repository / Deployed URL is required.", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            if (!isValidUrl(projectUrl)) {
                Toast.makeText(
                    this,
                    "Project #${i + 1}: Please enter a valid Repository / Deployed URL (e.g. https://github.com/...).",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            if (proj.description.isBlank()) {
                Toast.makeText(this, "Project #${i + 1}: Key Highlights & Features are required.", Toast.LENGTH_SHORT)
                    .show()
                return
            }
        }

        // Technical Skills Validation (Mandatory: at least one skill required)
        if (adapter.currentSkills.totalCount == 0) {
            Toast.makeText(this, "Please add at least one Technical Skill.", Toast.LENGTH_SHORT).show()
            return
        }

        // Custom / Optional Sections Validation (across both Left and Right panes)
        val allCustomSections = adapter.currentDynamicSections + (rightAdapter?.currentDynamicSections ?: emptyList())
        for ((sectionTitle, items) in allCustomSections) {
            if (items.isEmpty()) continue
            for ((i, item) in items.withIndex()) {
                if (item.title.isBlank()) {
                    Toast.makeText(this, "$sectionTitle #${i + 1}: Title is required.", Toast.LENGTH_SHORT).show()
                    return
                }
                if (item.bulletPoints.isEmpty() && item.description.isBlank()) {
                    Toast.makeText(
                        this,
                        "$sectionTitle #${i + 1}: Details & Highlights are required.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                if (item.link.isNotBlank() && !isValidUrl(item.link)) {
                    Toast.makeText(
                        this,
                        "$sectionTitle #${i + 1}: Please enter a valid URL for the link.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            }
        }

        // Education Validation
        if (adapter.currentEducation.isEmpty()) {
            Toast.makeText(this, "Please provide at least one Educational Credential.", Toast.LENGTH_SHORT).show()
            return
        }
        for ((i, edu) in adapter.currentEducation.withIndex()) {
            if (edu.institution.isBlank()) {
                Toast.makeText(this, "Education #${i + 1}: Institution Name is required.", Toast.LENGTH_SHORT).show()
                return
            }
            if (edu.degree.isBlank()) {
                Toast.makeText(this, "Education #${i + 1}: Degree / Course is required.", Toast.LENGTH_SHORT).show()
                return
            }
            if (!validateDurationString(edu.graduationYear)) {
                Toast.makeText(
                    this,
                    "Education #${i + 1}: Please specify valid duration (e.g. 2020 - 2024 or Aug 2018 - Jul 2022).",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        val compiledProfile = adapter.compileProfile(rightAdapter)

        lifecycleScope.launch {
            // Attempt Cloud Sync via ResumeCloudRepository
            val cloudRepo = ResumeCloudRepository()
            val uploadResult = cloudRepo.uploadResume(compiledProfile)

            val shareUrl = uploadResult.getOrNull() ?: "${ResumeCloudRepository.BASE_DEEP_LINK_URL}/local"
            val shareId = shareUrl.substringAfterLast("/")

            val showcase = com.example.model.SavedShowcase(
                shareId = shareId,
                shareUrl = shareUrl,
                candidateName = compiledProfile.hero.fullName,
                headline = compiledProfile.hero.headlineTitle.ifBlank { "Professional Resume" },
                profile = compiledProfile,
                targetCompany = compiledProfile.hero.targetCompany.trim()
            )
            preferencesRepository.saveShowcase(showcase)

            if (uploadResult.isFailure) {
                Toast.makeText(this@StructureVerificationActivity, R.string.toast_offline_sync_later, Toast.LENGTH_SHORT).show()
            }

            // Show Post-Compilation Celebration Modal
            val celebrationSheet = ShowcaseQrBottomSheet.newInstance(
                shareUrl = shareUrl,
                candidateName = compiledProfile.hero.fullName,
                isCelebration = true
            )
            celebrationSheet.onViewVisualizer = {
                val returnIntent = Intent().apply {
                    putExtra(EXTRA_COMPILED_PROFILE, compiledProfile.toJson())
                }
                setResult(RESULT_OK, returnIntent)
                finish()
            }
            celebrationSheet.isCancelable = true
            celebrationSheet.show(supportFragmentManager, ShowcaseQrBottomSheet.TAG)
        }
    }

    private fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith(
                "https://",
                ignoreCase = true
            )
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    private fun isValidUrl(url: String): Boolean {
        val normalized = normalizeUrl(url)
        val pattern = Regex("^(https?://)?[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+(:\\d+)?(/\\S*)?$")
        return pattern.matches(normalized) || android.util.Patterns.WEB_URL.matcher(normalized).matches()
    }

    private fun validateDuration(start: String, end: String): Boolean {
        if (start.isBlank() || end.isBlank()) return false
        val startYear = Regex("\\b(?:19|20)\\d{2}\\b").find(start)?.value?.toIntOrNull()
        val endYear = Regex("\\b(?:19|20)\\d{2}\\b").find(end)?.value?.toIntOrNull()
        if (startYear != null && endYear != null && startYear > endYear) {
            return false
        }
        return true
    }

    private fun validateDurationString(durationStr: String): Boolean {
        if (durationStr.isBlank()) return false
        val years = Regex("\\b(?:19|20)\\d{2}\\b").findAll(durationStr).map { it.value.toInt() }.toList()
        if (years.size >= 2) {
            return years[0] <= years[1]
        }
        return durationStr.contains("-") || durationStr.contains("–") || durationStr.contains("to") || durationStr.contains(
            "Present",
            ignoreCase = true
        )
    }

    companion object {
        const val EXTRA_PROFILE_JSON = "extra_profile_json"
        const val EXTRA_COMPILED_PROFILE = "extra_compiled_profile"

        fun createIntent(context: Context, profile: ResumeProfile?): Intent {
            return Intent(context, StructureVerificationActivity::class.java).apply {
                profile?.let { putExtra(EXTRA_PROFILE_JSON, it.toJson()) }
            }
        }
    }
}
