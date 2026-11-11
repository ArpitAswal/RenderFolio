package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.constant.AppConstants
import com.example.model.*
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.regex.Pattern
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

data class ExtractionResult(
    val profile: ResumeProfile,
    val rawText: String,
    val tierUsed: String
)

class PdfExtractionRepository(private val context: Context) {

    private val tag = "PdfExtractionRepo"

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            Log.w(tag, "PDFBox init error: ${e.message}")
        }
    }

    suspend fun extractAndParseResume(
        uri: Uri,
        onProgress: (step: String, progress: Float, activeIndex: Int, sections: List<String>) -> Unit = { _, _, _, _ -> }
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val initialSections = listOf(
            "Personal Info & Contact",
            "Summary / Profile",
            "Work Experience",
            "Featured Projects",
            "Technical Skills",
            "Education & Academics"
        )
        onProgress("Personal Info & Contact", 0.05f, 0, initialSections)

        val tempFile = File(context.cacheDir, "temp_resume_${System.currentTimeMillis()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open input stream for provided document")

            var headerLinksList: List<String>? = null
            val (tier1Text, extractedLinks) = if (!AppConstants.DISABLE_NATIVE_PDF_EXTRACTION) {
                try {
                    val nativeResult = extractNativePdfData(tempFile)
                    headerLinksList = nativeResult.headerLinks
                    Pair(nativeResult.text, nativeResult.links)
                } catch (e: Exception) {
                    Log.w(tag, "Native PDF extraction encountered error: ${e.message}. Falling back to ML Kit OCR.")
                    val links = extractAnnotationLinks(tempFile)
                    Pair("", links)
                }
            } else {
                Log.i(tag, "Native PDF extraction is turned OFF. Extracting text 100% via Google ML Kit On-Device OCR.")
                val links = extractAnnotationLinks(tempFile)
                Pair("", links)
            }

            val (finalRawText, tierUsed) = if (!AppConstants.DISABLE_NATIVE_PDF_EXTRACTION && tier1Text.trim().length >= AppConstants.TIER_1_CHAR_THRESHOLD) {
                Pair(tier1Text, "Tier 1: Native Text Layer (PDFBox)")
            } else {
                onProgress("Scanning pages with Google ML Kit OCR...", 0.15f, 0, initialSections)
                val tier2Text = extractWithMlKitFallback(tempFile) { step, progress ->
                    onProgress(step, progress, 0, initialSections)
                }
                if (tier2Text.isNotBlank()) {
                    Pair(tier2Text, "Tier 2: Google ML Kit Optical Character Recognition (On-Device OCR)")
                } else {
                    Pair(tier1Text, "Fallback Native Text")
                }
            }

            if (finalRawText.isBlank() || finalRawText.length < 50) {
                throw IllegalArgumentException("The attached PDF contains no readable text or is corrupted. Please ensure you upload a valid resume.")
            }

            // Detect actual section headings present in this specific resume
            val detectedSections = detectResumeSectionHeadings(finalRawText)
            val totalSections = detectedSections.size

            // Step dynamically through each section heading extracted from the resume
            detectedSections.forEachIndexed { index, sectionName ->
                val startProgress = (index.toFloat() / totalSections).coerceIn(0.08f, 0.95f)
                val targetProgress = ((index + 1).toFloat() / totalSections).coerceIn(0.15f, 1.0f)

                onProgress(sectionName, startProgress, index, detectedSections)
                kotlinx.coroutines.delay(250)

                val midProgress = startProgress + (targetProgress - startProgress) * 0.75f
                onProgress(sectionName, midProgress, index, detectedSections)
                kotlinx.coroutines.delay(500)
            }

            // Final completion
            onProgress(detectedSections.last(), 1.0f, detectedSections.lastIndex, detectedSections)
            kotlinx.coroutines.delay(350)

            val parsedProfile = parseRawTextToProfile(finalRawText, extractedLinks, detectedSections, headerLinksList)
            ExtractionResult(parsedProfile, finalRawText, tierUsed)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Inspects the raw text extracted from the uploaded PDF to dynamically detect the actual
     * section titles written by the candidate (e.g. "Summary" vs "Profile",
     * "Professional Experience" vs "Employment History", "Courses", "Achievements", "Hobbies", "Languages", etc.)
     * without relying on hardcoded rigid whitelists.
     */
    fun detectResumeSectionHeadings(rawText: String): List<String> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val detected = mutableListOf<String>()

        // First item is always contact / personal header
        detected.add("Personal Info & Contact")

        // Known personal/contact tags and metadata that are NOT body section headings
        val ignoredHeaderTags = setOf(
            "LINKS", "DETAILS", "INFO", "PERSONAL DETAILS", "CONTACT", "CONTACT ME",
            "DATE OF BIRTH", "PLACE OF BIRTH", "DRIVING LICENSE", "NATIONALITY",
            "ADDRESS", "PHONE", "EMAIL", "RESUME", "CURRICULUM VITAE", "CV", "PORTFOLIO"
        )

        // Common standard section keywords for semantic matching
        val knownSectionWords = listOf(
            "SUMMARY", "PROFILE", "OBJECTIVE", "OVERVIEW", "ABOUT ME",
            "EXPERIENCE", "EMPLOYMENT HISTORY", "WORK HISTORY", "CAREER HISTORY", "WORK EXPERIENCE",
            "PROJECTS", "FEATURED PROJECTS", "PERSONAL PROJECTS", "KEY PROJECTS", "ACADEMIC PROJECTS",
            "SKILLS", "TECHNICAL SKILLS", "CORE COMPETENCIES", "SKILLS & TOOLS", "AREAS OF EXPERTISE", "TECHNOLOGIES",
            "EDUCATION", "ACADEMICS", "ACADEMIC BACKGROUND", "QUALIFICATIONS", "CREDENTIALS",
            "COURSES", "COURSEWORK", "CERTIFICATIONS", "CERTIFICATES", "LICENSES",
            "ACHIEVEMENTS", "HONORS", "AWARDS", "PUBLICATIONS", "RESEARCH",
            "HOBBIES", "INTERESTS", "LANGUAGES", "REFERENCES", "COMMUNITY",
            "VOLUNTEER", "VOLUNTEERING", "OPEN SOURCE", "CONTRIBUTIONS"
        )

        // Non-heading metric/tech acronyms that should never be treated as section headings
        val ignoredMetricAndTechAcronyms = setOf(
            "DAU", "MAU", "WAU", "ROI", "CTR", "KPI", "KPIS", "ANR", "ANRS", "SLA", "SLAS",
            "API", "APIS", "SDK", "SDKS", "URL", "URLS", "QPS", "RAG", "LLM", "LLMS", "AI", "ML",
            "CI", "CD", "CI/CD", "AWS", "GCP", "GCS", "UI", "UX", "UI/UX", "B2B", "B2C",
            "SDE", "SWE", "MTS", "MLE", "EM", "VP", "CEO", "CTO", "CFO", "COO", "CMO",
            "SQL", "NOSQL", "REST", "JSON", "XML", "HTTP", "HTTPS", "TCP", "UDP", "IP"
        )

        val topLines = lines.take(3)
        val candidateName = topLines.firstOrNull()?.trim() ?: ""

        for (rawLine in lines) {
            if (rawLine.length > 55 || rawLine.length < 3) continue
            if (rawLine.contains("@") || rawLine.contains("http") || rawLine.contains(".com")) continue
            if (rawLine.startsWith("•") || rawLine.startsWith("-") || rawLine.startsWith("*") || rawLine.startsWith("·")) continue
            if (rawLine.matches(Regex("^[0-9]+[.)\\-].*"))) continue

            // Resumes often right-align dates/locations at the same vertical height as section headings
            // (e.g. "EDUCATION     Aug 2023 – Feb 2024"). Split on multiple spaces/tabs.
            val candidate = rawLine.split(Regex("\\s{2,}|\t"))[0].trim()
            val cleaned = cleanSpacedLetters(candidate)
                .replace(Regex("^[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*"), "")
                .removeSuffix(":")
                .trim()

            if (cleaned.length < 3 || cleaned.length > 45) continue
            // Section headings do not end with sentence punctuation
            if (cleaned.endsWith(".") || cleaned.endsWith(",") || cleaned.endsWith(";") || cleaned.endsWith("!")) continue

            val upper = cleaned.uppercase()
            if (ignoredHeaderTags.contains(upper)) continue
            if (ignoredMetricAndTechAcronyms.contains(upper)) continue
            if (candidateName.isNotBlank() && upper == candidateName.uppercase()) continue
            if (isLikelyLocation(cleaned)) continue

            // Heading identification criteria:
            // 1. Semantic match against known section keywords
            val matchedKnown = knownSectionWords.firstOrNull { kw ->
                upper == kw || upper.startsWith("$kw ") || upper.startsWith("$kw &") || upper.startsWith("$kw /") || upper.endsWith(" $kw")
            }

            // 2. Typographic heading detection: pure uppercase standalone heading line
            val isAllUpperTypography = cleaned.length >= 4 &&
                    cleaned.matches(Regex("^[A-Z0-9\\s&/,'-]+$")) &&
                    cleaned.any { it.isLetter() } &&
                    cleaned.split(Regex("\\s+")).size <= 5

            if (matchedKnown != null || isAllUpperTypography) {
                val rawHeadingName = if (matchedKnown != null && upper == matchedKnown) matchedKnown else cleaned
                val formatted = toTitleCase(rawHeadingName)

                if (!detected.any { it.equals(formatted, ignoreCase = true) }) {
                    detected.add(formatted)
                }
            }
        }

        if (detected.size < 3) {
            val defaults = listOf(
                "Personal Info & Contact",
                "Summary / Profile",
                "Work Experience",
                "Featured Projects",
                "Technical Skills",
                "Education & Academics"
            )
            return (detected + defaults.filter { d -> !detected.any { it.equals(d, ignoreCase = true) } })
        }

        return detected
    }

    internal fun toTitleCase(text: String): String {
        return text.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                val cleanWord = word.replace(Regex("[,.:;|•·]+$"), "")
                val suffix = word.substring(cleanWord.length)
                when {
                    word == "|" -> "|"
                    word == "·" -> "·"
                    word == "•" -> "•"
                    word.equals("Node.js", ignoreCase = true) -> "Node.js"
                    word.equals("PostgreSQL", ignoreCase = true) -> "PostgreSQL"
                    word.equals("TypeScript", ignoreCase = true) -> "TypeScript"
                    cleanWord.equals("AKTU", ignoreCase = true) -> "AKTU$suffix"
                    cleanWord.equals("CSE", ignoreCase = true) -> "CSE$suffix"
                    cleanWord.equals("UP", ignoreCase = true) -> "UP$suffix"
                    cleanWord.equals("B.Tech", ignoreCase = true) -> "B.Tech$suffix"
                    cleanWord.equals("M.Tech", ignoreCase = true) -> "M.Tech$suffix"
                    cleanWord.equals("B.E.", ignoreCase = true) -> "B.E.$suffix"
                    cleanWord.equals("M.E.", ignoreCase = true) -> "M.E.$suffix"
                    cleanWord.equals("BCA", ignoreCase = true) -> "BCA$suffix"
                    cleanWord.equals("MCA", ignoreCase = true) -> "MCA$suffix"
                    cleanWord.equals("A.K.T.U.", ignoreCase = true) -> "A.K.T.U.$suffix"
                    cleanWord.equals("R.D.", ignoreCase = true) -> "R.D.$suffix"
                    word.matches(Regex("^(?:[A-Za-z]\\.)+[A-Za-z]?$")) -> word.uppercase()
                    word.equals("CGPA", ignoreCase = true) || word.startsWith("CGPA:", ignoreCase = true) -> word.replace(Regex("(?i)cgpa"), "CGPA")
                    word.equals("GPA", ignoreCase = true) || word.startsWith("GPA:", ignoreCase = true) -> word.replace(Regex("(?i)gpa"), "GPA")
                    word.equals("AI", ignoreCase = true) -> "AI"
                    word.equals("ML", ignoreCase = true) -> "ML"
                    word.equals("iOS", ignoreCase = true) -> "iOS"
                    word.equals("UI", ignoreCase = true) -> "UI"
                    word.equals("UX", ignoreCase = true) -> "UX"
                    word.equals("API", ignoreCase = true) -> "API"
                    word.equals("APIs", ignoreCase = true) -> "APIs"
                    word.equals("SDK", ignoreCase = true) -> "SDK"
                    word.equals("SDKs", ignoreCase = true) -> "SDKs"
                    word.equals("REST", ignoreCase = true) -> "REST"
                    word.equals("MVVM", ignoreCase = true) -> "MVVM"
                    word.equals("BLoC", ignoreCase = true) || word.equals("Bloc", ignoreCase = true) -> "BLoC"
                    word.equals("IT", ignoreCase = true) -> "IT"
                    word.equals("IIT", ignoreCase = true) -> "IIT"
                    word.equals("NIT", ignoreCase = true) -> "NIT"
                    word.equals("IIIT", ignoreCase = true) -> "IIIT"
                    cleanWord.equals("ECE", ignoreCase = true) -> "ECE$suffix"
                    cleanWord.equals("AWS", ignoreCase = true) -> "AWS$suffix"
                    cleanWord.equals("GCP", ignoreCase = true) -> "GCP$suffix"
                    else -> word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
    }

    private fun cleanSpacedLetters(text: String): String {
        if (!text.contains(" ")) return text
        return text.split(Regex("\\s{2,}"))
            .joinToString(" ") { token ->
                val pieces = token.trim().split(" ").filter { it.isNotBlank() }
                if (pieces.size >= 3 && pieces.all { it.length == 1 }) {
                    pieces.joinToString("")
                } else {
                    token
                }
            }
    }

    data class NativePdfData(
        val text: String,
        val links: List<String> = emptyList(),
        val headerLinks: List<String> = emptyList()
    )

    private fun extractAnnotationLinks(file: File): List<String> {
        val extractedLinks = mutableListOf<String>()
        try {
            PDDocument.load(file).use { document ->
                for (page in document.pages) {
                    for (ann in page.annotations) {
                        if (ann is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink) {
                            val act = ann.action
                            if (act is com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI) {
                                val uri = act.uri
                                if (!uri.isNullOrBlank() && !extractedLinks.contains(uri)) {
                                    extractedLinks.add(uri)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Annotation link extraction note: ${e.message}")
        }
        return extractedLinks
    }

    internal data class SpatialTextLine(
        val text: String,
        val minX: Float,
        val maxX: Float,
        val y: Float
    )

    internal class SpatialPageStripper : PDFTextStripper() {
        val lines = mutableListOf<SpatialTextLine>()

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return

            var currentSegment = mutableListOf<TextPosition>()
            var hadSplit = false
            for (i in textPositions.indices) {
                val tp = textPositions[i]
                if (currentSegment.isNotEmpty()) {
                    val prev = currentSegment.last()
                    val gap = tp.xDirAdj - (prev.xDirAdj + prev.widthDirAdj)
                    if (gap > 24.0f) {
                        hadSplit = true
                        // Split into separate spatial text line across large column gap
                        val segMinX = currentSegment.minOf { it.xDirAdj }
                        val segMaxX = currentSegment.maxOf { it.xDirAdj + it.widthDirAdj }
                        val segY = currentSegment.minOf { it.yDirAdj }
                        val segText = buildSegmentText(currentSegment)
                        if (segText.isNotBlank()) {
                            lines.add(SpatialTextLine(segText, segMinX, segMaxX, segY))
                        }
                        currentSegment = mutableListOf()
                    }
                }
                currentSegment.add(tp)
            }
            if (currentSegment.isNotEmpty()) {
                val segMinX = currentSegment.minOf { it.xDirAdj }
                val segMaxX = currentSegment.maxOf { it.xDirAdj + it.widthDirAdj }
                val segY = currentSegment.minOf { it.yDirAdj }
                val segText = if (!hadSplit) {
                    text
                } else {
                    buildSegmentText(currentSegment)
                }
                if (segText.isNotBlank()) {
                    lines.add(SpatialTextLine(segText, segMinX, segMaxX, segY))
                }
            }
        }

        private fun buildSegmentText(segment: List<TextPosition>): String {
            if (segment.isEmpty()) return ""
            val sb = StringBuilder()
            for (j in segment.indices) {
                val tp = segment[j]
                if (j > 0) {
                    val prev = segment[j - 1]
                    val dist = tp.xDirAdj - (prev.xDirAdj + prev.widthDirAdj)
                    val spaceWidth = prev.widthOfSpace.coerceAtLeast(1.5f)
                    if (dist > spaceWidth * 0.7f) {
                        sb.append(" ")
                    }
                }
                sb.append(tp.unicode ?: "")
            }
            return sb.toString().trim()
        }
    }

    private fun extractSpatialTextFromDocument(document: PDDocument): String {
        val pageCount = document.numberOfPages
        val fullDocSb = StringBuilder()

        for (p in 0 until pageCount) {
            val page = document.getPage(p)
            val pageWidth = page.mediaBox.width

            val stripper = SpatialPageStripper()
            stripper.sortByPosition = true
            stripper.startPage = p + 1
            stripper.endPage = p + 1
            stripper.writeText(document, java.io.StringWriter())

            val rawLines = stripper.lines
            if (rawLines.isEmpty()) continue

            val mergedLines = consolidateSameYLines(rawLines)

            // Evaluate potential column gutter X between 18% and 82% of page width
            var bestGutter = -1f
            var minCross = Int.MAX_VALUE
            val step = 5f
            var xProbe = pageWidth * 0.18f

            while (xProbe <= pageWidth * 0.82f) {
                val cross = mergedLines.count { l -> l.minX < xProbe && l.maxX > xProbe }
                val left = mergedLines.count { l -> l.maxX <= xProbe }
                val right = mergedLines.count { l -> l.minX >= xProbe }

                if (left >= 4 && right >= 4 && cross < minCross) {
                    minCross = cross
                    bestGutter = xProbe
                }
                xProbe += step
            }

            val hasMultiColumn = bestGutter > 0 && minCross <= 6

            if (!hasMultiColumn) {
                // Single column layout
                mergedLines.sortedBy { it.y }.forEach { fullDocSb.appendLine(it.text) }
            } else {
                // Multi-column or sidebar layout
                val isLeftSidebar = bestGutter < pageWidth * 0.40f
                val crossingItems = mergedLines.filter { it.minX < bestGutter && it.maxX > bestGutter }
                val crossingMaxY = crossingItems.maxOfOrNull { it.y } ?: -1f

                val leftItems = mergedLines.filter { it.maxX <= bestGutter }
                val rightItems = mergedLines.filter { it.minX >= bestGutter }

                // Determine top header/banner region that precedes the two-column layout
                val bannerCutoffY = if (isLeftSidebar && leftItems.isNotEmpty()) {
                    val leftStartY = leftItems.minOf { it.y }
                    maxOf(crossingMaxY, leftStartY - 2.0f)
                } else if (!isLeftSidebar && rightItems.isNotEmpty()) {
                    val rightStartY = rightItems.minOf { it.y }
                    maxOf(crossingMaxY, rightStartY - 2.0f)
                } else {
                    crossingMaxY
                }

                val bannerLines = mergedLines.filter { it.y <= bannerCutoffY }.sortedBy { it.y }
                val leftLines = mergedLines.filter { it.y > bannerCutoffY && it.maxX <= bestGutter }.sortedBy { it.y }
                val rightLines = mergedLines.filter { it.y > bannerCutoffY && it.minX >= bestGutter }.sortedBy { it.y }

                bannerLines.forEach { fullDocSb.appendLine(it.text) }
                if (isLeftSidebar) {
                    leftLines.forEach { fullDocSb.appendLine(it.text) }
                    rightLines.forEach { fullDocSb.appendLine(it.text) }
                } else {
                    leftLines.forEach { fullDocSb.appendLine(it.text) }
                    rightLines.forEach { fullDocSb.appendLine(it.text) }
                }
            }
        }

        return fullDocSb.toString()
    }

    internal fun consolidateSameYLines(lines: List<SpatialTextLine>): List<SpatialTextLine> {
        if (lines.size <= 1) return lines
        val sorted = lines.sortedWith(compareBy({ it.y }, { it.minX }))
        val result = mutableListOf<SpatialTextLine>()

        var currentGroup = mutableListOf<SpatialTextLine>()

        for (line in sorted) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(line)
            } else {
                val prev = currentGroup.last()
                val isSameY = Math.abs(line.y - prev.y) <= 2.0f
                val xDist = line.minX - prev.maxX
                if (isSameY && xDist in -2.0f..14.0f) {
                    currentGroup.add(line)
                } else {
                    result.add(mergeTextLines(currentGroup))
                    currentGroup = mutableListOf(line)
                }
            }
        }
        if (currentGroup.isNotEmpty()) {
            result.add(mergeTextLines(currentGroup))
        }

        return result
    }

    private fun mergeTextLines(group: List<SpatialTextLine>): SpatialTextLine {
        if (group.size == 1) return SpatialTextLine(cleanSpacedLetters(group[0].text.trim()), group[0].minX, group[0].maxX, group[0].y)
        val minX = group.minOf { it.minX }
        val maxX = group.maxOf { it.maxX }
        val y = group.first().y
        val sb = StringBuilder()
        for (i in group.indices) {
            val item = group[i]
            if (i > 0) {
                val prev = group[i - 1]
                val xDist = item.minX - prev.maxX
                if (item.text.startsWith(" ") || prev.text.endsWith(" ") || xDist > 4.0f) {
                    if (!sb.endsWith(" ")) sb.append(" ")
                }
            }
            sb.append(item.text.trim())
        }
        val combinedText = sb.toString().trim()
        val cleaned = cleanSpacedLetters(combinedText)
        return SpatialTextLine(cleaned, minX, maxX, y)
    }

    private fun extractNativePdfData(file: File): NativePdfData {
        val extractedLinks = mutableListOf<String>()
        val headerLinks = mutableListOf<String>()
        // Tier 1A: Apache PDFBox for Android with Spatial Column Segmentation
        try {
            PDDocument.load(file).use { document ->
                for (page in document.pages) {
                    for (ann in page.annotations) {
                        if (ann is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink) {
                            val act = ann.action
                            if (act is com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI) {
                                val uri = act.uri
                                if (!uri.isNullOrBlank() && !extractedLinks.contains(uri)) {
                                    extractedLinks.add(uri)
                                }
                            }
                        }
                    }
                }

                if (document.numberOfPages > 0) {
                    val page0 = document.getPage(0)
                    val page0Height = page0.mediaBox.height
                    val page0Width = page0.mediaBox.width

                    val stripper = SpatialPageStripper()
                    stripper.sortByPosition = true
                    stripper.startPage = 1
                    stripper.endPage = 1
                    stripper.writeText(document, java.io.StringWriter())

                    val rawLines = stripper.lines
                    val page0Lines = consolidateSameYLines(rawLines)

                    val bodyHeadingWords = listOf(
                        "summary", "profile", "about me", "objective",
                        "experience", "work experience", "employment", "work history", "career",
                        "education", "academics", "qualifications",
                        "skills", "technical skills", "projects", "key projects", "featured projects",
                        "open source", "references", "courses"
                    )

                    fun isBodyHeadingLine(text: String): Boolean {
                        val cleaned = cleanSpacedLetters(text).trim().lowercase()
                        return bodyHeadingWords.any { kw ->
                            cleaned == kw || cleaned.startsWith("$kw ") || cleaned.startsWith("$kw &") ||
                                    cleaned.startsWith("$kw /") || cleaned.startsWith("$kw:") ||
                                    cleaned.endsWith(" $kw") || cleaned.contains(" $kw")
                        }
                    }

                    var bestGutter = -1f
                    var minCross = Int.MAX_VALUE
                    val step = 5f
                    var xProbe = page0Width * 0.18f
                    while (xProbe <= page0Width * 0.82f) {
                        val cross = page0Lines.count { l -> l.minX < xProbe && l.maxX > xProbe }
                        val left = page0Lines.count { l -> l.maxX <= xProbe }
                        val right = page0Lines.count { l -> l.minX >= xProbe }
                        if (left >= 4 && right >= 4 && cross < minCross) {
                            minCross = cross
                            bestGutter = xProbe
                        }
                        xProbe += step
                    }
                    val hasMultiColumn = bestGutter > 0 && minCross <= 6

                    if (!hasMultiColumn) {
                        val firstBodyHeadingY = page0Lines.firstOrNull { isBodyHeadingLine(it.text) }?.y ?: Float.MAX_VALUE
                        for (ann in page0.annotations) {
                            if (ann is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink) {
                                val act = ann.action
                                if (act is com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI) {
                                    val uri = act.uri
                                    if (!uri.isNullOrBlank()) {
                                        val topY = page0Height - ann.rectangle.upperRightY
                                        if (topY < firstBodyHeadingY - 3.0f && !headerLinks.contains(uri)) {
                                            headerLinks.add(uri)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val isLeftSidebar = bestGutter < page0Width * 0.40f
                        val crossingItems = page0Lines.filter { it.minX < bestGutter && it.maxX > bestGutter }
                        val crossingMaxY = crossingItems.maxOfOrNull { it.y } ?: -1f
                        val leftItems = page0Lines.filter { it.maxX <= bestGutter }
                        val rightItems = page0Lines.filter { it.minX >= bestGutter }

                        val bannerCutoffY = if (isLeftSidebar && leftItems.isNotEmpty()) {
                            val leftStartY = leftItems.minOf { it.y }
                            maxOf(crossingMaxY, leftStartY - 2.0f)
                        } else if (!isLeftSidebar && rightItems.isNotEmpty()) {
                            val rightStartY = rightItems.minOf { it.y }
                            maxOf(crossingMaxY, rightStartY - 2.0f)
                        } else {
                            crossingMaxY
                        }

                        val sidebarBodyHeadingY = if (isLeftSidebar) {
                            page0Lines.filter { it.maxX <= bestGutter && isBodyHeadingLine(it.text) }.minOfOrNull { it.y } ?: Float.MAX_VALUE
                        } else {
                            page0Lines.filter { it.minX >= bestGutter && isBodyHeadingLine(it.text) }.minOfOrNull { it.y } ?: Float.MAX_VALUE
                        }

                        for (ann in page0.annotations) {
                            if (ann is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink) {
                                val act = ann.action
                                if (act is com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI) {
                                    val uri = act.uri
                                    if (!uri.isNullOrBlank()) {
                                        val topY = page0Height - ann.rectangle.upperRightY
                                        val centerX = (ann.rectangle.lowerLeftX + ann.rectangle.upperRightX) / 2f
                                        val isSidebarAnn = if (isLeftSidebar) centerX <= bestGutter else centerX >= bestGutter
                                        val isHeader = topY <= bannerCutoffY || (isSidebarAnn && topY < sidebarBodyHeadingY - 3.0f)
                                        if (isHeader && !headerLinks.contains(uri)) {
                                            headerLinks.add(uri)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val text = extractSpatialTextFromDocument(document)
                if (text.isNotBlank() && text.length >= AppConstants.TIER_1_CHAR_THRESHOLD) {
                    return NativePdfData(cleanExtractedText(text), extractedLinks, headerLinks)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "PDFBox extraction notice: ${e.message}")
        }

        // Tier 1B: Raw stream decompressor fallback
        val fallbackText = try {
            val bytes = file.readBytes()
            val textBuilder = StringBuilder()

            val rawString = String(bytes, Charsets.ISO_8859_1)
            val btMatcher = Pattern.compile("BT(.*?)ET", Pattern.DOTALL).matcher(rawString)
            while (btMatcher.find()) {
                val btBlock = btMatcher.group(1) ?: ""
                extractStringsFromBlock(btBlock, textBuilder)
            }

            cleanExtractedText(textBuilder.toString())
        } catch (_: Exception) {
            ""
        }
        return NativePdfData(fallbackText, extractedLinks)
    }

    private fun extractStringsFromBlock(block: String, out: StringBuilder) {
        val tjPattern = Pattern.compile("\\((.*?)\\)\\s*Tj")
        val tjMatcher = tjPattern.matcher(block)
        while (tjMatcher.find()) {
            val s = tjMatcher.group(1)
            if (!s.isNullOrBlank()) {
                out.append(cleanPdfString(s)).append(" ")
            }
        }

        val arrayTjPattern = Pattern.compile("\\[(.*?)\\]\\s*TJ", Pattern.DOTALL)
        val arrayMatcher = arrayTjPattern.matcher(block)
        while (arrayMatcher.find()) {
            val inner = arrayMatcher.group(1) ?: continue
            val strPattern = Pattern.compile("\\((.*?)\\)")
            val strMatcher = strPattern.matcher(inner)
            while (strMatcher.find()) {
                val item = strMatcher.group(1)
                if (!item.isNullOrBlank()) {
                    out.append(cleanPdfString(item))
                }
            }
            out.append(" ")
        }
        out.append("\n")
    }

    private fun cleanPdfString(raw: String): String {
        return raw.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private suspend fun extractWithMlKitFallback(
        file: File,
        onProgress: (step: String, progress: Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val fullTextBuilder = StringBuilder()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            for (i in 0 until pageCount) {
                val pageProgress = 0.1f + (0.35f * ((i + 1).toFloat() / pageCount.coerceAtLeast(1)))
                onProgress("Google ML Kit OCR: Scanning page ${i + 1} of $pageCount...", pageProgress)

                val page = renderer.openPage(i)
                val width = page.width * 2
                val height = page.height * 2
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val task = recognizer.process(inputImage)
                val visionText = Tasks.await(task)

                fullTextBuilder.append(visionText.text).append("\n\n")
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(tag, "ML Kit OCR error: ${e.message}", e)
        } finally {
            try {
                renderer?.close()
                pfd?.close()
                recognizer.close()
            } catch (_: Exception) {}
        }

        cleanExtractedText(fullTextBuilder.toString())
    }

    private fun cleanExtractedText(text: String): String {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun parseRawTextToProfile(
        rawText: String,
        annotationLinks: List<String> = emptyList(),
        detectedHeadings: List<String> = emptyList(),
        headerAnnotationLinks: List<String>? = null
    ): ResumeProfile {
        if (rawText.isBlank() || rawText.lines().count { it.isNotBlank() } < 3) {
            throw IllegalArgumentException("The attached PDF contains no readable text or is corrupted. Please ensure you attach a valid PDF document.")
        }

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val bodyHeadingKeywords = listOf(
            "summary", "profile", "about me", "objective",
            "experience", "employment", "work history", "career",
            "education", "academics", "qualifications",
            "skills", "technical skills", "projects", "references", "courses"
        )

        val headerLines = mutableListOf<String>()
        for (line in lines) {
            val cleaned = cleanSpacedLetters(line).trim().lowercase()
            val isBodyHeading = bodyHeadingKeywords.any { kw ->
                cleaned == kw || cleaned.startsWith("$kw ") || cleaned.startsWith("$kw &") ||
                        cleaned.startsWith("$kw /") || cleaned.startsWith("$kw:") ||
                        cleaned.endsWith(" $kw") || cleaned.contains(" $kw")
            }
            if (isBodyHeading && headerLines.size >= 2) {
                break
            }
            headerLines.add(line)
        }
        val personalHeaderText = headerLines.joinToString("\n")

        val emailMatcher = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})").matcher(personalHeaderText)
        var email = if (emailMatcher.find()) emailMatcher.group(1) ?: "" else ""
        if (email.isBlank()) {
            val topEmailMatcher = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})").matcher(lines.take(20).joinToString("\n"))
            if (topEmailMatcher.find()) email = topEmailMatcher.group(1) ?: ""
        }

        val phoneMatcher = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}").matcher(personalHeaderText)
        var phone = if (phoneMatcher.find()) phoneMatcher.group(0) ?: "" else ""
        if (phone.isBlank()) {
            val topLinesText = lines.take(15).joinToString("\n")
            val topPhoneMatcher = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}").matcher(topLinesText)
            if (topPhoneMatcher.find()) {
                val candidatePhone = topPhoneMatcher.group(0) ?: ""
                if (!candidatePhone.matches(Regex("^(?:19|20)\\d{2}$"))) {
                    phone = candidatePhone
                }
            }
        }

        // Fallback to PDF annotations for embedded mailto / tel links
        if (email.isBlank()) {
            email = (headerAnnotationLinks ?: annotationLinks).firstOrNull { it.startsWith("mailto:", ignoreCase = true) }
                ?.removePrefix("mailto:")
                ?.removePrefix("MAILTO:")
                ?.substringBefore("?")
                ?.trim() ?: ""
        }

        if (phone.isBlank()) {
            phone = (headerAnnotationLinks ?: annotationLinks).firstOrNull { it.startsWith("tel:", ignoreCase = true) }
                ?.removePrefix("tel:")
                ?.removePrefix("TEL:")
                ?.trim() ?: ""
        }

        // Filter out direct mobile app store links, packages, and mail/tel protocols from portfolio links
        val candidateAnnotationLinks = (headerAnnotationLinks ?: annotationLinks).filter { link ->
            val lower = link.lowercase().trim()
            !lower.startsWith("mailto:") &&
                    !lower.startsWith("tel:") &&
                    !lower.contains("play.google.com") &&
                    !lower.contains("apps.apple.com") &&
                    !lower.contains("pub.dev") &&
                    !(lower.contains("github.com/") &&
                            link.removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/").filter { s -> s.isNotBlank() }.size > 2)
        }

        val linkedinMatcher = Pattern.compile("https?://(www\\.)?linkedin\\.com/in/[a-zA-Z0-9_-]+|https?://(www\\.)?linkedin\\.com/|linkedin\\.com/in/[a-zA-Z0-9_-]+").matcher(personalHeaderText)
        var linkedinUrl = if (linkedinMatcher.find()) {
            val matched = linkedinMatcher.group(0) ?: ""
            if (matched.startsWith("http") || matched.contains(".com")) matched else ""
        } else ""

        if (linkedinUrl.isBlank()) {
            linkedinUrl = candidateAnnotationLinks.firstOrNull { it.contains("linkedin.com", ignoreCase = true) } ?: ""
        }

        val githubMatcher = Pattern.compile("https?://(www\\.)?github\\.com/[a-zA-Z0-9_-]+|github\\.com/[a-zA-Z0-9_-]+").matcher(personalHeaderText)
        var githubUrl = if (githubMatcher.find()) {
            val matched = githubMatcher.group(0) ?: ""
            if (matched.startsWith("http") || matched.contains(".com")) matched else ""
        } else ""

        if (githubUrl.isBlank()) {
            githubUrl = candidateAnnotationLinks.firstOrNull {
                it.contains("github.com", ignoreCase = true) &&
                        !it.contains("packages", ignoreCase = true) &&
                        it.removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/").filter { s -> s.isNotBlank() }.size <= 2
            } ?: ""
        }

        // Top website/portfolio/social link: first non-linkedin, non-github candidate link
        val otherLinks = candidateAnnotationLinks.filter { it != linkedinUrl && it != githubUrl }
        var websiteUrl = otherLinks.firstOrNull() ?: ""
        if (websiteUrl.isBlank()) {
            val webMatcher = Pattern.compile("https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]*)?").matcher(personalHeaderText)
            while (webMatcher.find()) {
                val candidate = webMatcher.group(0) ?: ""
                val lower = candidate.lowercase()
                if (!lower.contains("linkedin.com") && !lower.contains("github.com") && !lower.contains("@")) {
                    websiteUrl = candidate
                    break
                }
            }
        }
        val extraLinks = if (otherLinks.size > 1) otherLinks.drop(1) else emptyList()

        val candidateNameLines = headerLines.filter { line ->
            val l = line.lowercase().trim()
            !l.contains("@") &&
                    !l.contains("http") &&
                    !l.contains("linkedin.com") &&
                    !l.contains("github.com") &&
                    !l.contains("resume") &&
                    !l.contains("curriculum vitae") &&
                    !l.contains("driving license") &&
                    !l.contains("nationality") &&
                    !l.contains("place of birth") &&
                    !l.contains("date of birth") &&
                    !l.startsWith("address") &&
                    !l.startsWith("phone") &&
                    !l.startsWith("email") &&
                    !l.equals("details", ignoreCase = true) &&
                    !l.equals("info", ignoreCase = true) &&
                    !l.equals("links", ignoreCase = true) &&
                    !isLikelyLocation(line) &&
                    !line.matches(Regex(".*\\b\\d{3}[-.\\s]?\\d{4}\\b.*")) &&
                    !line.matches(Regex(".*\\b\\d{5}\\b.*"))
        }

        var candidateName = ""
        var headline = ""

        val rawFirst = candidateNameLines.firstOrNull() ?: ""
        val cleanFirst = cleanSpacedLetters(rawFirst)
            .replace(Regex("(?i)\\b(Details|Info|Links|Resume|CV)\\b"), "")
            .trim()

        if (cleanFirst.contains(",")) {
            val parts = cleanFirst.split(",", limit = 2).map { it.trim() }
            val nameCand = parts[0]
            val roleCand = parts.getOrNull(1) ?: ""
            if (isLikelyPersonalRole(roleCand)) {
                candidateName = nameCand
                headline = roleCand
            } else {
                candidateName = cleanFirst
            }
        } else if (cleanFirst.isNotBlank() && cleanFirst.split(Regex("\\s+")).size == 1 && candidateNameLines.size > 1) {
            val second = cleanSpacedLetters(candidateNameLines[1])
                .replace(Regex("(?i)\\b(Details|Info|Links|Resume|CV)\\b"), "")
                .trim()
            if (second.isNotBlank() && second.split(Regex("\\s+")).size == 1 && !isLikelyPersonalRole(second) && !isLikelyLocation(second)) {
                candidateName = "$cleanFirst $second"
                val third = candidateNameLines.getOrNull(2)?.let { cleanSpacedLetters(it).trim() } ?: ""
                if (isLikelyPersonalRole(third)) {
                    headline = third
                }
            } else {
                candidateName = cleanFirst
                if (isLikelyPersonalRole(second)) {
                    headline = second
                }
            }
        } else {
            candidateName = cleanFirst
            for (line in candidateNameLines.drop(1)) {
                val cleaned = cleanSpacedLetters(line).trim()
                if (isLikelyPersonalRole(cleaned)) {
                    headline = cleaned
                    break
                }
            }
        }

        val detectedLocation = extractCandidateLocation(headerLines, email, phone)

        val contactInfo = ContactInfo(
            email = email,
            phone = phone,
            linkedinUrl = linkedinUrl,
            githubUrl = githubUrl,
            websiteUrl = websiteUrl,
            customLinks = extraLinks
        )

        // Dynamically evaluate section headings and slice document into section blocks
        val headingsToEvaluate = if (detectedHeadings.isNotEmpty()) detectedHeadings else detectResumeSectionHeadings(rawText)
        val sectionMap = sliceSections(rawText, headingsToEvaluate)

        // Dynamic Summary Extraction
        val summaryRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:SUMMARY|PROFESSIONAL SUMMARY|EXECUTIVE SUMMARY|PROFILE|ABOUT ME)[ \\t]*[|:–\\-]*[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EMPLOYMENT HISTORY|WORK HISTORY|CAREER HISTORY|(?:EXPERIENCE[ \\t]*(?:[|:–\\-]|$))|PROJECTS|FEATURED PROJECTS|OPEN SOURCE|TECHNICAL SKILLS|SKILLS|EDUCATION|ACADEMICS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val summaryHeading = headingsToEvaluate.firstOrNull { h ->
            val l = h.lowercase()
            l.contains("summary") || l.contains("profile") || l.contains("objective") || l.contains("about")
        }
        val extractedSummary = (if (summaryHeading != null) sectionMap[summaryHeading] else null)?.replace(Regex("[\\r\\n]+"), " ")
            ?: summaryRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim()?.replace(Regex("[\\r\\n]+"), " ")
            ?: ""

        // Dynamic Work Experience Extraction
        val expRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EMPLOYMENT HISTORY|WORK HISTORY|CAREER HISTORY|(?:EXPERIENCE[ \\t]*(?:[|:–\\-]|$)))[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:PROJECTS|FEATURED PROJECTS|PERSONAL PROJECTS|KEY PROJECTS|OPEN SOURCE|SKILLS|TECHNICAL SKILLS|EDUCATION|ACADEMICS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val expHeading = headingsToEvaluate.firstOrNull { h ->
            val l = h.lowercase()
            l.contains("experience") || l.contains("employment") || l.contains("work history") || l.contains("career")
        }
        val expText = (if (expHeading != null) sectionMap[expHeading] else null)
            ?: expRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val parsedWorkExperience = parseWorkExperience(expText, annotationLinks)

        // Dynamic Projects Extraction
        val projRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:FEATURED PROJECTS|PERSONAL PROJECTS|KEY PROJECTS|ACADEMIC PROJECTS|PROJECTS)[ \\t]*[|:–\\-]*[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:OPEN[- ]SOURCE(?: CONTRIBUTIONS)?|SKILLS|TECHNICAL SKILLS|EDUCATION|ACADEMICS|PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EXPERIENCE|CERTIFICATIONS|PUBLICATIONS|AWARDS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val projHeading = headingsToEvaluate.firstOrNull { h ->
            val l = h.lowercase()
            l.contains("project")
        }
        val projText = (if (projHeading != null) sectionMap[projHeading] else null)
            ?: projRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val parsedProjects = parseProjects(projText, annotationLinks)

        // Dynamic Skills Extraction
        val skillsRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?((?:TECHNICAL SKILLS|CORE COMPETENCIES|SKILLS & TOOLS|AREAS OF EXPERTISE|TECHNOLOGIES|SKILLS))[ \\t]*[|:–\\-]*[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:EDUCATION|ACADEMICS|PROJECTS|FEATURED PROJECTS|OPEN SOURCE|PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EXPERIENCE|CERTIFICATIONS|PUBLICATIONS|AWARDS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val skillsMatch = skillsRegex.find(rawText)
        val rawSkillsHeader = skillsMatch?.groupValues?.getOrNull(1)?.trim() ?: "Technical Skills"
        val skillsHeading = headingsToEvaluate.firstOrNull { h ->
            val l = h.lowercase()
            l.contains("skill") || l.contains("technolog") || l.contains("competenc")
        }
        val skillsText = (if (skillsHeading != null) sectionMap[skillsHeading] else null)
            ?: skillsMatch?.groupValues?.getOrNull(2)?.trim().orEmpty()
        val parsedSkills = parseSkills(skillsText, skillsHeading ?: rawSkillsHeader)

        // Dynamic Education Extraction
        val eduRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:EDUCATION & CREDENTIALS|ACADEMIC BACKGROUND|ACADEMICS|QUALIFICATIONS|EDUCATION)[ \\t]*[|:–\\-]*[^\\r\\n]*(.*?)[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:PROJECTS|OPEN SOURCE|SKILLS|TECHNICAL SKILLS|PROFESSIONAL EXPERIENCE)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val eduMatch = eduRegex.find(rawText)
        val eduHeading = headingsToEvaluate.firstOrNull { h ->
            val l = h.lowercase()
            l.contains("education") || l.contains("academic") || l.contains("qualification")
        }
        val eduText = (if (eduHeading != null) sectionMap[eduHeading] else null)
            ?: if (eduMatch != null) {
                val sameLineText = eduMatch.groupValues.getOrNull(1)?.trim() ?: ""
                val nextLinesText = eduMatch.groupValues.getOrNull(2)?.trim() ?: ""
                "$sameLineText\n$nextLinesText".trim()
            } else ""
        val parsedEducation = parseEducation(eduText)

        // Dynamic Additional Sections (e.g. Courses, Achievements, Hobbies, Languages, Open Source, etc.)
        val dynamicList = parseDynamicSections(rawText, annotationLinks, headingsToEvaluate, sectionMap)

        // Every resume always displays the 6 core sections in the exact standard order:
        // 1. Personal Info & Contact
        // 2. Professional Summary
        // 3. Work Experience
        // 4. Projects
        // 5. Technical Skills
        // 6. Education
        // followed by dynamic/custom sections in the order they appear.
        val standardCoreOrder = listOf(
            ResumeSectionConstants.SECTION_PERSONAL,
            ResumeSectionConstants.SECTION_SUMMARY,
            ResumeSectionConstants.SECTION_EXPERIENCE,
            ResumeSectionConstants.SECTION_PROJECTS,
            ResumeSectionConstants.SECTION_SKILLS,
            ResumeSectionConstants.SECTION_EDUCATION
        )

        val detectedCustomOrder = dynamicList.map { "custom_${it.sectionTitle}" }.distinct()
        val initialSectionOrder = standardCoreOrder + detectedCustomOrder

        return ResumeProfile(
            hero = ProfileHero(
                fullName = toTitleCase(candidateName),
                headlineTitle = toTitleCase(headline),
                location = detectedLocation,
                contactInfo = contactInfo
            ),
            coreSections = CoreSections(
                profileSummary = extractedSummary,
                workExperience = parsedWorkExperience,
                projects = parsedProjects,
                skills = parsedSkills,
                education = parsedEducation
            ),
            dynamicSections = dynamicList,
            sectionOrder = initialSectionOrder
        )
    }

    private fun isLikelyLocation(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.isBlank() || lower.length > 80) return false
        if (isLikelyPersonalRole(text)) return false
        val containsKnownPlace = lower.contains("remote") || lower.contains("india") ||
                lower.contains("noida") || lower.contains("ghaziabad") || lower.contains("uttar pradesh") ||
                lower.contains("punjab") || lower.contains("mohali") || lower.contains("delhi") ||
                lower.contains("bangalore") || lower.contains("bengaluru") || lower.contains("gurgaon") ||
                lower.contains("gurugram") ||
                lower.contains("hyderabad") || lower.contains("mumbai") || lower.contains("pune") ||
                lower.contains("dehradun") || lower.contains("uttarakhand") ||
                lower.contains("usa") || lower.contains("united states") || lower.contains("canada") ||
                lower.contains("uk") || lower.contains("united kingdom") || lower.contains("germany") ||
                lower.contains("los angeles") || lower.contains("new york") || lower.contains("san francisco")
        val looksLikeCityState = text.matches(Regex("^[A-Za-z\\s.'-]+(?:,\\s*[A-Za-z0-9\\s.'-]+)+$")) &&
                !lower.contains("designer") &&
                !lower.contains("developer") &&
                !lower.contains("engineer") &&
                !lower.contains("manager") &&
                !lower.contains("consultant") &&
                !lower.contains("director")
        val hasStreet = text.matches(Regex(".*\\b\\d{1,5}\\s+[A-Za-z0-9.\\s]+(?:St|Street|Ave|Avenue|Rd|Road|Blvd|Boulevard|Lane|Ln|Dr|Drive|Way)\\b.*", RegexOption.IGNORE_CASE))
        val hasZip = text.matches(Regex(".*\\b\\d{5}(?:-\\d{4})?\\b.*"))
        return containsKnownPlace || looksLikeCityState || hasStreet || hasZip
    }

    private fun isLikelyAddressContinuation(line: String): Boolean {
        if (line.isBlank() || isLikelyPersonalRole(line)) return false
        val lower = line.lowercase().trim()
        if (lower.contains("@") || lower.contains("http") || lower.contains("phone")) return false
        val hasZip = line.matches(Regex(".*\\b\\d{5}(?:-\\d{4})?\\b.*"))
        val hasCityCountry = lower.contains("usa") || lower.contains("united states") ||
                lower.contains("los angeles") || lower.contains("new york") ||
                lower.contains("ca") || lower.contains("ny") || lower.contains("india")
        val looksLikeCityState = line.matches(Regex("^[A-Za-z\\s.'-]+(?:,\\s*[A-Za-z0-9\\s.'-]+)+$"))
        return hasZip || hasCityCountry || looksLikeCityState
    }

    private fun extractCandidateLocation(
        headerLines: List<String>,
        candidateEmail: String,
        candidatePhone: String
    ): String {
        fun cleanContactTokens(line: String): String {
            var cleaned = line
            if (candidateEmail.isNotBlank()) cleaned = cleaned.replace(candidateEmail, "")
            if (candidatePhone.isNotBlank()) cleaned = cleaned.replace(candidatePhone, "")
            cleaned = cleaned.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "")
            cleaned = cleaned.replace(Regex("(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}"), "")
            cleaned = cleaned.replace(Regex("(?i)\\b(?:address|location):?\\b"), "")
            cleaned = cleaned.replace(Regex("(?i)\\b(?:phone|email|mobile|tel):?\\b"), "")
            cleaned = cleaned.replace(Regex("(?i)\\b(?:details|info|links|resume|cv)\\b"), "")
            cleaned = cleaned.replace(Regex("(?i)\\b(?:linkedin|github|portfolio|website)\\b"), "")
            cleaned = cleaned.replace(Regex("[|•·]"), " ")
            return cleaned.replace(Regex("\\s{2,}"), " ").trim()
        }

        val cleanedCandidateLines = mutableListOf<String>()
        for (rawLine in headerLines) {
            val l = rawLine.trim().lowercase()
            if (l.contains("linkedin.com") || l.contains("github.com") ||
                l.contains("driving license") || l.contains("nationality") ||
                l.contains("place of birth") || l.contains("date of birth") ||
                l.equals("details") || l.equals("info") || l.equals("links")) {
                continue
            }
            val cleaned = cleanContactTokens(rawLine)
            if (cleaned.isNotBlank() && !isLikelyPersonalRole(cleaned)) {
                cleanedCandidateLines.add(cleaned)
            }
        }

        // Look for lines matching street address, zip code, or known location patterns
        val candidateLines = mutableListOf<String>()

        for (i in cleanedCandidateLines.indices) {
            val line = cleanedCandidateLines[i]

            val hasStreet = line.matches(Regex(".*\\b\\d{1,5}\\s+[A-Za-z0-9.\\s]+(?:St|Street|Ave|Avenue|Rd|Road|Blvd|Boulevard|Lane|Ln|Dr|Drive|Way)\\b.*", RegexOption.IGNORE_CASE))
            val hasZip = line.matches(Regex(".*\\b\\d{5}(?:-\\d{4})?\\b.*")) || line.matches(Regex(".*\\b\\d{6}\\b.*"))
            val hasKnownPlace = isLikelyLocation(line)

            if (hasStreet || hasZip || hasKnownPlace) {
                candidateLines.add(line)

                // Check if the very next line is a continuation (like "Los Angeles" or "10005, USA")
                val nextLine = cleanedCandidateLines.getOrNull(i + 1)?.trim().orEmpty()
                if (isLikelyAddressContinuation(nextLine)) {
                    candidateLines.add(nextLine)
                }
                break
            }
        }

        if (candidateLines.isEmpty()) {
            return ""
        }

        // Join parts cleanly and clean up duplicate commas/spaces
        val combined = candidateLines.joinToString(" ")
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return combined.removePrefix(",").removeSuffix(",").trim()
    }

    private fun isLikelyPersonalRole(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2 || trimmed.length > 130) return false
        val lower = trimmed.lowercase()
        if (lower.contains("years") || lower.contains("experience in") || lower.contains("building") || trimmed.endsWith(".")) return false

        // Ignored non-role words / headings / metadata
        val ignoredWords = setOf(
            "details", "info", "links", "resume", "cv", "curriculum vitae",
            "profile", "summary", "experience", "education", "skills",
            "references", "contact", "address", "phone", "email",
            "nationality", "driving license", "place of birth", "date of birth"
        )
        if (ignoredWords.contains(lower)) return false
        if (lower.contains("@") || lower.contains("http") || lower.contains(".com")) return false

        // Common role keywords
        val roleKeywords = listOf(
            "developer", "engineer", "designer", "consultant", "manager",
            "director", "teller", "analyst", "architect", "lead",
            "specialist", "assistant", "coordinator", "officer", "administrator",
            "intern", "executive", "expert", "associate", "scientist",
            "technician", "representative", "programmer", "writer", "accountant",
            "banker", "recruiter", "strategist", "producer"
        )
        if (roleKeywords.any { lower.contains(it) }) return true

        // Compound headlines with separators e.g. "Flutter Developer | Mobile Application Developer"
        if (trimmed.contains("|") || trimmed.contains("/") || trimmed.contains("&")) {
            val segments = trimmed.split(Regex("[|/&]")).map { it.trim() }
            if (segments.any { seg -> roleKeywords.any { kw -> seg.lowercase().contains(kw) } }) {
                return true
            }
        }

        return false
    }

    private fun isInstitutionLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("college") || lower.contains("university") ||
                lower.contains("institute") || lower.contains("school") ||
                lower.contains("academy") || lower.contains("campus") ||
                lower.contains("aktu") || lower.contains("board") ||
                lower.contains("vidyalaya") || lower.contains("polytechnic") ||
                lower.contains("faculty") || lower.contains("institution")
    }

    private fun parseWorkExperience(expText: String, annotationLinks: List<String> = emptyList()): List<WorkExperienceItem> {
        if (expText.isBlank()) return emptyList()

        val items = mutableListOf<WorkExperienceItem>()
        val rawLines = expText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Normalize lines where dates are wrapped across line breaks:
        // Case 1: Line ends with open date range e.g. "Sep 2025 -" and next line is "Present"
        // Case 2: Line ends with "Apr 2023 – Oct" and next line starts with "2025 · Noida"
        val lines = mutableListOf<String>()
        var lineIdx = 0
        while (lineIdx < rawLines.size) {
            val curr = rawLines[lineIdx]
            val openDateMatch = Regex("(?:(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?|\\d{1,2}/)?\\s*\\d{4}|\\d{1,2}/\\d{2,4})\\s*(?:–|-|—|to)\\s*(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?)?\\s*$", RegexOption.IGNORE_CASE).find(curr)
            if (openDateMatch != null && lineIdx + 1 < rawLines.size) {
                val next = rawLines[lineIdx + 1]
                val closeDateMatch = Regex("^(?:(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?|\\d{1,2}/)?\\s*(?:\\d{2,4}|Present|Current))\\b", RegexOption.IGNORE_CASE).find(next)
                if (closeDateMatch != null) {
                    val remainingNext = next.substring(closeDateMatch.value.length).trim()
                    val joinedLine = "${curr.trim()} ${closeDateMatch.value.trim()}"
                    if (remainingNext.isNotBlank()) {
                        val cleanedRemaining = remainingNext.replace(Regex("^[·|•,—–\\s]+"), "").trim()
                        if (isLikelyLocation(cleanedRemaining) || cleanedRemaining.length < 30) {
                            lines.add("$joinedLine · $cleanedRemaining")
                        } else {
                            lines.add(joinedLine)
                            lines.add(cleanedRemaining)
                        }
                    } else {
                        lines.add(joinedLine)
                    }
                    lineIdx += 2
                    continue
                }
            }
            lines.add(curr)
            lineIdx++
        }

        val datePattern = Regex(
            "(?:(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?|\\d{1,2}/)?\\s*\\d{4}|\\d{1,2}/\\d{2,4})\\s*(?:–|-|—|to)\\s*(?:(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?|\\d{1,2}/)?\\s*(?:\\d{2,4}|Present|Current))",
            RegexOption.IGNORE_CASE
        )

        fun cleanTextFromDates(text: String): String {
            return text.replace(datePattern, "")
                .replace(Regex("\\b(?:19|20)\\d{2}\\b"), "")
                .replace(Regex("\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^[|:–\\-\\s,]+|[|:–\\-\\s,]+$"), "")
                .trim()
        }

        val roleWordRegex = Regex(
            "\\b(?:sde(?:-[0-9]+)?|swe(?:-[0-9]+)?|mts|mle|developer|engineer|architect|lead|manager|intern|internship|consultant|specialist|analyst|designer|programmer|administrator|associate|director|officer|cto|ceo|vp|trainee|fresher|tester|qa|fellow|instructor|researcher|scientist|coordinator|teller)\\b",
            RegexOption.IGNORE_CASE
        )

        fun isCompanyIndicator(text: String): Boolean {
            val lower = text.lowercase()
            return lower.contains("pvt") || lower.contains("ltd") || lower.contains("inc") ||
                    lower.contains("corp") || lower.contains("llc") || lower.contains("technologies") ||
                    lower.contains("services") || lower.contains("solutions") || lower.contains("company") ||
                    lower.contains("labs") || lower.contains("agency") || lower.contains("studio") ||
                    lower.contains("software")
        }

        fun isLikelyRole(text: String): Boolean {
            if (isCompanyIndicator(text)) return false
            val lower = text.lowercase().trim()
            if (lower.startsWith("integrated") || lower.startsWith("responsible") ||
                lower.startsWith("developed") || lower.startsWith("engineered") ||
                lower.startsWith("architected") || lower.startsWith("built") ||
                lower.startsWith("managed") || lower.startsWith("created") ||
                lower.startsWith("designed") || lower.startsWith("maintained") ||
                lower.startsWith("worked") || lower.startsWith("implemented") ||
                lower.startsWith("leveraged") || lower.startsWith("published") ||
                lower.startsWith("optimized") || lower.startsWith("deployed") ||
                lower.startsWith("client application")
            ) {
                return false
            }
            return roleWordRegex.containsMatchIn(lower)
        }

        fun parseHeaderSegments(text: String): Pair<String, String>? {
            val clean = cleanTextFromDates(text)
            // 1. Explicit [Role] at [Company] structure
            val atMatch = Regex("^(.*?)\\s+at\\s+(.*?)$", RegexOption.IGNORE_CASE).find(clean)
            if (atMatch != null) {
                val candidateRole = atMatch.groupValues[1].trim()
                val candidateComp = atMatch.groupValues[2].trim()
                if (candidateRole.isNotBlank() && candidateComp.isNotBlank()) {
                    return Pair(candidateComp, candidateRole)
                }
            }

            // 2. Delimit on |, •, ·, —, –, commas, or space-flanked hyphens (preserving embedded hyphens like SDE-2)
            val delimiterRegex = Regex("\\s*[|•·—–]\\s*|\\s+-\\s+|\\s*,\\s*")
            val segments = clean.split(delimiterRegex).map { it.trim() }.filter { it.isNotBlank() }
            if (segments.size >= 2) {
                var comp = ""
                var role = ""
                for (seg in segments) {
                    if (isCompanyIndicator(seg)) {
                        if (comp.isBlank()) comp = seg
                    } else if (isLikelyRole(seg)) {
                        if (role.isBlank()) role = seg
                    }
                }
                if (comp.isNotBlank() && role.isBlank()) {
                    val other = segments.firstOrNull { it != comp && !isLikelyLocation(it) }
                    if (other != null) role = other
                } else if (role.isNotBlank() && comp.isBlank()) {
                    val other = segments.firstOrNull { it != role && !isLikelyLocation(it) }
                    if (other != null) comp = other
                } else if (comp.isBlank() && role.isBlank()) {
                    comp = segments[0]
                    role = segments[1]
                }
                return Pair(comp, role)
            }
            return null
        }

        var currentCompany = ""
        var currentRole = ""
        var currentStart = ""
        var currentEnd = ""
        val currentBullets = mutableListOf<String>()
        val currentClientApps = mutableListOf<ClientAppItem>()

        fun parseClientAppsLine(line: String) {
            val clean = line.replace(Regex("^(?:Client applications|Client apps|Key projects|Applications|Apps):?\\s*", RegexOption.IGNORE_CASE), "")
            val appNames = clean.split(Regex("[|•·]")).map { it.trim() }.filter { it.isNotBlank() }
            for (appName in appNames) {
                val simplifiedName = appName.lowercase().replace(" ", "").replace("_", "").replace("-", "")
                val matchedUrl = annotationLinks.firstOrNull { link ->
                    val lower = link.lowercase()
                    if (lower.contains("linkedin.com") || lower.contains("github.com")) {
                        false
                    } else {
                        val cleanUrl = lower.replace("_", "").replace("-", "").replace(".", "")
                        cleanUrl.contains(simplifiedName) || (simplifiedName.length >= 4 && cleanUrl.contains(simplifiedName.take(6)))
                    }
                } ?: ""
                if (currentClientApps.none { it.name.equals(appName, ignoreCase = true) }) {
                    currentClientApps.add(ClientAppItem(name = appName, url = matchedUrl))
                }
            }
        }

        fun flushCurrent() {
            val cleanedCompany = cleanTextFromDates(currentCompany)
            if (cleanedCompany.isNotBlank()) {
                val cleanedRole = cleanTextFromDates(currentRole).trim()
                items.add(
                    WorkExperienceItem(
                        id = UUID.randomUUID().toString(),
                        companyName = cleanedCompany,
                        roleTitle = cleanedRole,
                        startDate = currentStart,
                        endDate = currentEnd.ifBlank { if (currentStart.isNotBlank()) "Present" else "" },
                        bulletPoints = currentBullets.toList(),
                        techStackTags = emptyList(),
                        clientApps = currentClientApps.toList()
                    )
                )
                currentBullets.clear()
                currentClientApps.clear()
                currentRole = ""
                currentCompany = ""
                currentStart = ""
                currentEnd = ""
            }
        }

        val pendingHeaderLines = mutableListOf<String>()

        for (line in lines) {
            val dateMatch = datePattern.find(line)
            if (dateMatch != null) {
                val dateStr = dateMatch.value.trim()
                val parts = dateStr.split(Regex("[–\\-—]|\\bto\\b"), 2).map { it.trim() }
                val start = parts.getOrNull(0) ?: ""
                val end = parts.getOrNull(1) ?: "Present"

                val withoutDate = line.replace(dateStr, "").replace(Regex("^[|:–\\-\\s,]+|[|:–\\-\\s,]+$"), "").trim()

                // If we already have bullets accumulated, this new date marks the start of the next job position
                if (currentBullets.isNotEmpty()) {
                    flushCurrent()
                }

                currentStart = start
                currentEnd = end

                val segmentsResult = parseHeaderSegments(withoutDate)
                if (segmentsResult != null) {
                    val (c, r) = segmentsResult
                    if (c.isNotBlank()) currentCompany = c
                    if (r.isNotBlank()) currentRole = r
                } else if (withoutDate.isNotBlank()) {
                    if (isLikelyRole(withoutDate)) {
                        currentRole = withoutDate
                    } else if (isCompanyIndicator(withoutDate)) {
                        if (currentCompany.isBlank()) currentCompany = withoutDate
                    } else if (!isLikelyLocation(withoutDate) && currentCompany.isBlank()) {
                        currentCompany = withoutDate
                    }
                }

                // Process any pending header lines collected before this date
                for (pending in pendingHeaderLines) {
                    val pSeg = parseHeaderSegments(pending)
                    if (pSeg != null) {
                        val (c, r) = pSeg
                        if (c.isNotBlank() && currentCompany.isBlank()) currentCompany = c
                        if (r.isNotBlank() && currentRole.isBlank()) currentRole = r
                    } else if (isLikelyRole(pending)) {
                        if (currentRole.isBlank()) currentRole = pending
                    } else if (isCompanyIndicator(pending)) {
                        if (currentCompany.isBlank()) currentCompany = pending
                    } else if (!isLikelyLocation(pending)) {
                        if (currentCompany.isBlank()) currentCompany = pending
                        else if (currentRole.isBlank() && isLikelyRole(pending)) currentRole = pending
                    }
                }
                pendingHeaderLines.clear()

            } else if (isBulletStart(line)) {
                // If we were accumulating pending header lines, resolve them first
                if (pendingHeaderLines.isNotEmpty()) {
                    for (pending in pendingHeaderLines) {
                        val pSeg = parseHeaderSegments(pending)
                        if (pSeg != null) {
                            val (c, r) = pSeg
                            if (c.isNotBlank() && currentCompany.isBlank()) currentCompany = c
                            if (r.isNotBlank() && currentRole.isBlank()) currentRole = r
                        } else if (isLikelyRole(pending) && currentRole.isBlank()) {
                            currentRole = pending
                        } else if (isCompanyIndicator(pending) && currentCompany.isBlank()) {
                            currentCompany = pending
                        } else if (!isLikelyLocation(pending) && currentCompany.isBlank()) {
                            currentCompany = pending
                        } else if (pending.startsWith("client app", ignoreCase = true) || pending.startsWith("client application", ignoreCase = true)) {
                            parseClientAppsLine(pending)
                        } else if (pending.startsWith("project", ignoreCase = true)) {
                            currentBullets.add(pending)
                        }
                    }
                    pendingHeaderLines.clear()
                }

                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    currentBullets.add(cleaned)
                }
            } else {
                // Non-bullet, non-date line
                val lowerLine = line.lowercase()
                if (lowerLine.startsWith("client application") || lowerLine.startsWith("client app") || lowerLine.startsWith("applications:") || lowerLine.startsWith("apps:")) {
                    parseClientAppsLine(line)
                } else if (lowerLine.startsWith("environment:") || lowerLine.startsWith("tech stack:")) {
                    currentBullets.add(line)
                } else if (currentBullets.isNotEmpty()) {
                    // We already have bullets for the current position.
                    // Is this line a multi-line wrap continuation of the preceding bullet?
                    val startsWithLower = line.firstOrNull()?.isLowerCase() == true
                    val startsWithOperatorOrDigit = line.matches(Regex("^[<>=+~%$0-9].*"))
                    val lastBullet = currentBullets.last()
                    val continuationWords = listOf("and", "with", "using", "for", "in", "to", "by", "from", "on", "into", "through", "across", "improving", "reducing", "ensuring", "achieving", "as", "or")
                    val lastEndsWithOpenSentence = !lastBullet.endsWith(".") && !lastBullet.endsWith("!") && !lastBullet.endsWith("?")
                    val lastEndsWithPreposition = continuationWords.any { lastBullet.trim().endsWith(" $it", ignoreCase = true) }
                    val startsWithPreposition = continuationWords.any { line.startsWith("$it ", ignoreCase = true) }

                    val isContinuation = startsWithLower ||
                            startsWithOperatorOrDigit ||
                            lastEndsWithPreposition ||
                            lastBullet.endsWith(",") ||
                            lastBullet.endsWith(";") ||
                            (lastEndsWithOpenSentence && (startsWithPreposition || line.length > 25))

                    if (isContinuation) {
                        val cleaned = cleanBulletLine(line)
                        if (cleaned.isNotBlank()) {
                            currentBullets[currentBullets.lastIndex] = "$lastBullet $cleaned"
                        }
                    } else {
                        // This line starts a new job entry (e.g. "Techwink Services")
                        flushCurrent()
                        pendingHeaderLines.add(line)
                    }
                } else {
                    if (currentCompany.isNotBlank() && currentRole.isNotBlank()) {
                        // Company and role are already extracted for this entry (e.g. from header line).
                        // Standalone line here is a foreign company location or branch (e.g. Arnsberg, Brymbo).
                        // Per requirement: locations must never be forcefully crammed into companyName or roleTitle.
                    } else {
                        val pSeg = parseHeaderSegments(line)
                        if (pSeg != null) {
                            val (c, r) = pSeg
                            if (c.isNotBlank() && currentCompany.isBlank()) currentCompany = c
                            if (r.isNotBlank() && currentRole.isBlank()) currentRole = r
                        } else if (isLikelyRole(line) && currentRole.isBlank()) {
                            currentRole = line
                        } else if (isCompanyIndicator(line) && currentCompany.isBlank()) {
                            currentCompany = line
                        } else if (!isLikelyLocation(line) && currentCompany.isBlank()) {
                            currentCompany = line
                        } else {
                            pendingHeaderLines.add(line)
                        }
                    }
                }
            }
        }
        flushCurrent()

        return items
    }

    private fun isBulletStart(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.matches(Regex("^[.•\\u2022\\u25cf\\u00b7\\u2043\\u25e6\\u25a0\\u25aa\\u25ab\\u25c6\\u25c7\\u27a4\\u27a2\\u2714\\u2713■▪▫◆◇❖➢➤✔✓\\-*–—].*"))) return true
        if (trimmed.matches(Regex("^[0-9]+[.)\\-]\\s+.*"))) return true
        if (trimmed.matches(Regex("^\\[[0-9]+\\]\\s+.*"))) return true
        val actionVerbs = listOf(
            "Engineered", "Designed", "Leveraged", "Integrated", "Published", "Developed",
            "Improved", "Architected", "Built", "Created", "Implemented", "Maintained",
            "Managed", "Led", "Spearheaded", "Formulated", "Orchestrated", "Automated",
            "Collaborated", "Resolved", "Refactored", "Constructed", "Configured",
            "Delivered", "Executed", "Authored", "Established", "Optimized", "Reduced",
            "Increased", "Enhanced", "Transformed", "Accelerated", "Pioneered",
            "Supported", "Assisted", "Prepared", "Ensured", "Provided", "Coached",
            "Supervised", "Coordinated", "Conducted", "Facilitated", "Directed",
            "Administered", "Trained", "Earned", "Outlined", "Shipped", "Revamped", "Scaled"
        )
        return actionVerbs.any { trimmed.startsWith("$it ", ignoreCase = false) }
    }

    private fun cleanBulletLine(line: String): String {
        return line.trim()
            .replace(Regex("^[.•\\u2022\\u25cf\\u00b7\\u2043\\u25e6\\u25a0\\u25aa\\u25ab\\u25c6\\u25c7\\u27a4\\u27a2\\u2714\\u2713■▪▫◆◇❖➢➤✔✓\\-*–—\\s]+"), "")
            .replace(Regex("^[0-9]+[.)\\-]\\s*"), "")
            .replace(Regex("^\\[[0-9]+\\]\\s*"), "")
            .trim()
    }

    private data class ParsedProjectHeader(
        val title: String,
        val techStackTags: List<String>
    )

    private val projectLinkKeywords = setOf(
        "github", "git", "gitlab", "bitbucket", "link", "demo", "live demo",
        "code", "repo", "frontend repo", "backend repo", "frontend", "backend",
        "play store", "app store", "pub.dev", "website", "live", "source", "source code"
    )

    private val knownPlatformTags = setOf(
        "android", "ios", "phones & tablets", "phone & tablet", "phones", "tablets", "tablet",
        "mobile", "web", "desktop", "cross-platform", "macos", "windows", "linux",
        "flutter", "dart", "kotlin", "java", "swift", "swiftui", "react", "react native",
        "next.js", "vue", "angular", "node.js", "python", "firebase", "aws", "gcp",
        "azure", "docker", "kubernetes", "graphql", "rest api", "rest apis", "bloc", "provider",
        "hive", "room", "sqlite", "ci/cd", "system_tray", "custompainter", "animationcontroller",
        "globalkey", "renderobject", "websocket", "coregraphics", "bonjour", "redux"
    )

    private fun isLinkDescriptor(segment: String): Boolean {
        val l = segment.lowercase().trim()
        return projectLinkKeywords.contains(l) ||
                l.contains("github.com") ||
                l.startsWith("http://") || l.startsWith("https://") || l.startsWith("www.") ||
                l.endsWith("repo") || l.endsWith("repository") || l.endsWith("demo") || l.endsWith("link") ||
                l == "code" || l == "live" || l == "source"
    }

    private fun parseProjectHeader(rawLine: String): ParsedProjectHeader {
        // Strip trailing Link words or markdown-style links first
        val cleanedLine = rawLine
            .replace(Regex("\\|\\s*(?:Live Demo|Frontend Repo|Backend Repo|Demo|Link|GitHub|Repo).*$", RegexOption.IGNORE_CASE), "")
            .trim()
        val segments = cleanedLine.split(Regex("[|•·]")).map { it.trim() }.filter { it.isNotBlank() }
        if (segments.isEmpty()) return ParsedProjectHeader(cleanedLine, emptyList())

        val titleSegments = mutableListOf<String>()
        val tags = mutableListOf<String>()

        for (segment in segments) {
            val lower = segment.lowercase()

            // 1. If it's a link keyword or direct URL, discard it
            if (isLinkDescriptor(segment)) {
                continue
            }

            // 2. Comma-separated tech tokens (e.g. "Flutter, Dart, Firebase" or "Android, iOS")
            if (segment.contains(",")) {
                val subTokens = segment.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (subTokens.all { knownPlatformTags.contains(it.lowercase()) || it.length <= 25 }) {
                    subTokens.forEach { token ->
                        if (!tags.any { it.equals(token, ignoreCase = true) }) {
                            tags.add(token)
                        }
                    }
                    continue
                }
            }

            // 3. Known platform / device / tech tag
            if (knownPlatformTags.contains(lower)) {
                if (!tags.any { it.equals(segment, ignoreCase = true) }) {
                    tags.add(segment)
                }
                continue
            }

            // 4. Trailing platform word attached to descriptor (e.g. "ADB Helper Flutter Desktop" or "Cognix Flutter")
            val trailingPlatformRegex = Regex("^(.*?)\\s+(Flutter Desktop|Flutter|Android|iOS|Web|macOS|Windows|Desktop|Tablet|Tablets|Mobile)$", RegexOption.IGNORE_CASE)
            val match = trailingPlatformRegex.find(segment)
            if (match != null) {
                val prefixText = match.groupValues[1].trim()
                val platformWord = match.groupValues[2].trim()

                if (platformWord.isNotBlank() && !tags.any { it.equals(platformWord, ignoreCase = true) }) {
                    tags.add(platformWord)
                }
                if (prefixText.isNotBlank()) {
                    titleSegments.add(prefixText)
                }
                continue
            }

            // 5. App Store / Play Store status note
            if (lower.contains("in review") || lower.contains("app store") || lower.contains("play store")) {
                continue
            }

            // 6. Otherwise, descriptive phrase or title part
            titleSegments.add(segment)
        }

        val finalTitle = if (titleSegments.isNotEmpty()) {
            titleSegments.joinToString(" | ")
        } else {
            segments.firstOrNull() ?: cleanedLine
        }

        return ParsedProjectHeader(finalTitle, tags)
    }

    private fun parseProjects(projText: String, annotationLinks: List<String> = emptyList()): List<ProjectItem> {
        if (projText.isBlank()) return emptyList()

        val items = mutableListOf<ProjectItem>()
        val rawLines = projText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Pass 1: Pre-process lines to join wrapped headers
        val lines = mutableListOf<String>()
        var lIdx = 0
        while (lIdx < rawLines.size) {
            val curr = rawLines[lIdx]
            val endsWithSep = curr.endsWith("·") || curr.endsWith("|") || curr.endsWith("•") || curr.endsWith(",")
            val nextIsAppStoreContinuation = lIdx + 1 < rawLines.size &&
                    curr.contains("In Review", ignoreCase = true) &&
                    (rawLines[lIdx + 1].contains("App Store", ignoreCase = true) || rawLines[lIdx + 1].contains("Play Store", ignoreCase = true))

            if (lIdx + 1 < rawLines.size && ((endsWithSep && !isBulletStart(rawLines[lIdx + 1])) || nextIsAppStoreContinuation)) {
                val next = rawLines[lIdx + 1]
                lines.add("${curr.trim()} ${next.trim()}")
                lIdx += 2
                continue
            }
            lines.add(curr)
            lIdx++
        }

        var currentTitle = ""
        var currentTags = mutableListOf<String>()
        val currentBullets = mutableListOf<String>()
        var currentCodeUrl = ""
        var currentDemoUrl = ""
        val pendingPlatformTags = mutableListOf<String>()

        fun flushProject() {
            if (currentTitle.isNotBlank()) {
                val finalBullets = currentBullets.filter { b ->
                    val isSource = b.matches(Regex("(?i)^(?:Source|Code|Repo|GitHub):.*"))
                    if (isSource && currentCodeUrl.isBlank()) {
                        val match = Regex("(?i)(?:github\\.com/[^\\s]+|https?://[^\\s]+)").find(b)
                        if (match != null) {
                            var u = match.value.trim()
                            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
                            currentCodeUrl = u
                        }
                    }
                    !isSource
                }

                items.add(
                    ProjectItem(
                        id = UUID.randomUUID().toString(),
                        projectTitle = currentTitle,
                        description = finalBullets.joinToString("\n").trim(),
                        techStackTags = (currentTags + pendingPlatformTags).distinct(),
                        demoUrl = currentDemoUrl.ifBlank { currentCodeUrl },
                        codeUrl = currentCodeUrl.ifBlank { currentDemoUrl }
                    )
                )
                currentBullets.clear()
                currentTags.clear()
                currentCodeUrl = ""
                currentDemoUrl = ""
                pendingPlatformTags.clear()
                currentTitle = ""
            }
        }

        for (line in lines) {
            // A. In-text Source / Code URL line (e.g. "■ Source: github.com/Molterous/mobile_mouse")
            val inTextSourceMatch = Regex("(?i)^(?:[■●•\\-*–—\\s]*)(?:Source|Code|Repo|GitHub):\\s*(?:https?://)?(github\\.com/[^\\s]+|[^\\s]+)").find(line)
            if (inTextSourceMatch != null) {
                var extractedUrl = inTextSourceMatch.groupValues[1].trim()
                if (!extractedUrl.startsWith("http://") && !extractedUrl.startsWith("https://")) {
                    extractedUrl = "https://$extractedUrl"
                }
                if (currentCodeUrl.isBlank()) {
                    currentCodeUrl = extractedUrl
                }
                continue
            }

            // B. Dedicated Tech Stack line (e.g. "Tech Stack: React 19, TypeScript, Node.js 20...")
            val techStackMatch = Regex("(?i)^(?:Tech(?:nology)?\\s*Stack|Technologies|Environment):\\s*(.*)$").find(line)
            if (techStackMatch != null) {
                val techContent = techStackMatch.groupValues[1].trim()
                val tokens = techContent.split(Regex("[,|•·]")).map { it.trim() }.filter { it.isNotBlank() }
                tokens.forEach { token ->
                    if (!currentTags.any { it.equals(token, ignoreCase = true) }) {
                        currentTags.add(token)
                    }
                }
                continue
            }

            // C. Pure platform/link header line preceding project title (e.g. "Android | iOS | Phones & Tablets | GitHub")
            val rawSegments = line.split(Regex("[|•·]")).map { it.trim() }.filter { it.isNotBlank() }
            val isPurePlatformLine = !isBulletStart(line) && rawSegments.isNotEmpty() && rawSegments.all { seg ->
                knownPlatformTags.contains(seg.lowercase()) || isLinkDescriptor(seg)
            }
            if (isPurePlatformLine) {
                val pHeader = parseProjectHeader(line)
                pendingPlatformTags.addAll(pHeader.techStackTags)
                continue
            }

            // D. Project Header Detection
            val isHeaderLine = !isBulletStart(line) && (
                line.contains("|") ||
                (line.contains("·") && (knownPlatformTags.any { line.contains(it, ignoreCase = true) } || line.contains("Flutter", ignoreCase = true))) ||
                (currentTitle.isBlank() && line.length < 80)
            )

            if (isHeaderLine) {
                if (currentTitle.isNotBlank()) flushProject()
                val parsedHeader = parseProjectHeader(line)
                currentTitle = parsedHeader.title
                currentTags.addAll(parsedHeader.techStackTags)
            } else if (isBulletStart(line)) {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    currentBullets.add(cleaned)
                }
            } else if (currentBullets.isNotEmpty()) {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    val last = currentBullets.last()
                    currentBullets[currentBullets.lastIndex] = "$last $cleaned"
                }
            } else {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    currentBullets.add(cleaned)
                }
            }
        }
        flushProject()

        // Associate project links from extracted annotation links if demoUrl/codeUrl is empty
        for (i in items.indices) {
            if (items[i].demoUrl.isBlank() && items[i].codeUrl.isBlank()) {
                val cleanTitle = items[i].projectTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                val titleWords = items[i].projectTitle.lowercase().split(Regex("[\\s_\\-|]+")).filter { it.length > 2 }

                // 1. First priority: a repository link whose URL slug matches the project title or title keywords
                val matchingRepoLink = annotationLinks.firstOrNull { link ->
                    val lowerLink = link.lowercase()
                    val cleanUrl = lowerLink.replace(Regex("[^a-z0-9]"), "")
                    val isRepo = lowerLink.contains("github.com/") &&
                            lowerLink.split("/").filter { s -> s.isNotBlank() }.size >= 4

                    if (!isRepo) return@firstOrNull false

                    cleanUrl.contains(cleanTitle) ||
                            (cleanTitle.length >= 8 && cleanUrl.contains(cleanTitle.take(8))) ||
                            titleWords.any { word -> lowerLink.substringAfter("github.com/").contains(word) }
                }

                // 2. Second priority: any non-profile link matching project keywords
                val matchingGeneralLink = matchingRepoLink ?: annotationLinks.firstOrNull { link ->
                    val lowerLink = link.lowercase()
                    if (lowerLink.contains("linkedin.com") || lowerLink.contains("play.google.com") || lowerLink.contains("pub.dev")) {
                        false
                    } else {
                        titleWords.any { word -> lowerLink.contains(word) }
                    }
                }

                // 3. Fallback: repo-level github link (at least owner/repo, not just root profile)
                val finalLink = matchingGeneralLink ?: annotationLinks.firstOrNull { link ->
                    val lower = link.lowercase()
                    lower.contains("github.com/") &&
                            lower.split("/").filter { s -> s.isNotBlank() }.size >= 4 &&
                            !lower.contains("linkedin.com") &&
                            !lower.contains("pub.dev")
                }

                if (finalLink != null) {
                    items[i] = items[i].copy(demoUrl = finalLink, codeUrl = finalLink)
                }
            }
        }

        return items
    }

    private fun parseSkills(skillsText: String, sectionHeader: String = "Technical Skills"): SkillCategories {
        val normalizedTitle = if (sectionHeader.equals("TECHNICAL SKILLS", ignoreCase = true)) {
            "Technical Skills"
        } else {
            sectionHeader
        }
        if (skillsText.isBlank()) {
            val defaultDomains = linkedMapOf(
                "Mobile Development" to listOf("Flutter", "Android SDK", "Swift", "Kotlin"),
                "Frameworks & Core" to listOf("Jetpack Compose", "Bloc", "Provider", "Coroutines"),
                "Cloud & Database" to listOf("Firebase", "Cloud Firestore", "SQLite", "REST APIs"),
                "Tools & Practices" to listOf("Git", "CI/CD (GitHub Actions)", "MVVM")
            )
            return SkillCategories(
                sectionTitle = normalizedTitle,
                languages = defaultDomains["Mobile Development"] ?: emptyList(),
                frameworks = defaultDomains["Frameworks & Core"] ?: emptyList(),
                cloudDevOps = defaultDomains["Cloud & Database"] ?: emptyList(),
                toolsMethodologies = defaultDomains["Tools & Practices"] ?: emptyList(),
                categorizedDomains = defaultDomains
            )
        }

        val rawLines = skillsText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Normalize line breaks where tokens or categories were wrapped across lines
        val lines = mutableListOf<String>()
        for (rawLine in rawLines) {
            if (lines.isEmpty()) {
                lines.add(rawLine)
                continue
            }
            val prev = lines.last()
            val isNewCategory = rawLine.contains(":") &&
                    rawLine.indexOf(':') in 2..55 &&
                    !rawLine.startsWith("http") &&
                    !rawLine.contains("@")
            val prevEndsWithDelim = prev.endsWith(",") || prev.endsWith(";") ||
                    prev.endsWith("|") || prev.endsWith("•") || prev.endsWith("·") ||
                    prev.endsWith("–") || prev.endsWith("-")
            val prevEndsWithColon = prev.endsWith(":")

            if (!isNewCategory && !prevEndsWithDelim && !prevEndsWithColon) {
                // Line wrapped mid-token or without trailing comma
                lines[lines.size - 1] = "$prev $rawLine"
            } else {
                lines.add(rawLine)
            }
        }

        val detectedCategories = linkedMapOf<String, MutableList<String>>()
        var currentCategory: String? = null
        val flatSkills = mutableListOf<String>()

        fun cleanSkillItem(item: String): String {
            return item.replace(Regex("^[•●\u2022\u25cf\u00b7\u25a0■\\-*–—\\s]+|[•●\u2022\u25cf\u00b7\u25a0■\\-*–—\\s]+$"), "")
                .replace(Regex("\\s+(?:Expert|Advanced|Intermediate|Beginner|Proficient)$", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        for (line in lines) {
            // Check if this line defines a category header (e.g. "Mobile Development:" or "● Frontend: React, ...")
            if (line.contains(":")) {
                val colonIdx = line.indexOf(':')
                val potentialCat = cleanSkillItem(line.substring(0, colonIdx))
                val remainder = line.substring(colonIdx + 1).trim()

                // A valid category header name is typically short (<= 55 chars) and doesn't look like a URL/email
                if (potentialCat.length in 2..55 && !potentialCat.startsWith("http") && !potentialCat.contains("@")) {
                    currentCategory = potentialCat
                    val list = detectedCategories.getOrPut(currentCategory) { mutableListOf() }
                    if (remainder.isNotBlank()) {
                        val skills = remainder.split(Regex("[,|•·]"))
                            .map { cleanSkillItem(it) }
                            .filter { it.isNotBlank() }
                        list.addAll(skills)
                    }
                    continue
                }
            }

            // If we have an active category and this line contains skills for it
            if (currentCategory != null) {
                val skills = line.split(Regex("[,|•·]"))
                    .map { cleanSkillItem(it) }
                    .filter { it.isNotBlank() }
                detectedCategories[currentCategory]?.addAll(skills)
            } else {
                // Flat / uncategorized skills
                val delimiter = if (line.contains(",") || line.contains("|") || line.contains("•") || line.contains("·")) {
                    Regex("[,|•·]")
                } else if (Regex("(?i:Expert|Advanced|Intermediate|Beginner|Proficient)").containsMatchIn(line)) {
                    Regex("(?<=(?i:Expert|Advanced|Intermediate|Beginner|Proficient))\\s+|\\s{2,}|\t")
                } else {
                    Regex("\\s{2,}|\t")
                }
                val skills = line.split(delimiter)
                    .map { cleanSkillItem(it) }
                    .filter { it.isNotBlank() }
                flatSkills.addAll(skills)
            }
        }

        // Clean up duplicates
        val cleanedCategories = linkedMapOf<String, List<String>>()
        detectedCategories.forEach { (cat, skills) ->
            val distinct = skills.distinct()
            if (distinct.isNotEmpty()) {
                cleanedCategories[cat] = distinct
            }
        }

        val cleanedFlat = flatSkills.distinct()

        // Populate standard categories for backward compatibility with older components
        val languages = mutableListOf<String>()
        val frameworks = mutableListOf<String>()
        val cloudDevOps = mutableListOf<String>()
        val toolsMethodologies = mutableListOf<String>()

        cleanedCategories.forEach { (cat, skills) ->
            val lower = cat.lowercase()
            when {
                lower.contains("language") || lower.contains("programming") -> languages.addAll(skills)
                lower.contains("framework") || lower.contains("mobile") || lower.contains("architecture") || lower.contains("state") || lower.contains("core") || lower.contains("frontend") || lower.contains("backend") -> frameworks.addAll(skills)
                lower.contains("firebase") || lower.contains("cloud") || lower.contains("devops") || lower.contains("database") || lower.contains("storage") || lower.contains("queue") -> cloudDevOps.addAll(skills)
                else -> toolsMethodologies.addAll(skills)
            }
        }

        return SkillCategories(
            sectionTitle = normalizedTitle,
            languages = languages.distinct(),
            frameworks = frameworks.distinct(),
            cloudDevOps = cloudDevOps.distinct(),
            toolsMethodologies = toolsMethodologies.distinct(),
            categorizedDomains = cleanedCategories,
            rawFlatSkills = cleanedFlat
        )
    }

    private fun parseEducation(eduText: String): List<EducationItem> {
        if (eduText.isBlank()) return emptyList()

        val rawLines = eduText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val dateRangePattern = Regex(
            "(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s*)?\\b\\d{4}\\s*(?:–|-|—|to)\\s*(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s*)?(?:\\d{4}|\\d{2}|Present|Current)\\b",
            RegexOption.IGNORE_CASE
        )
        val singleYearPattern = Regex("\\b(?:19|20)\\d{2}\\b")
        val gradePattern = Regex(
            "(?:\\b(?:CGPA|GPA|Percentage|Score|Grade)[ \\t]*[:=]?[ \\t]*[0-9.]+(?:/[0-9.]+)?%?|[0-9.]+[ \\t]*(?:CGPA|GPA|%|Percent))",
            RegexOption.IGNORE_CASE
        )

        fun isDegreeText(text: String): Boolean {
            val lower = text.lowercase()
            return lower.contains("bachelor") || lower.contains("b.tech") ||
                    lower.contains("btech") || lower.contains("b.e.") ||
                    lower.contains("b.e ") || lower.contains("be -") ||
                    lower.contains("master") || lower.contains("m.tech") ||
                    lower.contains("mtech") || lower.contains("m.e.") ||
                    lower.contains("bca") || lower.contains("mca") ||
                    lower.contains("b.sc") || lower.contains("bsc") ||
                    lower.contains("m.sc") || lower.contains("msc") ||
                    lower.contains("bba") || lower.contains("mba") ||
                    lower.contains("ph.d") || lower.contains("phd") ||
                    lower.contains("diploma") || lower.contains("degree") ||
                    lower.contains("intermediate") || lower.contains("matriculation") ||
                    lower.contains("class xii") || lower.contains("class x") ||
                    lower.contains("senior secondary") || lower.contains("high school")
        }

        fun isInstitutionText(text: String): Boolean = isInstitutionLine(text)

        fun normalizeEduDate(raw: String): String {
            val m = Regex("(\\d{4})\\s*(?:–|-|—|to)\\s*(\\d{2,4}|Present|Current)", RegexOption.IGNORE_CASE).find(raw)
            if (m != null) {
                val startYear = m.groupValues[1]
                var endYear = m.groupValues[2]
                if (endYear.length == 2 && endYear.all { it.isDigit() }) {
                    endYear = "20$endYear"
                }
                return "$startYear - $endYear"
            }
            return raw.replace(Regex("\\s*[–—]\\s*"), " - ").trim()
        }

        fun cleanSegment(s: String): String {
            return s.replace(Regex("^[•●\u2022\u25cf\u00b7\u25a0■\\-*–—|:,\\s]+|[•●\u2022\u25cf\u00b7\u25a0■\\-*–—|:,\\s]+$"), "").trim()
        }

        // Group lines into education blocks
        val blocks = mutableListOf<MutableList<String>>()
        var currentBlock = mutableListOf<String>()

        for (line in rawLines) {
            val pipeSegments = if (line.contains("|")) line.split("|").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
            val isPipeDelimitedEntry = pipeSegments.size >= 2 &&
                    pipeSegments.any { isInstitutionText(it) } &&
                    pipeSegments.any { isDegreeText(it) }

            if (isPipeDelimitedEntry) {
                if (currentBlock.isNotEmpty()) {
                    blocks.add(currentBlock)
                    currentBlock = mutableListOf()
                }
                blocks.add(mutableListOf(line))
                continue
            }

            val hasInst = isInstitutionText(line)
            val hasDeg = isDegreeText(line)
            val currentHasInst = currentBlock.any { isInstitutionText(it) }
            val currentHasDeg = currentBlock.any { isDegreeText(it) }

            if (currentBlock.isNotEmpty() && ((hasInst && currentHasInst) || (hasDeg && currentHasDeg))) {
                blocks.add(currentBlock)
                currentBlock = mutableListOf()
            }
            currentBlock.add(line)
        }
        if (currentBlock.isNotEmpty()) {
            blocks.add(currentBlock)
        }

        val items = mutableListOf<EducationItem>()

        for (block in blocks) {
            val fullBlockText = block.joinToString("\n")

            // 1. Detect Date
            val dateMatch = dateRangePattern.find(fullBlockText)
            val graduationYear = if (dateMatch != null) {
                normalizeEduDate(dateMatch.value)
            } else {
                val single = singleYearPattern.find(fullBlockText)?.value
                if (single != null) {
                    val y = single.toIntOrNull() ?: 2024
                    "${y - 4} - $single"
                } else {
                    ""
                }
            }

            // 2. Detect Grade
            val gradeMatch = gradePattern.find(fullBlockText)
            val detectedGrade = gradeMatch?.value?.trim()?.let { cleanSegment(it) }.orEmpty()

            // 3. Decompose lines / pipe segments
            val segments = if (block.size == 1 && block[0].contains("|")) {
                block[0].split("|").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                block
            }

            var institution = ""
            var degree = ""

            val instSegment = segments.firstOrNull { isInstitutionText(it) }
            if (instSegment != null) {
                institution = cleanSegment(
                    instSegment
                        .replace(dateRangePattern, "")
                        .replace(gradePattern, "")
                )
            }

            val degSegment = segments.firstOrNull { isDegreeText(it) && it != instSegment }
            if (degSegment != null) {
                degree = cleanSegment(
                    degSegment
                        .replace(dateRangePattern, "")
                        .replace(gradePattern, "")
                )
            }

            val remaining = segments.map {
                cleanSegment(it.replace(dateRangePattern, "").replace(gradePattern, ""))
            }.filter { it.isNotBlank() && it != institution && it != degree }

            if (institution.isBlank() && remaining.isNotEmpty()) {
                institution = remaining[0]
            }
            if (degree.isBlank() && remaining.size > (if (institution == remaining.getOrNull(0)) 1 else 0)) {
                degree = remaining.first { it != institution }
            }

            // Strip trailing city / location if erroneously attached
            val trailingCityPattern = Regex("\\s+(?:Arlington|Bossier City|Noida|Delhi|Mohali|Ghaziabad|Los Angeles|New York|London|Paris|Berlin)\\s*$", RegexOption.IGNORE_CASE)
            degree = degree.replace(trailingCityPattern, "").trim()
            institution = institution.replace(trailingCityPattern, "").trim()

            // Append detected grade to degree
            if (detectedGrade.isNotBlank() && !degree.contains(detectedGrade, ignoreCase = true)) {
                degree = if (degree.isNotBlank()) "$degree | $detectedGrade" else detectedGrade
            }

            if (institution.isNotBlank() || degree.isNotBlank()) {
                items.add(
                    EducationItem(
                        institution = toTitleCase(institution),
                        degree = toTitleCase(degree),
                        graduationYear = graduationYear
                    )
                )
            }
        }

        return items
    }

    internal fun sliceSections(rawText: String, detectedHeadings: List<String>): Map<String, String> {
        val lines = rawText.lines()
        val headingIndices = mutableListOf<Pair<String, Int>>()

        val bodyHeadings = detectedHeadings.filter {
            !it.equals("Personal Info & Contact", ignoreCase = true) &&
                    !it.contains("Personal", ignoreCase = true) &&
                    !it.contains("Contact", ignoreCase = true)
        }

        var searchFromLine = 0
        for (heading in bodyHeadings) {
            val upperHeading = heading.uppercase()
            for (i in searchFromLine until lines.size) {
                val line = lines[i].trim()
                val candidate = line.split(Regex("\\s{2,}|\t"))[0].trim()
                val cleaned = cleanSpacedLetters(candidate)
                    .replace(Regex("^[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*"), "")
                    .removeSuffix(":")
                    .trim()
                if (cleaned.equals(upperHeading, ignoreCase = true) || cleaned.equals(heading, ignoreCase = true)) {
                    headingIndices.add(Pair(heading, i))
                    searchFromLine = i + 1
                    break
                }
            }
        }

        val sectionMap = mutableMapOf<String, String>()
        for (idx in 0 until headingIndices.size) {
            val current = headingIndices[idx]
            val startLine = current.second + 1
            val endLine = if (idx + 1 < headingIndices.size) headingIndices[idx + 1].second else lines.size
            if (startLine <= endLine) {
                val content = lines.subList(startLine, endLine).joinToString("\n").trim()
                sectionMap[current.first] = content
            }
        }

        return sectionMap
    }

    private fun parseDynamicSections(
        rawText: String,
        annotationLinks: List<String> = emptyList(),
        detectedHeadings: List<String> = emptyList(),
        sectionMap: Map<String, String> = emptyMap()
    ): List<DynamicSection> {
        val list = mutableListOf<DynamicSection>()

        val coreHeadingsKeywords = listOf(
            "personal", "contact", "summary", "profile", "objective", "about",
            "experience", "employment", "work history", "career",
            "project", "skill", "technolog", "competenc",
            "education", "academic", "qualification"
        )

        val dynamicHeadings = detectedHeadings.filter { h ->
            val l = h.lowercase()
            coreHeadingsKeywords.none { l.contains(it) }
        }

        for (secHeading in dynamicHeadings) {
            val content = sectionMap[secHeading] ?: ""
            if (content.isNotBlank()) {
                if (secHeading.contains("Open Source", ignoreCase = true) || secHeading.contains("Contributions", ignoreCase = true)) {
                    val parsed = parseOpenSourceSection(content, annotationLinks, secHeading)
                    list.add(parsed)
                } else {
                    val parsed = parseGenericDynamicSection(secHeading, content, annotationLinks)
                    if (parsed.items.isNotEmpty()) {
                        list.add(parsed)
                    }
                }
            }
        }

        // Fallback for standalone OPEN-SOURCE heading line if not sliced in sectionMap
        if (!list.any { it.sectionTitle.contains("Open Source", ignoreCase = true) }) {
            val openSourceRegex = Regex(
                "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:OPEN[- ]SOURCE CONTRIBUTIONS|OPEN[- ]SOURCE)(?:\\s*\\([A-Za-z0-9\\s]+\\))?[ \\t]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:TECHNICAL SKILLS|SKILLS|EDUCATION|ACADEMICS|PROJECTS)\\b)|$)",
                RegexOption.IGNORE_CASE
            )
            val openSourceText = openSourceRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim()
            if (!openSourceText.isNullOrBlank()) {
                list.add(parseOpenSourceSection(openSourceText, annotationLinks))
            }
        }

        return list
    }

    private fun parseOpenSourceSection(
        openSourceText: String,
        annotationLinks: List<String>,
        sectionHeading: String = "Open Source Contributions"
    ): DynamicSection {
        val openSourceLines = openSourceText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val rawFirstLine = openSourceLines.firstOrNull() ?: "Open Source Package"

        val cleanedTitle = rawFirstLine
            .replace(Regex("(?:[|•·\\s]+)?(?:pub\\.dev|github(?:\\.com)?|gitlab|bitbucket|npm|link|demo|repo|package)(?:\\s*.*)?$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^[|:–\\-\\s,]+|[|:–\\-\\s,]+$"), "")
            .trim()
            .ifBlank { rawFirstLine.replace(Regex("\\|\\s*Link.*", RegexOption.IGNORE_CASE), "").trim() }

        val urlPattern = Regex("(https?://[^\\s]+|pub\\.dev/[^\\s]+|github\\.com/[^\\s]+)")
        var detectedLink = urlPattern.find(openSourceText)?.value ?: ""
        if (detectedLink.isBlank()) {
            detectedLink = annotationLinks.firstOrNull { it.contains("pub.dev") }
                ?: annotationLinks.firstOrNull { it.contains("github.com") && !it.endsWith("github.com") }
                ?: ""
        }
        if (detectedLink.isNotBlank() && !detectedLink.startsWith("http://") && !detectedLink.startsWith("https://")) {
            detectedLink = "https://$detectedLink"
        }

        val bulletLines = mutableListOf<String>()
        for (line in openSourceLines.drop(1)) {
            // Skip lines that are merely source/badge indicator tags e.g. "pub.dev", "github.com", "npm"
            if (line.matches(Regex("(?i)^[•●·\\s]*(?:pub\\.dev|github(?:\\.com)?|gitlab|bitbucket|npm|link|demo|repo|package)[\\s]*$"))) {
                continue
            }
            val isBullet = isBulletStart(line)
            if (isBullet) {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    bulletLines.add(cleaned)
                }
            } else {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    if (bulletLines.isNotEmpty()) {
                        val lastBullet = bulletLines.last()
                        bulletLines[bulletLines.lastIndex] = "$lastBullet $cleaned"
                    } else {
                        bulletLines.add(cleaned)
                    }
                }
            }
        }

        val desc = if (bulletLines.isNotEmpty()) {
            bulletLines.joinToString("\n")
        } else {
            "Architected and published reusable open source library."
        }

        val normalizedTitle = if (sectionHeading.contains("Open Source", ignoreCase = true) || sectionHeading.contains("Contributions", ignoreCase = true)) {
            "Open Source Contributions"
        } else {
            toTitleCase(sectionHeading)
        }

        return DynamicSection(
            sectionTitle = normalizedTitle,
            items = listOf(
                DynamicItem(
                    title = cleanedTitle,
                    subtitle = "Verified Contributor",
                    dateOrPeriod = "Active",
                    description = desc,
                    link = detectedLink,
                    bulletPoints = if (bulletLines.isNotEmpty()) bulletLines else listOf("Architected and published reusable open source library.")
                )
            )
        )
    }

    private fun parseGenericDynamicSection(
        sectionTitle: String,
        content: String,
        annotationLinks: List<String>
    ): DynamicSection {
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
        val items = mutableListOf<DynamicItem>()

        val datePattern = Regex(
            "(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s*)?\\d{4}\\s*(?:–|-|—|to)\\s*(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s*)?(?:\\d{4}|Present|Current)",
            RegexOption.IGNORE_CASE
        )

        val lowerTitle = sectionTitle.lowercase()

        // 1. Date ranges (e.g. Courses, Certifications, Experience-like entries)
        val linesWithDates = lines.filter { datePattern.containsMatchIn(it) }
        if (linesWithDates.isNotEmpty()) {
            val blocks = mutableListOf<MutableList<String>>()
            var currentBlock = mutableListOf<String>()
            for (line in lines) {
                if (datePattern.containsMatchIn(line) && currentBlock.isNotEmpty()) {
                    blocks.add(currentBlock)
                    currentBlock = mutableListOf()
                }
                currentBlock.add(line)
            }
            if (currentBlock.isNotEmpty()) blocks.add(currentBlock)

            for (block in blocks) {
                val blockText = block.joinToString("\n")
                val dateMatch = datePattern.find(blockText)
                val dateStr = dateMatch?.value?.trim() ?: ""

                val firstLine = block.firstOrNull() ?: sectionTitle
                val cleanedTitle = if (dateMatch != null) {
                    firstLine.replace(dateMatch.value, "").replace(Regex("^[|:–\\-\\s,]+|[|:–\\-\\s,]+$"), "").trim()
                } else {
                    firstLine
                }

                val isCourses = sectionTitle.contains("Course", ignoreCase = true) || sectionTitle.contains("Certif", ignoreCase = true)
                val otherLines = block.drop(1).map { cleanBulletLine(it) }.filter { it.isNotBlank() }
                val actualHighlights = if (isCourses) {
                    // Filter out publisher place / authorizing institution (e.g. INSEAD Business School, Online.)
                    otherLines.filter { line ->
                        !isInstitutionLine(line) &&
                                !line.contains("School", ignoreCase = true) &&
                                !line.contains("University", ignoreCase = true) &&
                                !line.contains("Online", ignoreCase = true) &&
                                !line.contains("United Kingdom", ignoreCase = true) &&
                                !line.contains("USA", ignoreCase = true) &&
                                !line.contains("Germany", ignoreCase = true) &&
                                !isLikelyLocation(line)
                    }
                } else {
                    otherLines
                }

                items.add(
                    DynamicItem(
                        title = toTitleCase(cleanedTitle.ifBlank { sectionTitle }),
                        subtitle = "",
                        dateOrPeriod = dateStr,
                        description = actualHighlights.joinToString("\n"),
                        link = "",
                        bulletPoints = actualHighlights
                    )
                )
            }
        }
        // 2. Bullet points (e.g. Achievements, Highlights, Notes)
        else if (lines.any { isBulletStart(it) }) {
            val bulletList = mutableListOf<String>()
            for (line in lines) {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    if (isBulletStart(line) || bulletList.isEmpty()) {
                        bulletList.add(cleaned)
                    } else {
                        val last = bulletList.last()
                        bulletList[bulletList.lastIndex] = "$last $cleaned"
                    }
                }
            }

            // Check if bullets follow "Title: Description" format (e.g. Anuj's achievements)
            val allHaveColon = bulletList.isNotEmpty() && bulletList.all { b ->
                val colonIdx = b.indexOf(':')
                colonIdx in 3..60 && !b.startsWith("http")
            }

            if (allHaveColon) {
                for (b in bulletList) {
                    val colonIdx = b.indexOf(':')
                    val itemTitle = b.substring(0, colonIdx).trim()
                    val itemDesc = b.substring(colonIdx + 1).trim()
                    items.add(
                        DynamicItem(
                            title = toTitleCase(itemTitle),
                            subtitle = "",
                            dateOrPeriod = "",
                            description = itemDesc,
                            link = "",
                            bulletPoints = if (itemDesc.isNotBlank()) listOf(itemDesc) else emptyList()
                        )
                    )
                }
            } else {
                items.add(
                    DynamicItem(
                        title = toTitleCase(sectionTitle),
                        subtitle = "",
                        dateOrPeriod = "",
                        description = bulletList.joinToString("\n"),
                        link = "",
                        bulletPoints = bulletList
                    )
                )
            }
        }
        // 3. Hobbies / Interests (comma-separated hobbies, no title/link/highlights needed)
        else if (lowerTitle.contains("hobbi") || lowerTitle.contains("interest")) {
            val fullText = lines.joinToString(", ")
            val commaTokens = fullText.split(",").map { it.trim() }.filter { it.isNotBlank() }
            items.add(
                DynamicItem(
                    title = "Hobbies",
                    subtitle = "",
                    dateOrPeriod = "",
                    description = fullText,
                    link = "",
                    bulletPoints = commaTokens
                )
            )
        }
        // 4. Languages (bullet points with language info, no title/link/highlights needed)
        else if (lowerTitle.contains("language")) {
            val knownLangs = "English|Spanish|French|German|Mandarin|Hindi|Arabic|Russian|Portuguese|Japanese|Italian|Chinese|Dutch|Korean"
            val langRegex = Regex("(?i)\\b($knownLangs)\\b\\s*[:\\-]?\\s*((?:(?!\\b(?:$knownLangs)\\b)[A-Za-z\\s])+)?")
            val fullContent = lines.joinToString(" ")
            val matches = langRegex.findAll(fullContent).toList()
            val bulletPoints = if (matches.isNotEmpty()) {
                matches.map { m ->
                    val lang = m.groupValues[1].trim()
                    val prof = m.groupValues.getOrNull(2)?.trim()?.replace(Regex("^[|:–\\-\\s,]+|[|:–\\-\\s,]+$"), "")?.take(40) ?: ""
                    if (prof.isNotBlank()) "${toTitleCase(lang)}: ${toTitleCase(prof)}" else toTitleCase(lang)
                }
            } else {
                lines
            }
            items.add(
                DynamicItem(
                    title = "Languages",
                    subtitle = "",
                    dateOrPeriod = "",
                    description = bulletPoints.joinToString("\n"),
                    link = "",
                    bulletPoints = bulletPoints
                )
            )
        }
        // 5. General fallback
        else {
            val bullets = lines.map { cleanBulletLine(it) }.filter { it.isNotBlank() }
            items.add(
                DynamicItem(
                    title = toTitleCase(sectionTitle),
                    subtitle = "",
                    dateOrPeriod = "",
                    description = bullets.joinToString("\n"),
                    link = "",
                    bulletPoints = bullets
                )
            )
        }

        return DynamicSection(
            sectionTitle = toTitleCase(sectionTitle),
            items = items
        )
    }
}

