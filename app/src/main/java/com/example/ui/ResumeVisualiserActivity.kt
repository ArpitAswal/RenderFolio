package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.example.databinding.LayoutShowcaseSubsequentSectionsBinding
import com.example.model.ContactInfo
import com.example.model.DynamicSection
import com.example.model.ProfileHero
import com.example.model.ResumeProfile
import com.example.ui.motion.HeroSectionMotionDriver
import com.example.ui.showcase.ExperienceTimelineAdapter
import com.example.ui.showcase.TimelineItemDecoration
import com.example.viewmodel.ResumeViewModel
import com.example.viewmodel.UiState
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import androidx.core.net.toUri

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
    private lateinit var preferencesRepository: ResumePreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Edge-to-Edge Canvas Rule
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityResumeVisualiserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesRepository = ResumePreferencesRepository(applicationContext)

        // Inflate and embed subsequent sections (Sections 2..7) inside containerContentSections
        subsequentBinding = LayoutShowcaseSubsequentSectionsBinding.inflate(layoutInflater, binding.containerContentSections, true)

        // Handle window insets on CoordinatorLayout / NestedScrollView
        setupWindowInsets()

        // Initialize motion driver targeting the whole hero container
        motionDriver = HeroSectionMotionDriver(binding.heroContainer)

        // Setup scroll-driven unified motion
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            motionDriver.onScrollChanged(scrollY)
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
                            bindSubsequentSections(state.profile)
                        }
                        is UiState.ExtractedNeedsVerification -> {
                            bindHeroSection(state.profile.hero)
                            bindSubsequentSections(state.profile)
                        }
                        is UiState.VerificationInProgress -> {
                            bindHeroSection(state.profile.hero)
                            bindSubsequentSections(state.profile)
                        }
                        is UiState.EditMode -> {
                            bindHeroSection(state.draftProfile.hero)
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
        val uri = "tel:${phone.trim()}".toUri()
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, uri)) }
            .onFailure { Toast.makeText(this, R.string.toast_error_no_phone_app, Toast.LENGTH_SHORT).show() }
    }

    private fun sendEmail(email: String) {
        val uri = "mailto:${email.trim()}".toUri()
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
        val uri = sanitized.toUri()
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { Toast.makeText(this, R.string.toast_error_cannot_open_link, Toast.LENGTH_SHORT).show() }
    }

    // --- Subsequent Sections (2..7) Binding ---

    private fun bindSubsequentSections(profile: ResumeProfile) {
        bindAboutSummary(profile)
        bindWorkExperience(profile)
        bindFeaturedProjects(profile)
        bindTechnicalCompetencies(profile)
        bindEducation(profile)
        bindCustomSections(profile.dynamicSections)
    }

    // 2. ABOUT / PROFESSIONAL SUMMARY
    private fun bindAboutSummary(profile: ResumeProfile) {
        val summary = profile.coreSections.profileSummary.trim()
        if (summary.isNotBlank()) {
            subsequentBinding.tvAboutSummaryBody.text = summary
            subsequentBinding.sectionAboutSummary.visibility = View.VISIBLE
        } else {
            subsequentBinding.sectionAboutSummary.visibility = View.GONE
        }
    }

    // 3. WORK EXPERIENCE (CONNECTED TIMELINE)
    private fun bindWorkExperience(profile: ResumeProfile) {
        val experiences = profile.coreSections.workExperience.filter {
            it.companyName.isNotBlank() || it.roleTitle.isNotBlank() || it.bulletPoints.isNotEmpty()
        }

        if (experiences.isNotEmpty()) {
            subsequentBinding.rvWorkExperienceTimeline.layoutManager = LinearLayoutManager(this)
            subsequentBinding.rvWorkExperienceTimeline.adapter = ExperienceTimelineAdapter(experiences)
            while (subsequentBinding.rvWorkExperienceTimeline.itemDecorationCount > 0) {
                subsequentBinding.rvWorkExperienceTimeline.removeItemDecorationAt(0)
            }
            subsequentBinding.rvWorkExperienceTimeline.addItemDecoration(TimelineItemDecoration(this))
            subsequentBinding.sectionWorkExperience.visibility = View.VISIBLE
        } else {
            subsequentBinding.sectionWorkExperience.visibility = View.GONE
        }
    }

    // 4. FEATURED PROJECTS
    private fun bindFeaturedProjects(profile: ResumeProfile) {
        val projects = profile.coreSections.projects.filter { it.projectTitle.isNotBlank() }
        subsequentBinding.containerProjectItems.removeAllViews()

        if (projects.isNotEmpty()) {
            val inflater = layoutInflater
            projects.forEach { proj ->
                val pBinding = ItemShowcaseProjectBinding.inflate(inflater, subsequentBinding.containerProjectItems, false)
                pBinding.tvProjectTitle.text = proj.projectTitle

                if (proj.description.isNotBlank()) {
                    pBinding.tvProjectDescription.text = proj.description
                    pBinding.tvProjectDescription.visibility = View.VISIBLE
                } else {
                    pBinding.tvProjectDescription.visibility = View.GONE
                }

                val targetUrl = proj.demoUrl.ifBlank { proj.codeUrl }
                if (targetUrl.isNotBlank()) {
                    pBinding.btnProjectDemo.visibility = View.VISIBLE
                    pBinding.btnProjectDemo.setOnClickListener { openProfileUrl(targetUrl) }
                } else {
                    pBinding.btnProjectDemo.visibility = View.GONE
                }

                pBinding.chipGroupProjectTech.removeAllViews()
                proj.techStackTags.forEach { tag ->
                    val tagChip = Chip(this).apply {
                        text = tag
                        setChipBackgroundColorResource(R.color.hero_chip_bg)
                        setChipStrokeColorResource(R.color.hero_chip_stroke)
                        chipStrokeWidth = resources.displayMetrics.density * 1f
                        setTextColor(getColor(R.color.hero_chip_text))
                        shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(resources.displayMetrics.density * 6f)
                            .build()
                    }
                    pBinding.chipGroupProjectTech.addView(tagChip)
                }
                pBinding.chipGroupProjectTech.visibility =
                    if (proj.techStackTags.isNotEmpty()) View.VISIBLE else View.GONE

                subsequentBinding.containerProjectItems.addView(pBinding.root)
            }
            subsequentBinding.sectionFeaturedProjects.visibility = View.VISIBLE
        } else {
            subsequentBinding.sectionFeaturedProjects.visibility = View.GONE
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
