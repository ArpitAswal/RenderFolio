package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.R
import com.example.data.ResumePreferencesRepository
import com.example.databinding.ActivityResumeVisualiserBinding
import com.example.databinding.ItemShowcaseCustomSectionBinding
import com.example.databinding.ItemShowcaseEducationBinding
import com.example.databinding.ItemShowcaseProjectBinding
import com.example.databinding.ItemVisualiserContactChipBinding
import com.example.databinding.ItemVisualiserExperienceEntryBinding
import com.example.databinding.ItemVisualiserProjectEntryBinding
import com.example.databinding.LayoutShowcaseSubsequentSectionsBinding
import com.example.model.ContactInfo
import com.example.model.DynamicSection
import com.example.model.ProfileHero
import com.example.model.ProjectItem
import com.example.model.ResumeProfile
import com.example.model.WorkExperienceItem
import com.example.ui.motion.HeroSectionMotionDriver
import com.example.ui.motion.ProjectsMotionDriver
import com.example.ui.motion.SummarySectionMotionDriver
import com.example.ui.motion.WorkExperienceMotionDriver
import com.example.viewmodel.ResumeViewModel
import com.example.viewmodel.UiState
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Screen 4: Resume Visualizer (ResumeVisualiserActivity)
 *
 * Implements Section 1 (Profile Info & Contact) adhering to section_1_plan.md:
 * - 100% Pure XML (Zero Hybrid Rule - CoordinatorLayout, ConstraintLayout, Flow, NestedScrollView).
 * - Symmetrical Horizontal Centering of candidate name, headline, location, and Flow-wrapped contact chips.
 * - Whole-Section Unified Motion: HeroSectionMotionDriver drives parallax fade and pull-down elastic
 *   scale strictly targeting binding.heroContainer.
 * - Dynamic ConstraintLayout Flow multi-line wrapping for contact chips with guaranteed 48x48dp touch bounds.
 * - Resilient intent dispatching with graceful Toast fallbacks.
 * - Seamless integration of subsequent sections (2..7) in containerContentSections.
 */
class ResumeVisualiserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResumeVisualiserBinding
    private lateinit var subsequentBinding: LayoutShowcaseSubsequentSectionsBinding
    private val viewModel: ResumeViewModel by viewModels()
    private lateinit var motionDriver: HeroSectionMotionDriver
    private lateinit var summaryMotionDriver: SummarySectionMotionDriver
    private lateinit var experienceMotionDriver: WorkExperienceMotionDriver
    private lateinit var projectsMotionDriver: ProjectsMotionDriver
    private lateinit var preferencesRepository: ResumePreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Edge-to-Edge Canvas Rule
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityResumeVisualiserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesRepository = ResumePreferencesRepository(applicationContext)

        // Inflate and embed subsequent sections (Sections 3..7) inside containerContentSections
        subsequentBinding = LayoutShowcaseSubsequentSectionsBinding.inflate(layoutInflater, binding.containerContentSections, true)

        // Handle window insets on CoordinatorLayout / NestedScrollView
        setupWindowInsets()

        // Initialize motion driver targeting the whole hero container
        motionDriver = HeroSectionMotionDriver(binding.heroContainer)

        // Setup scroll-driven unified motion
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            motionDriver.onScrollChanged(scrollY)
            if (::summaryMotionDriver.isInitialized) {
                summaryMotionDriver.onScrollChanged(scrollY)
            }
            if (::experienceMotionDriver.isInitialized) {
                experienceMotionDriver.onScrollChanged(binding.scrollView.height)
            }
            if (::projectsMotionDriver.isInitialized) {
                projectsMotionDriver.onScrollChanged(binding.scrollView.height)
            }
        }

        // Observe ViewModel and load intent profile or persisted profile
        observeViewModel()
        loadInitialData()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.scrollView.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
    }

    private fun loadInitialData() {
        val rawJson = intent.getStringExtra(EXTRA_PROFILE_JSON)
        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val profile = ResumeProfile.fromJson(rawJson)
                bindHeroSection(profile.hero)
                setupSection2(profile.coreSections.profileSummary)
                setupSection3(profile.coreSections.workExperience)
                setupSection4(profile.coreSections.projects)
                bindSubsequentSections(profile)
                viewModel.updateProfile(profile)
            }.onFailure {
                loadFromPreferencesOrSample()
            }
        } else {
            loadFromPreferencesOrSample()
        }
    }

    private fun loadFromPreferencesOrSample() {
        lifecycleScope.launch {
            val cached = preferencesRepository.profileFlow.firstOrNull() ?: ResumeProfile.sampleProfile()
            viewModel.updateProfile(cached)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            bindHeroSection(state.profile.hero)
                            setupSection2(state.profile.coreSections.profileSummary)
                            setupSection3(state.profile.coreSections.workExperience)
                            setupSection4(state.profile.coreSections.projects)
                            bindSubsequentSections(state.profile)
                        }
                        is UiState.ExtractedNeedsVerification -> {
                            bindHeroSection(state.profile.hero)
                            setupSection2(state.profile.coreSections.profileSummary)
                            setupSection3(state.profile.coreSections.workExperience)
                            setupSection4(state.profile.coreSections.projects)
                            bindSubsequentSections(state.profile)
                        }
                        is UiState.VerificationInProgress -> {
                            bindHeroSection(state.profile.hero)
                            setupSection2(state.profile.coreSections.profileSummary)
                            setupSection3(state.profile.coreSections.workExperience)
                            setupSection4(state.profile.coreSections.projects)
                            bindSubsequentSections(state.profile)
                        }
                        is UiState.EditMode -> {
                            bindHeroSection(state.draftProfile.hero)
                            setupSection2(state.draftProfile.coreSections.profileSummary)
                            setupSection3(state.draftProfile.coreSections.workExperience)
                            setupSection4(state.draftProfile.coreSections.projects)
                            bindSubsequentSections(state.draftProfile)
                        }
                        else -> {
                            // UiState.Idle or InProgress
                        }
                    }
                }
            }
        }
    }

    /**
     * SECTION 1: Binds candidate data and dynamically populates wrapping Flow chips.
     * Symmetrical horizontal centering with whole-section target.
     */
    private fun bindHeroSection(hero: ProfileHero) {
        // 1. Candidate Full Name (Centered)
        binding.tvHeroName.text = hero.fullName.ifBlank { getString(R.string.hero_default_name) }

        // 2. Professional Headline (Centered)
        binding.tvHeroHeadline.text = hero.headlineTitle.ifBlank { getString(R.string.hero_default_headline) }

        // 3. Location with Pin (Centered, with visibility guard)
        if (hero.location.isNotBlank()) {
            binding.tvHeroLocation.visibility = View.VISIBLE
            binding.tvHeroLocation.text = hero.location
        } else {
            binding.tvHeroLocation.visibility = View.GONE
        }

        // 4. Populate Contact Chips into ConstraintLayout Flow
        populateContactChips(hero.contactInfo)

        // 5. Initial Load Entrance Animation (Fade and Scale-up)
        binding.heroContainer.post {
            motionDriver.startEntranceAnimation()
        }
    }

    /**
     * Dynamically instantiates contact chip views with guaranteed 48x48dp touch bounds
     * and binds their generated IDs into ConstraintLayout Flow for multi-line centered wrapping.
     */
    private fun populateContactChips(contact: ContactInfo) {
        val flow = binding.flowContactChips
        val container = binding.heroContainer

        // Clear existing dynamically generated chip views
        val currentReferencedIds = flow.referencedIds
        for (id in currentReferencedIds) {
            container.findViewById<View>(id)?.let { container.removeView(it) }
        }

        val referencedIds = mutableListOf<Int>()

        fun addChip(iconRes: Int, text: String, contentDesc: String, onClick: () -> Unit) {
            val chipBinding = ItemVisualiserContactChipBinding.inflate(layoutInflater, container, false)
            val chipId = View.generateViewId()
            chipBinding.root.id = chipId

            chipBinding.ivChipIcon.setImageResource(iconRes)
            chipBinding.tvChipLabel.text = text
            chipBinding.root.contentDescription = contentDesc
            chipBinding.root.setOnClickListener { onClick() }

            container.addView(chipBinding.root)
            referencedIds.add(chipId)
        }

        // Phone chip (ACTION_DIAL)
        if (contact.phone.isNotBlank()) {
            addChip(
                iconRes = R.drawable.ic_phone_14,
                text = contact.phone,
                contentDesc = getString(R.string.cd_hero_phone, contact.phone)
            ) {
                dialPhoneNumber(contact.phone)
            }
        }

        // Email chip (ACTION_SENDTO)
        if (contact.email.isNotBlank()) {
            addChip(
                iconRes = R.drawable.ic_email_14,
                text = contact.email,
                contentDesc = getString(R.string.cd_hero_email, contact.email)
            ) {
                sendEmail(contact.email)
            }
        }

        // LinkedIn chip
        if (contact.linkedinUrl.isNotBlank()) {
            addChip(
                iconRes = R.drawable.ic_linkedin_14,
                text = "LinkedIn",
                contentDesc = getString(R.string.cd_hero_profile_link, "LinkedIn")
            ) {
                openProfileUrl(contact.linkedinUrl)
            }
        }

        // GitHub chip
        if (contact.githubUrl.isNotBlank()) {
            addChip(
                iconRes = R.drawable.ic_github_14,
                text = "GitHub",
                contentDesc = getString(R.string.cd_hero_profile_link, "GitHub")
            ) {
                openProfileUrl(contact.githubUrl)
            }
        }

        // Website / Portfolio chip
        if (contact.websiteUrl.isNotBlank()) {
            addChip(
                iconRes = R.drawable.ic_link_14,
                text = "Portfolio",
                contentDesc = getString(R.string.cd_hero_profile_link, "Portfolio")
            ) {
                openProfileUrl(contact.websiteUrl)
            }
        }

        // Connect chips to the Flow helper
        flow.referencedIds = referencedIds.toIntArray()
    }

    // --- Intent Helpers with Resilience ---

    private fun dialPhoneNumber(phone: String) {
        val uri = Uri.parse("tel:${phone.trim()}")
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, uri)) }
            .onFailure { Toast.makeText(this, R.string.toast_error_no_phone_app, Toast.LENGTH_SHORT).show() }
    }

    private fun sendEmail(email: String) {
        val uri = Uri.parse("mailto:${email.trim()}")
        runCatching { startActivity(Intent(Intent.ACTION_SENDTO, uri)) }
            .onFailure { Toast.makeText(this, R.string.toast_error_no_email_app, Toast.LENGTH_SHORT).show() }
    }

    private fun openProfileUrl(url: String) {
        val sanitized = if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            url.trim()
        } else {
            "https://${url.trim()}"
        }
        if (!Patterns.WEB_URL.matcher(sanitized).matches()) {
            Toast.makeText(this, R.string.toast_error_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse(sanitized)
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { Toast.makeText(this, R.string.toast_error_cannot_open_link, Toast.LENGTH_SHORT).show() }
    }

    // --- Subsequent Sections (5..7) Binding ---

    private fun bindSubsequentSections(profile: ResumeProfile) {
        bindTechnicalCompetencies(profile)
        bindEducation(profile)
        bindCustomSections(profile.dynamicSections)
    }

    /**
     * SECTION 2: Professional Overview & Summary
     * Implements Dual-Phase Animation Contract:
     * - Phase 1 (Launch Entrance): Staggered vertical lift (Title 250ms Decelerate, Card 350ms FastOutSlowIn +80ms delay).
     * - Phase 2 (On-Scroll Reactivity): Reactive parallax glide & elastic over-scroll pull-down scaling.
     * Visibility guard: Cleanly hidden when summary is blank.
     */
    private fun setupSection2(summaryText: String) {
        val summaryLayout = binding.includedSectionSummary
        val cleanSummary = summaryText.trim()

        if (cleanSummary.isBlank()) {
            summaryLayout.sectionSummaryRoot.visibility = View.GONE
            return
        }

        summaryLayout.sectionSummaryRoot.visibility = View.VISIBLE
        summaryLayout.tvSummaryBody.text = cleanSummary

        // Initialize motion driver targeting the whole summary container
        summaryMotionDriver = SummarySectionMotionDriver(
            sectionContainer = summaryLayout.sectionSummaryRoot
        )

        // Trigger Phase 1 initial entrance once layout pass is complete
        summaryLayout.sectionSummaryRoot.doOnPreDraw {
            summaryMotionDriver.playInitialEntrance()
        }
    }

    /**
     * SECTION 3: Work Experience
     * Implements Approach A (Whole Section unified block) with 20% viewport threshold state tracker.
     */
    private fun setupSection3(experiences: List<WorkExperienceItem>) {
        val expLayout = binding.includedSectionExperience

        val validExperiences = experiences.filter {
            it.companyName.isNotBlank() || it.roleTitle.isNotBlank() || it.bulletPoints.isNotEmpty()
        }

        if (validExperiences.isEmpty()) {
            expLayout.sectionExperienceRoot.visibility = View.GONE
            return
        }

        expLayout.sectionExperienceRoot.visibility = View.VISIBLE
        val container = expLayout.containerExperienceEntries
        container.removeAllViews()

        validExperiences.forEachIndexed { index, item ->
            val entryBinding = ItemVisualiserExperienceEntryBinding.inflate(layoutInflater, container, false)

            // 1. Role Title
            entryBinding.tvEntryRole.text = item.roleTitle.ifBlank { getString(R.string.exp_default_role) }

            // 2. Company Name
            entryBinding.tvEntryCompany.text = item.companyName.ifBlank { getString(R.string.exp_default_company) }

            // 3. Duration formatted string
            val duration = when {
                item.startDate.isNotBlank() && item.endDate.isNotBlank() ->
                    getString(R.string.exp_date_range_format, item.startDate, item.endDate)
                item.startDate.isNotBlank() ->
                    getString(R.string.exp_single_date_format, item.startDate)
                else -> ""
            }
            entryBinding.tvEntryDuration.text = duration

            // 4. Populate Bullets
            entryBinding.containerEntryBullets.removeAllViews()
            item.bulletPoints.forEach { bullet ->
                val bulletView = TextView(this).apply {
                    setTextAppearance(R.style.TextAppearance_ResumeVisualiser_Exp_Bullet)
                    text = getString(R.string.exp_bullet_format, bullet)
                    setPadding(0, 4, 0, 4)
                }
                entryBinding.containerEntryBullets.addView(bulletView)
            }

            // 5. Tech Stack Tags
            if (item.techStackTags.isNotEmpty()) {
                entryBinding.chipGroupEntryTags.removeAllViews()
                item.techStackTags.forEach { tag ->
                    val tagChip = Chip(this).apply {
                        text = tag
                        setChipBackgroundColorResource(R.color.exp_tag_bg)
                        setTextColor(getColor(R.color.exp_tag_text))
                        shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(resources.displayMetrics.density * 8f)
                            .build()
                    }
                    entryBinding.chipGroupEntryTags.addView(tagChip)
                }
                entryBinding.chipGroupEntryTags.visibility = View.VISIBLE
            } else {
                entryBinding.chipGroupEntryTags.visibility = View.GONE
            }

            // Hide divider on the last item
            if (index == validExperiences.lastIndex) {
                entryBinding.dividerEntry.visibility = View.GONE
            } else {
                entryBinding.dividerEntry.visibility = View.VISIBLE
            }

            container.addView(entryBinding.itemExperienceEntryRoot)
        }

        // Initialize motion driver targeting the whole section container
        experienceMotionDriver = WorkExperienceMotionDriver(expLayout.sectionExperienceRoot)

        // Trigger Phase 1 initial entrance once layout pass is complete (synchronized with Section 1 and Section 2)
        expLayout.sectionExperienceRoot.doOnPreDraw {
            experienceMotionDriver.playInitialEntrance()
        }
    }

    /**
     * SECTION 4: Featured Projects / Project Experience
     * Implements Vice-Versa Lateral Motion (Right-to-Left) with 20% viewport threshold state tracker.
     * Transparent, borderless container directly on canvas (no background card or graphic overlay).
     */
    private fun setupSection4(projects: List<ProjectItem>) {
        val projectsLayout = binding.includedSectionProjects

        val validProjects = projects.filter { it.projectTitle.isNotBlank() }

        if (validProjects.isEmpty()) {
            projectsLayout.sectionProjectsRoot.visibility = View.GONE
            return
        }

        projectsLayout.sectionProjectsRoot.visibility = View.VISIBLE
        val container = projectsLayout.containerProjectEntries
        container.removeAllViews()

        validProjects.forEachIndexed { index, item ->
            val entryBinding = ItemVisualiserProjectEntryBinding.inflate(layoutInflater, container, false)

            // 1. Project Title
            entryBinding.tvEntryProjectTitle.text = item.projectTitle

            // 2. Action Button (Live Demo / Code Repository)
            val targetUrl = item.demoUrl.ifBlank { item.codeUrl }
            if (targetUrl.isNotBlank()) {
                entryBinding.btnEntryProjectAction.visibility = View.VISIBLE
                val isDemo = item.demoUrl.isNotBlank()
                entryBinding.btnEntryProjectAction.text = if (isDemo) {
                    getString(R.string.action_project_live_demo)
                } else {
                    getString(R.string.action_project_code_repo)
                }
                entryBinding.btnEntryProjectAction.contentDescription = if (isDemo) {
                    getString(R.string.cd_project_live_demo, item.projectTitle)
                } else {
                    getString(R.string.cd_project_code_repo, item.projectTitle)
                }
                entryBinding.btnEntryProjectAction.setOnClickListener {
                    openProfileUrl(targetUrl)
                }
            } else {
                entryBinding.btnEntryProjectAction.visibility = View.GONE
            }

            // 3. Description
            if (item.description.isNotBlank()) {
                entryBinding.tvEntryProjectDescription.visibility = View.VISIBLE
                entryBinding.tvEntryProjectDescription.text = item.description
            } else {
                entryBinding.tvEntryProjectDescription.visibility = View.GONE
            }

            // 4. Tech Stack Tags
            if (item.techStackTags.isNotEmpty()) {
                entryBinding.chipGroupProjectTech.removeAllViews()
                item.techStackTags.forEach { tag ->
                    val tagChip = Chip(this).apply {
                        text = tag
                        setChipBackgroundColorResource(R.color.project_tag_bg)
                        setTextColor(getColor(R.color.project_tag_text))
                        shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(resources.displayMetrics.density * 8f)
                            .build()
                    }
                    entryBinding.chipGroupProjectTech.addView(tagChip)
                }
                entryBinding.chipGroupProjectTech.visibility = View.VISIBLE
            } else {
                entryBinding.chipGroupProjectTech.visibility = View.GONE
            }

            // Hide divider on the last item
            if (index == validProjects.lastIndex) {
                entryBinding.dividerProjectEntry.visibility = View.GONE
            } else {
                entryBinding.dividerProjectEntry.visibility = View.VISIBLE
            }

            container.addView(entryBinding.itemProjectEntryRoot)
        }

        // Initialize motion driver targeting the whole section container
        projectsMotionDriver = ProjectsMotionDriver(projectsLayout.sectionProjectsRoot)

        // Trigger Phase 1 initial entrance once layout pass is complete (synchronized with Sections 1..3)
        projectsLayout.sectionProjectsRoot.doOnPreDraw {
            projectsMotionDriver.playInitialEntrance()
        }
    }

    // 5. TECHNICAL COMPETENCIES
    private fun bindTechnicalCompetencies(profile: ResumeProfile) {
        val skills = profile.coreSections.skills
        if (skills.totalCount == 0) {
            subsequentBinding.sectionTechnicalCompetencies.visibility = View.GONE
            return
        }

        fun populateSkillsGroup(chipGroup: com.google.android.material.chip.ChipGroup, container: View, items: List<String>) {
            chipGroup.removeAllViews()
            val valid = items.filter { it.isNotBlank() }
            if (valid.isNotEmpty()) {
                valid.forEach { skill ->
                    val chip = Chip(this).apply {
                        text = skill
                        setChipBackgroundColorResource(R.color.hero_chip_bg)
                        setChipStrokeColorResource(R.color.hero_chip_stroke)
                        chipStrokeWidth = resources.displayMetrics.density * 1f
                        setTextColor(getColor(R.color.hero_chip_text))
                        shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(resources.displayMetrics.density * 16f)
                            .build()
                    }
                    chipGroup.addView(chip)
                }
                container.visibility = View.VISIBLE
            } else {
                container.visibility = View.GONE
            }
        }

        populateSkillsGroup(subsequentBinding.chipGroupLanguages, subsequentBinding.groupLanguages, skills.languages)
        populateSkillsGroup(subsequentBinding.chipGroupFrameworks, subsequentBinding.groupFrameworks, skills.frameworks)
        populateSkillsGroup(subsequentBinding.chipGroupCloudDevOps, subsequentBinding.groupCloudDevOps, skills.cloudDevOps)
        populateSkillsGroup(subsequentBinding.chipGroupTools, subsequentBinding.groupTools, skills.toolsMethodologies)

        subsequentBinding.sectionTechnicalCompetencies.visibility = View.VISIBLE
    }

    // 6. EDUCATIONAL BACKGROUND
    private fun bindEducation(profile: ResumeProfile) {
        val education = profile.coreSections.education.filter { it.institution.isNotBlank() || it.degree.isNotBlank() }
        subsequentBinding.containerEducationItems.removeAllViews()

        if (education.isNotEmpty()) {
            val inflater = layoutInflater
            education.forEach { edu ->
                val eBinding = ItemShowcaseEducationBinding.inflate(inflater, subsequentBinding.containerEducationItems, false)
                eBinding.tvEducationInstitution.text = edu.institution
                eBinding.tvEducationDegree.text = edu.degree
                if (edu.graduationYear.isNotBlank()) {
                    eBinding.tvEducationYear.text = edu.graduationYear
                    eBinding.tvEducationYear.visibility = View.VISIBLE
                } else {
                    eBinding.tvEducationYear.visibility = View.GONE
                }
                subsequentBinding.containerEducationItems.addView(eBinding.root)
            }
            subsequentBinding.sectionEducation.visibility = View.VISIBLE
        } else {
            subsequentBinding.sectionEducation.visibility = View.GONE
        }
    }

    // 7. DYNAMIC CUSTOM SECTIONS
    private fun bindCustomSections(dynamicSections: List<DynamicSection>) {
        subsequentBinding.containerDynamicSections.removeAllViews()
        val validSections = dynamicSections.filter { it.items.isNotEmpty() }
        if (validSections.isEmpty()) {
            subsequentBinding.containerDynamicSections.visibility = View.GONE
            return
        }

        val inflater = layoutInflater
        validSections.forEach { section ->
            val sBinding = ItemShowcaseCustomSectionBinding.inflate(inflater, subsequentBinding.containerDynamicSections, false)
            sBinding.tvCustomSectionHeader.text = section.sectionTitle.uppercase()

            sBinding.containerCustomItems.removeAllViews()
            section.items.forEach { item ->
                val itemView = layoutInflater.inflate(R.layout.item_showcase_project, sBinding.containerCustomItems, false)
                val tvTitle = itemView.findViewById<android.widget.TextView>(R.id.tvProjectTitle)
                val tvDesc = itemView.findViewById<android.widget.TextView>(R.id.tvProjectDescription)
                val btnLink = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnProjectDemo)
                val chipGroup = itemView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupProjectTech)

                tvTitle.text = item.title
                if (item.description.isNotBlank()) {
                    tvDesc.text = item.description
                    tvDesc.visibility = View.VISIBLE
                } else {
                    tvDesc.visibility = View.GONE
                }

                if (item.link.isNotBlank()) {
                    btnLink.visibility = View.VISIBLE
                    btnLink.text = "[ ↗ Open Link ]"
                    btnLink.setOnClickListener { openProfileUrl(item.link) }
                } else {
                    btnLink.visibility = View.GONE
                }
                chipGroup.visibility = View.GONE

                sBinding.containerCustomItems.addView(itemView)
            }
            subsequentBinding.containerDynamicSections.addView(sBinding.root)
        }
        subsequentBinding.containerDynamicSections.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_PROFILE_JSON = "extra_profile_json"

        fun createIntent(context: Context, profile: ResumeProfile?): Intent {
            return Intent(context, ResumeVisualiserActivity::class.java).apply {
                profile?.let { putExtra(EXTRA_PROFILE_JSON, it.toJson()) }
            }
        }
    }
}
