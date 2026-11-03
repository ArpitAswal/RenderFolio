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
import com.example.data.ResumeCloudRepository
import com.example.data.ResumePreferencesRepository
import com.example.databinding.ActivityResumeVisualiserBinding
import com.example.databinding.ItemVisualiserContactChipBinding
import com.example.databinding.ItemVisualiserCustomEntryBinding
import com.example.databinding.ItemVisualiserEducationEntryBinding
import com.example.databinding.ItemVisualiserExperienceEntryBinding
import com.example.databinding.ItemVisualiserProjectEntryBinding
import com.example.databinding.ItemVisualiserSkillDomainBinding
import com.example.databinding.LayoutSectionCustomDynamicBinding
import com.example.model.ContactInfo
import com.example.model.DynamicSection
import com.example.model.EducationItem
import com.example.model.ProfileHero
import com.example.model.ProjectItem
import com.example.model.ResumeProfile
import com.example.model.SkillCategories
import com.example.model.WorkExperienceItem
import com.example.ui.motion.CustomSectionMotionDriver
import com.example.ui.motion.EducationMotionDriver
import com.example.ui.motion.HeroSectionMotionDriver
import com.example.ui.motion.ProjectsMotionDriver
import com.example.ui.motion.SummarySectionMotionDriver
import com.example.ui.motion.TechnicalSkillsMotionDriver
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
    private val viewModel: ResumeViewModel by viewModels()
    private lateinit var motionDriver: HeroSectionMotionDriver
    private lateinit var summaryMotionDriver: SummarySectionMotionDriver
    private lateinit var experienceMotionDriver: WorkExperienceMotionDriver
    private lateinit var projectsMotionDriver: ProjectsMotionDriver
    private lateinit var skillsMotionDriver: TechnicalSkillsMotionDriver
    private lateinit var educationMotionDriver: EducationMotionDriver
    private val customSectionMotionDrivers = mutableListOf<CustomSectionMotionDriver>()
    private lateinit var preferencesRepository: ResumePreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Edge-to-Edge Canvas Rule
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityResumeVisualiserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesRepository = ResumePreferencesRepository.getInstance(applicationContext)

        // Handle window insets on CoordinatorLayout / NestedScrollView
        setupWindowInsets()

        // Initialize motion driver targeting the whole hero container
        motionDriver = HeroSectionMotionDriver(binding.heroContainer)

        // Setup scroll-driven unified motion
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            motionDriver.onScrollChanged(scrollY, binding.scrollView.height)
            if (::summaryMotionDriver.isInitialized) {
                summaryMotionDriver.onScrollChanged(scrollY, binding.scrollView.height)
            }
            if (::experienceMotionDriver.isInitialized) {
                experienceMotionDriver.onScrollChanged(binding.scrollView.height)
            }
            if (::projectsMotionDriver.isInitialized) {
                projectsMotionDriver.onScrollChanged(binding.scrollView.height)
            }
            if (::skillsMotionDriver.isInitialized) {
                skillsMotionDriver.onScrollChanged(binding.scrollView.height)
            }
            if (::educationMotionDriver.isInitialized) {
                educationMotionDriver.onScrollChanged(binding.scrollView.height)
            }
            customSectionMotionDrivers.forEach { driver ->
                driver.onScrollChanged(binding.scrollView.height)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data
        if (uri != null) {
            handleDeepLinkUri(uri)
        }
    }

    private fun loadInitialData() {
        val rawJson = intent.getStringExtra(EXTRA_PROFILE_JSON)
        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val profile = ResumeProfile.fromJson(rawJson)
                bindProfileToVisualizer(profile)
            }.onFailure {
                loadFromPreferencesOrSample()
            }
        } else {
            val uri = intent.data
            if (uri != null) {
                handleDeepLinkUri(uri)
            } else {
                loadFromPreferencesOrSample()
            }
        }
    }

    private fun handleDeepLinkUri(uri: Uri) {
        val shareId = uri.lastPathSegment
        if (shareId.isNullOrBlank()) {
            loadFromPreferencesOrSample()
            return
        }

        // Hide empty canvas with static headings while fetching over network
        binding.scrollView.visibility = View.INVISIBLE
        binding.pbVisualiserLoading?.visibility = View.VISIBLE

        lifecycleScope.launch {
            val cloudRepo = ResumeCloudRepository()
            val result = cloudRepo.fetchResume(shareId)
            binding.pbVisualiserLoading?.visibility = View.GONE

            result.onSuccess { profile ->
                bindProfileToVisualizer(profile)
                binding.scrollView.visibility = View.VISIBLE
            }.onFailure {
                Toast.makeText(this@ResumeVisualiserActivity, R.string.toast_showcase_expired, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun loadFromPreferencesOrSample() {
        lifecycleScope.launch {
            val cached = preferencesRepository.profileFlow.firstOrNull() ?: ResumeProfile.sampleProfile()
            bindProfileToVisualizer(cached)
        }
    }

    private fun bindProfileToVisualizer(profile: ResumeProfile) {
        bindHeroSection(profile.hero)
        setupSection2(profile.coreSections.profileSummary)
        setupSection3(profile.coreSections.workExperience)
        setupSection4(profile.coreSections.projects)
        setupSection5(profile.coreSections.skills)
        setupSection6(profile.coreSections.education)
        setupSection7(profile.dynamicSections)
        binding.pbVisualiserLoading?.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE
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
                            setupSection5(state.profile.coreSections.skills)
                            setupSection6(state.profile.coreSections.education)
                            setupSection7(state.profile.dynamicSections)
                        }
                        is UiState.ExtractedNeedsVerification -> {
                            bindHeroSection(state.profile.hero)
                            setupSection2(state.profile.coreSections.profileSummary)
                            setupSection3(state.profile.coreSections.workExperience)
                            setupSection4(state.profile.coreSections.projects)
                            setupSection5(state.profile.coreSections.skills)
                            setupSection6(state.profile.coreSections.education)
                            setupSection7(state.profile.dynamicSections)
                        }
                        is UiState.VerificationInProgress -> {
                            bindHeroSection(state.profile.hero)
                            setupSection2(state.profile.coreSections.profileSummary)
                            setupSection3(state.profile.coreSections.workExperience)
                            setupSection4(state.profile.coreSections.projects)
                            setupSection5(state.profile.coreSections.skills)
                            setupSection6(state.profile.coreSections.education)
                            setupSection7(state.profile.dynamicSections)
                        }
                        is UiState.EditMode -> {
                            bindHeroSection(state.draftProfile.hero)
                            setupSection2(state.draftProfile.coreSections.profileSummary)
                            setupSection3(state.draftProfile.coreSections.workExperience)
                            setupSection4(state.draftProfile.coreSections.projects)
                            setupSection5(state.draftProfile.coreSections.skills)
                            setupSection6(state.draftProfile.coreSections.education)
                            setupSection7(state.draftProfile.dynamicSections)
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

            // 5. Client Apps & Tech Stack Tags
            val hasClientApps = item.clientApps.isNotEmpty()
            val hasTags = item.techStackTags.isNotEmpty()
            if (hasClientApps || hasTags) {
                entryBinding.chipGroupEntryTags.removeAllViews()
                item.clientApps.forEach { app ->
                    val appChip = Chip(this).apply {
                        text = app.name
                        setChipBackgroundColorResource(R.color.exp_tag_bg)
                        setTextColor(getColor(R.color.exp_tag_text))
                        shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(resources.displayMetrics.density * 8f)
                            .build()
                        if (app.url.isNotBlank()) {
                            setChipIconResource(R.drawable.ic_link_14)
                            setChipIconTintResource(R.color.app_primary)
                            isChipIconVisible = true
                            setOnClickListener {
                                try {
                                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(app.url)))
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    entryBinding.chipGroupEntryTags.addView(appChip)
                }
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

    /**
     * SECTION 5: Technical Skills (The Semantic Domain Matrix)
     * Implements Alternating Lateral Motion (Left-to-Right arrival, alternating with Section 4)
     * with 20% viewport threshold state tracker.
     * Transparent, borderless container directly on canvas (no background card or graphic overlay).
     */
    private fun setupSection5(skills: SkillCategories) {
        val skillsLayout = binding.includedSectionSkills

        if (skills.totalCount == 0) {
            skillsLayout.sectionSkillsRoot.visibility = View.GONE
            return
        }

        skillsLayout.sectionSkillsRoot.visibility = View.VISIBLE

        // Section header title as detected from resume (default fallback to string resource)
        skillsLayout.tvSkillsSectionTitle.text = skills.sectionTitle.ifBlank {
            getString(R.string.section_title_technical_skills)
        }

        val container = skillsLayout.containerSkillsDomains
        container.removeAllViews()

        val dotSeparator = getString(R.string.skills_dot_separator)

        // Extract categorized domain map from parsed resume data
        val categorizedMap = linkedMapOf<String, List<String>>()
        if (skills.categorizedDomains.isNotEmpty()) {
            skills.categorizedDomains.forEach { (cat, list) ->
                val valid = list.filter { it.isNotBlank() }
                if (valid.isNotEmpty()) categorizedMap[cat] = valid
            }
        } else {
            val standardEntries = listOf(
                getString(R.string.skills_domain_languages) to skills.languages,
                getString(R.string.skills_domain_frameworks) to skills.frameworks,
                getString(R.string.skills_domain_cloud) to skills.cloudDevOps,
                getString(R.string.skills_domain_tools) to skills.toolsMethodologies
            ).map { it.first to it.second.filter { item -> item.isNotBlank() } }
                .filter { it.second.isNotEmpty() }

            if (standardEntries.size > 1) {
                standardEntries.forEach { (k, v) -> categorizedMap[k] = v }
            }
            skills.customCategories.forEach { (k, v) ->
                val valid = v.filter { it.isNotBlank() }
                if (valid.isNotEmpty()) categorizedMap[k] = valid
            }
        }

        if (categorizedMap.isNotEmpty()) {
            // CASE 1: Categorized Skills (Domain Label + Inline Skills Stream)
            val domainEntries = categorizedMap.entries.toList()
            domainEntries.forEachIndexed { index, (domainLabel, skillsList) ->
                val rowBinding = ItemVisualiserSkillDomainBinding.inflate(layoutInflater, container, false)
                rowBinding.tvDomainHeader.visibility = View.VISIBLE
                rowBinding.tvDomainHeader.text = domainLabel
                rowBinding.tvDomainSkillsStream.text = skillsList.joinToString(dotSeparator)

                if (index == domainEntries.lastIndex) {
                    rowBinding.dividerSkillDomain.visibility = View.GONE
                } else {
                    rowBinding.dividerSkillDomain.visibility = View.VISIBLE
                }

                container.addView(rowBinding.itemSkillDomainRoot)
            }
        } else {
            // CASE 2: Uncategorized / Flat List (Bullets, Zero Random Domain Labels)
            val flatSkillsList = when {
                skills.rawFlatSkills.isNotEmpty() -> skills.rawFlatSkills
                skills.frameworks.isNotEmpty() -> skills.frameworks
                skills.languages.isNotEmpty() -> skills.languages
                else -> buildList {
                    addAll(skills.languages)
                    addAll(skills.frameworks)
                    addAll(skills.cloudDevOps)
                    addAll(skills.toolsMethodologies)
                }
            }.distinct().filter { it.isNotBlank() }

            if (flatSkillsList.isNotEmpty()) {
                val rowBinding = ItemVisualiserSkillDomainBinding.inflate(layoutInflater, container, false)
                rowBinding.tvDomainHeader.visibility = View.GONE
                val bulletPrefix = getString(R.string.skills_bullet_prefix)
                rowBinding.tvDomainSkillsStream.text = flatSkillsList.joinToString("   ") { "$bulletPrefix$it" }
                rowBinding.dividerSkillDomain.visibility = View.GONE
                container.addView(rowBinding.itemSkillDomainRoot)
            }
        }

        // Initialize motion driver targeting the whole section container
        skillsMotionDriver = TechnicalSkillsMotionDriver(skillsLayout.sectionSkillsRoot)

        // Trigger Phase 1 initial entrance once layout pass is complete (synchronized with Sections 1..4)
        skillsLayout.sectionSkillsRoot.doOnPreDraw {
            skillsMotionDriver.playInitialEntrance()
        }
    }

    /**
     * SECTION 6: Education & Academics
     * Implements Dual-Phase Vertical Lift & Soft Fade (+24dp -> 0dp, alpha 0 -> 1, 350ms) matching Section 2.
     * Transparent, borderless container directly on canvas (no card background, elevation, or stroke).
     */
    private fun setupSection6(educationList: List<EducationItem>) {
        val eduLayout = binding.includedSectionEducation

        val validEdu = educationList.filter { it.institution.isNotBlank() || it.degree.isNotBlank() }
        if (validEdu.isEmpty()) {
            eduLayout.sectionEducationRoot.visibility = View.GONE
            return
        }

        eduLayout.sectionEducationRoot.visibility = View.VISIBLE
        val container = eduLayout.containerEducationEntries
        container.removeAllViews()

        validEdu.forEachIndexed { index, item ->
            val entryBinding = ItemVisualiserEducationEntryBinding.inflate(layoutInflater, container, false)
            entryBinding.tvEntryDegree.text = item.degree.ifBlank { getString(R.string.hint_degree) }
            entryBinding.tvEntryInstitution.text = item.institution
            entryBinding.tvEntryEduYear.text = item.graduationYear

            if (index == validEdu.lastIndex) {
                entryBinding.dividerEducationEntry.visibility = View.GONE
            } else {
                entryBinding.dividerEducationEntry.visibility = View.VISIBLE
            }

            container.addView(entryBinding.root)
        }

        educationMotionDriver = EducationMotionDriver(eduLayout.sectionEducationRoot)

        eduLayout.sectionEducationRoot.doOnPreDraw {
            educationMotionDriver.playInitialEntrance()
        }
    }

    /**
     * SECTION 7: All Custom Dynamic Sections
     * Implements "The Credential Scale Reveal":
     * - Expands symmetrically from geometric center (pivot 50%, 50%).
     * - Initial state: scale 0.94, alpha 0, translationY 20dp.
     * - Viewport threshold: 20%.
     * - Reading zone locked at scale 1.0, alpha 1.0.
     * - Exit: scale 1.0 -> 0.94, alpha 1 -> 0 over 400ms.
     * - Dynamic N-section support with independent motion drivers per section.
     */
    private fun setupSection7(customSections: List<DynamicSection>) {
        val containerCustom = binding.containerCustomSections
        containerCustom.removeAllViews()
        customSectionMotionDrivers.clear()

        val validSections = customSections.filter { it.sectionTitle.isNotBlank() && it.items.isNotEmpty() }
        if (validSections.isEmpty()) {
            containerCustom.visibility = View.GONE
            return
        }

        containerCustom.visibility = View.VISIBLE

        validSections.forEach { section ->
            val sectionBinding = LayoutSectionCustomDynamicBinding.inflate(layoutInflater, containerCustom, false)
            sectionBinding.tvCustomSectionTitle.text = section.sectionTitle
            sectionBinding.sectionCustomRoot.contentDescription = getString(R.string.cd_custom_section_format, section.sectionTitle)

            val itemsContainer = sectionBinding.containerCustomItems
            itemsContainer.removeAllViews()

            section.items.forEachIndexed { index, item ->
                val itemBinding = ItemVisualiserCustomEntryBinding.inflate(layoutInflater, itemsContainer, false)
                itemBinding.tvEntryCustomTitle.text = item.title

                if (item.dateOrPeriod.isNotBlank()) {
                    itemBinding.tvEntryCustomPeriod.visibility = View.VISIBLE
                    itemBinding.tvEntryCustomPeriod.text = item.dateOrPeriod
                } else {
                    itemBinding.tvEntryCustomPeriod.visibility = View.GONE
                }

                if (item.subtitle.isNotBlank() || item.link.isNotBlank()) {
                    val label = item.subtitle.ifBlank { item.link }
                    itemBinding.tvEntryCustomSubtitle.visibility = View.VISIBLE
                    itemBinding.tvEntryCustomSubtitle.text = label
                    if (item.link.isNotBlank()) {
                        itemBinding.tvEntryCustomSubtitle.setOnClickListener {
                            openProfileUrl(item.link)
                        }
                    }
                } else {
                    itemBinding.tvEntryCustomSubtitle.visibility = View.GONE
                }

                if (item.description.isNotBlank()) {
                    itemBinding.tvEntryCustomDescription.visibility = View.VISIBLE
                    itemBinding.tvEntryCustomDescription.text = item.description
                } else {
                    itemBinding.tvEntryCustomDescription.visibility = View.GONE
                }

                if (item.bulletPoints.isNotEmpty()) {
                    itemBinding.containerEntryCustomBullets.visibility = View.VISIBLE
                    itemBinding.containerEntryCustomBullets.removeAllViews()
                    item.bulletPoints.forEach { bullet ->
                        val bulletTv = TextView(this).apply {
                            setTextAppearance(R.style.TextAppearance_ResumeVisualiser_Exp_Bullet)
                            text = getString(R.string.custom_bullet_format, bullet)
                            setPadding(0, 4, 0, 4)
                        }
                        itemBinding.containerEntryCustomBullets.addView(bulletTv)
                    }
                } else {
                    itemBinding.containerEntryCustomBullets.visibility = View.GONE
                }

                if (index == section.items.lastIndex) {
                    itemBinding.dividerCustomEntry.visibility = View.GONE
                } else {
                    itemBinding.dividerCustomEntry.visibility = View.VISIBLE
                }

                itemsContainer.addView(itemBinding.root)
            }

            // Attach dedicated motion driver for this custom section
            val driver = CustomSectionMotionDriver(sectionBinding.sectionCustomRoot)
            customSectionMotionDrivers.add(driver)

            sectionBinding.sectionCustomRoot.doOnPreDraw {
                driver.playInitialEntrance()
            }

            containerCustom.addView(sectionBinding.root)
        }
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
