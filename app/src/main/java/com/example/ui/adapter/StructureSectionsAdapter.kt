package com.example.ui.adapter

import android.annotation.SuppressLint
import android.app.AlertDialog
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
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import androidx.core.graphics.toColorInt

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
        if (profile.hero.contactInfo.linkedinUrl.isNotBlank()) profileLinks.add(profile.hero.contactInfo.linkedinUrl)
        if (profile.hero.contactInfo.githubUrl.isNotBlank()) profileLinks.add(profile.hero.contactInfo.githubUrl)
        if (profileLinks.isEmpty()) {
            profileLinks.add("https://github.com/candidate")
        }

        buildSectionsList()
    }

    private fun buildSectionsList() {
        sections.clear()
        when (paneTarget) {
            PaneTarget.ALL -> {
                sections.add(SectionItem(SectionType.PERSONAL_INFO))
                sections.add(SectionItem(SectionType.ABOUT_SUMMARY))
                sections.add(SectionItem(SectionType.WORK_EXPERIENCE))
                sections.add(SectionItem(SectionType.PROJECTS))
                sections.add(SectionItem(SectionType.TECHNICAL_SKILLS))
                sections.add(SectionItem(SectionType.EDUCATION))
                profile.dynamicSections.forEach { dynamic ->
                    sections.add(SectionItem(SectionType.CUSTOM, dynamic.copy()))
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
            val inputLayout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    val margin = (20 * resources.displayMetrics.density).toInt()
                    setMargins(margin, (12 * resources.displayMetrics.density).toInt(), margin, 0)
                }
                hint = context.getString(R.string.hint_profile_link_url)
                isErrorEnabled = true
            }
            val input = TextInputEditText(inputLayout.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setText(existingLink)
                setSelection(existingLink.length)
            }
            inputLayout.addView(input)

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(inputLayout)
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_title_edit_profile_link))
                .setView(container)
                .setPositiveButton(context.getString(R.string.action_save), null)
                .setNegativeButton(context.getString(R.string.action_cancel)) { d, _ -> d.dismiss() }
                .create()

            dialog.show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
                    dialog.dismiss()
                }
            }
            input.addTextChangedListener(SimpleTextWatcher {
                inputLayout.error = null
            })
        }

        private fun showAddLinkDialog() {
            val context = binding.root.context
            val inputLayout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    val margin = (20 * resources.displayMetrics.density).toInt()
                    setMargins(margin, (12 * resources.displayMetrics.density).toInt(), margin, 0)
                }
                hint = context.getString(R.string.hint_profile_link_url)
                isErrorEnabled = true
            }
            val input = TextInputEditText(inputLayout.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            inputLayout.addView(input)

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(inputLayout)
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_title_add_profile_link))
                .setView(container)
                .setPositiveButton(context.getString(R.string.action_add), null)
                .setNegativeButton(context.getString(R.string.action_cancel)) { d, _ -> d.dismiss() }
                .create()

            dialog.show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
                    dialog.dismiss()
                }
            }
            input.addTextChangedListener(SimpleTextWatcher {
                inputLayout.error = null
            })
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
            binding.etSummary.setText(currentSummary)

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

            updateCounter(currentSummary.length)

            binding.etSummary.addTextChangedListener(SimpleTextWatcher(trim = false) {
                currentSummary = it
                updateCounter(it.length)
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

        private fun updateCounter(length: Int) {
            val context = binding.root.context
            binding.tvCharCounter.text = context.getString(R.string.char_counter_format, length, 600)
            if (length > 600) {
                binding.tvCharCounter.setTextColor("#EF4444".toColorInt())
                binding.tilSummary.error = context.getString(R.string.error_summary_limit)
            } else {
                binding.tvCharCounter.setTextColor("#94A3B8".toColorInt())
                binding.tilSummary.error = null
                binding.tilSummary.isErrorEnabled = false
            }
        }
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
        val bulletInput = EditText(context).apply {
            hint = context.getString(R.string.hint_bullet_description)
            inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialog_title_add_bullet))
            .setView(bulletInput)
            .setPositiveButton(context.getString(R.string.action_add)) { d, _ ->
                val text = bulletInput.text.toString().trim()
                if (text.isNotBlank()) {
                    onAdd(text)
                }
                d.dismiss()
            }
            .setNegativeButton(context.getString(R.string.action_cancel)) { d, _ -> d.dismiss() }
            .show()
    }

    private fun showEditBulletDialog(
        context: Context,
        existingText: String,
        onSave: (String) -> Unit
    ) {
        val bulletInput = EditText(context).apply {
            hint = context.getString(R.string.hint_bullet_description)
            inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            setText(existingText)
            setSelection(existingText.length)
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialog_title_edit_bullet))
            .setView(bulletInput)
            .setPositiveButton(context.getString(R.string.action_save)) { d, _ ->
                val text = bulletInput.text.toString().trim()
                if (text.isNotBlank()) {
                    onSave(text)
                }
                d.dismiss()
            }
            .setNegativeButton(context.getString(R.string.action_cancel)) { d, _ -> d.dismiss() }
            .show()
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
            } else {
                binding.toggleCandidateType.check(R.id.btnFresher)
                binding.containerWorkPositions.visibility = View.GONE
            }
            updateToggleStyles(binding)

            binding.toggleCandidateType.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val exp = (checkedId == R.id.btnExperienced)
                    isExperienced = exp
                    binding.containerWorkPositions.visibility = if (exp) View.VISIBLE else View.GONE
                    updateWorkExperienceHeader()
                    updateToggleStyles(binding)
                }
            }

            renderWorkExperienceEntries()

            binding.btnAddWorkExperience.setOnClickListener {
                currentExperiences.add(
                    WorkExperienceItem(
                        id = UUID.randomUUID().toString(),
                        companyName = "",
                        roleTitle = "",
                        startDate = "",
                        endDate = "",
                        bulletPoints = emptyList(),
                        techStackTags = emptyList()
                    )
                )
                renderWorkExperienceEntries()
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
            val inflater = LayoutInflater.from(binding.root.context)

            currentExperiences.forEachIndexed { index, expItem ->
                val entryBinding = ItemWorkExperienceEntryBinding.inflate(
                    inflater,
                    binding.llWorkExperienceEntries,
                    false
                )
                val context = entryBinding.root.context
                entryBinding.dividerEntry.visibility = if (index > 0) View.VISIBLE else View.GONE
                entryBinding.tvEntryHeader.text = context.getString(R.string.position_header_format, index + 1)

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
                    val updated = currentExperiences[index].copy(companyName = it)
                    currentExperiences[index] = updated
                })

                entryBinding.etCompanyDuration.addTextChangedListener(SimpleTextWatcher {
                    val parts = it.split(Regex("[–\\-]|to"), 2).map { p -> p.trim() }
                    val sDate = parts.getOrNull(0) ?: it
                    val eDate = parts.getOrNull(1) ?: context.getString(R.string.duration_present)
                    val updated = currentExperiences[index].copy(startDate = sDate, endDate = eDate)
                    currentExperiences[index] = updated
                })

                entryBinding.etRoleTitle.addTextChangedListener(SimpleTextWatcher {
                    val updated = currentExperiences[index].copy(roleTitle = it)
                    currentExperiences[index] = updated
                })

                entryBinding.etRoleTitle.setOnEditorActionListener { v, actionId, _ ->
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                        v.clearFocus()
                        val imm =
                            v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                        imm?.hideSoftInputFromWindow(v.windowToken, 0)
                        true
                    } else false
                }

                entryBinding.tvRoleHeader.setRequiredHeader(R.string.header_role_responsibilities)

                fun renderBullets() {
                    entryBinding.llBulletsContainer.removeAllViews()
                    val bullets = currentExperiences[index].bulletPoints

                    bullets.forEachIndexed { bIdx, bText ->
                        val bulletView = createBulletView(
                            context = entryBinding.root.context,
                            text = bText,
                            onEdit = {
                                showEditBulletDialog(entryBinding.root.context, bText) { newText ->
                                    val updatedList = currentExperiences[index].bulletPoints.toMutableList()
                                    if (bIdx in updatedList.indices) {
                                        updatedList[bIdx] = newText
                                        currentExperiences[index] =
                                            currentExperiences[index].copy(bulletPoints = updatedList)
                                        renderBullets()
                                    }
                                }
                            },
                            onDelete = {
                                val updatedList = currentExperiences[index].bulletPoints.toMutableList()
                                if (bIdx in updatedList.indices) {
                                    updatedList.removeAt(bIdx)
                                    currentExperiences[index] =
                                        currentExperiences[index].copy(bulletPoints = updatedList)
                                    renderBullets()
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
                        val currentList = currentExperiences[index].bulletPoints.toMutableList()
                        currentList.add(newBullet)
                        currentExperiences[index] = currentExperiences[index].copy(bulletPoints = currentList)
                        renderBullets()
                    }
                }

                entryBinding.btnRemovePosition.setOnClickListener {
                    if (index in currentExperiences.indices) {
                        currentExperiences.removeAt(index)
                        renderWorkExperienceEntries()
                    }
                }

                binding.llWorkExperienceEntries.addView(entryBinding.root)
            }
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
                currentProjects.add(
                    ProjectItem(
                        id = UUID.randomUUID().toString(),
                        projectTitle = "",
                        description = "",
                        techStackTags = emptyList(),
                        demoUrl = "",
                        codeUrl = ""
                    )
                )
                renderProjectEntries()
            }
        }

        private fun renderProjectEntries() {
            binding.llProjectsEntries.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)

            currentProjects.forEachIndexed { index, projItem ->
                val entryBinding = ItemProjectEntryBinding.inflate(
                    inflater,
                    binding.llProjectsEntries,
                    false
                )
                val context = entryBinding.root.context
                entryBinding.dividerEntry.visibility = if (index > 0) View.VISIBLE else View.GONE
                entryBinding.tvProjectHeader.text = context.getString(R.string.project_header_format, index + 1)
                entryBinding.tilProjectTitle.setRequiredHint(R.string.project_title_name)
                entryBinding.tilProjectLink.setRequiredHint(R.string.repository_deployed_url)

                entryBinding.etProjectTitle.setText(projItem.projectTitle)
                val url = projItem.demoUrl.ifBlank { projItem.codeUrl }
                entryBinding.etProjectLink.setText(url)

                entryBinding.etProjectTitle.addTextChangedListener(SimpleTextWatcher {
                    val updated = currentProjects[index].copy(projectTitle = it)
                    currentProjects[index] = updated
                })

                entryBinding.etProjectLink.addTextChangedListener(SimpleTextWatcher {
                    val updated = currentProjects[index].copy(demoUrl = it, codeUrl = it)
                    currentProjects[index] = updated
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

                val bulletList = projItem.description.split(Regex("[\\r\\n]+|\\.\\s+"))
                    .map { it.replace(Regex("^[•\\-*]\\s*"), "").trim().removeSuffix(".") }
                    .filter { it.isNotBlank() }
                    .toMutableList()

                entryBinding.tvProjectDetailsHeader.setRequiredHeader(R.string.key_highlights_features)

                fun renderProjectBullets() {
                    entryBinding.llProjectBulletsContainer.removeAllViews()

                    bulletList.forEachIndexed { bIdx, bText ->
                        val bulletView = createBulletView(
                            context = entryBinding.root.context,
                            text = bText,
                            onEdit = {
                                showEditBulletDialog(entryBinding.root.context, bText) { newText ->
                                    if (bIdx in bulletList.indices) {
                                        bulletList[bIdx] = newText
                                        currentProjects[index] = currentProjects[index].copy(
                                            description = bulletList.joinToString("\n")
                                        )
                                        renderProjectBullets()
                                    }
                                }
                            },
                            onDelete = {
                                if (bIdx in bulletList.indices) {
                                    bulletList.removeAt(bIdx)
                                    currentProjects[index] = currentProjects[index].copy(
                                        description = bulletList.joinToString("\n")
                                    )
                                    renderProjectBullets()
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
                        bulletList.add(newBullet)
                        currentProjects[index] = currentProjects[index].copy(
                            description = bulletList.joinToString("\n")
                        )
                        renderProjectBullets()
                    }
                }

                entryBinding.btnRemoveProject.setOnClickListener {
                    if (index in currentProjects.indices) {
                        currentProjects.removeAt(index)
                        renderProjectEntries()
                    }
                }

                binding.llProjectsEntries.addView(entryBinding.root)
            }
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

            // Bind exact section title as detected from candidate's resume (e.g. "TECHNICAL SKILLS")
            binding.tvSectionTitle.text = currentSkills.sectionTitle.ifBlank { "TECHNICAL SKILLS" }
            binding.tvSectionTitle.setOnClickListener {
                showRenameSectionDialog()
            }

            renderSemanticSkillsDomains()

            // Header "+ Add" button to add a new domain or skill category
            binding.btnAddSkill.setOnClickListener {
                showAddOrEditDomainDialog(null, emptyList())
            }
        }

        private fun showRenameSectionDialog() {
            val context = binding.root.context
            val input = EditText(context).apply {
                setText(currentSkills.sectionTitle.ifBlank { "TECHNICAL SKILLS" })
                setSelection(text.length)
                setPadding(40, 30, 40, 30)
            }
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_title_rename_section))
                .setView(input)
                .setPositiveButton(context.getString(R.string.action_save)) { d, _ ->
                    val newTitle = input.text.toString().trim()
                    if (newTitle.isNotBlank()) {
                        currentSkills = currentSkills.copy(sectionTitle = newTitle)
                        binding.tvSectionTitle.text = newTitle
                    }
                    d.dismiss()
                }
                .setNegativeButton(context.getString(R.string.action_cancel)) { d, _ -> d.dismiss() }
                .show()
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
            binding.tvSectionTitle.text = currentSkills.sectionTitle.ifBlank { "TECHNICAL SKILLS" }

            // Extract categorized domain map from parsed resume data
            val categorizedMap = linkedMapOf<String, List<String>>()
            if (currentSkills.categorizedDomains.isNotEmpty()) {
                currentSkills.categorizedDomains.forEach { (cat, skills) ->
                    if (skills.isNotEmpty()) categorizedMap[cat] = skills
                }
            } else {
                // If categorizedDomains is empty, check legacy standard lists if populated with distinct content
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

                    rowBinding.btnEditDomain.setOnClickListener {
                        showAddOrEditDomainDialog(domainLabel, skillsList)
                    }

                    rowBinding.btnDeleteDomain.setOnClickListener {
                        deleteDomain(domainLabel)
                    }

                    // Bottom divider between domain rows (hidden on last item)
                    rowBinding.dividerDomain.visibility = if (index == domainEntries.lastIndex) View.GONE else View.VISIBLE
                    container.addView(rowBinding.itemSkillsDomainRoot)
                }
            } else {
                // =========================================================================
                // CASE 2: UNCATEGORIZED / FLAT LIST (Bullets, ZERO Random Domain Labels)
                // • Flutter      • Android SDK   • Kotlin        • Swift
                // • Jetpack Compose              • Firebase      • Git
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

                val inflater = LayoutInflater.from(context)
                val rowBinding = ItemSkillsDomainEntryBinding.inflate(inflater, container, false)

                // CRITICAL: Absolutely NO random domain label in Case 2!
                rowBinding.tvDomainTitle.visibility = View.GONE

                // Clean bulleted stream: "•  Flutter   •  Android SDK   •  Kotlin   •  Swift"
                rowBinding.tvDomainSkillsContent.text = flatSkillsList.joinToString("   •   ", prefix = "•   ")

                rowBinding.btnEditDomain.setOnClickListener {
                    showAddOrEditDomainDialog(null, flatSkillsList)
                }

                rowBinding.btnDeleteDomain.setOnClickListener {
                    currentSkills = currentSkills.copy(
                        rawFlatSkills = emptyList(),
                        categorizedDomains = emptyMap(),
                        languages = emptyList(),
                        frameworks = emptyList(),
                        cloudDevOps = emptyList(),
                        toolsMethodologies = emptyList(),
                        customCategories = emptyMap()
                    )
                    renderSemanticSkillsDomains()
                }

                rowBinding.dividerDomain.visibility = View.GONE
                container.addView(rowBinding.itemSkillsDomainRoot)
            }
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

            val dialogTitle = if (existingDomain != null) {
                context.getString(R.string.dialog_title_edit_skills_domain, existingDomain)
            } else if (existingSkills.isNotEmpty()) {
                context.getString(R.string.dialog_title_edit_flat_skills)
            } else {
                context.getString(R.string.dialog_title_add_skills_domain)
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle(dialogTitle)
                .setView(dialogBinding.dialogEditSkillsRoot)
                .setPositiveButton(context.getString(R.string.action_save), null)
                .setNegativeButton(context.getString(R.string.action_cancel)) { d, _ -> d.dismiss() }
                .create()

            dialog.show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val domainName = dialogBinding.etDialogDomainName.text?.toString()?.trim().orEmpty()
                val rawSkills = dialogBinding.etDialogSkillsComma.text?.toString().orEmpty()

                // Parse comma, bullet, or newline separated skills
                val parsedSkills = rawSkills.split(Regex("[,\\r\\n|•·]"))
                    .map { it.trim().removePrefix("•").removePrefix("-").trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                // If existingDomain was null and user didn't enter a domain name:
                // Treat as Case 2 (flat skills list)
                if (domainName.isBlank() && existingDomain == null) {
                    currentSkills = currentSkills.copy(
                        rawFlatSkills = parsedSkills,
                        categorizedDomains = emptyMap()
                    )
                    renderSemanticSkillsDomains()
                    dialog.dismiss()
                    return@setOnClickListener
                }

                val finalDomainName = domainName.ifBlank { existingDomain ?: "General" }
                updateSkills(existingDomain, finalDomainName, parsedSkills)
                renderSemanticSkillsDomains()
                dialog.dismiss()
            }
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
                currentEducation.add(
                    EducationItem(
                        institution = "",
                        degree = "",
                        graduationYear = ""
                    )
                )
                renderEducationEntries()
            }
        }

        private fun renderEducationEntries() {
            binding.llEducationEntries.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)

            currentEducation.forEachIndexed { index, eduItem ->
                val entryBinding = ItemEducationEntryBinding.inflate(
                    inflater,
                    binding.llEducationEntries,
                    false
                )
                val context = entryBinding.root.context
                entryBinding.dividerEntry.visibility = if (index > 0) View.VISIBLE else View.GONE
                entryBinding.tvEducationHeader.text = context.getString(R.string.education_header_format, index + 1)
                entryBinding.tilInstitution.setRequiredHint(R.string.hint_institution_name)
                entryBinding.tilDegree.setRequiredHint(R.string.hint_degree)
                entryBinding.tilDuration.setRequiredHint(R.string.hint_duration_example)

                entryBinding.etInstitution.setText(eduItem.institution)
                entryBinding.etDegree.setText(eduItem.degree)
                entryBinding.etDuration.setText(eduItem.graduationYear)

                entryBinding.etInstitution.addTextChangedListener(SimpleTextWatcher {
                    val updated = currentEducation[index].copy(institution = it)
                    currentEducation[index] = updated
                })
                entryBinding.etDegree.addTextChangedListener(SimpleTextWatcher {
                    val updated = currentEducation[index].copy(degree = it)
                    currentEducation[index] = updated
                })
                entryBinding.etDuration.addTextChangedListener(SimpleTextWatcher {
                    val updated = currentEducation[index].copy(graduationYear = it)
                    currentEducation[index] = updated
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

                entryBinding.btnRemoveEducation.visibility = View.VISIBLE
                entryBinding.btnRemoveEducation.setOnClickListener {
                    if (index in currentEducation.indices) {
                        currentEducation.removeAt(index)
                        renderEducationEntries()
                    }
                }

                binding.llEducationEntries.addView(entryBinding.root)
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
                    val input = EditText(context).apply {
                        hint = context.getString(R.string.hint_section_title)
                        setText(currentSec.sectionTitle)
                        setSelection(currentSec.sectionTitle.length)
                        setPadding(40, 36, 40, 36)
                    }
                    AlertDialog.Builder(context)
                        .setTitle(context.getString(R.string.dialog_title_rename_section))
                        .setView(input)
                        .setPositiveButton(context.getString(R.string.action_save)) { d, _ ->
                            val newTitle = input.text.toString().trim()
                            if (newTitle.isNotBlank()) {
                                val updatedSec = currentSec.copy(sectionTitle = newTitle)
                                sectionItem.customSection = updatedSec
                                binding.tvSectionTitle.text = newTitle
                                // Re-render items and button label to reflect the updated singular type and link hints
                                bind(sectionItem)
                            }
                            d.dismiss()
                        }
                        .setNegativeButton(context.getString(R.string.action_cancel)) { d, _ -> d.dismiss() }
                        .show()
                }
            }

            binding.tvSectionTitle.setOnClickListener { openRenameDialog() }
            binding.btnEditSection.setOnClickListener { openRenameDialog() }

            val context = binding.root.context
            val singularType = when {
                sec.sectionTitle.contains("Open Source", ignoreCase = true) -> context.getString(R.string.custom_type_source)
                sec.sectionTitle.contains("Certif", ignoreCase = true) -> context.getString(R.string.custom_type_certificate)
                sec.sectionTitle.contains("Award", ignoreCase = true) || sec.sectionTitle.contains(
                    "Honor",
                    ignoreCase = true
                ) -> context.getString(R.string.custom_type_award)

                sec.sectionTitle.contains("Publication", ignoreCase = true) -> context.getString(R.string.custom_type_publication)
                else -> context.getString(R.string.custom_type_item)
            }
            binding.btnAddCustomItem.text = context.getString(R.string.add_new_item_format, singularType)

            // Circular minus button click removes optional section
            binding.btnRemoveSection.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    removeCustomSection(pos)
                }
            }

            renderCustomItems(sectionItem, singularType)

            binding.btnAddCustomItem.setOnClickListener {
                val currentSec = sectionItem.customSection ?: return@setOnClickListener
                val updatedItems = currentSec.items.toMutableList()
                updatedItems.add(
                    DynamicItem(
                        title = "",
                        subtitle = "",
                        dateOrPeriod = "",
                        description = "",
                        link = "",
                        bulletPoints = emptyList()
                    )
                )
                val updatedSec = currentSec.copy(items = updatedItems)
                sectionItem.customSection = updatedSec
                renderCustomItems(sectionItem, singularType)
            }
        }

        private fun renderCustomItems(sectionItem: SectionItem, singularType: String? = null) {
            val section = sectionItem.customSection ?: return
            val context = binding.root.context
            val resolvedSingularType = singularType ?: context.getString(R.string.custom_type_item)
            binding.llCustomItems.removeAllViews()
            val inflater = LayoutInflater.from(context)

            section.items.forEachIndexed { itemIndex, item ->
                val entryBinding = ItemCustomEntryBinding.inflate(inflater, binding.llCustomItems, false)
                entryBinding.dividerEntry.visibility = if (itemIndex > 0) View.VISIBLE else View.GONE
                entryBinding.tvCustomItemHeader.text = context.getString(R.string.custom_item_header_format, resolvedSingularType, itemIndex + 1)
                entryBinding.btnRemoveCustomItem.text = context.getString(R.string.remove_item_format, resolvedSingularType)
                entryBinding.tilCustomTitle.setRequiredHint(R.string.hint_custom_title)
                entryBinding.etCustomTitle.setText(item.title)

                val linkHint = when {
                    section.sectionTitle.contains("Certif", ignoreCase = true) ||
                            section.sectionTitle.contains("License", ignoreCase = true) ->
                        context.getString(R.string.hint_link_certification)

                    section.sectionTitle.contains("Publication", ignoreCase = true) ||
                            section.sectionTitle.contains("Paper", ignoreCase = true) ||
                            section.sectionTitle.contains("Article", ignoreCase = true) ->
                        context.getString(R.string.hint_link_publication)

                    section.sectionTitle.contains("Open Source", ignoreCase = true) ||
                            section.sectionTitle.contains("Repo", ignoreCase = true) ||
                            section.sectionTitle.contains("Source", ignoreCase = true) ->
                        context.getString(R.string.hint_link_open_source)

                    else ->
                        context.getString(R.string.hint_link_general)
                }
                entryBinding.tilCustomLink.hint = linkHint
                entryBinding.etCustomLink.setText(item.link)

                entryBinding.etCustomTitle.addTextChangedListener(SimpleTextWatcher { newTitle ->
                    val cur = sectionItem.customSection ?: return@SimpleTextWatcher
                    if (itemIndex in cur.items.indices) {
                        val updatedItems = cur.items.toMutableList()
                        updatedItems[itemIndex] = updatedItems[itemIndex].copy(title = newTitle)
                        sectionItem.customSection = cur.copy(items = updatedItems)
                    }
                })

                entryBinding.etCustomLink.addTextChangedListener(SimpleTextWatcher(trim = false) { rawLink ->
                    val cur = sectionItem.customSection ?: return@SimpleTextWatcher
                    if (itemIndex in cur.items.indices) {
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
                        updatedItems[itemIndex] = updatedItems[itemIndex].copy(link = normalized)
                        sectionItem.customSection = cur.copy(items = updatedItems)
                    }
                })

                entryBinding.etCustomLink.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val cur = sectionItem.customSection ?: return@setOnFocusChangeListener
                        if (itemIndex in cur.items.indices) {
                            val savedLink = cur.items[itemIndex].link
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

                entryBinding.tvCustomDetailsHeader.setRequiredHeader(R.string.header_custom_details)

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
                                        if (cur != null && itemIndex in cur.items.indices) {
                                            val updatedItems = cur.items.toMutableList()
                                            updatedItems[itemIndex] = updatedItems[itemIndex].copy(
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
                                    if (cur != null && itemIndex in cur.items.indices) {
                                        val updatedItems = cur.items.toMutableList()
                                        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
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
                renderCustomBullets()

                // Add Bullet
                entryBinding.btnAddCustomBullet.setOnClickListener {
                    val ctx = entryBinding.root.context
                    showAddBulletDialog(ctx) { newBullet ->
                        bullets.add(newBullet)
                        val cur = sectionItem.customSection
                        if (cur != null && itemIndex in cur.items.indices) {
                            val updatedItems = cur.items.toMutableList()
                            updatedItems[itemIndex] = updatedItems[itemIndex].copy(
                                bulletPoints = bullets,
                                description = bullets.joinToString("\n")
                            )
                            sectionItem.customSection = cur.copy(items = updatedItems)
                        }
                        renderCustomBullets()
                    }
                }

                entryBinding.btnRemoveCustomItem.setOnClickListener {
                    val cur = sectionItem.customSection ?: return@setOnClickListener
                    if (itemIndex in cur.items.indices) {
                        val updatedItems = cur.items.toMutableList()
                        updatedItems.removeAt(itemIndex)
                        val updatedSec = cur.copy(items = updatedItems)
                        sectionItem.customSection = updatedSec
                        renderCustomItems(sectionItem, singularType)
                    }
                }

                binding.llCustomItems.addView(entryBinding.root)
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

        val finalHero = targetLeft.currentHero.copy(
            contactInfo = targetLeft.currentHero.contactInfo.copy(
                linkedinUrl = linkedIn,
                githubUrl = github
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
            .filter { sec -> sec.items.any { it.title.isNotBlank() } }
            .map { sec ->
                sec.copy(
                    items = sec.items.filter { it.title.isNotBlank() }.map { item ->
                        if (item.link.isNotBlank()) item.copy(link = normalizeUrl(item.link)) else item
                    }
                )
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
            dynamicSections = finalDynamicSections
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
