package com.example.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.util.QrCodeGenerator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream

class ShowcaseQrBottomSheet : BottomSheetDialogFragment() {

    private var shareUrl: String = ""
    private var candidateName: String = ""
    private var isCelebrationMode: Boolean = true
    private var qrBitmap: Bitmap? = null
    var onViewVisualizer: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            shareUrl = it.getString(ARG_SHARE_URL, "")
            candidateName = it.getString(ARG_CANDIDATE_NAME, "")
            isCelebrationMode = it.getBoolean(ARG_IS_CELEBRATION, true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_showcase_qr, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tv_qr_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_qr_subtitle)
        val ivQrCode = view.findViewById<ImageView>(R.id.iv_qr_code)
        val btnSaveQr = view.findViewById<ImageButton>(R.id.btn_action_save_qr)
        val btnShareQr = view.findViewById<ImageButton>(R.id.btn_action_share_qr)
        val btnViewVisualizer = view.findViewById<MaterialButton>(R.id.btn_qr_view_visualizer)

        if (isCelebrationMode) {
            // Post-compilation celebration mode from Structure Verification
            tvTitle.text = getString(R.string.title_showcase_ready)
            tvSubtitle.text = getString(R.string.subtitle_showcase_ready)
            btnViewVisualizer.visibility = View.VISIBLE
        } else {
            // Standalone QR display from Cold Start (candidate name header, no redundant visualizer button)
            tvTitle.text = if (candidateName.isNotBlank()) candidateName else getString(R.string.title_showcase_ready)
            tvSubtitle.text = getString(R.string.subtitle_showcase_ready)
            btnViewVisualizer.visibility = View.GONE
        }

        // Generate QR Code Bitmap in-memory
        val qrTargetUrl = if (shareUrl.isNotBlank()) shareUrl else "https://lightweight-firebase-projects.web.app"
        try {
            val bitmap = QrCodeGenerator.generateQrBitmap(content = qrTargetUrl, sizePx = 600)
            qrBitmap = bitmap
            ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            ivQrCode.setImageResource(R.drawable.ic_qr_code)
        }

        // Top Right Action 1: Save QR Code Image to Gallery
        btnSaveQr.setOnClickListener {
            val bitmap = qrBitmap
            if (bitmap != null) {
                val result = QrCodeGenerator.saveBitmapToGallery(
                    context = requireContext(),
                    bitmap = bitmap,
                    title = "ResumeShowcase_${candidateName.replace(" ", "_")}"
                )
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), R.string.toast_saved_to_gallery, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to save photo: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "QR code not ready yet.", Toast.LENGTH_SHORT).show()
            }
        }

        // Top Right Action 2: Share QR Code Image Across Device Apps
        btnShareQr.setOnClickListener {
            val bitmap = qrBitmap
            if (bitmap != null) {
                shareQrImage(bitmap)
            } else {
                Toast.makeText(requireContext(), "QR code not ready yet.", Toast.LENGTH_SHORT).show()
            }
        }

        // Primary Action: View Visualizer
        btnViewVisualizer.setOnClickListener {
            dismiss()
            onViewVisualizer?.invoke()
        }
    }

    /**
     * Shares the generated QR code as an image across device apps (WhatsApp, Drive, Gmail, etc.)
     * via FileProvider and Android Intent.ACTION_SEND.
     */
    private fun shareQrImage(bitmap: Bitmap) {
        try {
            val cacheImagesDir = File(requireContext().cacheDir, "images")
            cacheImagesDir.mkdirs()
            val qrFile = File(cacheImagesDir, "resume_showcase_qr.png")
            FileOutputStream(qrFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val contentUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                qrFile
            )

            val shareText = "Scan to explore my interactive digital resume on ResumeShowcase:\n$shareUrl"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "$candidateName - Digital Resume Showcase QR")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_qr_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not share QR image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TAG = "ShowcaseQrBottomSheet"
        private const val ARG_SHARE_URL = "arg_share_url"
        private const val ARG_CANDIDATE_NAME = "arg_candidate_name"
        private const val ARG_IS_CELEBRATION = "arg_is_celebration"

        fun newInstance(
            shareUrl: String,
            candidateName: String = "",
            isCelebration: Boolean = true
        ): ShowcaseQrBottomSheet {
            return ShowcaseQrBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SHARE_URL, shareUrl)
                    putString(ARG_CANDIDATE_NAME, candidateName)
                    putBoolean(ARG_IS_CELEBRATION, isCelebration)
                }
            }
        }
    }
}
