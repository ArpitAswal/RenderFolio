package com.example.ui.dialog

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.databinding.BottomSheetAddSectionBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class AddCustomSectionBottomSheet(
    private val onSectionAdded: (title: String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddSectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddSectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // When a template chip is clicked, populate the custom section title input
        for (i in 0 until binding.chipGroupTemplates.childCount) {
            val chip = binding.chipGroupTemplates.getChildAt(i) as? Chip
            chip?.setOnClickListener {
                binding.etCustomSectionTitle.setText(chip.text)
                binding.etCustomSectionTitle.setSelection(chip.text.length)
            }
        }

        binding.btnConfirmAddSection.setOnClickListener {
            val title = binding.etCustomSectionTitle.text?.toString()?.trim() ?: ""
            if (title.isBlank()) {
                binding.tilCustomSectionTitle.error = "Please enter or select a section title"
                return@setOnClickListener
            }
            binding.tilCustomSectionTitle.error = null
            onSectionAdded(title)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val d = dialog as? BottomSheetDialog ?: return
        val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true

        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        if (isTablet) {
            val density = resources.displayMetrics.density
            val widthPx = (560 * density).toInt()
            val params = bottomSheet.layoutParams
            if (params is CoordinatorLayout.LayoutParams) {
                params.width = widthPx
                params.gravity = Gravity.CENTER_HORIZONTAL
                bottomSheet.layoutParams = params
            } else if (params != null) {
                params.width = widthPx
                bottomSheet.layoutParams = params
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddCustomSectionBottomSheet"
    }
}
