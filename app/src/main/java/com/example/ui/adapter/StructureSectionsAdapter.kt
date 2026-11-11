package com.example.ui.adapter

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.databinding.*
import com.example.model.*
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout
import java.util.UUID

import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

enum class PaneTarget {
    ALL,
    LEFT,
    RIGHT
}

enum class SectionType {
    PERSONAL_INFO,
    ABOUT_SUMMARY,
    WORK_EXPERIENCE,
    PROJECTS,
    TECHNICAL_SKILLS,
    EDUCATION,
    CUSTOM,
    FOOTER
}

enum class CustomSectionArchetype {
    CERTIFICATIONS,
    ACHIEVEMENTS,
    PUBLICATIONS,
    VOLUNTEER,
    AWARDS,
    OPEN_SOURCE,
    LANGUAGES,
    HOBBIES,
    GENERIC
}

fun resolveCustomSectionArchetype(title: String): CustomSectionArchetype {
    val lower = title.lowercase()
    return when {
        lower.contains("certif") || lower.contains("course") || lower.contains("license") -> CustomSectionArchetype.CERTIFICATIONS
        lower.contains("achieve") -> CustomSectionArchetype.ACHIEVEMENTS
        lower.contains("publicat") || lower.contains("paper") || lower.contains("article") -> CustomSectionArchetype.PUBLICATIONS
        lower.contains("volunt") || lower.contains("community") -> CustomSectionArchetype.VOLUNTEER
        lower.contains("award") || lower.contains("honor") -> CustomSectionArchetype.AWARDS
        lower.contains("open source") || lower.contains("open-source") || lower.contains("repo") || lower.contains("source") -> CustomSectionArchetype.OPEN_SOURCE
        lower.contains("language") -> CustomSectionArchetype.LANGUAGES
        lower.contains("hobbi") || lower.contains("interest") -> CustomSectionArchetype.HOBBIES
        else -> CustomSectionArchetype.GENERIC
    }
}

data class SectionItem(
    val type: SectionType,
    var customSection: DynamicSection? = null
)

fun TextInputLayout.setRequiredHint(resId: Int) {
    val base = context.getString(resId)
    val html = "$base <font color='#EF4444'>*</font>"
    this.hint = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
}

fun TextInputLayout.setRequiredHint(label: String) {
    val html = "$label <font color='#EF4444'>*</font>"
    this.hint = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
}

fun TextView.setRequiredHeader(resId: Int) {
    val base = context.getString(resId)
    val html = "$base <font color='#EF4444'>*</font>"
    this.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
}

fun TextView.setRequiredHeader(label: String) {
    val html = "$label <font color='#EF4444'>*</font>"
    this.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
}

@SuppressLint("SetTextI18n")
class StructureSectionsAdapter(
    private var profile: ResumeProfile,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onAddCustomSectionClicked: () -> Unit,
    val paneTarget: PaneTarget = PaneTarget.ALL
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var isExperienced: Boolean = true

    val sections = mutableListOf<SectionItem>()

    // In-memory holding objects that stay updated as user edits
    var currentHero = profile.hero.copy(
        contactInfo = profile.hero.contactInfo.copy()
    )
        private set

    var currentSummary = profile.coreSections.profileSummary
        private set

    val currentExperiences = profile.coreSections.workExperience.toMutableList()
    val currentProjects = profile.coreSections.projects.toMutableList()
    var currentSkills = profile.coreSections.skills
        private set
    val currentEducation = profile.coreSections.education.toMutableList()
    val currentDynamicSections: List<DynamicSection>
        get() = sections.filter { it.type == SectionType.CUSTOM }.mapNotNull { it.customSection }

    val profileLinks = mutableListOf<String>()

    init {
        val initialLinks = mutableListOf<String>()
        if (profile.hero.contactInfo.linkedinUrl.isNotBlank()) initialLinks.add(profile.hero.contactInfo.linkedinUrl)
        if (profile.hero.contactInfo.githubUrl.isNotBlank()) initialLinks.add(profile.hero.contactInfo.githubUrl)
        if (profile.hero.contactInfo.websiteUrl.isNotBlank() && !initialLinks.contains(profile.hero.contactInfo.websiteUrl)) {
            initialLinks.add(profile.hero.contactInfo.websiteUrl)
        }
        profile.hero.contactInfo.customLinks.forEach { link ->
            if (link.isNotBlank() && !initialLinks.contains(link)) {
                initialLinks.add(link)
            }
        }
        profileLinks.addAll(initialLinks.distinct())

        buildSectionsList()
    }

    private fun buildSectionsList() {
        sections.clear()
        when (paneTarget) {
            PaneTarget.ALL -> {
                val order = if (profile.sectionOrder.isNotEmpty()) {
                    profile.sectionOrder
                } else {
                    ResumeSectionConstants.DEFAULT_CORE_ORDER + profile.dynamicSections.map { "custom_${it.sectionTitle}" }
                }

                val addedCoreTypes = mutableSetOf<SectionType>()
                val addedCustomTitles = mutableSetOf<String>()

                for (secId in order) {
                    when (secId) {
                        ResumeSectionConstants.SECTION_PERSONAL -> {
                            sections.add(SectionItem(SectionType.PERSONAL_INFO))
                            addedCoreTypes.add(SectionType.PERSONAL_INFO)
                        }
                        ResumeSectionConstants.SECTION_SUMMARY -> {
                            sections.add(SectionItem(SectionType.ABOUT_SUMMARY))
                            addedCoreTypes.add(SectionType.ABOUT_SUMMARY)
                        }
                        ResumeSectionConstants.SECTION_EXPERIENCE -> {
                            sections.add(SectionItem(SectionType.WORK_EXPERIENCE))
                            addedCoreTypes.add(SectionType.WORK_EXPERIENCE)
                        }
                        ResumeSectionConstants.SECTION_PROJECTS -> {
                            sections.add(SectionItem(SectionType.PROJECTS))
                            addedCoreTypes.add(SectionType.PROJECTS)
                        }
                        ResumeSectionConstants.SECTION_SKILLS -> {
                            sections.add(SectionItem(SectionType.TECHNICAL_SKILLS))
                            addedCoreTypes.add(SectionType.TECHNICAL_SKILLS)
                        }
                        ResumeSectionConstants.SECTION_EDUCATION -> {
                            sections.add(SectionItem(SectionType.EDUCATION))
                            addedCoreTypes.add(SectionType.EDUCATION)
                        }
                        else -> {
                            if (secId.startsWith("custom_")) {
                                val title = secId.removePrefix("custom_")
                                val match = profile.dynamicSections.firstOrNull { it.sectionTitle.equals(title, ignoreCase = true) }
                                    ?: DynamicSection(sectionTitle = title)
                                sections.add(SectionItem(SectionType.CUSTOM, match.copy()))
                                addedCustomTitles.add(match.sectionTitle.lowercase())
                            }
                        }
                    }
                }

                // If any core section wasn't in order, insert it at its canonical position
                if (!addedCoreTypes.contains(SectionType.PERSONAL_INFO)) sections.add(0, SectionItem(SectionType.PERSONAL_INFO))
                if (!addedCoreTypes.contains(SectionType.ABOUT_SUMMARY)) {
                    val insertIdx = if (sections.isNotEmpty() && sections[0].type == SectionType.PERSONAL_INFO) 1 else 0
                    sections.add(insertIdx, SectionItem(SectionType.ABOUT_SUMMARY))
                }
                if (!addedCoreTypes.contains(SectionType.WORK_EXPERIENCE)) sections.add(SectionItem(SectionType.WORK_EXPERIENCE))
                if (!addedCoreTypes.contains(SectionType.PROJECTS)) sections.add(SectionItem(SectionType.PROJECTS))
                if (!addedCoreTypes.contains(SectionType.TECHNICAL_SKILLS)) sections.add(SectionItem(SectionType.TECHNICAL_SKILLS))
                if (!addedCoreTypes.contains(SectionType.EDUCATION)) sections.add(SectionItem(SectionType.EDUCATION))

                // If any dynamic sections weren't in order, append them
                profile.dynamicSections.forEach { dynamic ->
                    if (!addedCustomTitles.contains(dynamic.sectionTitle.lowercase())) {
                        sections.add(SectionItem(SectionType.CUSTOM, dynamic.copy()))
                    }
                }

                sections.add(SectionItem(SectionType.FOOTER))
            }
            PaneTarget.LEFT -> {
                sections.add(SectionItem(SectionType.PERSONAL_INFO))
                sections.add(SectionItem(SectionType.ABOUT_SUMMARY))
                sections.add(SectionItem(SectionType.TECHNICAL_SKILLS))
                sections.add(SectionItem(SectionType.EDUCATION))
                profile.dynamicSections.forEachIndexed { index, dynamic ->
                    if (index % 2 == 0) {
                        sections.add(SectionItem(SectionType.CUSTOM, dynamic.copy()))
                    }
                }
            }
            PaneTarget.RIGHT -> {
                sections.add(SectionItem(SectionType.WORK_EXPERIENCE))
                sections.add(SectionItem(SectionType.PROJECTS))
                profile.dynamicSections.forEachIndexed { index, dynamic ->
                    if (index % 2 != 0) {
                        sections.add(SectionItem(SectionType.CUSTOM, dynamic.copy()))
                    }
                }
            }
        }
    }

    fun addCustomSection(title: String) {
        val newSection = DynamicSection(
            sectionTitle = title,
            items = listOf(
                DynamicItem(
                    title = "",
                    subtitle = "",
                    dateOrPeriod = "",
                    description = "",
                    link = "",
                    bulletPoints = emptyList()
                )
            )
        )
        val footerIndex = sections.indexOfFirst { it.type == SectionType.FOOTER }
        val insertIndex = if (footerIndex != -1) footerIndex else sections.size
        sections.add(insertIndex, SectionItem(SectionType.CUSTOM, newSection))
        notifyItemInserted(insertIndex)
    }

    fun removeCustomSection(position: Int) {
        if (position in sections.indices) {
            sections.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        val footerPos = sections.indexOfFirst { it.type == SectionType.FOOTER }
        if (footerPos != -1 && (fromPosition == footerPos || toPosition >= footerPos)) {
            return false
        }
        if (fromPosition !in sections.indices || toPosition !in sections.indices) {
            return false
        }
        val moved = sections.removeAt(fromPosition)
        sections.add(toPosition, moved)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun getItemViewType(position: Int): Int {
        return sections[position].type.ordinal
    }

    override fun getItemCount(): Int = sections.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (SectionType.entries[viewType]) {
            SectionType.PERSONAL_INFO -> {
                val binding = ItemPersonalInfoBinding.inflate(inflater, parent, false)
                PersonalInfoViewHolder(binding)
            }

            SectionType.ABOUT_SUMMARY -> {
                val binding = ItemAboutSummaryBinding.inflate(inflater, parent, false)
                AboutSummaryViewHolder(binding)
            }

            SectionType.WORK_EXPERIENCE -> {
                val binding = ItemWorkExperienceBinding.inflate(inflater, parent, false)
                WorkExperienceViewHolder(binding)
            }

            SectionType.PROJECTS -> {
                val binding = ItemProjectsBinding.inflate(inflater, parent, false)
                ProjectsViewHolder(binding)
            }

            SectionType.TECHNICAL_SKILLS -> {
                val binding = ItemTechnicalSkillsBinding.inflate(inflater, parent, false)
                TechnicalSkillsViewHolder(binding)
            }

            SectionType.EDUCATION -> {
                val binding = ItemEducationBinding.inflate(inflater, parent, false)
                EducationViewHolder(binding)
            }

            SectionType.CUSTOM -> {
                val binding = ItemCustomSectionBinding.inflate(inflater, parent, false)
                CustomSectionViewHolder(binding)
            }

            SectionType.FOOTER -> {
                val binding = ItemSectionFooterBinding.inflate(inflater, parent, false)
                FooterViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PersonalInfoViewHolder -> holder.bind()
            is AboutSummaryViewHolder -> holder.bind()
            is WorkExperienceViewHolder -> holder.bind()
            is ProjectsViewHolder -> holder.bind()
            is TechnicalSkillsViewHolder -> holder.bind()
            is EducationViewHolder -> holder.bind()
            is CustomSectionViewHolder -> holder.bind(sections[position])
            is FooterViewHolder -> holder.bind()
        }
    }

    // --- DIALOG HELPERS ---

    private fun createDialogContainer(context: Context): LinearLayout {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padHorizontal = (24 * density).toInt()
            val padTop = (16 * density).toInt()
            val padBottom = (4 * density).toInt()
            setPadding(padHorizontal, padTop, padHorizontal, padBottom)
        }
    }

    private fun createDialogTextInputLayout(
        context: Context,
        hint: String,
        startIconRes: Int? = null,
        isMultiline: Boolean = false,
        minLines: Int = 1
    ): Pair<TextInputLayout, TextInputEditText> {
        val density = context.resources.displayMetrics.density
        val til = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            this.hint = hint
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isErrorEnabled = true
            setBoxCornerRadii(12 * density, 12 * density, 12 * density, 12 * density)
            if (startIconRes != null) {
                setStartIconDrawable(startIconRes)
                setStartIconTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_primary)))
                startIconMinSize = (44 * density).toInt()
            }
        }
        val et = TextInputEditText(til.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(ContextCompat.getColor(context, R.color.app_text_primary))
            textSize = 14f
            if (startIconRes != null) {
                setPadding((46 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            }
            if (isMultiline) {
                this.minLines = minLines
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }
        til.addView(et)
        return Pair(til, et)
    }

    private fun createMaterialDialog(
        context: Context,
        title: String,
        message: CharSequence? = null,
        customView: View? = null,
        positiveText: String = context.getString(R.string.action_save),
        negativeText: String = context.getString(R.string.action_cancel),
        isDestructive: Boolean = false,
        onPositiveClick: ((AlertDialog) -> Unit)? = null
    ): AlertDialog {
        val builder = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(title)
            .setPositiveButton(positiveText, null)
            .setNegativeButton(negativeText) { d, _ -> d.dismiss() }

        if (message != null) {
            builder.setMessage(message)
        }
        if (customView != null) {
            builder.setView(customView)
        }

        val dialog = builder.create()

        val density = context.resources.displayMetrics.density
        val bgDrawable = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCornerSizes(20 * density)
                .build()
        ).apply {
            fillColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_surface))
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_border))
            strokeWidth = 1 * density
        }
        val insetH = (24 * density).toInt()
        val insetV = (24 * density).toInt()
        val windowBg = InsetDrawable(bgDrawable, insetH, insetV, insetH, insetV)
        dialog.window?.setBackgroundDrawable(windowBg)

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(windowBg)

            val titleView = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
            titleView?.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary))

            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            messageView?.setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
            messageView?.textSize = 14f

            val btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val posColorRes = if (isDestructive) R.color.app_danger else R.color.app_primary
            btnPos?.setTextColor(ContextCompat.getColor(context, posColorRes))
            btnPos?.typeface = android.graphics.Typeface.DEFAULT_BOLD
            if (onPositiveClick != null) {
                btnPos?.setOnClickListener { onPositiveClick(dialog) }
            }
            val btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            btnNeg?.setTextColor(ContextCompat.getColor(context, R.color.app_text_muted))
        }
        return dialog
    }

    private fun formatBoldMessage(context: Context, resId: Int, boldText: String): CharSequence {
        val encoded = android.text.TextUtils.htmlEncode(boldText)
        val formatted = context.getString(resId, encoded)
        return HtmlCompat.fromHtml(formatted, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun showConfirmationDialog(
        context: Context,
        title: String,
        message: CharSequence,
        positiveButtonText: String = context.getString(R.string.action_remove),
        onConfirm: () -> Unit
    ) {
        val dialog = createMaterialDialog(
            context = context,
            title = title,
            message = message,
            positiveText = positiveButtonText,
            negativeText = context.getString(R.string.action_cancel),
            isDestructive = true
        ) { d ->
            onConfirm()
            d.dismiss()
        }
        dialog.show()
    }

    // --- VIEW HOLDERS ---

    inner class PersonalInfoViewHolder(val binding: ItemPersonalInfoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
        fun bind() {
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }

            // Field labels
            binding.tilFullName.setRequiredHint(R.string.hint_full_name)
            binding.tilJobTitle.setRequiredHint(R.string.hint_job_title)
            binding.tilTargetCompany.setRequiredHint(R.string.hint_target_company)
            binding.tilPhone.setRequiredHint(R.string.hint_phone)
            binding.tilEmail.setRequiredHint(R.string.hint_email)
            binding.tilLocation.setRequiredHint(R.string.hint_location)

            binding.etFullName.setText(currentHero.fullName)
            binding.etJobTitle.setText(currentHero.headlineTitle)
            binding.etTargetCompany.setText(currentHero.targetCompany)
            binding.etPhone.setText(currentHero.contactInfo.phone)
            binding.etEmail.setText(currentHero.contactInfo.email)
            binding.etLocation.setText(currentHero.location)

            binding.etFullName.addTextChangedListener(SimpleTextWatcher {
                currentHero = currentHero.copy(fullName = it)
            })
            binding.etJobTitle.addTextChangedListener(SimpleTextWatcher {
                currentHero = currentHero.copy(headlineTitle = it)
            })
            binding.etTargetCompany.addTextChangedListener(SimpleTextWatcher {
                currentHero = currentHero.copy(targetCompany = it)
            })
            binding.etPhone.addTextChangedListener(SimpleTextWatcher {
                currentHero = currentHero.copy(
                    contactInfo = currentHero.contactInfo.copy(phone = it)
                )
            })
            binding.etEmail.addTextChangedListener(SimpleTextWatcher {
                currentHero = currentHero.copy(
                    contactInfo = currentHero.contactInfo.copy(email = it)
                )
            })
            binding.etLocation.addTextChangedListener(SimpleTextWatcher {
                currentHero = currentHero.copy(location = it)
            })

            binding.etLocation.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus()
                    val imm =
                        v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }

            refreshProfileLinks()

            binding.btnAddProfileLink.setOnClickListener {
                showAddLinkDialog()
            }
        }

        private fun refreshProfileLinks() {
            binding.llProfileLinks.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)

            profileLinks.forEachIndexed { index, link ->
                val linkBinding = ItemProfileLinkBinding.inflate(inflater, binding.llProfileLinks, false)
                linkBinding.tvProfileLinkUrl.text = link

                // Dynamic platform-aware icon
                when {
                    link.contains("github.com", ignoreCase = true) -> {
                        linkBinding.ivProfileLinkIcon.setImageResource(R.drawable.ic_github_14)
                    }
                    link.contains("linkedin.com", ignoreCase = true) -> {
                        linkBinding.ivProfileLinkIcon.setImageResource(R.drawable.ic_linkedin_14)
                    }
                    else -> {
                        linkBinding.ivProfileLinkIcon.setImageResource(R.drawable.ic_link)
                    }
                }

                // Tap-to-edit for profile links
                linkBinding.root.setOnClickListener {
                    showEditLinkDialog(index, link)
                }

                // Inline delete with undo Snackbar
                linkBinding.btnRemoveLink.setOnClickListener {
                    if (index in profileLinks.indices) {
                        val removedLink = profileLinks.removeAt(index)
                        refreshProfileLinks()
                        val context = binding.root.context
                        Snackbar.make(binding.root, context.getString(R.string.msg_profile_link_removed), Snackbar.LENGTH_LONG)
                            .setAction(context.getString(R.string.action_undo)) {
                                if (index in 0..profileLinks.size) {
                                    profileLinks.add(index, removedLink)
                                } else {
                                    profileLinks.add(removedLink)
                                }
                                refreshProfileLinks()
                            }
                            .show()
                    }
                }
                binding.llProfileLinks.addView(linkBinding.root)
            }
        }

        private fun showEditLinkDialog(index: Int, existingLink: String) {
            val context = binding.root.context
            val container = createDialogContainer(context)
            val (inputLayout, input) = createDialogTextInputLayout(
                context,
                hint = context.getString(R.string.hint_profile_link_url),
                startIconRes = R.drawable.ic_link_14
            )
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            input.setText(existingLink)
            input.setSelection(existingLink.length)
            container.addView(inputLayout)

            val dialog = createMaterialDialog(
                context = context,
                title = context.getString(R.string.dialog_title_edit_profile_link),
                customView = container,
                positiveText = context.getString(R.string.action_save)
            ) { d ->
                var text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank() && !text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
                    text = "https://$text"
                }
                if (text.isBlank() || !android.util.Patterns.WEB_URL.matcher(text).matches()) {
                    inputLayout.error = context.getString(R.string.error_invalid_url)
                } else {
                    inputLayout.error = null
                    if (index in profileLinks.indices) {
                        profileLinks[index] = text
                        refreshProfileLinks()
                    }
                    d.dismiss()
                }
            }
            input.addTextChangedListener(SimpleTextWatcher {
                inputLayout.error = null
            })
            dialog.show()
        }

        private fun showAddLinkDialog() {
            val context = binding.root.context
            val container = createDialogContainer(context)
            val (inputLayout, input) = createDialogTextInputLayout(
                context,
                hint = context.getString(R.string.hint_profile_link_url),
                startIconRes = R.drawable.ic_link_14
            )
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            container.addView(inputLayout)

            val dialog = createMaterialDialog(
                context = context,
                title = context.getString(R.string.dialog_title_add_profile_link),
                customView = container,
                positiveText = context.getString(R.string.action_add)
            ) { d ->
                var text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank() && !text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
                    text = "https://$text"
                }
                if (text.isBlank() || !android.util.Patterns.WEB_URL.matcher(text).matches()) {
                    inputLayout.error = context.getString(R.string.error_invalid_url)
                } else {
                    inputLayout.error = null
                    profileLinks.add(text)
                    refreshProfileLinks()
                    d.dismiss()
                }
            }
            input.addTextChangedListener(SimpleTextWatcher {
                inputLayout.error = null
            })
            dialog.show()
        }
    }

    inner class AboutSummaryViewHolder(val binding: ItemAboutSummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
        fun bind() {
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }

            binding.tilSummary.setRequiredHint(R.string.hint_profile_summary)
            if (binding.etSummary.text?.toString() != currentSummary) {
                binding.etSummary.setText(currentSummary)
            }

            // Allow internal vertical scrolling inside etSummary when nested in RecyclerView
            // and preserve default ArrowKeyMovementMethod for native long-press text selection
            binding.etSummary.setOnTouchListener { v, event ->
                if (v.id == binding.etSummary.id) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            if (v.canScrollVertically(-1) || v.canScrollVertically(1)) {
                                v.parent.requestDisallowInterceptTouchEvent(true)
                            }
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
                false
            }

            validateSummaryLength(currentSummary.length)

            binding.etSummary.addTextChangedListener(SimpleTextWatcher(trim = false) {
                currentSummary = it
                validateSummaryLength(it.length)
            })

            binding.etSummary.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus()
                    val imm =
                        v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }
        }

        private fun validateSummaryLength(length: Int) {
            val context = binding.root.context
            if (length > 600) {
                binding.tilSummary.error = context.getString(R.string.error_summary_limit)
            } else {
                binding.tilSummary.error = null
            }
        }
    }

    private fun cleanBulletLine(line: String): String {
        return line.trim()
            .replace(Regex("^[.•\\u2022\\u25cf\\u00b7\\u2043\\u25e6\\-*–—\\s]+"), "")
            .replace(Regex("^[0-9]+[.)\\-]\\s*"), "")
            .trim()
    }

    private fun createBulletView(
        context: Context,
        text: String,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ): View {
        val density = context.resources.displayMetrics.density

        // Root bullet row container
        val layout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (6 * density).toInt()
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.TOP
            clipChildren = false
            clipToPadding = false
            setBackgroundResource(R.drawable.bg_bullet_ripple)
            isClickable = true
            isFocusable = true
            // Provide 6dp top and 6dp bottom padding for balanced vertical touch target and clearance
            setPadding((6 * density).toInt(), (4 * density).toInt(), (2 * density).toInt(), (6 * density).toInt())
            setOnClickListener { onEdit() }
        }

        // Bullet indicator dot (•)
        val bulletPrefix = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP
                topMargin = (2 * density).toInt()
                rightMargin = (8 * density).toInt()
            }
            this.text = "•"
            setTextColor(ContextCompat.getColor(context, R.color.app_primary))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = false
        }
        layout.addView(bulletPrefix)

        // Bullet text content (with dedicated bottom padding to avoid descender clipping)
        val bulletText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = android.view.Gravity.TOP
            }
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.app_text_primary))
            textSize = 13f
            includeFontPadding = true
            setLineSpacing(3 * density, 1f)
            // 6dp bottom padding prevents StaticLayout canvas from clipping descenders ('g', 'y', 'p', 'q', 'j') on the last line
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        layout.addView(bulletText)

        // Delete button (X)
        val deleteBtn = ImageView(context).apply {
            val size36 = (36 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size36, size36).apply {
                gravity = android.view.Gravity.TOP
            }
            val borderless = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, borderless, true)
            setBackgroundResource(borderless.resourceId)
            contentDescription = context.getString(R.string.content_desc_remove_bullet)
            setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_text_muted))
            setOnClickListener { onDelete() }
        }
        layout.addView(deleteBtn)

        return layout
    }

    private fun showAddBulletDialog(
        context: Context,
        onAdd: (String) -> Unit
    ) {
        val container = createDialogContainer(context)
        val (til, et) = createDialogTextInputLayout(
            context,
            hint = context.getString(R.string.hint_bullet_description),
            startIconRes = R.drawable.ic_edit,
            isMultiline = true,
            minLines = 3
        )
        container.addView(til)

        val dialog = createMaterialDialog(
            context = context,
            title = context.getString(R.string.dialog_title_add_bullet),
            customView = container,
            positiveText = context.getString(R.string.action_add)
        ) { d ->
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) {
                onAdd(text)
                d.dismiss()
            } else {
                til.error = context.getString(R.string.error_domain_name_required)
            }
        }
        et.addTextChangedListener(SimpleTextWatcher { til.error = null })
        dialog.show()
    }

    private fun showEditBulletDialog(
        context: Context,
        existingText: String,
        onSave: (String) -> Unit
    ) {
        val container = createDialogContainer(context)
        val (til, et) = createDialogTextInputLayout(
            context,
            hint = context.getString(R.string.hint_bullet_description),
            startIconRes = R.drawable.ic_edit,
            isMultiline = true,
            minLines = 3
        )
        et.setText(existingText)
        et.setSelection(existingText.length)
        container.addView(til)

        val dialog = createMaterialDialog(
            context = context,
            title = context.getString(R.string.dialog_title_edit_bullet),
            customView = container,
            positiveText = context.getString(R.string.action_save)
        ) { d ->
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) {
                onSave(text)
                d.dismiss()
            } else {
                til.error = context.getString(R.string.error_domain_name_required)
            }
        }
        et.addTextChangedListener(SimpleTextWatcher { til.error = null })
        dialog.show()
    }

    inner class WorkExperienceViewHolder(val binding: ItemWorkExperienceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
        fun bind() {
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }

            updateWorkExperienceHeader()

            // Initialize toggle state
            binding.toggleCandidateType.clearOnButtonCheckedListeners()
            if (isExperienced) {
                binding.toggleCandidateType.check(R.id.btnExperienced)
                binding.containerWorkPositions.visibility = View.VISIBLE
                binding.btnAddWorkExperience.visibility = View.VISIBLE
            } else {
                binding.toggleCandidateType.check(R.id.btnFresher)
                binding.containerWorkPositions.visibility = View.GONE
                binding.btnAddWorkExperience.visibility = View.GONE
            }
            updateToggleStyles(binding)

            binding.toggleCandidateType.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val exp = (checkedId == R.id.btnExperienced)
                    isExperienced = exp
                    binding.containerWorkPositions.visibility = if (exp) View.VISIBLE else View.GONE
                    binding.btnAddWorkExperience.visibility = if (exp) View.VISIBLE else View.GONE
                    updateWorkExperienceHeader()
                    updateToggleStyles(binding)
                }
            }

            renderWorkExperienceEntries()

            binding.btnAddWorkExperience.setOnClickListener {
                val newPosition = WorkExperienceItem(
                    id = UUID.randomUUID().toString(),
                    companyName = "",
                    roleTitle = "",
                    startDate = "",
                    endDate = "",
                    bulletPoints = emptyList(),
                    techStackTags = emptyList(),
                    clientApps = emptyList()
                )
                currentExperiences.add(newPosition)
                addWorkExperienceEntryView(newPosition)
                updateWorkExperienceEntryHeaders()
            }
        }

        private fun updateWorkExperienceHeader() {
            val context = binding.root.context
            if (isExperienced) {
                binding.tvSectionTitle.text = context.getString(R.string.section_title_work_experience)
            } else {
                binding.tvSectionTitle.text = context.getString(R.string.section_title_work_experience_optional)
            }
        }

        private fun updateToggleStyles(binding: ItemWorkExperienceBinding) {
            val context = binding.root.context
            val primaryColor = ContextCompat.getColor(context, R.color.app_primary)
            val whiteColor = ContextCompat.getColor(context, android.R.color.white)
            val borderColor = ContextCompat.getColor(context, R.color.app_border)
            val mutedColor = ContextCompat.getColor(context, R.color.app_text_muted)
            val transparentColor = ContextCompat.getColor(context, android.R.color.transparent)

            if (isExperienced) {
                binding.btnExperienced.apply {
                    backgroundTintList = ColorStateList.valueOf(primaryColor)
                    setTextColor(whiteColor)
                    strokeColor = ColorStateList.valueOf(primaryColor)
                }
                binding.btnFresher.apply {
                    backgroundTintList = ColorStateList.valueOf(transparentColor)
                    setTextColor(mutedColor)
                    strokeColor = ColorStateList.valueOf(borderColor)
                }
            } else {
                binding.btnFresher.apply {
                    backgroundTintList = ColorStateList.valueOf(primaryColor)
                    setTextColor(whiteColor)
                    strokeColor = ColorStateList.valueOf(primaryColor)
                }
                binding.btnExperienced.apply {
                    backgroundTintList = ColorStateList.valueOf(transparentColor)
                    setTextColor(mutedColor)
                    strokeColor = ColorStateList.valueOf(borderColor)
                }
            }
        }

        private fun renderWorkExperienceEntries() {
            binding.llWorkExperienceEntries.removeAllViews()
            currentExperiences.forEach { expItem ->
                addWorkExperienceEntryView(expItem)
            }
            updateWorkExperienceEntryHeaders()
        }

        private fun updateWorkExperienceEntryHeaders() {
            val context = binding.root.context
            for (i in 0 until binding.llWorkExperienceEntries.childCount) {
                val child = binding.llWorkExperienceEntries.getChildAt(i)
                val tvHeader = child.findViewById<TextView>(R.id.tvEntryHeader)
                val divider = child.findViewById<View>(R.id.dividerEntry)
                tvHeader?.text = context.getString(R.string.position_header_format, i + 1)
                divider?.visibility = if (i > 0) View.VISIBLE else View.GONE
            }
        }

        private fun addWorkExperienceEntryView(expItem: WorkExperienceItem) {
            val inflater = LayoutInflater.from(binding.root.context)
            val entryBinding = ItemWorkExperienceEntryBinding.inflate(
                inflater,
                binding.llWorkExperienceEntries,
                false
            )
            entryBinding.root.tag = expItem.id
            binding.llWorkExperienceEntries.addView(entryBinding.root)
            val context = entryBinding.root.context

            fun getPos(): Int {
                val idx = binding.llWorkExperienceEntries.indexOfChild(entryBinding.root)
                if (idx >= 0) return idx
                return currentExperiences.indexOfFirst { it.id == expItem.id }
            }

            entryBinding.tilCompanyName.setRequiredHint(R.string.hint_company_name)
            entryBinding.tilCompanyDuration.setRequiredHint(R.string.hint_duration_example)
            entryBinding.tilRoleTitle.setRequiredHint(R.string.hint_role_title)

            entryBinding.etCompanyName.setText(expItem.companyName)
            entryBinding.etCompanyDuration.setText(
                if (expItem.startDate.isNotBlank()) {
                    context.getString(R.string.duration_range_format, expItem.startDate, expItem.endDate)
                } else {
                    expItem.endDate
                }
            )
            entryBinding.etRoleTitle.setText(expItem.roleTitle)

            entryBinding.etCompanyName.addTextChangedListener(SimpleTextWatcher {
                val pos = getPos()
                if (pos in currentExperiences.indices) {
                    currentExperiences[pos] = currentExperiences[pos].copy(companyName = it)
                }
            })

            entryBinding.etCompanyDuration.addTextChangedListener(SimpleTextWatcher {
                val pos = getPos()
                if (pos in currentExperiences.indices) {
                    val parts = it.split(Regex("[–\\-]|to"), 2).map { p -> p.trim() }
                    val sDate = parts.getOrNull(0) ?: it
                    val eDate = parts.getOrNull(1) ?: context.getString(R.string.duration_present)
                    currentExperiences[pos] = currentExperiences[pos].copy(startDate = sDate, endDate = eDate)
                }
            })

            entryBinding.etRoleTitle.addTextChangedListener(SimpleTextWatcher {
                val pos = getPos()
                if (pos in currentExperiences.indices) {
                    currentExperiences[pos] = currentExperiences[pos].copy(roleTitle = it)
                }
            })

            entryBinding.etCompanyDuration.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus()
                    val imm =
                        v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }

            entryBinding.tvRoleHeader.setRequiredHeader(R.string.header_role_responsibilities)

            fun renderClientApps() {
                entryBinding.cgClientApps.removeAllViews()
                val pos = getPos()
                val apps = if (pos in currentExperiences.indices) currentExperiences[pos].clientApps else expItem.clientApps

                apps.forEachIndexed { aIdx, appItem ->
                    val chip = Chip(context).apply {
                        text = appItem.name
                        isCloseIconVisible = true
                        closeIconContentDescription = context.getString(R.string.cd_remove_client_app)
                        setChipBackgroundColorResource(R.color.exp_tag_bg)
                        setTextColor(ContextCompat.getColor(context, R.color.exp_tag_text))
                        chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_border))
                        chipStrokeWidth = 1f * resources.displayMetrics.density
                        shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(8f * resources.displayMetrics.density)
                            .build()
                        if (appItem.url.isNotBlank()) {
                            setChipIconResource(R.drawable.ic_link_14)
                            setChipIconTintResource(R.color.app_primary)
                            isChipIconVisible = true
                        } else {
                            isChipIconVisible = false
                        }
                        setOnCloseIconClickListener {
                            val currentPos = getPos()
                            if (currentPos in currentExperiences.indices) {
                                val updatedList = currentExperiences[currentPos].clientApps.toMutableList()
                                if (aIdx in updatedList.indices) {
                                    updatedList.removeAt(aIdx)
                                    currentExperiences[currentPos] =
                                        currentExperiences[currentPos].copy(clientApps = updatedList)
                                    renderClientApps()
                                }
                            }
                        }
                        setOnClickListener {
                            showEditClientAppDialog(context, appItem) { updatedApp ->
                                val currentPos = getPos()
                                if (currentPos in currentExperiences.indices) {
                                    val updatedList = currentExperiences[currentPos].clientApps.toMutableList()
                                    if (aIdx in updatedList.indices) {
                                        updatedList[aIdx] = updatedApp
                                        currentExperiences[currentPos] =
                                            currentExperiences[currentPos].copy(clientApps = updatedList)
                                        renderClientApps()
                                    }
                                }
                            }
                        }
                    }
                    entryBinding.cgClientApps.addView(chip)
                }
            }
            renderClientApps()

            entryBinding.btnAddClientApp.setOnClickListener {
                showAddClientAppDialog(context) { newApp ->
                    val currentPos = getPos()
                    if (currentPos in currentExperiences.indices) {
                        val currentList = currentExperiences[currentPos].clientApps.toMutableList()
                        currentList.add(newApp)
                        currentExperiences[currentPos] = currentExperiences[currentPos].copy(clientApps = currentList)
                        renderClientApps()
                    }
                }
            }

            fun renderBullets() {
                entryBinding.llBulletsContainer.removeAllViews()
                val currentPos = getPos()
                val bullets = if (currentPos in currentExperiences.indices) currentExperiences[currentPos].bulletPoints else expItem.bulletPoints

                bullets.forEachIndexed { bIdx, bText ->
                    val bulletView = createBulletView(
                        context = entryBinding.root.context,
                        text = bText,
                        onEdit = {
                            showEditBulletDialog(entryBinding.root.context, bText) { newText ->
                                val posNow = getPos()
                                if (posNow in currentExperiences.indices) {
                                    val updatedList = currentExperiences[posNow].bulletPoints.toMutableList()
                                    if (bIdx in updatedList.indices) {
                                        updatedList[bIdx] = newText
                                        currentExperiences[posNow] =
                                            currentExperiences[posNow].copy(bulletPoints = updatedList)
                                        renderBullets()
                                    }
                                }
                            }
                        },
                        onDelete = {
                            val posNow = getPos()
                            if (posNow in currentExperiences.indices) {
                                val updatedList = currentExperiences[posNow].bulletPoints.toMutableList()
                                if (bIdx in updatedList.indices) {
                                    updatedList.removeAt(bIdx)
                                    currentExperiences[posNow] =
                                        currentExperiences[posNow].copy(bulletPoints = updatedList)
                                    renderBullets()
                                }
                            }
                        }
                    )

                    entryBinding.llBulletsContainer.addView(bulletView)
                }
            }
            renderBullets()

            // Add Bullet button
            entryBinding.btnAddBullet.setOnClickListener {
                val ctx = entryBinding.root.context
                showAddBulletDialog(ctx) { newBullet ->
                    val currentPos = getPos()
                    if (currentPos in currentExperiences.indices) {
                        val currentList = currentExperiences[currentPos].bulletPoints.toMutableList()
                        currentList.add(newBullet)
                        currentExperiences[currentPos] = currentExperiences[currentPos].copy(bulletPoints = currentList)
                        renderBullets()
                    }
                }
            }

            entryBinding.btnRemovePosition.setOnClickListener {
                val currentPos = getPos()
                if (currentPos in currentExperiences.indices) {
                    val exp = currentExperiences[currentPos]
                    val isBlank = exp.companyName.isBlank() && exp.roleTitle.isBlank() &&
                            exp.startDate.isBlank() && exp.endDate.isBlank() &&
                            exp.bulletPoints.isEmpty() && exp.clientApps.isEmpty()
                    val doRemove = {
                        val posToRemove = getPos()
                        if (posToRemove in currentExperiences.indices) {
                            currentExperiences.removeAt(posToRemove)
                            binding.llWorkExperienceEntries.removeViewAt(posToRemove)
                            updateWorkExperienceEntryHeaders()
                        }
                    }
                    if (isBlank) {
                        doRemove()
                    } else {
                        val ctx = entryBinding.root.context
                        val company = exp.companyName.trim()
                        val msg: CharSequence = if (company.isNotBlank()) {
                            formatBoldMessage(ctx, R.string.dialog_message_remove_position, company)
                        } else {
                            ctx.getString(R.string.dialog_title_remove_position_generic)
                        }
                        showConfirmationDialog(
                            context = ctx,
                            title = ctx.getString(R.string.dialog_title_remove_position),
                            message = msg,
                            onConfirm = doRemove
                        )
                    }
                }
            }
        }

        private fun showAddClientAppDialog(context: Context, onAdd: (ClientAppItem) -> Unit) {
            val container = createDialogContainer(context)
            val (tilName, etName) = createDialogTextInputLayout(
                context,
                hint = context.getString(R.string.hint_client_app_name),
                startIconRes = R.drawable.ic_work
            )
            etName.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS

            val (tilUrl, etUrl) = createDialogTextInputLayout(
                context,
                hint = context.getString(R.string.hint_client_app_url),
                startIconRes = R.drawable.ic_link_14
            )
            etUrl.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI

            val density = context.resources.displayMetrics.density
            (tilUrl.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = (10 * density).toInt()

            container.addView(tilName)
            container.addView(tilUrl)

            val dialog = createMaterialDialog(
                context = context,
                title = context.getString(R.string.dialog_title_add_client_app),
                customView = container,
                positiveText = context.getString(R.string.action_add)
            ) { d ->
                val name = etName.text?.toString()?.trim().orEmpty()
                var url = etUrl.text?.toString()?.trim().orEmpty()
                if (url.isNotBlank() && !url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                    url = "https://$url"
                }

                var hasError = false
                if (name.isBlank()) {
                    tilName.error = context.getString(R.string.error_app_name_required)
                    hasError = true
                } else {
                    tilName.error = null
                }

                if (url.isBlank()) {
                    tilUrl.error = context.getString(R.string.error_app_url_required)
                    hasError = true
                } else if (!android.util.Patterns.WEB_URL.matcher(url).matches()) {
                    tilUrl.error = context.getString(R.string.error_invalid_url)
                    hasError = true
                } else {
                    tilUrl.error = null
                }

                if (!hasError) {
                    onAdd(ClientAppItem(name = name, url = url))
                    d.dismiss()
                }
            }

            etName.addTextChangedListener(SimpleTextWatcher { tilName.error = null })
            etUrl.addTextChangedListener(SimpleTextWatcher { tilUrl.error = null })
            dialog.show()
        }

        private fun showEditClientAppDialog(context: Context, existing: ClientAppItem, onUpdate: (ClientAppItem) -> Unit) {
            val container = createDialogContainer(context)
            val (tilName, etName) = createDialogTextInputLayout(
                context,
                hint = context.getString(R.string.hint_client_app_name),
                startIconRes = R.drawable.ic_work
            )
            etName.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            etName.setText(existing.name)

            val (tilUrl, etUrl) = createDialogTextInputLayout(
                context,
                hint = context.getString(R.string.hint_client_app_url),
                startIconRes = R.drawable.ic_link_14
            )
            etUrl.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            etUrl.setText(existing.url)

            val density = context.resources.displayMetrics.density
            (tilUrl.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = (10 * density).toInt()

            container.addView(tilName)
            container.addView(tilUrl)

            val dialog = createMaterialDialog(
                context = context,
                title = context.getString(R.string.dialog_title_edit_client_app),
                customView = container,
                positiveText = context.getString(R.string.action_save)
            ) { d ->
                val name = etName.text?.toString()?.trim().orEmpty()
                var url = etUrl.text?.toString()?.trim().orEmpty()
                if (url.isNotBlank() && !url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                    url = "https://$url"
                }

                var hasError = false
                if (name.isBlank()) {
                    tilName.error = context.getString(R.string.error_app_name_required)
                    hasError = true
                } else {
                    tilName.error = null
                }

                if (url.isBlank()) {
                    tilUrl.error = context.getString(R.string.error_app_url_required)
                    hasError = true
                } else if (!android.util.Patterns.WEB_URL.matcher(url).matches()) {
                    tilUrl.error = context.getString(R.string.error_invalid_url)
                    hasError = true
                } else {
                    tilUrl.error = null
                }

                if (!hasError) {
                    onUpdate(existing.copy(name = name, url = url))
                    d.dismiss()
                }
            }

            etName.addTextChangedListener(SimpleTextWatcher { tilName.error = null })
            etUrl.addTextChangedListener(SimpleTextWatcher { tilUrl.error = null })
            dialog.show()
        }
    }

    inner class ProjectsViewHolder(val binding: ItemProjectsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
        fun bind() {
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }

            renderProjectEntries()

            binding.btnAddProject.setOnClickListener {
                val newProject = ProjectItem(
                    id = UUID.randomUUID().toString(),
                    projectTitle = "",
                    description = "",
                    techStackTags = emptyList(),
                    demoUrl = "",
                    codeUrl = ""
                )
                currentProjects.add(newProject)
                addProjectEntryView(newProject)
                updateProjectEntryHeaders()
            }
        }

        private fun renderProjectEntries() {
            binding.llProjectsEntries.removeAllViews()
            currentProjects.forEach { projItem ->
                addProjectEntryView(projItem)
            }
            updateProjectEntryHeaders()
        }

        private fun updateProjectEntryHeaders() {
            val context = binding.root.context
            for (i in 0 until binding.llProjectsEntries.childCount) {
                val child = binding.llProjectsEntries.getChildAt(i)
                val tvHeader = child.findViewById<TextView>(R.id.tvProjectHeader)
                val divider = child.findViewById<View>(R.id.dividerEntry)
                tvHeader?.text = context.getString(R.string.project_header_format, i + 1)
                divider?.visibility = if (i > 0) View.VISIBLE else View.GONE
            }
        }

        private fun addProjectEntryView(projItem: ProjectItem) {
            val inflater = LayoutInflater.from(binding.root.context)
            val entryBinding = ItemProjectEntryBinding.inflate(
                inflater,
                binding.llProjectsEntries,
                false
            )
            entryBinding.root.tag = projItem.id
            binding.llProjectsEntries.addView(entryBinding.root)
            val context = entryBinding.root.context

            fun getPos(): Int {
                val idx = binding.llProjectsEntries.indexOfChild(entryBinding.root)
                if (idx >= 0) return idx
                return currentProjects.indexOfFirst { it.id == projItem.id }
            }

            entryBinding.tilProjectTitle.setRequiredHint(R.string.project_title_name)
            entryBinding.tilProjectLink.setRequiredHint(R.string.repository_deployed_url)

            entryBinding.etProjectTitle.setText(projItem.projectTitle)
            val url = projItem.demoUrl.ifBlank { projItem.codeUrl }
            entryBinding.etProjectLink.setText(url)

            entryBinding.etProjectTitle.addTextChangedListener(SimpleTextWatcher {
                entryBinding.tilProjectTitle.error = null
                val pos = getPos()
                if (pos in currentProjects.indices) {
                    currentProjects[pos] = currentProjects[pos].copy(projectTitle = it)
                }
            })

            entryBinding.etProjectLink.addTextChangedListener(SimpleTextWatcher {
                entryBinding.tilProjectLink.error = null
                val pos = getPos()
                if (pos in currentProjects.indices) {
                    currentProjects[pos] = currentProjects[pos].copy(demoUrl = it, codeUrl = it)
                }
            })

            entryBinding.etProjectLink.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus()
                    val imm =
                        v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }

            // Tech Stack & Platform Tags (Option A)
            fun renderTechTags() {
                entryBinding.chipGroupProjectTech.removeAllViews()
                val pos = getPos()
                val tags = if (pos in currentProjects.indices) currentProjects[pos].techStackTags else projItem.techStackTags

                tags.forEachIndexed { tIdx, tag ->
                    val chip = Chip(context).apply {
                        text = tag
                        isCloseIconVisible = true
                        closeIconContentDescription = context.getString(R.string.cd_remove_tech_tag)
                        setChipBackgroundColorResource(R.color.project_tag_bg)
                        setTextColor(ContextCompat.getColor(context, R.color.project_tag_text))
                        chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_border))
                        chipStrokeWidth = 1f * resources.displayMetrics.density
                        shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(8f * resources.displayMetrics.density)
                            .build()
                        setOnCloseIconClickListener {
                            val currentPos = getPos()
                            if (currentPos in currentProjects.indices) {
                                val updatedList = currentProjects[currentPos].techStackTags.toMutableList()
                                if (tIdx in updatedList.indices) {
                                    updatedList.removeAt(tIdx)
                                    currentProjects[currentPos] =
                                        currentProjects[currentPos].copy(techStackTags = updatedList)
                                    renderTechTags()
                                }
                            }
                        }
                        setOnClickListener {
                            showEditTechTagDialog(context, tag) { updatedTag ->
                                val currentPos = getPos()
                                if (currentPos in currentProjects.indices) {
                                    val updatedList = currentProjects[currentPos].techStackTags.toMutableList()
                                    if (tIdx in updatedList.indices) {
                                        updatedList[tIdx] = updatedTag
                                        currentProjects[currentPos] =
                                            currentProjects[currentPos].copy(techStackTags = updatedList)
                                        renderTechTags()
                                    }
                                }
                            }
                        }
                    }
                    entryBinding.chipGroupProjectTech.addView(chip)
                }
            }
            renderTechTags()

            entryBinding.btnAddProjectTechTag.setOnClickListener {
                showAddTechTagDialog(context) { newTag ->
                    val currentPos = getPos()
                    if (currentPos in currentProjects.indices) {
                        val updatedList = currentProjects[currentPos].techStackTags.toMutableList()
                        updatedList.add(newTag)
                        currentProjects[currentPos] =
                            currentProjects[currentPos].copy(techStackTags = updatedList)
                        renderTechTags()
                    }
                }
            }

            entryBinding.tvProjectDetailsHeader.setRequiredHeader(R.string.key_highlights_features)

            fun getBulletList(): MutableList<String> {
                val pos = getPos()
                val desc = if (pos in currentProjects.indices) currentProjects[pos].description else projItem.description
                return desc.lines()
                    .map { cleanBulletLine(it) }
                    .filter { it.isNotBlank() }
                    .toMutableList()
            }

            fun renderProjectBullets() {
                entryBinding.llProjectBulletsContainer.removeAllViews()
                val bulletList = getBulletList()

                bulletList.forEachIndexed { bIdx, bText ->
                    val bulletView = createBulletView(
                        context = entryBinding.root.context,
                        text = bText,
                        onEdit = {
                            showEditBulletDialog(entryBinding.root.context, bText) { newText ->
                                val posNow = getPos()
                                if (posNow in currentProjects.indices) {
                                    val currentList = getBulletList()
                                    if (bIdx in currentList.indices) {
                                        currentList[bIdx] = newText
                                        currentProjects[posNow] = currentProjects[posNow].copy(
                                            description = currentList.joinToString("\n")
                                        )
                                        renderProjectBullets()
                                    }
                                }
                            }
                        },
                        onDelete = {
                            val posNow = getPos()
                            if (posNow in currentProjects.indices) {
                                val currentList = getBulletList()
                                if (bIdx in currentList.indices) {
                                    currentList.removeAt(bIdx)
                                    currentProjects[posNow] = currentProjects[posNow].copy(
                                        description = currentList.joinToString("\n")
                                    )
                                    renderProjectBullets()
                                }
                            }
                        }
                    )

                    entryBinding.llProjectBulletsContainer.addView(bulletView)
                }
            }
            renderProjectBullets()

            // Add Project Bullet
            entryBinding.btnAddProjectBullet.setOnClickListener {
                val ctx = entryBinding.root.context
                showAddBulletDialog(ctx) { newBullet ->
                    val posNow = getPos()
                    if (posNow in currentProjects.indices) {
                        val currentList = getBulletList()
                        currentList.add(newBullet)
                        currentProjects[posNow] = currentProjects[posNow].copy(
                            description = currentList.joinToString("\n")
                        )
                        renderProjectBullets()
                    }
                }
            }

            entryBinding.btnRemoveProject.setOnClickListener {
                val currentPos = getPos()
                if (currentPos in currentProjects.indices) {
                    val proj = currentProjects[currentPos]
                    val isBlank = proj.projectTitle.isBlank() && proj.description.isBlank() &&
                            proj.codeUrl.isBlank() && proj.demoUrl.isBlank() && proj.techStackTags.isEmpty()
                    val doRemove = {
                        val posToRemove = getPos()
                        if (posToRemove in currentProjects.indices) {
                            currentProjects.removeAt(posToRemove)
                            binding.llProjectsEntries.removeViewAt(posToRemove)
                            updateProjectEntryHeaders()
                        }
                    }
                    if (isBlank) {
                        doRemove()
                    } else {
                        val ctx = entryBinding.root.context
                        val title = proj.projectTitle.trim()
                        val msg: CharSequence = if (title.isNotBlank()) {
                            formatBoldMessage(ctx, R.string.dialog_message_remove_project, title)
                        } else {
                            ctx.getString(R.string.dialog_title_remove_project_generic)
                        }
                        showConfirmationDialog(
                            context = ctx,
                            title = ctx.getString(R.string.dialog_title_remove_project),
                            message = msg,
                            onConfirm = doRemove
                        )
                    }
                }
            }
        }

        private fun showAddTechTagDialog(context: Context, onAdd: (String) -> Unit) {
            val container = createDialogContainer(context)
            val (tilTag, etTag) = createDialogTextInputLayout(
                context,
                hint = context.getString(R.string.hint_tech_tag),
                startIconRes = R.drawable.ic_work
            )
            etTag.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            container.addView(tilTag)

            val dialog = createMaterialDialog(
                context = context,
                title = context.getString(R.string.dialog_title_add_tech_tag),
                customView = container,
                positiveText = context.getString(R.string.action_add)
            ) { d ->
                val text = etTag.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) {
                    tilTag.error = context.getString(R.string.error_tag_required)
                } else {
                    tilTag.error = null
                    onAdd(text)
                    d.dismiss()
                }
            }
            etTag.addTextChangedListener(SimpleTextWatcher { tilTag.error = null })
            dialog.show()
        }

        private fun showEditTechTagDialog(context: Context, initialTag: String, onUpdate: (String) -> Unit) {
            val container = createDialogContainer(context)
            val (tilTag, etTag) = createDialogTextInputLayout(
                context,
                hint = context.getString(R.string.hint_tech_tag),
                startIconRes = R.drawable.ic_work
            )
            etTag.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            etTag.setText(initialTag)
            etTag.setSelection(initialTag.length)
            container.addView(tilTag)

            val dialog = createMaterialDialog(
                context = context,
                title = context.getString(R.string.dialog_title_edit_tech_tag),
                customView = container,
                positiveText = context.getString(R.string.action_save)
            ) { d ->
                val text = etTag.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) {
                    tilTag.error = context.getString(R.string.error_tag_required)
                } else {
                    tilTag.error = null
                    onUpdate(text)
                    d.dismiss()
                }
            }
            etTag.addTextChangedListener(SimpleTextWatcher { tilTag.error = null })
            dialog.show()
        }
    }

    inner class TechnicalSkillsViewHolder(val binding: ItemTechnicalSkillsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind() {
            // Preserve drag-to-reorder functionality on ivDragHandle
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }

            val context = binding.root.context
            val rawTitle = currentSkills.sectionTitle.ifBlank { "Technical Skills" }
            binding.tvSectionTitle.text = if (rawTitle.equals("TECHNICAL SKILLS", ignoreCase = true)) {
                context.getString(R.string.section_title_technical_skills)
            } else {
                rawTitle
            }
            binding.tvSectionTitle.setOnClickListener {
                showRenameSectionDialog()
            }

            renderSemanticSkillsDomains()

            // Header "+ Add Category" button to add a new domain or skill category
            binding.btnAddSkill.setOnClickListener {
                showAddOrEditDomainDialog(null, emptyList())
            }
        }

        private fun showRenameSectionDialog() {
            val context = binding.root.context
            val container = createDialogContainer(context)
            val (til, et) = createDialogTextInputLayout(
                context,
                hint = context.getString(R.string.hint_section_title),
                startIconRes = R.drawable.ic_edit
            )
            et.setText(binding.tvSectionTitle.text)
            et.setSelection(et.text?.length ?: 0)
            container.addView(til)

            val dialog = createMaterialDialog(
                context = context,
                title = context.getString(R.string.dialog_title_rename_section),
                customView = container,
                positiveText = context.getString(R.string.action_save)
            ) { d ->
                val newTitle = et.text?.toString()?.trim().orEmpty()
                if (newTitle.isNotBlank()) {
                    currentSkills = currentSkills.copy(sectionTitle = newTitle)
                    binding.tvSectionTitle.text = newTitle
                    d.dismiss()
                } else {
                    til.error = context.getString(R.string.error_domain_name_required)
                }
            }
            et.addTextChangedListener(SimpleTextWatcher { til.error = null })
            dialog.show()
        }

        /**
         * Normalizes and renders the semantic domain matrix:
         *
         * CASE 1: CATEGORIZED SKILLS (Domain Label + Inline Skills Stream)
         * TECHNICAL SKILLS
         * Mobile Development    Flutter  ·  Android SDK  ·  Swift  ·  Kotlin
         * Frameworks & Core     Jetpack Compose  ·  Bloc  ·  Provider  ·  Coroutines
         * Cloud & Database      Firebase  ·  Cloud Firestore  ·  SQLite  ·  REST APIs
         * Tools & Practices     Git  ·  CI/CD (GitHub Actions)  ·  MVVM
         *
         * CASE 2: UNCATEGORIZED / FLAT LIST (Bullets, Zero Random Domain Labels)
         * TECHNICAL SKILLS
         * • Flutter      • Android SDK   • Kotlin        • Swift
         * • Jetpack Compose              • Firebase      • Git
         */
        private fun renderSemanticSkillsDomains() {
            val container = binding.containerSkillsDomains
            container.removeAllViews()
            val context = binding.root.context

            // Ensure section header matches resume
            val rawTitle = currentSkills.sectionTitle.ifBlank { "Technical Skills" }
            binding.tvSectionTitle.text = if (rawTitle.equals("TECHNICAL SKILLS", ignoreCase = true)) {
                context.getString(R.string.section_title_technical_skills)
            } else {
                rawTitle
            }

            // Extract categorized domain map from parsed resume data
            val categorizedMap = linkedMapOf<String, List<String>>()
            if (currentSkills.categorizedDomains.isNotEmpty()) {
                currentSkills.categorizedDomains.forEach { (cat, skills) ->
                    if (skills.isNotEmpty()) categorizedMap[cat] = skills
                }
            } else if (currentSkills.rawFlatSkills.isEmpty()) {
                // Only if categorizedDomains AND rawFlatSkills are empty, check legacy standard lists
                val standardEntries = listOf(
                    context.getString(R.string.skills_domain_languages) to currentSkills.languages,
                    context.getString(R.string.skills_domain_frameworks) to currentSkills.frameworks,
                    context.getString(R.string.skills_domain_cloud) to currentSkills.cloudDevOps,
                    context.getString(R.string.skills_domain_tools) to currentSkills.toolsMethodologies
                ).filter { it.second.isNotEmpty() }

                if (standardEntries.size > 1) {
                    standardEntries.forEach { (k, v) -> categorizedMap[k] = v }
                }
                currentSkills.customCategories.forEach { (k, v) ->
                    if (v.isNotEmpty()) categorizedMap[k] = v
                }
            }

            // Case 1 vs Case 2 check
            val isCase1Categorized = categorizedMap.isNotEmpty()

            if (isCase1Categorized) {
                binding.btnAddSkill.text = context.getString(R.string.action_add_domain)
                binding.btnAddSkill.setOnClickListener {
                    showAddOrEditDomainDialog(null, emptyList())
                }

                // =========================================================================
                // CASE 1: CATEGORIZED SKILLS (Domain Label + Inline Skills Stream)
                // Use exact domain names from the resume (e.g. Mobile Development, Networking)
                // =========================================================================
                val domainEntries = categorizedMap.entries.toList()
                val inflater = LayoutInflater.from(context)

                domainEntries.forEachIndexed { index, (domainLabel, skillsList) ->
                    val rowBinding = ItemSkillsDomainEntryBinding.inflate(inflater, container, false)
                    rowBinding.tvDomainTitle.visibility = View.VISIBLE
                    rowBinding.tvDomainTitle.text = domainLabel

                    // Clean midpoint-dot ('·') text stream
                    rowBinding.tvDomainSkillsContent.text = skillsList.joinToString("  ·  ")

                    val openEdit = {
                        showAddOrEditDomainDialog(domainLabel, skillsList)
                    }
                    rowBinding.btnEditDomain.setOnClickListener { openEdit() }

                    rowBinding.btnDeleteDomain.setOnClickListener {
                        val message = formatBoldMessage(context, R.string.dialog_message_remove_skill_domain, domainLabel)
                        showConfirmationDialog(
                            context = context,
                            title = context.getString(R.string.dialog_title_remove_skill_domain),
                            message = message,
                            onConfirm = {
                                deleteDomain(domainLabel)
                            }
                        )
                    }

                    // Bottom divider between domain rows (hidden on last item)
                    rowBinding.dividerDomain.visibility = if (index == domainEntries.lastIndex) View.GONE else View.VISIBLE
                    container.addView(rowBinding.itemSkillsDomainRoot)
                }
            } else {
                binding.btnAddSkill.text = "+ Add Skill"
                binding.btnAddSkill.setOnClickListener {
                    showAddSingleSkillDialog()
                }

                // =========================================================================
                // CASE 2: UNCATEGORIZED / FLAT LIST
                // Render direct bullet items with "X" to delete and tap to edit.
                // =========================================================================
                val flatSkillsList = when {
                    currentSkills.rawFlatSkills.isNotEmpty() -> currentSkills.rawFlatSkills
                    currentSkills.frameworks.isNotEmpty() -> currentSkills.frameworks
                    currentSkills.languages.isNotEmpty() -> currentSkills.languages
                    else -> buildList {
                        addAll(currentSkills.languages)
                        addAll(currentSkills.frameworks)
                        addAll(currentSkills.cloudDevOps)
                        addAll(currentSkills.toolsMethodologies)
                    }
                }.distinct().filter { it.isNotBlank() }

                if (flatSkillsList.isEmpty()) {
                    val emptyTv = TextView(context).apply {
                        text = context.getString(R.string.skills_empty_state)
                        setTextColor(ContextCompat.getColor(context, R.color.app_text_muted))
                        textSize = 13f
                        val pad = (8 * resources.displayMetrics.density).toInt()
                        setPadding(0, pad, 0, pad)
                    }
                    container.addView(emptyTv)
                    return
                }

                flatSkillsList.forEachIndexed { sIdx, skillText ->
                    val skillBulletView = createBulletView(
                        context = context,
                        text = skillText,
                        onEdit = {
                            showEditSingleSkillDialog(sIdx, skillText)
                        },
                        onDelete = {
                            val currentFlat = (if (currentSkills.rawFlatSkills.isNotEmpty()) currentSkills.rawFlatSkills else flatSkillsList).toMutableList()
                            if (sIdx in currentFlat.indices) {
                                currentFlat.removeAt(sIdx)
                                currentSkills = currentSkills.copy(
                                    rawFlatSkills = currentFlat,
                                    categorizedDomains = emptyMap()
                                )
                                renderSemanticSkillsDomains()
                            }
                        }
                    )
                    container.addView(skillBulletView)
                }
            }
        }

        private fun showAddSingleSkillDialog() {
            val context = binding.root.context
            val container = createDialogContainer(context)
            val (til, et) = createDialogTextInputLayout(
                context,
                hint = "Skill Name (or comma-separated)",
                startIconRes = R.drawable.ic_work
            )
            container.addView(til)

            val dialog = createMaterialDialog(
                context = context,
                title = "Add Skill",
                customView = container,
                positiveText = context.getString(R.string.action_save)
            ) { d ->
                val text = et.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    val newSkills = text.split(",").map { it.trim().removePrefix("•").removePrefix("-").trim() }.filter { it.isNotBlank() }
                    val currentFlat = (if (currentSkills.rawFlatSkills.isNotEmpty()) currentSkills.rawFlatSkills else currentSkills.frameworks + currentSkills.languages).toMutableList()
                    currentFlat.addAll(newSkills)
                    currentSkills = currentSkills.copy(
                        rawFlatSkills = currentFlat.distinct(),
                        categorizedDomains = emptyMap()
                    )
                    renderSemanticSkillsDomains()
                    d.dismiss()
                } else {
                    til.error = context.getString(R.string.error_skills_required)
                }
            }
            et.addTextChangedListener(SimpleTextWatcher { til.error = null })
            dialog.show()
        }

        private fun showEditSingleSkillDialog(index: Int, oldSkill: String) {
            val context = binding.root.context
            val container = createDialogContainer(context)
            val (til, et) = createDialogTextInputLayout(
                context,
                hint = "Skill Name",
                startIconRes = R.drawable.ic_work
            )
            et.setText(oldSkill)
            et.setSelection(oldSkill.length)
            container.addView(til)

            val dialog = createMaterialDialog(
                context = context,
                title = "Edit Skill",
                customView = container,
                positiveText = context.getString(R.string.action_save)
            ) { d ->
                val text = et.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    val currentFlat = (if (currentSkills.rawFlatSkills.isNotEmpty()) currentSkills.rawFlatSkills else currentSkills.frameworks + currentSkills.languages).toMutableList()
                    if (index in currentFlat.indices) {
                        currentFlat[index] = text
                        currentSkills = currentSkills.copy(
                            rawFlatSkills = currentFlat,
                            categorizedDomains = emptyMap()
                        )
                        renderSemanticSkillsDomains()
                    }
                    d.dismiss()
                } else {
                    til.error = context.getString(R.string.error_skills_required)
                }
            }
            et.addTextChangedListener(SimpleTextWatcher { til.error = null })
            dialog.show()
        }

        /**
         * Opens rapid editing / adding dialog for comma-separated skills.
         * Allows quick pasting and editing of skills without closing individual chips.
         */
        private fun showAddOrEditDomainDialog(existingDomain: String?, existingSkills: List<String>) {
            val context = binding.root.context
            val dialogBinding = DialogEditSkillsDomainBinding.inflate(LayoutInflater.from(context))

            val isEditing = existingSkills.isNotEmpty()
            if (existingDomain != null) {
                dialogBinding.etDialogDomainName.setText(existingDomain)
            }
            if (existingSkills.isNotEmpty()) {
                dialogBinding.etDialogSkillsComma.setText(existingSkills.joinToString(", "))
            }

            dialogBinding.etDialogDomainName.addTextChangedListener(SimpleTextWatcher {
                dialogBinding.tilDialogDomainName.error = null
            })
            dialogBinding.etDialogSkillsComma.addTextChangedListener(SimpleTextWatcher {
                dialogBinding.tilDialogSkillsComma.error = null
            })

            val dialogTitle = if (existingDomain != null) {
                context.getString(R.string.dialog_title_edit_skills_domain, existingDomain)
            } else if (existingSkills.isNotEmpty()) {
                context.getString(R.string.dialog_title_edit_flat_skills)
            } else {
                context.getString(R.string.dialog_title_add_skills_domain)
            }

            val dialog = createMaterialDialog(
                context = context,
                title = dialogTitle,
                customView = dialogBinding.dialogEditSkillsRoot,
                positiveText = context.getString(R.string.action_save)
            ) { d ->
                val domainName = dialogBinding.etDialogDomainName.text?.toString()?.trim().orEmpty()
                val rawSkills = dialogBinding.etDialogSkillsComma.text?.toString().orEmpty()

                // Parse comma, bullet, or newline separated skills
                val parsedSkills = rawSkills.split(Regex("[,\\r\\n|•·]"))
                    .map { it.trim().removePrefix("•").removePrefix("-").trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                val isCategorizedContext = currentSkills.categorizedDomains.isNotEmpty() || existingDomain != null
                var hasError = false

                if (isCategorizedContext || domainName.isNotBlank()) {
                    if (domainName.isBlank()) {
                        dialogBinding.tilDialogDomainName.error = context.getString(R.string.error_domain_name_required)
                        hasError = true
                    }
                }

                if (parsedSkills.isEmpty()) {
                    dialogBinding.tilDialogSkillsComma.error = context.getString(R.string.error_skills_required)
                    hasError = true
                }

                if (hasError) return@createMaterialDialog

                if (isCategorizedContext || domainName.isNotBlank()) {
                    val finalDomainName = domainName.ifBlank { existingDomain ?: "General" }
                    updateSkills(existingDomain, finalDomainName, parsedSkills)
                } else {
                    currentSkills = currentSkills.copy(
                        rawFlatSkills = parsedSkills,
                        categorizedDomains = emptyMap()
                    )
                }
                renderSemanticSkillsDomains()
                d.dismiss()
            }

            dialog.show()
        }

        /**
         * Updates the underlying SkillCategories data structure.
         * Preserves the exact category domain names and keeps backward-compatible fields in sync.
         */
        private fun updateSkills(oldDomain: String?, newDomain: String, skills: List<String>) {
            val updatedMap = LinkedHashMap(currentSkills.categorizedDomains)
            if (oldDomain != null && oldDomain != newDomain) {
                updatedMap.remove(oldDomain)
            }
            updatedMap[newDomain] = skills

            // Also synchronize standard backward-compatible lists
            val languages = mutableListOf<String>()
            val frameworks = mutableListOf<String>()
            val cloudDevOps = mutableListOf<String>()
            val toolsMethodologies = mutableListOf<String>()

            updatedMap.forEach { (cat, list) ->
                val lower = cat.lowercase()
                when {
                    lower.contains("language") || lower.contains("programming") -> languages.addAll(list)
                    lower.contains("framework") || lower.contains("mobile") || lower.contains("architecture") || lower.contains("state") || lower.contains("core") -> frameworks.addAll(list)
                    lower.contains("cloud") || lower.contains("devops") || lower.contains("database") || lower.contains("storage") -> cloudDevOps.addAll(list)
                    else -> toolsMethodologies.addAll(list)
                }
            }

            currentSkills = currentSkills.copy(
                categorizedDomains = updatedMap,
                rawFlatSkills = emptyList(), // Converted to categorized
                languages = languages.distinct(),
                frameworks = frameworks.distinct(),
                cloudDevOps = cloudDevOps.distinct(),
                toolsMethodologies = toolsMethodologies.distinct()
            )
        }

        /**
         * Deletes a category and its associated skills.
         */
        private fun deleteDomain(domainName: String) {
            val updatedMap = LinkedHashMap(currentSkills.categorizedDomains)
            updatedMap.remove(domainName)

            val languages = mutableListOf<String>()
            val frameworks = mutableListOf<String>()
            val cloudDevOps = mutableListOf<String>()
            val toolsMethodologies = mutableListOf<String>()

            updatedMap.forEach { (cat, list) ->
                val lower = cat.lowercase()
                when {
                    lower.contains("language") || lower.contains("programming") -> languages.addAll(list)
                    lower.contains("framework") || lower.contains("mobile") || lower.contains("architecture") || lower.contains("state") || lower.contains("core") -> frameworks.addAll(list)
                    lower.contains("cloud") || lower.contains("devops") || lower.contains("database") || lower.contains("storage") -> cloudDevOps.addAll(list)
                    else -> toolsMethodologies.addAll(list)
                }
            }

            currentSkills = currentSkills.copy(
                categorizedDomains = updatedMap,
                languages = languages.distinct(),
                frameworks = frameworks.distinct(),
                cloudDevOps = cloudDevOps.distinct(),
                toolsMethodologies = toolsMethodologies.distinct()
            )
            renderSemanticSkillsDomains()
        }
    }

    inner class EducationViewHolder(val binding: ItemEducationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
        fun bind() {
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }

            renderEducationEntries()

            binding.btnAddEducation.setOnClickListener {
                val newEdu = EducationItem(
                    institution = "",
                    degree = "",
                    graduationYear = ""
                )
                currentEducation.add(newEdu)
                addEducationEntryView(newEdu)
                updateEducationEntryHeaders()
            }
        }

        private fun renderEducationEntries() {
            binding.llEducationEntries.removeAllViews()
            currentEducation.forEach { eduItem ->
                addEducationEntryView(eduItem)
            }
            updateEducationEntryHeaders()
        }

        private fun updateEducationEntryHeaders() {
            val context = binding.root.context
            for (i in 0 until binding.llEducationEntries.childCount) {
                val child = binding.llEducationEntries.getChildAt(i)
                val tvHeader = child.findViewById<TextView>(R.id.tvEducationHeader)
                val divider = child.findViewById<View>(R.id.dividerEntry)
                tvHeader?.text = context.getString(R.string.education_header_format, i + 1)
                divider?.visibility = if (i > 0) View.VISIBLE else View.GONE
            }
        }

        private fun addEducationEntryView(eduItem: EducationItem) {
            val inflater = LayoutInflater.from(binding.root.context)
            val entryBinding = ItemEducationEntryBinding.inflate(
                inflater,
                binding.llEducationEntries,
                false
            )
            binding.llEducationEntries.addView(entryBinding.root)

            fun getPos(): Int {
                val idx = binding.llEducationEntries.indexOfChild(entryBinding.root)
                if (idx >= 0) return idx
                return currentEducation.indexOf(eduItem)
            }

            entryBinding.tilInstitution.setRequiredHint(R.string.hint_institution_name)
            entryBinding.tilDegree.setRequiredHint(R.string.hint_degree)
            entryBinding.tilDuration.setRequiredHint(R.string.hint_duration_example)

            entryBinding.etInstitution.setText(eduItem.institution)
            entryBinding.etDegree.setText(eduItem.degree)
            entryBinding.etDuration.setText(eduItem.graduationYear)

            entryBinding.etInstitution.addTextChangedListener(SimpleTextWatcher {
                entryBinding.tilInstitution.error = null
                val pos = getPos()
                if (pos in currentEducation.indices) {
                    currentEducation[pos] = currentEducation[pos].copy(institution = it)
                }
            })
            entryBinding.etDegree.addTextChangedListener(SimpleTextWatcher {
                entryBinding.tilDegree.error = null
                val pos = getPos()
                if (pos in currentEducation.indices) {
                    currentEducation[pos] = currentEducation[pos].copy(degree = it)
                }
            })
            entryBinding.etDuration.addTextChangedListener(SimpleTextWatcher {
                entryBinding.tilDuration.error = null
                val pos = getPos()
                if (pos in currentEducation.indices) {
                    currentEducation[pos] = currentEducation[pos].copy(graduationYear = it)
                }
            })

            entryBinding.etDuration.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus()
                    val imm =
                        v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }

            entryBinding.btnRemoveEducation.setOnClickListener {
                val currentPos = getPos()
                if (currentPos in currentEducation.indices) {
                    val edu = currentEducation[currentPos]
                    val isBlank = edu.institution.isBlank() && edu.degree.isBlank() && edu.graduationYear.isBlank()
                    val doRemove = {
                        val posToRemove = getPos()
                        if (posToRemove in currentEducation.indices) {
                            currentEducation.removeAt(posToRemove)
                            binding.llEducationEntries.removeViewAt(posToRemove)
                            updateEducationEntryHeaders()
                        }
                    }
                    if (isBlank) {
                        doRemove()
                    } else {
                        val ctx = entryBinding.root.context
                        val label = edu.degree.ifBlank { edu.institution }.trim()
                        val msg: CharSequence = if (label.isNotBlank()) {
                            formatBoldMessage(ctx, R.string.dialog_message_remove_education, label)
                        } else {
                            ctx.getString(R.string.dialog_title_remove_education_generic)
                        }
                        showConfirmationDialog(
                            context = ctx,
                            title = ctx.getString(R.string.dialog_title_remove_education),
                            message = msg,
                            onConfirm = doRemove
                        )
                    }
                }
            }
        }
    }

    inner class CustomSectionViewHolder(val binding: ItemCustomSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
        fun bind(sectionItem: SectionItem) {
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }

            val sec = sectionItem.customSection ?: return
            binding.tvSectionTitle.text = sec.sectionTitle

            val openRenameDialog = {
                val context = binding.root.context
                val currentSec = sectionItem.customSection
                if (currentSec != null) {
                    val container = createDialogContainer(context)
                    val (til, et) = createDialogTextInputLayout(
                        context,
                        hint = context.getString(R.string.hint_section_title),
                        startIconRes = R.drawable.ic_edit
                    )
                    et.setText(currentSec.sectionTitle)
                    et.setSelection(currentSec.sectionTitle.length)
                    container.addView(til)

                    val dialog = createMaterialDialog(
                        context = context,
                        title = context.getString(R.string.dialog_title_rename_section),
                        customView = container,
                        positiveText = context.getString(R.string.action_save)
                    ) { d ->
                        val newTitle = et.text?.toString()?.trim().orEmpty()
                        if (newTitle.isNotBlank()) {
                            val updatedSec = currentSec.copy(sectionTitle = newTitle)
                            sectionItem.customSection = updatedSec
                            binding.tvSectionTitle.text = newTitle
                            bind(sectionItem)
                            d.dismiss()
                        } else {
                            til.error = context.getString(R.string.error_domain_name_required)
                        }
                    }
                    et.addTextChangedListener(SimpleTextWatcher { til.error = null })
                    dialog.show()
                }
            }

            binding.btnEditSection.setOnClickListener { openRenameDialog() }

            val context = binding.root.context
            val archetype = resolveCustomSectionArchetype(sec.sectionTitle)
            val singularType = when (archetype) {
                CustomSectionArchetype.CERTIFICATIONS -> context.getString(R.string.custom_type_certificate)
                CustomSectionArchetype.ACHIEVEMENTS -> context.getString(R.string.custom_type_achievement)
                CustomSectionArchetype.PUBLICATIONS -> context.getString(R.string.custom_type_publication)
                CustomSectionArchetype.VOLUNTEER -> context.getString(R.string.custom_type_volunteer)
                CustomSectionArchetype.AWARDS -> context.getString(R.string.custom_type_award)
                CustomSectionArchetype.OPEN_SOURCE -> context.getString(R.string.custom_type_source)
                CustomSectionArchetype.LANGUAGES -> context.getString(R.string.custom_type_language)
                CustomSectionArchetype.HOBBIES -> "Hobby"
                CustomSectionArchetype.GENERIC -> context.getString(R.string.custom_type_item)
            }

            if (archetype == CustomSectionArchetype.LANGUAGES) {
                binding.btnAddCustomItem.text = context.getString(R.string.action_add_language)
                binding.btnAddCustomItem.visibility = View.VISIBLE
            } else if (archetype == CustomSectionArchetype.HOBBIES) {
                binding.btnAddCustomItem.visibility = View.GONE
            } else {
                binding.btnAddCustomItem.text = context.getString(R.string.add_new_item_format, singularType)
                binding.btnAddCustomItem.visibility = View.VISIBLE
            }

            // Circular minus button click removes optional section with confirmation
            binding.btnRemoveSection.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val secTitle = sectionItem.customSection?.sectionTitle ?: binding.tvSectionTitle.text?.toString().orEmpty()
                    val ctx = binding.root.context
                    val message = formatBoldMessage(ctx, R.string.dialog_message_remove_section, secTitle)
                    showConfirmationDialog(
                        context = ctx,
                        title = ctx.getString(R.string.dialog_title_remove_section),
                        message = message,
                        onConfirm = {
                            val currentPos = bindingAdapterPosition
                            if (currentPos != RecyclerView.NO_POSITION) {
                                removeCustomSection(currentPos)
                            }
                        }
                    )
                }
            }

            renderCustomItems(sectionItem, singularType, archetype)

            binding.btnAddCustomItem.setOnClickListener {
                val currentSec = sectionItem.customSection ?: return@setOnClickListener
                val newItem = DynamicItem(
                    title = "",
                    subtitle = "",
                    dateOrPeriod = "",
                    description = "",
                    link = "",
                    bulletPoints = emptyList()
                )
                val updatedItems = currentSec.items.toMutableList()
                updatedItems.add(newItem)
                sectionItem.customSection = currentSec.copy(items = updatedItems)
                addCustomItemView(sectionItem, newItem, singularType, archetype)
                updateCustomItemHeaders(archetype, singularType)
            }
        }

        private fun updateCustomItemHeaders(archetype: CustomSectionArchetype, resolvedSingularType: String) {
            val context = binding.root.context
            for (i in 0 until binding.llCustomItems.childCount) {
                val child = binding.llCustomItems.getChildAt(i)
                val tvHeader = child.findViewById<TextView>(R.id.tvCustomItemHeader)
                val btnRemove = child.findViewById<TextView>(R.id.btnRemoveCustomItem)
                val divider = child.findViewById<View>(R.id.dividerEntry)
                if (archetype == CustomSectionArchetype.HOBBIES) {
                    tvHeader?.visibility = View.GONE
                    btnRemove?.visibility = View.GONE
                    divider?.visibility = View.GONE
                } else {
                    tvHeader?.visibility = View.VISIBLE
                    btnRemove?.visibility = View.VISIBLE
                    tvHeader?.text = context.getString(R.string.custom_item_header_format, resolvedSingularType, i + 1)
                    btnRemove?.text = context.getString(R.string.remove_item_format, resolvedSingularType)
                    divider?.visibility = if (i > 0) View.VISIBLE else View.GONE
                }
            }
        }

        private fun renderCustomItems(
            sectionItem: SectionItem,
            singularType: String? = null,
            archetype: CustomSectionArchetype
        ) {
            val section = sectionItem.customSection ?: return
            val context = binding.root.context

            if (archetype == CustomSectionArchetype.HOBBIES) {
                binding.btnAddCustomItem.visibility = View.GONE
                if (section.items.isEmpty()) {
                    val defaultItem = DynamicItem(
                        title = "Hobbies",
                        subtitle = "",
                        dateOrPeriod = "",
                        description = "",
                        link = "",
                        bulletPoints = emptyList()
                    )
                    sectionItem.customSection = section.copy(items = listOf(defaultItem))
                }
            } else if (archetype == CustomSectionArchetype.LANGUAGES) {
                binding.btnAddCustomItem.visibility = View.VISIBLE
                binding.btnAddCustomItem.text = context.getString(R.string.action_add_language)
                // If items contains 1 generic "Languages" container with bullets, unroll into individual items
                val unrolled = mutableListOf<DynamicItem>()
                for (it in section.items) {
                    if ((it.title.equals("Languages", ignoreCase = true) || it.title.isBlank()) && it.bulletPoints.isNotEmpty()) {
                        for (bp in it.bulletPoints) {
                            if (bp.isNotBlank()) unrolled.add(DynamicItem(title = bp))
                        }
                    } else if (it.title.isNotBlank() && !it.title.equals("Languages", ignoreCase = true)) {
                        unrolled.add(it)
                    } else if (it.description.isNotBlank()) {
                        val parsed = it.description.split("\n").map { l -> l.trim() }.filter { l -> l.isNotBlank() }
                        for (l in parsed) {
                            unrolled.add(DynamicItem(title = l))
                        }
                    }
                }
                if (unrolled.isEmpty()) {
                    unrolled.add(DynamicItem(title = ""))
                }
                sectionItem.customSection = section.copy(items = unrolled)
            } else {
                binding.btnAddCustomItem.visibility = View.VISIBLE
                if (section.items.isEmpty()) {
                    sectionItem.customSection = section.copy(
                        items = listOf(
                            DynamicItem(
                                title = "",
                                subtitle = "",
                                dateOrPeriod = "",
                                description = "",
                                link = "",
                                bulletPoints = emptyList()
                            )
                        )
                    )
                }
            }

            val curSection = sectionItem.customSection ?: section
            val resolvedSingularType = singularType ?: context.getString(R.string.custom_type_item)
            binding.llCustomItems.removeAllViews()
            curSection.items.forEach { item ->
                addCustomItemView(sectionItem, item, resolvedSingularType, archetype)
            }
            if (archetype != CustomSectionArchetype.HOBBIES) {
                updateCustomItemHeaders(archetype, resolvedSingularType)
            }
        }

        private fun addCustomItemView(
            sectionItem: SectionItem,
            item: DynamicItem,
            resolvedSingularType: String,
            archetype: CustomSectionArchetype
        ) {
            val context = binding.root.context
            val inflater = LayoutInflater.from(context)
            val entryBinding = ItemCustomEntryBinding.inflate(inflater, binding.llCustomItems, false)
            binding.llCustomItems.addView(entryBinding.root)

            fun getPos(): Int {
                val idx = binding.llCustomItems.indexOfChild(entryBinding.root)
                if (idx >= 0) return idx
                val cur = sectionItem.customSection ?: return -1
                return cur.items.indexOf(item)
            }

            val section = sectionItem.customSection ?: return

            // Handle Hobbies special single input view
            if (archetype == CustomSectionArchetype.HOBBIES) {
                entryBinding.dividerEntry.visibility = View.GONE
                entryBinding.tvCustomItemHeader.visibility = View.GONE
                entryBinding.btnRemoveCustomItem.visibility = View.GONE
                entryBinding.tilCustomSubtitle.visibility = View.GONE
                entryBinding.tilCustomPeriod.visibility = View.GONE
                entryBinding.tilCustomLink.visibility = View.GONE
                entryBinding.layoutCustomBulletsSection.visibility = View.GONE

                entryBinding.tilCustomTitle.visibility = View.VISIBLE
                entryBinding.tilCustomTitle.hint = "Hobbies (comma separated)"
                entryBinding.tilCustomTitle.helperText = "e.g. Painting, Flying, Kite Surfing"
                entryBinding.tilCustomTitle.isErrorEnabled = false

                val hobbiesInitial = if (item.description.isNotBlank()) item.description else item.bulletPoints.joinToString(", ")
                entryBinding.etCustomTitle.setText(hobbiesInitial)

                entryBinding.etCustomTitle.addTextChangedListener(SimpleTextWatcher { newText ->
                    val cur = sectionItem.customSection ?: return@SimpleTextWatcher
                    val pos = getPos()
                    if (pos in cur.items.indices) {
                        val tokens = newText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val updatedItems = cur.items.toMutableList()
                        updatedItems[pos] = updatedItems[pos].copy(
                            title = "Hobbies",
                            description = newText,
                            bulletPoints = tokens
                        )
                        sectionItem.customSection = cur.copy(items = updatedItems)
                    }
                })
                return
            }

            // Divider visibility
            entryBinding.dividerEntry.visibility = if (getPos() > 0) View.VISIBLE else View.GONE

            // Configure fields by archetype
            when (archetype) {
                CustomSectionArchetype.CERTIFICATIONS -> {
                    // Title: Certificate Name *
                    entryBinding.tilCustomTitle.visibility = View.VISIBLE
                    val titleLabel = if (section.sectionTitle.contains("Course", ignoreCase = true)) "Course Name" else context.getString(R.string.hint_custom_title_cert)
                    entryBinding.tilCustomTitle.setRequiredHint(titleLabel)

                    // Subtitle: Issuing Organization *
                    entryBinding.tilCustomSubtitle.visibility = View.VISIBLE
                    entryBinding.tilCustomSubtitle.setRequiredHint(R.string.hint_custom_subtitle_issuer)

                    // Period: DO NOT ask Issue/Validity Date per user requirement
                    entryBinding.tilCustomPeriod.visibility = View.GONE

                    // Link: Credential URL (Optional)
                    entryBinding.tilCustomLink.visibility = View.VISIBLE
                    entryBinding.tilCustomLink.hint = context.getString(R.string.hint_link_certification)

                    // Bullets: Key Learnings / Highlights (Optional)
                    entryBinding.layoutCustomBulletsSection.visibility = View.VISIBLE
                    entryBinding.tvCustomDetailsHeader.text = context.getString(R.string.header_custom_details_cert)
                }

                CustomSectionArchetype.ACHIEVEMENTS -> {
                    // Title: Achievement Headline *
                    entryBinding.tilCustomTitle.visibility = View.VISIBLE
                    entryBinding.tilCustomTitle.setRequiredHint(R.string.hint_custom_title_achieve)

                    // Subtitle: Context / Organization (Optional)
                    entryBinding.tilCustomSubtitle.visibility = View.VISIBLE
                    entryBinding.tilCustomSubtitle.hint = context.getString(R.string.hint_custom_subtitle_achieve_ctx)

                    // Period: Year / Date (Optional)
                    entryBinding.tilCustomPeriod.visibility = View.VISIBLE
                    entryBinding.tilCustomPeriod.hint = context.getString(R.string.hint_custom_period_date)

                    // Link: Proof / Article Link (Optional)
                    entryBinding.tilCustomLink.visibility = View.VISIBLE
                    entryBinding.tilCustomLink.hint = context.getString(R.string.hint_link_achievement)

                    // Bullets: Details & Impact (Optional)
                    entryBinding.layoutCustomBulletsSection.visibility = View.VISIBLE
                    entryBinding.tvCustomDetailsHeader.text = context.getString(R.string.header_custom_details_achieve)
                }

                CustomSectionArchetype.PUBLICATIONS -> {
                    // Title: Paper / Article Title *
                    entryBinding.tilCustomTitle.visibility = View.VISIBLE
                    entryBinding.tilCustomTitle.setRequiredHint(R.string.hint_custom_title_pub)

                    // Subtitle: DO NOT ask Journal / Conference per user requirement
                    entryBinding.tilCustomSubtitle.visibility = View.GONE

                    // Period: Publication Date / Year (Optional)
                    entryBinding.tilCustomPeriod.visibility = View.VISIBLE
                    entryBinding.tilCustomPeriod.hint = context.getString(R.string.hint_custom_period_pub)

                    // Link: DOI / Paper URL (Optional)
                    entryBinding.tilCustomLink.visibility = View.VISIBLE
                    entryBinding.tilCustomLink.hint = context.getString(R.string.hint_link_publication)

                    // Bullets: Summary / Findings (Optional)
                    entryBinding.layoutCustomBulletsSection.visibility = View.VISIBLE
                    entryBinding.tvCustomDetailsHeader.text = context.getString(R.string.header_custom_details_pub)
                }

                CustomSectionArchetype.VOLUNTEER -> {
                    // Title: Role / Title *
                    entryBinding.tilCustomTitle.visibility = View.VISIBLE
                    entryBinding.tilCustomTitle.setRequiredHint(R.string.hint_custom_title_vol_role)

                    // Subtitle: Organization / Cause *
                    entryBinding.tilCustomSubtitle.visibility = View.VISIBLE
                    entryBinding.tilCustomSubtitle.setRequiredHint(R.string.hint_custom_subtitle_vol_org)

                    // Period: Dates of Service (Optional)
                    entryBinding.tilCustomPeriod.visibility = View.VISIBLE
                    entryBinding.tilCustomPeriod.hint = context.getString(R.string.hint_custom_period_vol)

                    // Link: DO NOT ask Organization URL per user requirement
                    entryBinding.tilCustomLink.visibility = View.GONE

                    // Bullets: Impact & Duties (Optional)
                    entryBinding.layoutCustomBulletsSection.visibility = View.VISIBLE
                    entryBinding.tvCustomDetailsHeader.text = context.getString(R.string.header_custom_details_vol)
                }

                CustomSectionArchetype.AWARDS -> {
                    // Title: Award Name *
                    entryBinding.tilCustomTitle.visibility = View.VISIBLE
                    entryBinding.tilCustomTitle.setRequiredHint(R.string.hint_custom_title_award)

                    // Subtitle: Granting Organization / Issuer *
                    entryBinding.tilCustomSubtitle.visibility = View.VISIBLE
                    entryBinding.tilCustomSubtitle.setRequiredHint(R.string.hint_custom_subtitle_award_issuer)

                    // Period: Year / Date (Optional)
                    entryBinding.tilCustomPeriod.visibility = View.VISIBLE
                    entryBinding.tilCustomPeriod.hint = context.getString(R.string.hint_custom_period_date)

                    // Link: Verification / Recognition Link (Optional)
                    entryBinding.tilCustomLink.visibility = View.VISIBLE
                    entryBinding.tilCustomLink.hint = context.getString(R.string.hint_link_award)

                    // Bullets: Scope & Details (Optional)
                    entryBinding.layoutCustomBulletsSection.visibility = View.VISIBLE
                    entryBinding.tvCustomDetailsHeader.text = context.getString(R.string.header_custom_details_award)
                }

                CustomSectionArchetype.OPEN_SOURCE -> {
                    // Title: Package / Repository Name *
                    entryBinding.tilCustomTitle.visibility = View.VISIBLE
                    entryBinding.tilCustomTitle.setRequiredHint(R.string.hint_custom_title_oss)

                    // Subtitle: Role / Subtitle (Optional)
                    entryBinding.tilCustomSubtitle.visibility = View.VISIBLE
                    entryBinding.tilCustomSubtitle.hint = context.getString(R.string.hint_custom_subtitle_oss_role)

                    // Period: DO NOT ask Status / Period per user requirement
                    entryBinding.tilCustomPeriod.visibility = View.GONE

                    // Link: Package / Repository Link (Optional)
                    entryBinding.tilCustomLink.visibility = View.VISIBLE
                    entryBinding.tilCustomLink.hint = context.getString(R.string.hint_link_open_source)

                    // Bullets: Highlights & Contributions (Optional)
                    entryBinding.layoutCustomBulletsSection.visibility = View.VISIBLE
                    entryBinding.tvCustomDetailsHeader.text = context.getString(R.string.header_custom_details_oss)
                }

                CustomSectionArchetype.LANGUAGES -> {
                    // Option A: Single input per row for Language & Proficiency
                    entryBinding.tilCustomTitle.visibility = View.VISIBLE
                    entryBinding.tilCustomTitle.setRequiredHint(R.string.hint_custom_title_lang)

                    entryBinding.tilCustomSubtitle.visibility = View.GONE
                    entryBinding.tilCustomPeriod.visibility = View.GONE
                    entryBinding.tilCustomLink.visibility = View.GONE
                    entryBinding.layoutCustomBulletsSection.visibility = View.GONE
                }

                CustomSectionArchetype.GENERIC -> {
                    // Generic default
                    entryBinding.tilCustomTitle.visibility = View.VISIBLE
                    entryBinding.tilCustomTitle.setRequiredHint(R.string.hint_custom_title)

                    entryBinding.tilCustomSubtitle.visibility = View.VISIBLE
                    entryBinding.tilCustomSubtitle.hint = context.getString(R.string.hint_custom_subtitle)

                    entryBinding.tilCustomPeriod.visibility = View.VISIBLE
                    entryBinding.tilCustomPeriod.hint = context.getString(R.string.hint_custom_period)

                    entryBinding.tilCustomLink.visibility = View.VISIBLE
                    entryBinding.tilCustomLink.hint = context.getString(R.string.hint_link_general)

                    entryBinding.layoutCustomBulletsSection.visibility = View.VISIBLE
                    entryBinding.tvCustomDetailsHeader.text = context.getString(R.string.header_custom_details)
                }
            }

            // Populate initial values
            entryBinding.etCustomTitle.setText(item.title)
            entryBinding.etCustomSubtitle.setText(item.subtitle)
            entryBinding.etCustomPeriod.setText(item.dateOrPeriod)
            entryBinding.etCustomLink.setText(item.link)

            // Watchers for Title, Subtitle, Period
            entryBinding.etCustomTitle.addTextChangedListener(SimpleTextWatcher { newTitle ->
                entryBinding.tilCustomTitle.error = null
                val cur = sectionItem.customSection ?: return@SimpleTextWatcher
                val pos = getPos()
                if (pos in cur.items.indices) {
                    val updatedItems = cur.items.toMutableList()
                    updatedItems[pos] = updatedItems[pos].copy(title = newTitle)
                    sectionItem.customSection = cur.copy(items = updatedItems)
                }
            })

            entryBinding.etCustomSubtitle.addTextChangedListener(SimpleTextWatcher { newSubtitle ->
                entryBinding.tilCustomSubtitle.error = null
                val cur = sectionItem.customSection ?: return@SimpleTextWatcher
                val pos = getPos()
                if (pos in cur.items.indices) {
                    val updatedItems = cur.items.toMutableList()
                    updatedItems[pos] = updatedItems[pos].copy(subtitle = newSubtitle)
                    sectionItem.customSection = cur.copy(items = updatedItems)
                }
            })

            entryBinding.etCustomPeriod.addTextChangedListener(SimpleTextWatcher { newPeriod ->
                entryBinding.tilCustomPeriod.error = null
                val cur = sectionItem.customSection ?: return@SimpleTextWatcher
                val pos = getPos()
                if (pos in cur.items.indices) {
                    val updatedItems = cur.items.toMutableList()
                    updatedItems[pos] = updatedItems[pos].copy(dateOrPeriod = newPeriod)
                    sectionItem.customSection = cur.copy(items = updatedItems)
                }
            })

            // Link text watcher and blur formatting
            entryBinding.etCustomLink.addTextChangedListener(SimpleTextWatcher(trim = false) { rawLink ->
                val cur = sectionItem.customSection ?: return@SimpleTextWatcher
                val pos = getPos()
                if (pos in cur.items.indices) {
                    val trimmed = rawLink.trim()
                    val normalized = if (trimmed.isNotEmpty() && !trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                        "https://$trimmed"
                    } else {
                        trimmed
                    }

                    if (normalized.isNotEmpty() && !android.util.Patterns.WEB_URL.matcher(normalized).matches()) {
                        entryBinding.tilCustomLink.error = context.getString(R.string.error_invalid_url)
                    } else {
                        entryBinding.tilCustomLink.error = null
                        entryBinding.tilCustomLink.isErrorEnabled = false
                    }

                    val updatedItems = cur.items.toMutableList()
                    updatedItems[pos] = updatedItems[pos].copy(link = normalized)
                    sectionItem.customSection = cur.copy(items = updatedItems)
                }
            })

            entryBinding.etCustomLink.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val cur = sectionItem.customSection ?: return@setOnFocusChangeListener
                    val pos = getPos()
                    if (pos in cur.items.indices) {
                        val savedLink = cur.items[pos].link
                        if (entryBinding.etCustomLink.text?.toString() != savedLink) {
                            entryBinding.etCustomLink.setText(savedLink)
                        }
                    }
                }
            }

            entryBinding.etCustomLink.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus()
                    val imm =
                        v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }

            // Bullet points
            val bullets = if (item.bulletPoints.isNotEmpty()) {
                item.bulletPoints.toMutableList()
            } else {
                item.description.split(Regex("[\\r\\n]+"))
                    .map { it.replace(Regex("^[•\\-*]\\s*"), "").trim() }
                    .filter { it.isNotBlank() }
                    .toMutableList()
            }

            fun renderCustomBullets() {
                entryBinding.llCustomBulletsContainer.removeAllViews()
                bullets.forEachIndexed { bIdx, bText ->
                    val bulletView = createBulletView(
                        context = entryBinding.root.context,
                        text = bText,
                        onEdit = {
                            showEditBulletDialog(entryBinding.root.context, bText) { newText ->
                                if (bIdx in bullets.indices) {
                                    bullets[bIdx] = newText
                                    val cur = sectionItem.customSection
                                    val pos = getPos()
                                    if (cur != null && pos in cur.items.indices) {
                                        val updatedItems = cur.items.toMutableList()
                                        updatedItems[pos] = updatedItems[pos].copy(
                                            bulletPoints = bullets,
                                            description = bullets.joinToString("\n")
                                        )
                                        sectionItem.customSection = cur.copy(items = updatedItems)
                                    }
                                    renderCustomBullets()
                                }
                            }
                        },
                        onDelete = {
                            if (bIdx in bullets.indices) {
                                bullets.removeAt(bIdx)
                                val cur = sectionItem.customSection
                                val pos = getPos()
                                if (cur != null && pos in cur.items.indices) {
                                    val updatedItems = cur.items.toMutableList()
                                    updatedItems[pos] = updatedItems[pos].copy(
                                        bulletPoints = bullets,
                                        description = bullets.joinToString("\n")
                                    )
                                    sectionItem.customSection = cur.copy(items = updatedItems)
                                }
                                renderCustomBullets()
                            }
                        }
                    )
                    entryBinding.llCustomBulletsContainer.addView(bulletView)
                }
            }

            if (entryBinding.layoutCustomBulletsSection.visibility == View.VISIBLE) {
                renderCustomBullets()

                entryBinding.btnAddCustomBullet.setOnClickListener {
                    val ctx = entryBinding.root.context
                    showAddBulletDialog(ctx) { newBullet ->
                        bullets.add(newBullet)
                        val cur = sectionItem.customSection
                        val pos = getPos()
                        if (cur != null && pos in cur.items.indices) {
                            val updatedItems = cur.items.toMutableList()
                            updatedItems[pos] = updatedItems[pos].copy(
                                bulletPoints = bullets,
                                description = bullets.joinToString("\n")
                            )
                            sectionItem.customSection = cur.copy(items = updatedItems)
                        }
                        renderCustomBullets()
                    }
                }
            }

            entryBinding.btnRemoveCustomItem.setOnClickListener {
                val cur = sectionItem.customSection ?: return@setOnClickListener
                val pos = getPos()
                if (pos in cur.items.indices) {
                    val curItem = cur.items[pos]
                    val isBlank = curItem.title.isBlank() && curItem.subtitle.isBlank() &&
                            curItem.link.isBlank() && curItem.bulletPoints.isEmpty() &&
                            curItem.dateOrPeriod.isBlank()
                    val doRemove = {
                        val posToRemove = getPos()
                        val currentSec = sectionItem.customSection
                        if (currentSec != null && posToRemove in currentSec.items.indices) {
                            val updatedItems = currentSec.items.toMutableList()
                            updatedItems.removeAt(posToRemove)
                            val updatedSec = currentSec.copy(items = updatedItems)
                            sectionItem.customSection = updatedSec
                            binding.llCustomItems.removeViewAt(posToRemove)
                            updateCustomItemHeaders(archetype, resolvedSingularType)
                        }
                    }
                    if (isBlank) {
                        doRemove()
                    } else {
                        val ctx = entryBinding.root.context
                        val title = curItem.title.trim()
                        val msg: CharSequence = if (title.isNotBlank()) {
                            formatBoldMessage(ctx, R.string.dialog_message_remove_custom_item, title)
                        } else {
                            ctx.getString(R.string.dialog_title_remove_custom_item_generic)
                        }
                        showConfirmationDialog(
                            context = ctx,
                            title = ctx.getString(R.string.dialog_title_remove_custom_item, resolvedSingularType),
                            message = msg,
                            onConfirm = doRemove
                        )
                    }
                }
            }
        }
    }

    inner class FooterViewHolder(val binding: ItemSectionFooterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.btnAddCustomSection.setOnClickListener {
                onAddCustomSectionClicked()
            }
        }
    }

    /**
     * Compiles and returns the verified ResumeProfile with all user edits.
     * When rightAdapter is provided (dual-pane tablet mode), foundations & credentials
     * (Personal Info, Summary, Skills, Education) are taken from this adapter (left),
     * and career & portfolio (Work Experience, Projects, Dynamic Custom Sections) are
     * taken from rightAdapter.
     */
    fun compileProfile(rightAdapter: StructureSectionsAdapter? = null): ResumeProfile {
        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim()
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

        val targetLeft = this
        val targetRight = rightAdapter ?: this

        val normalizedProfileLinks = targetLeft.profileLinks.filter { it.isNotBlank() }.map { normalizeUrl(it) }
        val linkedIn = normalizedProfileLinks.firstOrNull { it.contains("linkedin", ignoreCase = true) }
            ?: normalizedProfileLinks.getOrNull(0) ?: ""
        val github = normalizedProfileLinks.firstOrNull { it.contains("github", ignoreCase = true) }
            ?: normalizedProfileLinks.getOrNull(1) ?: ""
        val website = normalizedProfileLinks.firstOrNull {
            !it.contains("linkedin", ignoreCase = true) && !it.contains("github", ignoreCase = true)
        } ?: ""

        val extraLinks = normalizedProfileLinks.filter { it != linkedIn && it != github && it != website }

        val finalHero = targetLeft.currentHero.copy(
            contactInfo = targetLeft.currentHero.contactInfo.copy(
                linkedinUrl = linkedIn,
                githubUrl = github,
                websiteUrl = website,
                customLinks = extraLinks
            )
        )

        val finalExperiences = if (targetRight.isExperienced) {
            targetRight.currentExperiences.filter { it.companyName.isNotBlank() }
        } else {
            emptyList()
        }

        val finalProjects = targetRight.currentProjects.filter { it.projectTitle.isNotBlank() }.map { proj ->
            val u = proj.demoUrl.ifBlank { proj.codeUrl }
            val normalized = normalizeUrl(u)
            proj.copy(demoUrl = normalized, codeUrl = normalized)
        }

        val allDynamicSections = if (rightAdapter != null) {
            this.currentDynamicSections + rightAdapter.currentDynamicSections
        } else {
            this.currentDynamicSections
        }

        val finalDynamicSections = allDynamicSections
            .filter { sec -> sec.items.any { it.title.isNotBlank() || it.bulletPoints.isNotEmpty() || it.description.isNotBlank() } }
            .map { sec ->
                sec.copy(
                    items = sec.items.filter { it.title.isNotBlank() || it.bulletPoints.isNotEmpty() || it.description.isNotBlank() }.map { item ->
                        val fixedTitle = if (item.title.isNotBlank()) {
                            item.title
                        } else if (sec.sectionTitle.contains("Hobbi", ignoreCase = true) || sec.sectionTitle.contains("Interest", ignoreCase = true)) {
                            "Hobbies"
                        } else if (sec.sectionTitle.contains("Language", ignoreCase = true)) {
                            "Languages"
                        } else {
                            sec.sectionTitle
                        }
                        val itemWithTitle = item.copy(title = fixedTitle)
                        if (itemWithTitle.link.isNotBlank()) itemWithTitle.copy(link = normalizeUrl(itemWithTitle.link)) else itemWithTitle
                    }
                )
            }

        val allSectionsForOrder = if (rightAdapter != null && rightAdapter !== this) {
            sections.filter { it.type != SectionType.FOOTER } + rightAdapter.sections.filter { it.type != SectionType.FOOTER }
        } else {
            sections.filter { it.type != SectionType.FOOTER }
        }

        val compiledOrder = allSectionsForOrder.mapNotNull { item ->
            when (item.type) {
                SectionType.PERSONAL_INFO -> ResumeSectionConstants.SECTION_PERSONAL
                SectionType.ABOUT_SUMMARY -> ResumeSectionConstants.SECTION_SUMMARY
                SectionType.WORK_EXPERIENCE -> ResumeSectionConstants.SECTION_EXPERIENCE
                SectionType.PROJECTS -> ResumeSectionConstants.SECTION_PROJECTS
                SectionType.TECHNICAL_SKILLS -> ResumeSectionConstants.SECTION_SKILLS
                SectionType.EDUCATION -> ResumeSectionConstants.SECTION_EDUCATION
                SectionType.CUSTOM -> item.customSection?.let { "custom_${it.sectionTitle}" }
                else -> null
            }
        }

        return ResumeProfile(
            hero = finalHero,
            coreSections = CoreSections(
                profileSummary = targetLeft.currentSummary,
                workExperience = finalExperiences,
                projects = finalProjects,
                skills = targetLeft.currentSkills,
                education = targetLeft.currentEducation.filter { it.institution.isNotBlank() }
            ),
            dynamicSections = finalDynamicSections,
            sectionOrder = compiledOrder
        )
    }
}

class SimpleTextWatcher(
    private val trim: Boolean = true,
    private val afterChanged: (String) -> Unit
) : TextWatcher {
    constructor(afterChanged: (String) -> Unit) : this(trim = true, afterChanged = afterChanged)

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {
        val str = s?.toString() ?: ""
        afterChanged(if (trim) str.trim() else str)
    }
}
