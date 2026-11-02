package com.example.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.ResumeCloudRepository
import com.example.data.ResumePreferencesRepository
import com.example.model.SavedShowcase
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TailoredShowcaseSelectorBottomSheet : BottomSheetDialogFragment() {

    enum class Mode {
        VISUALISE,
        SHOW_QR
    }

    private var mode: Mode = Mode.VISUALISE
    var onShowcaseSelected: ((SavedShowcase) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modeStr = arguments?.getString(ARG_MODE, Mode.VISUALISE.name) ?: Mode.VISUALISE.name
        mode = runCatching { Mode.valueOf(modeStr) }.getOrDefault(Mode.VISUALISE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_select_tailored_showcase, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tv_selector_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_selector_subtitle)
        val rvShowcases = view.findViewById<RecyclerView>(R.id.rv_tailored_showcases)

        if (mode == Mode.VISUALISE) {
            tvTitle.text = "Select Resume to Visualise"
            tvSubtitle.text = "Choose which tailored resume to view (ordered by earliest expiration):"
        } else {
            tvTitle.text = "Select Showcase QR Code"
            tvSubtitle.text = "Choose which tailored resume QR code to display:"
        }

        val repo = ResumePreferencesRepository.getInstance(requireContext())
        val showcases = repo.getShowcases()

        if (showcases.size <= 1) {
            dismiss()
            showcases.firstOrNull()?.let { onShowcaseSelected?.invoke(it) }
            return
        }

        rvShowcases.layoutManager = LinearLayoutManager(requireContext())
        val adapter = ShowcaseAdapter(
            items = showcases.toMutableList(),
            onClick = { selected ->
                dismiss()
                onShowcaseSelected?.invoke(selected)
            },
            onDelete = { item, position, adapterRef ->
                showDeleteConfirmationDialog(item) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val cloudRepo = ResumeCloudRepository()
                        cloudRepo.deleteResume(item.shareId)
                        repo.deleteShowcase(item.shareId)
                        adapterRef.removeItem(position)
                        Toast.makeText(requireContext(), R.string.toast_showcase_deleted, Toast.LENGTH_SHORT).show()

                        val remaining = repo.getShowcases()
                        if (remaining.isEmpty()) {
                            dismiss()
                        }
                    }
                }
            }
        )
        rvShowcases.adapter = adapter
    }

    private fun showDeleteConfirmationDialog(item: SavedShowcase, onConfirm: () -> Unit) {
        val targetName = if (item.targetCompany.isNotBlank()) item.targetCompany else item.headline
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_showcase_title)
            .setMessage("Are you sure you want to delete this tailored resume for $targetName? This will remove its cloud link, QR code, and visualization.")
            .setPositiveButton(R.string.action_delete) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class ShowcaseAdapter(
        private val items: MutableList<SavedShowcase>,
        private val onClick: (SavedShowcase) -> Unit,
        private val onDelete: (SavedShowcase, Int, ShowcaseAdapter) -> Unit
    ) : RecyclerView.Adapter<ShowcaseAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

        fun removeItem(position: Int) {
            if (position in items.indices) {
                items.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, items.size - position)
            }
        }

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvHeadline: TextView = view.findViewById(R.id.tv_card_headline)
            val tvCandidate: TextView = view.findViewById(R.id.tv_card_candidate)
            val tvExpiry: TextView = view.findViewById(R.id.tv_card_expiry)
            val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_showcase)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tailored_showcase_card, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvHeadline.text = item.displayHeadline.ifBlank { "Professional Resume" }
            holder.tvCandidate.text = item.candidateName

            val days = item.daysRemaining
            val createdDateStr = dateFormat.format(Date(item.createdAtMillis))
            holder.tvExpiry.text = if (days <= 1) {
                "⚠️ Expires tomorrow (Created $createdDateStr)"
            } else {
                "⏱ Expires in $days days (Created $createdDateStr)"
            }

            holder.view.setOnClickListener {
                onClick(item)
            }

            holder.btnDelete.setOnClickListener {
                onDelete(item, holder.bindingAdapterPosition, this)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val TAG = "TailoredShowcaseSelectorBottomSheet"
        private const val ARG_MODE = "arg_mode"

        fun newInstance(mode: Mode): TailoredShowcaseSelectorBottomSheet {
            return TailoredShowcaseSelectorBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, mode.name)
                }
            }
        }
    }
}

