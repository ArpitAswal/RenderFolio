package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.R
import com.example.data.ResumeCloudRepository
import com.example.data.ResumePreferencesRepository
import com.example.databinding.ActivityResumeVisualiserBinding
import com.example.databinding.ItemVisualiserBulletRowBinding
import com.example.databinding.ItemVisualiserCustomEntryBinding
import com.example.databinding.ItemVisualiserDeliverableChipBinding
import com.example.databinding.ItemVisualiserEducationEntryBinding
import com.example.databinding.ItemVisualiserExperienceEntryBinding
import com.example.databinding.ItemVisualiserProjectEntryBinding
import com.example.databinding.ItemVisualiserSkillChipBinding
import com.example.databinding.ItemVisualiserSkillDomainBinding
import com.example.databinding.LayoutSectionCustomDynamicBinding
import com.example.model.ContactInfo
import com.example.model.DynamicSection
import com.example.model.EducationItem
import com.example.model.ProfileHero
import com.example.model.ProjectItem
import com.example.model.ResumeProfile
import com.example.model.ResumeSectionConstants
import com.example.model.SkillCategories
import com.example.model.WorkExperienceItem
import com.example.ui.adapter.CustomSectionArchetype
import com.example.ui.adapter.resolveCustomSectionArchetype
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
 * Screen 4: Resume Visualiser (ResumeVisualiserActivity)
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

        // Force fixed portrait orientation for mobile devices (< 600dp)
        if (resources.configuration.smallestScreenWidthDp < 600) {
            try {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } catch (_: Exception) {}
        }

        // Strict Edge-to-Edge Canvas Rule
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityResumeVisualiserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesRepository = ResumePreferencesRepository.getInstance(applicationContext)

        // Handle window insets on CoordinatorLayout / NestedScrollView
        setupWindowInsets()

        // Initialize motion driver targeting the whole hero container
        motionDriver = HeroSectionMotionDriver(binding.heroContainer, binding.scrollView)

        // Setup scroll-driven unified motion
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            motionDriver.onScrollChanged(scrollY, binding.scrollView.height)
            if (::summaryMotionDriver.isInitialized) {
                summaryMotionDriver.onScrollChanged(scrollY, binding.scrollView.height)
            }
            if (::experienceMotionDriver.isInitialized) {
                experienceMotionDriver.onScrollChanged(binding.scrollView)
            }
            if (::projectsMotionDriver.isInitialized) {
                projectsMotionDriver.onScrollChanged(binding.scrollView)
            }
            if (::skillsMotionDriver.isInitialized) {
                skillsMotionDriver.onScrollChanged(binding.scrollView)
            }
            if (::educationMotionDriver.isInitialized) {
                educationMotionDriver.onScrollChanged(binding.scrollView)
            }
            customSectionMotionDrivers.forEach { driver ->
                driver.onScrollChanged(binding.scrollView)
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
                bindProfileToVisualiser(profile)
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
            val cloudRepo = ResumeCloudRepository.getInstance()
            val result = cloudRepo.fetchResume(shareId)
            binding.pbVisualiserLoading?.visibility = View.GONE

            result.onSuccess { profile ->
                bindProfileToVisualiser(profile)
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
            bindProfileToVisualiser(cached)
        }
    }

    private fun bindProfileToVisualiser(profile: ResumeProfile) {
        applySectionOrder(profile.sectionOrder, profile.dynamicSections)
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

    /**
     * Dynamically reorders the section containers inside the linear layout
     * to match the exact sequence preserved from Structure Verification (and detected from the PDF).
     */
    private fun applySectionOrder(order: List<String>, customSections: List<DynamicSection> = emptyList()) {
        val parent = binding.layoutVisualiserSections ?: return

        val effectiveOrder = if (order.isNotEmpty()) {
            order
        } else {
            ResumeSectionConstants.DEFAULT_CORE_ORDER + customSections.map { "${ResumeSectionConstants.CUSTOM_PREFIX}${it.sectionTitle}" }
        }

        val viewMap = mutableMapOf<String, View>()
        viewMap[ResumeSectionConstants.SECTION_PERSONAL] = binding.heroContainer
        binding.wrapperSectionSummary?.let { viewMap[ResumeSectionConstants.SECTION_SUMMARY] = it }
        binding.wrapperSectionExperience?.let { viewMap[ResumeSectionConstants.SECTION_EXPERIENCE] = it }
        binding.wrapperSectionProjects?.let { viewMap[ResumeSectionConstants.SECTION_PROJECTS] = it }
        binding.wrapperSectionSkills?.let { viewMap[ResumeSectionConstants.SECTION_SKILLS] = it }
        binding.wrapperSectionEducation?.let { viewMap[ResumeSectionConstants.SECTION_EDUCATION] = it }

        // Temporarily detach all section children from parent layout
        parent.removeAllViews()

        val addedViews = mutableSetOf<View>()

        for (secId in effectiveOrder) {
            val view = viewMap[secId]
            if (view != null && !addedViews.contains(view)) {
                parent.addView(view)
                addedViews.add(view)
            } else if (secId.startsWith(ResumeSectionConstants.CUSTOM_PREFIX)) {
                if (!addedViews.contains(binding.containerCustomSections)) {
                    parent.addView(binding.containerCustomSections)
                    addedViews.add(binding.containerCustomSections)
                }
            }
        }

        // Add any remaining core views that were not explicitly listed in order
        for (view in viewMap.values) {
            if (!addedViews.contains(view)) {
                parent.addView(view)
                addedViews.add(view)
            }
        }

        // Ensure dynamic custom sections container is present if not added yet
        if (!addedViews.contains(binding.containerCustomSections)) {
            parent.addView(binding.containerCustomSections)
            addedViews.add(binding.containerCustomSections)
        }

        // Maintain consistent 32dp vertical inter-section spacing (0dp for the first child)
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                val topDp = if (i == 0) 0 else 32
                val topPx = (topDp * resources.displayMetrics.density).toInt()
                if (lp.topMargin != topPx) {
                    lp.topMargin = topPx
                    child.layoutParams = lp
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            bindProfileToVisualiser(state.profile)
                        }
                        is UiState.ExtractedNeedsVerification -> {
                            bindProfileToVisualiser(state.profile)
                        }
                        is UiState.VerificationInProgress -> {
                            bindProfileToVisualiser(state.profile)
                        }
                        is UiState.EditMode -> {
                            bindProfileToVisualiser(state.draftProfile)
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
     * SECTION 1: Binds candidate data and sets up the interactive avatar monogram,
     * status badge, primary email CTA, and quick-contact toolbar (Call, GitHub, LinkedIn, Portfolio).
     */
    private fun bindHeroSection(hero: ProfileHero) {
        // 1. Monogram Initials & Glassmorphism Syllable Disc
        binding.tvHeroInitials.text = extractInitials(hero.fullName)

        // 2. Candidate Full Name (Centered)
        binding.tvHeroName.text = hero.fullName.ifBlank { getString(R.string.hero_default_name) }
        binding.tvHeroName.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        binding.tvHeroName.paint.isFakeBoldText = true

        // 3. Professional Headline (Centered)
        binding.tvHeroHeadline.text = hero.headlineTitle.ifBlank { getString(R.string.hero_default_headline) }

        // 4. Location Chip / Pill (Centered, with visibility guard)
        if (hero.location.isNotBlank()) {
            binding.tvHeroLocation.visibility = View.VISIBLE
            binding.tvHeroLocation.text = hero.location
        } else {
            binding.tvHeroLocation.visibility = View.GONE
        }

        // 5. Direct Outreach CTA Button Label ("Email {FirstName}" or "Email")
        val firstName = hero.fullName.trim().split("\\s+".toRegex()).firstOrNull()?.replaceFirstChar { it.uppercase() } ?: ""
        val emailBtnText = if (firstName.isNotBlank()) getString(R.string.btn_hero_email_format, firstName) else getString(R.string.btn_hero_email)
        binding.btnActionEmail.text = emailBtnText

        // 6. Populate Structured Interactive Contact Actions
        setupContactActions(hero.contactInfo)

        // 7. Harmonized Initial Load Entrance Animation (identical behavior to Section 2 Summary)
        motionDriver = HeroSectionMotionDriver(binding.heroContainer, binding.scrollView)
        binding.heroContainer.doOnPreDraw {
            motionDriver.playInitialEntrance()
        }
    }

    /**
     * Binds Direct Outreach Primary CTA (Email) and Quick-Contact & Social Toolbar (Call, GitHub, LinkedIn, Portfolio).
     * Provides tap to launch and long-press to copy with feedback.
     */
    private fun setupContactActions(contact: ContactInfo) {
        val hasEmail = contact.email.isNotBlank()

        // 1. Direct Outreach CTA: Email
        if (hasEmail) {
            binding.btnActionEmail.visibility = View.VISIBLE
            binding.btnActionEmail.setOnClickListener { sendEmail(contact.email) }
            binding.btnActionEmail.setOnLongClickListener {
                copyToClipboard("Email", contact.email)
                true
            }
        } else {
            binding.btnActionEmail.visibility = View.GONE
        }

        // 2. Quick-Contact & Social Toolbar (Call, GitHub, LinkedIn, Portfolio)
        if (contact.websiteUrl.isNotBlank()) {
            val portfolioIcon = resolveSocialPortfolioIcon(contact.websiteUrl)
            binding.ivSocialPortfolioIcon?.setImageResource(portfolioIcon)
        }

        val toolbarItems = listOf(
            Triple(binding.btnActionCall, contact.phone, "Phone"),
            Triple(binding.btnSocialGithub, contact.githubUrl, "GitHub URL"),
            Triple(binding.btnSocialLinkedin, contact.linkedinUrl, "LinkedIn URL"),
            Triple(binding.btnSocialPortfolio, contact.websiteUrl, "Portfolio URL")
        )

        val density = resources.displayMetrics.density
        var visibleToolbarCount = 0
        for ((button, rawValue, label) in toolbarItems) {
            val value = rawValue.trim()
            if (value.isNotBlank()) {
                button.visibility = View.VISIBLE
                val params = button.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                if (params != null) {
                    params.marginStart = if (visibleToolbarCount == 0) 0 else (10 * density).toInt()
                    button.layoutParams = params
                }
                button.setOnClickListener {
                    button.animate()
                        .scaleX(0.88f).scaleY(0.88f).setDuration(100)
                        .withEndAction {
                            button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220)
                                .setInterpolator(android.view.animation.OvershootInterpolator(2.0f)).start()
                            if (button == binding.btnActionCall) {
                                dialPhoneNumber(value)
                            } else {
                                openProfileUrl(value)
                            }
                        }.start()
                }
                button.setOnLongClickListener {
                    copyToClipboard(label, value)
                    true
                }
                visibleToolbarCount++
            } else {
                button.visibility = View.GONE
            }
        }

        binding.layoutSocialDock.visibility = if (visibleToolbarCount > 0) View.VISIBLE else View.GONE
    }

    /**
     * Dynamically extracts up to 2 uppercase monogram initials from candidate full name.
     * Uses first letter of the first word and first letter of the last word for multi-word names.
     * Falls back to @string/hero_default_initials if name is blank or devoid of letters.
     */
    private fun extractInitials(fullName: String): String {
        val words = fullName.trim()
            .split("\\s+".toRegex())
            .map { it.filter { char -> char.isLetter() } }
            .filter { it.isNotBlank() }

        return when {
            words.isEmpty() -> getString(R.string.hero_default_initials)
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "${words.first().first().uppercase()}${words.last().first().uppercase()}"
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied to clipboard! ✨", Toast.LENGTH_SHORT).show()
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
            binding.wrapperSectionSummary?.visibility = View.GONE
            summaryLayout.sectionSummaryRoot.visibility = View.GONE
            return
        }

        binding.wrapperSectionSummary?.visibility = View.VISIBLE
        summaryLayout.sectionSummaryRoot.visibility = View.VISIBLE
        summaryLayout.tvSummaryBody.text = cleanSummary

        // Initialize motion driver targeting the whole summary container
        summaryMotionDriver = SummarySectionMotionDriver(
            sectionContainer = summaryLayout.sectionSummaryRoot,
            scrollView = binding.scrollView
        )

        // Trigger Phase 1 initial entrance once layout pass is complete
        summaryLayout.sectionSummaryRoot.doOnPreDraw {
            // Clamp watermark height so it never exceeds content height and never introduces extra bottom space
            val contentBottom = summaryLayout.tvSummaryBody.bottom
            if (contentBottom > 0 && summaryLayout.ivSummaryWatermark.bottom > contentBottom) {
                val overflow = summaryLayout.ivSummaryWatermark.bottom - contentBottom
                summaryLayout.ivSummaryWatermark.updateLayoutParams<FrameLayout.LayoutParams> {
                    height = (height - overflow).coerceAtLeast(0)
                }
            }
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
            binding.wrapperSectionExperience?.visibility = View.GONE
            expLayout.sectionExperienceRoot.visibility = View.GONE
            return
        }

        binding.wrapperSectionExperience?.visibility = View.VISIBLE
        expLayout.sectionExperienceRoot.visibility = View.VISIBLE
        val container = expLayout.containerExperienceEntries
        container.removeAllViews()

        validExperiences.forEachIndexed { index, item ->
            val entryBinding = ItemVisualiserExperienceEntryBinding.inflate(layoutInflater, container, false)

            // 1. Company Name (Top Headline)
            entryBinding.tvEntryCompany.text = item.companyName.ifBlank { getString(R.string.exp_default_company) }

            // 2. Role Title (Sub-header)
            entryBinding.tvEntryRole.text = item.roleTitle.ifBlank { getString(R.string.exp_default_role) }

            // 3. Duration formatted string (No dot)
            val duration = when {
                item.startDate.isNotBlank() && item.endDate.isNotBlank() ->
                    getString(R.string.exp_date_range_format, item.startDate, item.endDate)
                item.startDate.isNotBlank() ->
                    getString(R.string.exp_single_date_format, item.startDate)
                else -> ""
            }
            if (duration.isNotBlank()) {
                entryBinding.tvEntryDuration.text = duration
                entryBinding.tvEntryDuration.visibility = View.VISIBLE
            } else {
                entryBinding.tvEntryDuration.visibility = View.GONE
            }

            // 4. Populate Bullets using item_visualiser_bullet_row (4dp dot, 14sp Inter #434655)
            val hasClientApps = item.clientApps.isNotEmpty()
            val hasTags = item.techStackTags.isNotEmpty()
            val bullets = item.bulletPoints.filter { it.isNotBlank() }

            entryBinding.containerEntryBullets.removeAllViews()
            bullets.forEachIndexed { bulletIdx, bullet ->
                val bulletBinding = ItemVisualiserBulletRowBinding.inflate(layoutInflater, entryBinding.containerEntryBullets, false)
                bulletBinding.tvBulletText.text = bullet.trim()
                if (bulletIdx == bullets.lastIndex && !hasClientApps && !hasTags) {
                    (bulletBinding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                        lp.bottomMargin = 0
                        bulletBinding.root.layoutParams = lp
                    }
                }
                entryBinding.containerEntryBullets.addView(bulletBinding.root)
            }

            // 5. Client Apps & Tech Stack Deliverables (Pill Chips with ↗ prefix)
            if (hasClientApps || hasTags) {
                entryBinding.layoutEntryDeliverables.visibility = View.VISIBLE
                entryBinding.chipGroupEntryTags.removeAllViews()

                if (hasClientApps) {
                    entryBinding.tvDeliverablesLabel.setText(R.string.exp_deliverables_title)
                } else {
                    entryBinding.tvDeliverablesLabel.setText(R.string.exp_technologies_title)
                }

                item.clientApps.forEach { app ->
                    val chipBinding = ItemVisualiserDeliverableChipBinding.inflate(layoutInflater, entryBinding.chipGroupEntryTags, false)
                    val chip = chipBinding.root

                    val spannable = SpannableStringBuilder().apply {
                        append("↗  ")
                        setSpan(
                            ForegroundColorSpan(getColor(R.color.exp_deliverable_chip_arrow)),
                            0, 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            0, 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        append(app.name)
                        setSpan(
                            ForegroundColorSpan(getColor(R.color.exp_deliverable_chip_text)),
                            3, length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    chip.text = spannable
                    if (app.url.isNotBlank()) {
                        chip.isClickable = true
                        chip.setOnClickListener {
                            openProfileUrl(app.url)
                        }
                    } else {
                        chip.isClickable = false
                    }
                    entryBinding.chipGroupEntryTags.addView(chip)
                }

                // If no client apps exist, display tech stack tags as deliverable pill chips
                if (!hasClientApps) {
                    item.techStackTags.forEach { tag ->
                        val chipBinding = ItemVisualiserDeliverableChipBinding.inflate(layoutInflater, entryBinding.chipGroupEntryTags, false)
                        val chip = chipBinding.root
                        chip.text = tag
                        chip.isClickable = false
                        entryBinding.chipGroupEntryTags.addView(chip)
                    }
                }
            } else {
                entryBinding.layoutEntryDeliverables.visibility = View.GONE
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
        experienceMotionDriver = WorkExperienceMotionDriver(expLayout.sectionExperienceRoot, binding.scrollView)

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
            binding.wrapperSectionProjects?.visibility = View.GONE
            projectsLayout.sectionProjectsRoot.visibility = View.GONE
            return
        }

        binding.wrapperSectionProjects?.visibility = View.VISIBLE
        projectsLayout.sectionProjectsRoot.visibility = View.VISIBLE
        val container = projectsLayout.containerProjectEntries
        container.removeAllViews()

        validProjects.forEachIndexed { index, item ->
            val entryBinding = ItemVisualiserProjectEntryBinding.inflate(layoutInflater, container, false)

            // 1. Project Title Headline (Single full-width row without redundant inline action button)
            entryBinding.tvEntryProjectTitle.text = item.projectTitle

            // 2. Tech Stack Tags
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

            // 3. Single Dynamic Project Link Source in Deliverables Dock (GitHub, Storage, Web Hosting, etc.)
            val primaryUrl = when {
                item.codeUrl.isNotBlank() && item.demoUrl.isNotBlank() && item.codeUrl == item.demoUrl -> item.codeUrl
                item.codeUrl.isNotBlank() && item.demoUrl.isBlank() -> item.codeUrl
                item.demoUrl.isNotBlank() && item.codeUrl.isBlank() -> item.demoUrl
                item.demoUrl.isNotBlank() && item.codeUrl.isNotBlank() -> {
                    if (item.demoUrl.contains("github.com", ignoreCase = true) && !item.codeUrl.contains("github.com", ignoreCase = true)) {
                        item.codeUrl
                    } else {
                        item.demoUrl
                    }
                }
                else -> ""
            }.trim()

            // 4. Bullet Points (Features & Capabilities matching Professional Experience)
            entryBinding.containerProjectBullets.removeAllViews()
            val bullets = parseBulletsFromText(item.description)
            if (bullets.isNotEmpty()) {
                bullets.forEachIndexed { bulletIdx, bullet ->
                    val bulletBinding = ItemVisualiserBulletRowBinding.inflate(layoutInflater, entryBinding.containerProjectBullets, false)
                    bulletBinding.tvBulletText.text = bullet
                    if (bulletIdx == bullets.lastIndex && primaryUrl.isBlank()) {
                        (bulletBinding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                            lp.bottomMargin = 0
                            bulletBinding.root.layoutParams = lp
                        }
                    }
                    entryBinding.containerProjectBullets.addView(bulletBinding.root)
                }
                entryBinding.containerProjectBullets.visibility = View.VISIBLE
                entryBinding.tvEntryProjectDescription.visibility = View.GONE
            } else if (item.description.isNotBlank()) {
                // Fallback for non-bulletized raw description text
                entryBinding.containerProjectBullets.visibility = View.GONE
                entryBinding.tvEntryProjectDescription.visibility = View.VISIBLE
                entryBinding.tvEntryProjectDescription.text = item.description
            } else {
                entryBinding.containerProjectBullets.visibility = View.GONE
                entryBinding.tvEntryProjectDescription.visibility = View.GONE
            }

            // 5. Populate Project Deliverables Dock
            if (primaryUrl.isNotBlank()) {
                entryBinding.layoutProjectDeliverables.visibility = View.VISIBLE
                entryBinding.chipGroupProjectLinks.removeAllViews()

                val linkMeta = resolveProjectLinkMetadata(primaryUrl)
                val chipBinding = ItemVisualiserDeliverableChipBinding.inflate(layoutInflater, entryBinding.chipGroupProjectLinks, false)
                val chip = chipBinding.root

                chip.chipIcon = ContextCompat.getDrawable(this, linkMeta.iconRes)
                chip.isChipIconVisible = true
                chip.chipIconSize = resources.displayMetrics.density * 15f
                chip.chipIconTint = ColorStateList.valueOf(getColor(R.color.exp_deliverable_chip_text))
                chip.iconStartPadding = resources.displayMetrics.density * 6f
                chip.iconEndPadding = resources.displayMetrics.density * 4f

                val labelText = getString(linkMeta.labelRes)
                val spannable = SpannableStringBuilder().apply {
                    append(labelText)
                    append("  ↗")
                    setSpan(
                        ForegroundColorSpan(getColor(R.color.exp_deliverable_chip_arrow)),
                        length - 1, length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        length - 1, length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                chip.text = spannable
                chip.isClickable = true
                chip.setOnClickListener { openProfileUrl(primaryUrl) }
                entryBinding.chipGroupProjectLinks.addView(chip)
            } else {
                entryBinding.layoutProjectDeliverables.visibility = View.GONE
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
        projectsMotionDriver = ProjectsMotionDriver(projectsLayout.sectionProjectsRoot, binding.scrollView)

        // Trigger Phase 1 initial entrance once layout pass is complete (synchronized with Sections 1..3)
        projectsLayout.sectionProjectsRoot.doOnPreDraw {
            projectsMotionDriver.playInitialEntrance()
        }
    }

    /**
     * Parses bullet items from multi-line text, splitting on newlines and bullet symbols
     * (•, \u2022, ·) and stripping bullet prefixes, ensuring clean list representation.
     */
    private fun parseBulletsFromText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = text.lines()
        val bullets = mutableListOf<String>()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            if (line.contains("•") || line.contains("\u2022") || line.contains("·")) {
                val parts = line.split(Regex("[•\\u2022·]"))
                for (part in parts) {
                    val cleaned = cleanBulletLine(part)
                    if (cleaned.isNotBlank()) {
                        bullets.add(cleaned)
                    }
                }
            } else {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    bullets.add(cleaned)
                }
            }
        }
        return bullets
    }

    private fun cleanBulletLine(line: String): String {
        return line.trim()
            .replace(Regex("^[.•\\u2022\\u25cf\\u00b7\\u2043\\u25e6\\-*–—\\s]+"), "")
            .replace(Regex("^[0-9]+[.)\\-]\\s*"), "")
            .trim()
    }

    private data class ProjectLinkMetadata(
        @DrawableRes val iconRes: Int,
        @StringRes val labelRes: Int
    )

    /**
     * Resolves single project link type to an appropriate icon and label:
     * - GitHub / Git repository: GitHub icon + "GitHub Repository"
     * - Cloud Storage / Drive / Files: Cloud Storage icon + "Cloud Storage"
     * - Play Store / App Store: Play Store / Store icon + "Play Store"
     * - Web Hosting / Live App: Globe icon + "Live Web App"
     */
    private fun resolveProjectLinkMetadata(rawUrl: String): ProjectLinkMetadata {
        val lower = rawUrl.lowercase()
        return when {
            lower.contains("github.com") || lower.contains("gitlab.com") || lower.contains("bitbucket.org") -> {
                ProjectLinkMetadata(R.drawable.ic_github_16, R.string.project_link_github)
            }
            lower.contains("pub.dev") -> {
                ProjectLinkMetadata(R.drawable.ic_code_16, R.string.project_link_pub_dev)
            }
            lower.contains("drive.google.com") || lower.contains("docs.google.com") ||
            lower.contains("firebasestorage.googleapis.com") || lower.contains("dropbox.com") ||
            lower.contains("onedrive.live.com") || lower.contains("1drv.ms") ||
            lower.contains("box.com") || lower.contains("storage.googleapis.com") ||
            lower.endsWith(".apk") || lower.endsWith(".zip") || lower.endsWith(".pdf") -> {
                ProjectLinkMetadata(R.drawable.ic_cloud_storage_16, R.string.project_link_storage)
            }
            lower.contains("play.google.com") -> {
                ProjectLinkMetadata(R.drawable.ic_play_store_16, R.string.project_link_play_store)
            }
            lower.contains("apps.apple.com") -> {
                ProjectLinkMetadata(R.drawable.ic_globe_16, R.string.project_link_app_store)
            }
            lower.contains("credly.com") || lower.contains("coursera.org") || lower.contains("udacity.com") ||
            lower.contains("credential") || lower.contains("certificate") || lower.contains("badge") -> {
                ProjectLinkMetadata(R.drawable.ic_star_badge_16, R.string.custom_link_credential)
            }
            else -> {
                ProjectLinkMetadata(R.drawable.ic_globe_16, R.string.project_link_web)
            }
        }
    }

    private fun resolveSocialPortfolioIcon(rawUrl: String): Int {
        val lower = rawUrl.lowercase().trim()
        return when {
            lower.contains("play.google.com") -> R.drawable.ic_play_store_20
            lower.contains("pub.dev") || lower.contains("leetcode.com") || lower.contains("hackerrank.com") || lower.contains("codeforces.com") -> R.drawable.ic_code_20
            lower.contains("x.com") || lower.contains("twitter.com") -> R.drawable.ic_x_twitter_20
            else -> R.drawable.ic_globe_20 // Industry-standard Globe for Portfolio / Website
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
            binding.wrapperSectionSkills?.visibility = View.GONE
            skillsLayout.sectionSkillsRoot.visibility = View.GONE
            return
        }

        binding.wrapperSectionSkills?.visibility = View.VISIBLE
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
        } else if (skills.rawFlatSkills.isEmpty()) {
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
            // CASE 1: Categorized Skills (Domain Label + Stitch Pill Chips)
            val domainEntries = categorizedMap.entries.toList()
            domainEntries.forEachIndexed { index, (domainLabel, skillsList) ->
                val rowBinding = ItemVisualiserSkillDomainBinding.inflate(layoutInflater, container, false)
                rowBinding.tvDomainHeader.visibility = View.VISIBLE
                rowBinding.tvDomainHeader.text = domainLabel

                // Zero out bottom padding on the last domain row to preserve exact 32dp gap to next section
                if (index == domainEntries.lastIndex) {
                    rowBinding.itemSkillDomainRoot.setPadding(0, 0, 0, 0)
                }

                rowBinding.chipGroupDomainSkills.removeAllViews()
                skillsList.forEach { skill ->
                    val chipBinding = ItemVisualiserSkillChipBinding.inflate(layoutInflater, rowBinding.chipGroupDomainSkills, false)
                    chipBinding.root.text = skill
                    rowBinding.chipGroupDomainSkills.addView(chipBinding.root)
                }

                container.addView(rowBinding.itemSkillDomainRoot)
            }
        } else {
            // CASE 2: Uncategorized / Flat List (Stitch Pill Chips)
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
                rowBinding.itemSkillDomainRoot.setPadding(0, 0, 0, 0)

                rowBinding.chipGroupDomainSkills.removeAllViews()
                flatSkillsList.forEach { skill ->
                    val chipBinding = ItemVisualiserSkillChipBinding.inflate(layoutInflater, rowBinding.chipGroupDomainSkills, false)
                    chipBinding.root.text = skill
                    rowBinding.chipGroupDomainSkills.addView(chipBinding.root)
                }

                container.addView(rowBinding.itemSkillDomainRoot)
            }
        }

        // Initialize motion driver targeting the whole section container
        skillsMotionDriver = TechnicalSkillsMotionDriver(skillsLayout.sectionSkillsRoot, binding.scrollView)

        // Trigger Phase 1 initial entrance once layout pass is complete (synchronized with Sections 1..4)
        skillsLayout.sectionSkillsRoot.doOnPreDraw {
            skillsMotionDriver.playInitialEntrance()
        }
    }

    private data class FormattedEducation(
        val cleanDegree: String,
        val institution: String,
        val detailsLine: String
    )

    /**
     * Parses and separates Degree name, Institution name, and combines CGPA and duration
     * into the same line separated by a vertical bar (' | ') per design requirements.
     */
    private fun formatEducationItem(item: EducationItem): FormattedEducation {
        var rawDegree = item.degree.trim()
        var cgpaText = ""

        // Extract CGPA / GPA if embedded in degree string
        val cgpaRegex = Regex("""(?i)(?:[|,•·\-\(\[\{]\s*)?(CGPA|GPA)[\s:]*([0-9]+(?:\.[0-9]+)?(?:\s*/\s*[0-9]+(?:\.[0-9]+)?)?)\s*[)\]}]?""")
        val matchDegree = cgpaRegex.find(rawDegree)
        if (matchDegree != null) {
            val label = matchDegree.groupValues[1].uppercase()
            val value = matchDegree.groupValues[2]
            cgpaText = "$label: $value"
            rawDegree = rawDegree.replace(matchDegree.value, "").trim()
                .trimEnd('|', '-', '•', '·', ',', '(')
                .trimStart('|', '-', '•', '·', ',', ')')
                .trim()
        }

        var year = item.graduationYear.trim()
        if (cgpaText.isBlank()) {
            val matchYear = cgpaRegex.find(year)
            if (matchYear != null) {
                val label = matchYear.groupValues[1].uppercase()
                val value = matchYear.groupValues[2]
                cgpaText = "$label: $value"
                year = year.replace(matchYear.value, "").trim()
                    .trimEnd('|', '-', '•', '·', ',', '(')
                    .trimStart('|', '-', '•', '·', ',', ')')
                    .trim()
            }
        }

        val detailsLine = when {
            cgpaText.isNotBlank() && year.isNotBlank() -> "$cgpaText  |  $year"
            cgpaText.isNotBlank() -> cgpaText
            year.isNotBlank() -> year
            else -> ""
        }

        return FormattedEducation(
            cleanDegree = rawDegree.ifBlank { item.degree },
            institution = item.institution.trim(),
            detailsLine = detailsLine
        )
    }

    /**
     * SECTION 6: Education & Academics
     * Direct clean porcelain typography matching Section 3:
     * - Line 1: Degree Name (Headline First, 17sp Bold Inter #0F172A)
     * - Line 2: Institution / University Name (Sub-headline, 14sp Semi-bold/Bold Royal Blue #2563EB)
     * - Line 3: CGPA & Duration in Same Line (13sp Slate #737686 separated by ' | ')
     * Transparent, borderless container directly on canvas (no vertical timeline line or milestone node).
     */
    private fun setupSection6(educationList: List<EducationItem>) {
        val eduLayout = binding.includedSectionEducation

        val validEdu = educationList.filter { it.institution.isNotBlank() || it.degree.isNotBlank() }
        if (validEdu.isEmpty()) {
            binding.wrapperSectionEducation?.visibility = View.GONE
            eduLayout.sectionEducationRoot.visibility = View.GONE
            return
        }

        binding.wrapperSectionEducation?.visibility = View.VISIBLE
        eduLayout.sectionEducationRoot.visibility = View.VISIBLE
        val container = eduLayout.containerEducationEntries
        container.removeAllViews()

        validEdu.forEachIndexed { index, item ->
            val entryBinding = ItemVisualiserEducationEntryBinding.inflate(layoutInflater, container, false)
            val formatted = formatEducationItem(item)

            // 1. Degree Name (Headline First, 17sp Bold #0F172A)
            entryBinding.tvEntryDegree.text = formatted.cleanDegree.ifBlank { getString(R.string.hint_degree) }

            // 2. Institution / University Name (Sub-headline Second, 14sp Bold Royal Blue #2563EB)
            if (formatted.institution.isNotBlank()) {
                entryBinding.tvEntryInstitution.text = formatted.institution
                entryBinding.tvEntryInstitution.visibility = View.VISIBLE
            } else {
                entryBinding.tvEntryInstitution.visibility = View.GONE
            }

            // 3. CGPA and Duration in Same Line Separated by Vertical Line (13sp Slate #737686)
            if (formatted.detailsLine.isNotBlank()) {
                entryBinding.tvEntryEduYear.text = formatted.detailsLine
                entryBinding.tvEntryEduYear.visibility = View.VISIBLE
            } else {
                entryBinding.tvEntryEduYear.visibility = View.GONE
            }

            // 4. Hairline Divider between entries (hidden on last item)
            if (index == validEdu.lastIndex) {
                entryBinding.dividerEducationEntry.visibility = View.GONE
            } else {
                entryBinding.dividerEducationEntry.visibility = View.VISIBLE
            }

            container.addView(entryBinding.root)
        }

        educationMotionDriver = EducationMotionDriver(eduLayout.sectionEducationRoot, binding.scrollView)

        eduLayout.sectionEducationRoot.doOnPreDraw {
            // Clamp watermark height so it never exceeds content height and never introduces extra bottom space
            val contentBottom = eduLayout.containerEducationEntries.bottom
            if (contentBottom > 0 && eduLayout.ivEducationWatermark.bottom > contentBottom) {
                val overflow = eduLayout.ivEducationWatermark.bottom - contentBottom
                eduLayout.ivEducationWatermark.updateLayoutParams<FrameLayout.LayoutParams> {
                    height = (height - overflow).coerceAtLeast(0)
                }
            }
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

        validSections.forEachIndexed { sectionIdx, section ->
            val sectionBinding = LayoutSectionCustomDynamicBinding.inflate(layoutInflater, containerCustom, false)

            val archetype = resolveCustomSectionArchetype(section.sectionTitle)
            val isHobbies = archetype == CustomSectionArchetype.HOBBIES
            val isLanguages = archetype == CustomSectionArchetype.LANGUAGES

            // 1. Spacing between sections: tighten to 20dp when entering or leaving compact sections (Languages, Hobbies)
            if (sectionIdx > 0) {
                val prevSection = validSections[sectionIdx - 1]
                val prevArchetype = resolveCustomSectionArchetype(prevSection.sectionTitle)
                val prevIsCompact = prevArchetype == CustomSectionArchetype.LANGUAGES || prevArchetype == CustomSectionArchetype.HOBBIES
                val currentIsCompact = isLanguages || isHobbies
                val marginDp = if (currentIsCompact || prevIsCompact) 20 else 32
                val params = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (marginDp * resources.displayMetrics.density).toInt()
                }
                sectionBinding.root.layoutParams = params
            }

            // 2. Section Header Title & Thematic Icon
            sectionBinding.tvCustomSectionTitle.text = section.sectionTitle
            sectionBinding.sectionCustomRoot.contentDescription = getString(R.string.cd_custom_section_format, section.sectionTitle)

            val headerIconRes = when (archetype) {
                CustomSectionArchetype.CERTIFICATIONS -> R.drawable.ic_description_16
                CustomSectionArchetype.OPEN_SOURCE -> R.drawable.ic_code_16
                CustomSectionArchetype.LANGUAGES -> R.drawable.ic_globe_16
                CustomSectionArchetype.PUBLICATIONS -> R.drawable.ic_description_16
                else -> R.drawable.ic_star_badge_16
            }
            val headerIcon = ContextCompat.getDrawable(this, headerIconRes)?.mutate()?.apply {
                setTint(getColor(R.color.summary_section_title))
            }
            sectionBinding.tvCustomSectionTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                headerIcon, null, null, null
            )

            // 3. Compact Header Margin Bottom for Languages and Hobbies (8dp instead of 20dp)
            if (isLanguages || isHobbies) {
                (sectionBinding.tvCustomSectionTitle.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.bottomMargin = (8 * resources.displayMetrics.density).toInt()
                    sectionBinding.tvCustomSectionTitle.layoutParams = lp
                }
            }

            // 4. Distinct Thematic Watermark Artwork per Custom Section
            val watermarkRes = when (archetype) {
                CustomSectionArchetype.CERTIFICATIONS -> R.drawable.bg_watermark_certifications
                CustomSectionArchetype.AWARDS -> R.drawable.bg_watermark_awards
                CustomSectionArchetype.VOLUNTEER -> R.drawable.bg_watermark_volunteer
                CustomSectionArchetype.PUBLICATIONS -> R.drawable.bg_watermark_publications
                CustomSectionArchetype.OPEN_SOURCE -> R.drawable.bg_watermark_opensource
                CustomSectionArchetype.LANGUAGES -> R.drawable.bg_watermark_languages
                CustomSectionArchetype.HOBBIES -> R.drawable.bg_watermark_hobbies
                CustomSectionArchetype.ACHIEVEMENTS, CustomSectionArchetype.GENERIC -> R.drawable.bg_watermark_custom
            }
            sectionBinding.ivCustomWatermark.setImageResource(watermarkRes)

            if (isLanguages || isHobbies) {
                val compactSize = (100 * resources.displayMetrics.density).toInt()
                sectionBinding.ivCustomWatermark.updateLayoutParams<FrameLayout.LayoutParams> {
                    width = compactSize
                    height = compactSize
                }
            }

            val itemsContainer = sectionBinding.containerCustomItems
            itemsContainer.removeAllViews()

            if (isLanguages) {
                // Languages: Render all language bullets together in a single compact container
                val allBullets = section.items.flatMap { item ->
                    if (item.bulletPoints.isNotEmpty()) {
                        item.bulletPoints.filter { it.isNotBlank() }
                    } else if (item.description.isNotBlank()) {
                        parseBulletsFromText(item.description)
                    } else if (item.title.isNotBlank() && !item.title.equals("Languages", ignoreCase = true)) {
                        listOf(item.title)
                    } else {
                        emptyList()
                    }
                }

                val itemBinding = ItemVisualiserCustomEntryBinding.inflate(layoutInflater, itemsContainer, false)
                itemBinding.tvEntryCustomTitle.visibility = View.GONE
                itemBinding.layoutEntryCustomMetadataRow.visibility = View.GONE
                itemBinding.tvEntryCustomPeriod.visibility = View.GONE
                itemBinding.tvEntryCustomSubtitle.visibility = View.GONE
                itemBinding.tvEntryCustomDescription.visibility = View.GONE
                itemBinding.layoutCustomDeliverables.visibility = View.GONE
                itemBinding.dividerCustomEntry.visibility = View.GONE

                itemBinding.containerEntryCustomBullets.visibility = View.VISIBLE
                (itemBinding.containerEntryCustomBullets.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.topMargin = 0
                    itemBinding.containerEntryCustomBullets.layoutParams = lp
                }
                itemBinding.containerEntryCustomBullets.removeAllViews()

                allBullets.forEachIndexed { bulletIdx, bullet ->
                    val bulletBinding = ItemVisualiserBulletRowBinding.inflate(layoutInflater, itemBinding.containerEntryCustomBullets, false)
                    bulletBinding.tvBulletText.text = bullet.trim()
                    (bulletBinding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                        lp.bottomMargin = if (bulletIdx == allBullets.lastIndex) 0 else (6 * resources.displayMetrics.density).toInt()
                        bulletBinding.root.layoutParams = lp
                    }
                    itemBinding.containerEntryCustomBullets.addView(bulletBinding.root)
                }
                itemsContainer.addView(itemBinding.root)
            } else if (isHobbies) {
                // Hobbies: Simply comma-separated text or narrative, compact spacing directly below title
                val allHobbies = section.items.mapNotNull { item ->
                    if (item.description.isNotBlank()) {
                        item.description.trim()
                    } else if (item.bulletPoints.isNotEmpty()) {
                        item.bulletPoints.filter { it.isNotBlank() }.joinToString(", ")
                    } else if (item.title.isNotBlank() && !item.title.equals("Hobbies", ignoreCase = true)) {
                        item.title.trim()
                    } else {
                        null
                    }
                }.filter { it.isNotBlank() }.joinToString(", ")

                val itemBinding = ItemVisualiserCustomEntryBinding.inflate(layoutInflater, itemsContainer, false)
                itemBinding.tvEntryCustomTitle.visibility = View.GONE
                itemBinding.layoutEntryCustomMetadataRow.visibility = View.GONE
                itemBinding.tvEntryCustomPeriod.visibility = View.GONE
                itemBinding.tvEntryCustomSubtitle.visibility = View.GONE
                itemBinding.containerEntryCustomBullets.visibility = View.GONE
                itemBinding.layoutCustomDeliverables.visibility = View.GONE
                itemBinding.dividerCustomEntry.visibility = View.GONE

                itemBinding.tvEntryCustomDescription.visibility = View.VISIBLE
                (itemBinding.tvEntryCustomDescription.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.topMargin = 0
                    itemBinding.tvEntryCustomDescription.layoutParams = lp
                }
                itemBinding.tvEntryCustomDescription.text = allHobbies
                itemsContainer.addView(itemBinding.root)
            } else {
                section.items.forEachIndexed { index, item ->
                    val itemBinding = ItemVisualiserCustomEntryBinding.inflate(layoutInflater, itemsContainer, false)

                    // 1. Item Title Headline (17sp Bold Inter #0F172A, full-width)
                    itemBinding.tvEntryCustomTitle.visibility = View.VISIBLE
                    itemBinding.tvEntryCustomTitle.text = item.title

                    // 2. Metadata Sub-Header Row: Subtitle/Issuer on left, Duration/Period on right (matching Professional Experience)
                    val isVerifiedContributor = item.subtitle.trim().contains("Verified Contributor", ignoreCase = true)
                    val hasPublishedLink = item.link.isNotBlank()
                    val showSubtitle = item.subtitle.isNotBlank() && !(isVerifiedContributor && hasPublishedLink)
                    val showPeriod = item.dateOrPeriod.isNotBlank() && !item.dateOrPeriod.equals("Active", ignoreCase = true)

                    if (showSubtitle || showPeriod) {
                        itemBinding.layoutEntryCustomMetadataRow.visibility = View.VISIBLE

                        if (showSubtitle) {
                            itemBinding.tvEntryCustomSubtitle.visibility = View.VISIBLE
                            itemBinding.tvEntryCustomSubtitle.text = item.subtitle
                        } else {
                            itemBinding.tvEntryCustomSubtitle.visibility = View.GONE
                        }

                        if (showPeriod) {
                            itemBinding.tvEntryCustomPeriod.visibility = View.VISIBLE
                            itemBinding.tvEntryCustomPeriod.text = item.dateOrPeriod
                        } else {
                            itemBinding.tvEntryCustomPeriod.visibility = View.GONE
                        }
                    } else {
                        itemBinding.layoutEntryCustomMetadataRow.visibility = View.GONE
                    }

                    // 4. Bullet Points vs Description (Prevent text duplication matching Projects & Experience)
                    val bullets = if (item.bulletPoints.isNotEmpty()) {
                        item.bulletPoints.filter { it.isNotBlank() }
                    } else if (item.description.isNotBlank()) {
                        parseBulletsFromText(item.description)
                    } else if (item.title.isNotBlank() && !item.title.equals("Languages", ignoreCase = true)) {
                        listOf(item.title)
                    } else {
                        emptyList()
                    }

                    if (bullets.isNotEmpty()) {
                        itemBinding.containerEntryCustomBullets.visibility = View.VISIBLE
                        itemBinding.containerEntryCustomBullets.removeAllViews()
                        bullets.forEachIndexed { bulletIdx, bullet ->
                            val bulletBinding = ItemVisualiserBulletRowBinding.inflate(layoutInflater, itemBinding.containerEntryCustomBullets, false)
                            bulletBinding.tvBulletText.text = bullet.trim()
                            if (bulletIdx == bullets.lastIndex && item.link.isBlank()) {
                                (bulletBinding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                                    lp.bottomMargin = 0
                                    bulletBinding.root.layoutParams = lp
                                }
                            }
                            itemBinding.containerEntryCustomBullets.addView(bulletBinding.root)
                        }
                        // Cleanly hide description when bullet representation is available
                        itemBinding.tvEntryCustomDescription.visibility = View.GONE
                    } else if (item.description.isNotBlank()) {
                        itemBinding.containerEntryCustomBullets.visibility = View.GONE
                        itemBinding.tvEntryCustomDescription.visibility = View.VISIBLE
                        itemBinding.tvEntryCustomDescription.text = item.description
                    } else {
                        itemBinding.containerEntryCustomBullets.visibility = View.GONE
                        itemBinding.tvEntryCustomDescription.visibility = View.GONE
                    }

                    // 5. Deliverable / Credential Link Pill Chip (Never repeats subtitle; uses specific source label)
                    if (item.link.isNotBlank()) {
                        itemBinding.layoutCustomDeliverables.visibility = View.VISIBLE
                        itemBinding.chipGroupCustomLinks.removeAllViews()

                        val chipBinding = ItemVisualiserDeliverableChipBinding.inflate(layoutInflater, itemBinding.chipGroupCustomLinks, false)
                        val chip = chipBinding.root

                        val linkMeta = resolveProjectLinkMetadata(item.link)
                        chip.chipIcon = ContextCompat.getDrawable(this, linkMeta.iconRes)
                        chip.isChipIconVisible = true
                        chip.chipIconSize = resources.displayMetrics.density * 15f
                        chip.chipIconTint = ColorStateList.valueOf(getColor(R.color.exp_deliverable_chip_text))
                        chip.iconStartPadding = resources.displayMetrics.density * 6f
                        chip.iconEndPadding = resources.displayMetrics.density * 4f

                        var label = getString(linkMeta.labelRes)
                        if (label.equals(item.subtitle, ignoreCase = true)) {
                            label = getString(R.string.project_link_web)
                        }

                        val spannable = SpannableStringBuilder().apply {
                            append(label)
                            append("  ↗")
                            setSpan(
                                ForegroundColorSpan(getColor(R.color.exp_deliverable_chip_arrow)),
                                length - 1, length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            setSpan(
                                StyleSpan(Typeface.BOLD),
                                length - 1, length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        chip.text = spannable
                        chip.isClickable = true
                        chip.setOnClickListener { openProfileUrl(item.link) }
                        itemBinding.chipGroupCustomLinks.addView(chip)
                    } else {
                        itemBinding.layoutCustomDeliverables.visibility = View.GONE
                    }

                    // 7. Hairline Divider between entries (hidden on last item)
                    itemBinding.dividerCustomEntry.visibility = if (index == section.items.lastIndex) View.GONE else View.VISIBLE
                    itemsContainer.addView(itemBinding.root)
                }
            }

            // Attach dedicated motion driver for this custom section
            val driver = CustomSectionMotionDriver(sectionBinding.sectionCustomRoot, binding.scrollView)
            customSectionMotionDrivers.add(driver)

            sectionBinding.sectionCustomRoot.doOnPreDraw {
                // Clamp watermark height so it never exceeds content height and never introduces extra bottom space
                val contentBottom = sectionBinding.containerCustomItems.bottom
                if (contentBottom > 0 && sectionBinding.ivCustomWatermark.bottom > contentBottom) {
                    val overflow = sectionBinding.ivCustomWatermark.bottom - contentBottom
                    sectionBinding.ivCustomWatermark.updateLayoutParams<FrameLayout.LayoutParams> {
                        height = (height - overflow).coerceAtLeast(0)
                    }
                }
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
