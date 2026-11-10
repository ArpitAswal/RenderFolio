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

            val (tier1Text, extractedLinks) = if (!AppConstants.DISABLE_NATIVE_PDF_EXTRACTION) {
                try {
                    val nativeResult = extractNativePdfData(tempFile)
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

            val parsedProfile = parseRawTextToProfile(finalRawText, extractedLinks, detectedSections)
            ExtractionResult(parsedProfile, finalRawText, tierUsed)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Inspects the raw text extracted from the uploaded PDF to detect the actual
     * section titles written by the candidate (e.g. "Summary" vs "Profile",
     * "Professional Experience" vs "Work Experience", "Open Source Contributions", etc.).
     */
    fun detectResumeSectionHeadings(rawText: String): List<String> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val detected = mutableListOf<String>()

        // First item is always contact / personal header
        detected.add("Personal Info & Contact")

        val headingKeywords = listOf(
            Regex("^(?:PROFESSIONAL SUMMARY|EXECUTIVE SUMMARY|CAREER OBJECTIVE|ABOUT ME|SUMMARY|PROFILE|OBJECTIVE|OVERVIEW)\\b", RegexOption.IGNORE_CASE),
            Regex("^(?:PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EMPLOYMENT HISTORY|WORK HISTORY|CAREER HISTORY|EXPERIENCE)\\b", RegexOption.IGNORE_CASE),
            Regex("^(?:FEATURED PROJECTS|PERSONAL PROJECTS|KEY PROJECTS|ACADEMIC PROJECTS|PROJECTS)\\b", RegexOption.IGNORE_CASE),
            Regex("^(?:OPEN[- ]SOURCE CONTRIBUTIONS|OPEN[- ]SOURCE|CONTRIBUTIONS|COMMUNITY INITIATIVES)\\b", RegexOption.IGNORE_CASE),
            Regex("^(?:TECHNICAL SKILLS|CORE COMPETENCIES|SKILLS & TOOLS|AREAS OF EXPERTISE|TECHNOLOGIES|SKILLS)\\b", RegexOption.IGNORE_CASE),
            Regex("^(?:EDUCATION & CREDENTIALS|ACADEMIC BACKGROUND|ACADEMICS|QUALIFICATIONS|EDUCATION)\\b", RegexOption.IGNORE_CASE),
            Regex("^(?:HONORS & AWARDS|CERTIFICATIONS|ACHIEVEMENTS|PUBLICATIONS)\\b", RegexOption.IGNORE_CASE)
        )

        for (rawLine in lines) {
            // Filter out obviously non-heading long paragraphs or contact details
            if (rawLine.length > 90) continue
            if (rawLine.contains("@") || rawLine.contains("http") || rawLine.contains("linkedin") || rawLine.contains("github") || rawLine.contains(".com")) continue
            if (rawLine.startsWith("•") || rawLine.startsWith("-") || rawLine.startsWith("*")) continue

            // Resumes often right-align dates/locations at the same vertical height as section headings
            // (e.g. "EDUCATION     Aug 2023 – Feb 2024"). Split on multiple spaces/tabs or colon/pipe.
            val candidate = rawLine.split(Regex("\\s{2,}|\t"))[0].trim()
            val cleanedCandidate = candidate
                .replace(Regex("^[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*"), "") // remove numbering e.g. "1. " or "I. "
                .removeSuffix(":")
                .trim()

            for (pattern in headingKeywords) {
                val match = pattern.find(cleanedCandidate)
                if (match != null && match.range.first == 0) {
                    val matchedKeyword = match.value.trim()
                    val remainder = cleanedCandidate.substring(match.range.last + 1).trim()

                    // Remainder must be either empty, bracketed note like "(Selected)", or contain dates/symbols
                    val isValidHeading = remainder.isEmpty() ||
                            remainder.startsWith("(") ||
                            remainder.matches(Regex("^[|:–\\-\\s0-9,/]*(?:(?:19|20)\\d{2}|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|Present|Current).*"))

                    if (isValidHeading) {
                        val formatted = toTitleCase(matchedKeyword)
                        if (!detected.contains(formatted)) {
                            detected.add(formatted)
                        }
                        break
                    }
                }
            }
        }

        if (detected.size < 3) {
            return listOf(
                "Personal Info & Contact",
                "Summary / Profile",
                "Work Experience",
                "Featured Projects",
                "Technical Skills",
                "Education & Academics"
            )
        }

        return detected
    }

    internal fun toTitleCase(text: String): String {
        return text.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                when {
                    word == "|" -> "|"
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
                    else -> word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
    }

    private fun cleanSpacedLetters(text: String): String {
        return if (text.matches(Regex(".*(?:[A-Za-z]\\s){2,}[A-Za-z].*"))) {
            text.split(Regex("\\s{2,}"))
                .joinToString(" ") { word -> word.replace(" ", "") }
        } else {
            text
        }
    }

    data class NativePdfData(
        val text: String,
        val links: List<String> = emptyList()
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

    private fun extractNativePdfData(file: File): NativePdfData {
        val extractedLinks = mutableListOf<String>()
        // Tier 1A: Apache PDFBox for Android (zero-latency, full native font, stream & hyperlink support)
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

                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val text = stripper.getText(document)
                if (text.isNotBlank() && text.length >= AppConstants.TIER_1_CHAR_THRESHOLD) {
                    return NativePdfData(cleanExtractedText(text), extractedLinks)
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
        detectedHeadings: List<String> = emptyList()
    ): ResumeProfile {
        if (rawText.isBlank() || rawText.lines().count { it.isNotBlank() } < 3) {
            throw IllegalArgumentException("The attached PDF contains no readable text or is corrupted. Please ensure you attach a valid PDF document.")
        }

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val emailMatcher = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})").matcher(rawText)
        var email = if (emailMatcher.find()) emailMatcher.group(1) ?: "" else ""

        val phoneMatcher = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}").matcher(rawText)
        var phone = if (phoneMatcher.find()) phoneMatcher.group(0) ?: "" else ""

        // Fallback to PDF annotations for embedded mailto / tel links
        if (email.isBlank()) {
            email = annotationLinks.firstOrNull { it.startsWith("mailto:", ignoreCase = true) }
                ?.removePrefix("mailto:")
                ?.removePrefix("MAILTO:")
                ?.substringBefore("?")
                ?.trim() ?: ""
        }

        if (phone.isBlank()) {
            phone = annotationLinks.firstOrNull { it.startsWith("tel:", ignoreCase = true) }
                ?.removePrefix("tel:")
                ?.removePrefix("TEL:")
                ?.trim() ?: ""
        }

        val linkedinMatcher = Pattern.compile("(https?://(www\\.)?linkedin\\.com/in/[a-zA-Z0-9_-]+|linkedin\\.com/in/[a-zA-Z0-9_-]+|LinkedIn Portfolio)").matcher(rawText)
        var linkedinUrl = if (linkedinMatcher.find()) {
            val matched = linkedinMatcher.group(0) ?: ""
            if (matched.startsWith("http") || matched.contains(".com")) matched else "https://linkedin.com"
        } else ""

        if (linkedinUrl.isBlank()) {
            linkedinUrl = annotationLinks.firstOrNull { it.contains("linkedin.com/in", ignoreCase = true) }
                ?: annotationLinks.firstOrNull { it.contains("linkedin.com", ignoreCase = true) } ?: ""
        }

        val githubMatcher = Pattern.compile("(https?://(www\\.)?github\\.com/[a-zA-Z0-9_-]+|github\\.com/[a-zA-Z0-9_-]+|Github Portfolio)").matcher(rawText)
        var githubUrl = if (githubMatcher.find()) {
            val matched = githubMatcher.group(0) ?: ""
            if (matched.startsWith("http") || matched.contains(".com")) matched else "https://github.com"
        } else ""

        if (githubUrl.isBlank()) {
            githubUrl = annotationLinks.firstOrNull {
                it.contains("github.com", ignoreCase = true) &&
                        !it.contains("packages", ignoreCase = true) &&
                        it.removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/").filter { s -> s.isNotBlank() }.size <= 2
            } ?: ""
        }

        // Top profile links: only from the header section before the first major heading
        val firstHeadingIndex = listOf("SUMMARY", "PROFESSIONAL SUMMARY", "EXPERIENCE", "WORK EXPERIENCE", "PROJECTS", "SKILLS", "EDUCATION")
            .mapNotNull { kw ->
                val idx = rawText.indexOf(kw, ignoreCase = true)
                if (idx != -1) idx else null
            }.minOrNull() ?: (rawText.length / 4)

        val headerText = rawText.substring(0, firstHeadingIndex)

        // Only include websiteUrl if explicitly present in the header text, and ignore app store/deep links
        val websiteUrl = annotationLinks.firstOrNull { link ->
            val lower = link.lowercase()
            !lower.contains("linkedin.com") &&
                    !lower.contains("github.com") &&
                    !lower.contains("play.google.com") &&
                    !lower.contains("apps.apple.com") &&
                    !lower.startsWith("mailto:") &&
                    !lower.startsWith("tel:") &&
                    headerText.contains(link.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/"), ignoreCase = true)
        } ?: ""

        val topLines = lines.take(5).filter { line ->
            !line.contains("@") && !line.contains("linkedin", ignoreCase = true) && !line.contains("github", ignoreCase = true) && !line.contains("http", ignoreCase = true)
        }

        val rawName = topLines.firstOrNull()?.takeIf { it.length <= 40 && !it.contains("resume", ignoreCase = true) } ?: ""
        val candidateName = cleanSpacedLetters(rawName)
        val secondLine = topLines.getOrNull(1)?.takeIf { line ->
            line.length in 3..80 &&
                    !line.contains(Regex("[0-9]{3,}")) &&
                    !line.contains("phone", ignoreCase = true) &&
                    !line.contains("tel", ignoreCase = true) &&
                    !isLikelyLocation(line) &&
                    !line.equals("Summary", ignoreCase = true) &&
                    !line.equals("Profile", ignoreCase = true) &&
                    !line.equals("Experience", ignoreCase = true) &&
                    !line.equals("Education", ignoreCase = true) &&
                    !line.equals("Skills", ignoreCase = true) &&
                    !line.equals("Projects", ignoreCase = true)
        }
        val headline = secondLine ?: ""

        val contactInfo = ContactInfo(
            email = email,
            phone = phone,
            linkedinUrl = linkedinUrl,
            githubUrl = githubUrl,
            websiteUrl = websiteUrl
        )

        var detectedLocation = ""

        // Strategy 1: Check the header lines for a pipe/bullet-delimited segment or standalone location line
        for (line in lines.take(10)) {
            if (line.startsWith("Summary", ignoreCase = true) || line.startsWith("Professional Summary", ignoreCase = true)) break
            val segments = line.split(Regex("[|•·]")).map { it.trim() }
            for (seg in segments) {
                if (seg.isNotBlank() &&
                    !seg.contains("@") &&
                    !seg.contains("linkedin", ignoreCase = true) &&
                    !seg.contains("github", ignoreCase = true) &&
                    !seg.contains("http", ignoreCase = true) &&
                    !seg.contains(Regex("^\\+?\\d[\\d\\s()-]{6,}")) &&
                    isLikelyLocation(seg) &&
                    seg.length <= 60
                ) {
                    detectedLocation = seg
                    break
                }
            }
            if (detectedLocation.isNotBlank()) break
        }

        // Strategy 2: Fallback to comma-separated location match (e.g. City, State, Country)
        if (detectedLocation.isBlank()) {
            val locationPattern = Pattern.compile("([A-Za-z\\s.'-]+,\\s*[A-Za-z\\s.'-]+(?:,\\s*[A-Za-z\\s.'-]+)?|(?:Remote|Remote / Global))", Pattern.CASE_INSENSITIVE)
            for (line in lines.take(10)) {
                val cleanLine = line.replace(email, "").replace(phone, "")
                val m = locationPattern.matcher(cleanLine)
                if (m.find()) {
                    val candidate = m.group(1)?.trim().orEmpty()
                    if (candidate.length in 3..60 && isLikelyLocation(candidate)) {
                        detectedLocation = candidate
                        break
                    }
                }
            }
        }

        if (detectedLocation.isBlank()) {
            detectedLocation = "Remote / Global"
        }

        // Dynamic Summary Extraction
        val summaryRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:SUMMARY|PROFESSIONAL SUMMARY|EXECUTIVE SUMMARY|PROFILE|ABOUT ME)[ \\t]*[|:–\\-]*[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EMPLOYMENT HISTORY|WORK HISTORY|CAREER HISTORY|(?:EXPERIENCE[ \\t]*(?:[|:–\\-]|$))|PROJECTS|FEATURED PROJECTS|OPEN SOURCE|TECHNICAL SKILLS|SKILLS|EDUCATION|ACADEMICS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val extractedSummary = summaryRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim()?.replace(Regex("[\\r\\n]+"), " ")
            ?: "Professional background extracted from document."

        // Dynamic Work Experience Extraction
        val expRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EMPLOYMENT HISTORY|WORK HISTORY|CAREER HISTORY|(?:EXPERIENCE[ \\t]*(?:[|:–\\-]|$)))[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:PROJECTS|FEATURED PROJECTS|PERSONAL PROJECTS|KEY PROJECTS|OPEN SOURCE|SKILLS|TECHNICAL SKILLS|EDUCATION|ACADEMICS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val expText = expRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val parsedWorkExperience = parseWorkExperience(expText, annotationLinks)

        // Dynamic Projects Extraction
        val projRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:FEATURED PROJECTS|PERSONAL PROJECTS|KEY PROJECTS|ACADEMIC PROJECTS|PROJECTS)[ \\t]*[|:–\\-]*[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:OPEN[- ]SOURCE(?: CONTRIBUTIONS)?|SKILLS|TECHNICAL SKILLS|EDUCATION|ACADEMICS|PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EXPERIENCE|CERTIFICATIONS|PUBLICATIONS|AWARDS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val projText = projRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val parsedProjects = parseProjects(projText, annotationLinks)

        // Dynamic Skills Extraction
        val skillsRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?((?:TECHNICAL SKILLS|CORE COMPETENCIES|SKILLS & TOOLS|AREAS OF EXPERTISE|TECHNOLOGIES|SKILLS))[ \\t]*[|:–\\-]*[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:EDUCATION|ACADEMICS|PROJECTS|FEATURED PROJECTS|OPEN SOURCE|PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EXPERIENCE|CERTIFICATIONS|PUBLICATIONS|AWARDS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val skillsMatch = skillsRegex.find(rawText)
        val rawSkillsHeader = skillsMatch?.groupValues?.getOrNull(1)?.trim() ?: "Technical Skills"
        val skillsText = skillsMatch?.groupValues?.getOrNull(2)?.trim() ?: ""
        val parsedSkills = parseSkills(skillsText, rawSkillsHeader)

        // Dynamic Education Extraction
        val eduRegex = Regex(
            "(?:^|[\\r\\n]+)[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:EDUCATION & CREDENTIALS|ACADEMIC BACKGROUND|ACADEMICS|QUALIFICATIONS|EDUCATION)[ \\t]*[|:–\\-]*[^\\r\\n]*(.*?)[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:[0-9IVXLCDMivxlcdm]+[.)\\-]\\s*)?(?:PROJECTS|OPEN SOURCE|SKILLS|TECHNICAL SKILLS|PROFESSIONAL EXPERIENCE)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val eduMatch = eduRegex.find(rawText)
        val eduText = if (eduMatch != null) {
            val sameLineText = eduMatch.groupValues.getOrNull(1)?.trim() ?: ""
            val nextLinesText = eduMatch.groupValues.getOrNull(2)?.trim() ?: ""
            "$sameLineText\n$nextLinesText".trim()
        } else ""
        val parsedEducation = parseEducation(eduText)

        // Dynamic Additional Sections (e.g. Open Source Contributions, Certifications, Awards, etc.)
        val dynamicList = parseDynamicSections(rawText, annotationLinks)

        // Build the initial section order strictly reflecting the PDF structure:
        // 1. Core sections present in the PDF appear first in their detected PDF order.
        // 2. Any core section missing from the PDF is appended to complete the 6 core sections.
        // 3. Dynamic / Custom sections follow.
        val headingsToEvaluate = if (detectedHeadings.isNotEmpty()) detectedHeadings else detectResumeSectionHeadings(rawText)
        val detectedCoreOrder = mutableListOf<String>()
        val detectedCustomOrder = mutableListOf<String>()

        // Personal Info & Contact is always the first core section
        detectedCoreOrder.add(ResumeSectionConstants.SECTION_PERSONAL)

        for (heading in headingsToEvaluate) {
            val lower = heading.lowercase()
            when {
                lower.contains("personal") || lower.contains("contact") -> {
                    // Already added as first item
                }
                lower.contains("summary") || lower.contains("profile") || lower.contains("objective") || lower.contains("about") -> {
                    if (!detectedCoreOrder.contains(ResumeSectionConstants.SECTION_SUMMARY)) {
                        detectedCoreOrder.add(ResumeSectionConstants.SECTION_SUMMARY)
                    }
                }
                lower.contains("experience") || lower.contains("employment") || lower.contains("work history") || lower.contains("career") -> {
                    if (!detectedCoreOrder.contains(ResumeSectionConstants.SECTION_EXPERIENCE)) {
                        detectedCoreOrder.add(ResumeSectionConstants.SECTION_EXPERIENCE)
                    }
                }
                lower.contains("project") -> {
                    if (!detectedCoreOrder.contains(ResumeSectionConstants.SECTION_PROJECTS)) {
                        detectedCoreOrder.add(ResumeSectionConstants.SECTION_PROJECTS)
                    }
                }
                lower.contains("skill") || lower.contains("technolog") || lower.contains("competenc") -> {
                    if (!detectedCoreOrder.contains(ResumeSectionConstants.SECTION_SKILLS)) {
                        detectedCoreOrder.add(ResumeSectionConstants.SECTION_SKILLS)
                    }
                }
                lower.contains("education") || lower.contains("academic") || lower.contains("qualification") -> {
                    if (!detectedCoreOrder.contains(ResumeSectionConstants.SECTION_EDUCATION)) {
                        detectedCoreOrder.add(ResumeSectionConstants.SECTION_EDUCATION)
                    }
                }
                lower.contains("open source") || lower.contains("contribution") -> {
                    val key = "custom_Open Source Contributions"
                    if (!detectedCustomOrder.contains(key)) {
                        detectedCustomOrder.add(key)
                    }
                }
            }
        }

        // Guarantee all 6 core sections are present: append any missing core section at the end of the core sections
        val allCoreCandidates = listOf(
            ResumeSectionConstants.SECTION_SUMMARY,
            ResumeSectionConstants.SECTION_EXPERIENCE,
            ResumeSectionConstants.SECTION_PROJECTS,
            ResumeSectionConstants.SECTION_SKILLS,
            ResumeSectionConstants.SECTION_EDUCATION
        )
        for (coreKey in allCoreCandidates) {
            if (!detectedCoreOrder.contains(coreKey)) {
                detectedCoreOrder.add(coreKey)
            }
        }

        // Add any remaining dynamic sections
        dynamicList.forEach { dynamic ->
            val key = "custom_${dynamic.sectionTitle}"
            if (!detectedCustomOrder.contains(key)) {
                detectedCustomOrder.add(key)
            }
        }

        val initialSectionOrder = detectedCoreOrder + detectedCustomOrder

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
        val containsKnownPlace = lower.contains("remote") || lower.contains("india") ||
                lower.contains("noida") || lower.contains("ghaziabad") || lower.contains("uttar pradesh") ||
                lower.contains("punjab") || lower.contains("mohali") || lower.contains("delhi") ||
                lower.contains("bangalore") || lower.contains("bengaluru") || lower.contains("gurgaon") ||
                lower.contains("hyderabad") || lower.contains("mumbai") || lower.contains("pune") ||
                lower.contains("dehradun") || lower.contains("uttarakhand") ||
                lower.contains("usa") || lower.contains("united states") || lower.contains("canada") ||
                lower.contains("uk") || lower.contains("united kingdom") || lower.contains("germany")
        val looksLikeCityState = text.matches(Regex("^[A-Za-z\\s.'-]+(?:,\\s*[A-Za-z\\s.'-]+)+$"))
        return containsKnownPlace || looksLikeCityState
    }

    private fun parseWorkExperience(expText: String, annotationLinks: List<String> = emptyList()): List<WorkExperienceItem> {
        if (expText.isBlank()) return emptyList()

        val items = mutableListOf<WorkExperienceItem>()
        val lines = expText.lines().map { it.trim() }.filter { it.isNotBlank() }

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
            "\\b(?:developer|engineer|architect|lead|manager|intern|internship|consultant|specialist|analyst|designer|programmer|administrator|associate|director|officer|cto|ceo|vp|trainee|fresher|tester|qa|fellow|instructor|researcher|scientist|coordinator)\\b",
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
            val delimiterRegex = Regex("\\s*[|•·—–-]\\s*|\\s+at\\s+")
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
                    val lastBullet = currentBullets.last()
                    val continuationWords = listOf("and", "with", "using", "for", "in", "to", "by", "from", "on", "into", "through", "across", "improving", "reducing", "ensuring", "achieving")
                    val isContinuation = startsWithLower ||
                            continuationWords.any { line.startsWith("$it ", ignoreCase = true) } ||
                            lastBullet.endsWith(",") || lastBullet.endsWith(";") ||
                            (!lastBullet.endsWith(".") && line.length > 40)

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
        flushCurrent()

        return items
    }

    private fun isBulletStart(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.matches(Regex("^[.•\\u2022\\u25cf\\u00b7\\u2043\\u25e6\\-*–—].*"))) return true
        if (trimmed.matches(Regex("^[0-9]+[.)\\-]\\s+.*"))) return true
        if (trimmed.matches(Regex("^\\[[0-9]+\\]\\s+.*"))) return true
        val actionVerbs = listOf(
            "Engineered", "Designed", "Leveraged", "Integrated", "Published", "Developed",
            "Improved", "Architected", "Built", "Created", "Implemented", "Maintained",
            "Managed", "Led", "Spearheaded", "Formulated", "Orchestrated", "Automated",
            "Collaborated", "Resolved", "Refactored", "Constructed", "Configured",
            "Delivered", "Executed", "Authored", "Established", "Optimized", "Reduced",
            "Increased", "Enhanced", "Transformed", "Accelerated", "Pioneered"
        )
        return actionVerbs.any { trimmed.startsWith("$it ", ignoreCase = false) }
    }

    private fun cleanBulletLine(line: String): String {
        return line.trim()
            .replace(Regex("^[.•\\u2022\\u25cf\\u00b7\\u2043\\u25e6\\-*–—\\s]+"), "")
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
        "code", "repo", "play store", "app store", "pub.dev", "website"
    )

    private val knownPlatformTags = setOf(
        "android", "ios", "phones & tablets", "phone & tablet", "phones", "tablets", "tablet",
        "mobile", "web", "desktop", "cross-platform", "macos", "windows", "linux",
        "flutter", "dart", "kotlin", "java", "swift", "swiftui", "react", "react native",
        "next.js", "vue", "angular", "node.js", "python", "firebase", "aws", "gcp",
        "azure", "docker", "kubernetes", "graphql", "rest api", "rest apis", "bloc", "provider",
        "hive", "room", "sqlite", "ci/cd"
    )

    private fun parseProjectHeader(rawLine: String): ParsedProjectHeader {
        // Strip trailing Link words or markdown-style links first
        val cleanedLine = rawLine.replace(Regex("\\|\\s*Link.*", RegexOption.IGNORE_CASE), "").trim()
        val segments = cleanedLine.split(Regex("[|•·]")).map { it.trim() }.filter { it.isNotBlank() }
        if (segments.isEmpty()) return ParsedProjectHeader(cleanedLine, emptyList())

        val titleSegments = mutableListOf<String>()
        val tags = mutableListOf<String>()

        for (segment in segments) {
            val lower = segment.lowercase()

            // 1. If it's a link keyword or direct URL, discard it (already parsed to codeUrl/demoUrl)
            if (projectLinkKeywords.contains(lower) || lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
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

            // 4. Trailing platform word attached to descriptor (e.g. "Multi-Model AI Assistant Android")
            val trailingPlatformRegex = Regex("^(.*?)\\s+(Android|iOS|Flutter|Web|Tablet|Tablets|Mobile)$", RegexOption.IGNORE_CASE)
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

            // 5. Otherwise, descriptive phrase or title part
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
        val lines = projText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var currentTitle = ""
        var currentTags = mutableListOf<String>()
        val currentBullets = mutableListOf<String>()

        fun flushProject() {
            if (currentTitle.isNotBlank()) {
                items.add(
                    ProjectItem(
                        id = UUID.randomUUID().toString(),
                        projectTitle = currentTitle,
                        description = currentBullets.joinToString("\n").trim(),
                        techStackTags = currentTags.toList(),
                        demoUrl = "",
                        codeUrl = ""
                    )
                )
                currentBullets.clear()
                currentTags.clear()
            }
        }

        for (line in lines) {
            val isBullet = isBulletStart(line)
            if (!isBullet && (line.contains("|") || line.contains("Link", ignoreCase = true) || (currentTitle.isEmpty() && line.length < 70))) {
                if (currentTitle.isNotBlank()) flushProject()
                val parsedHeader = parseProjectHeader(line)
                currentTitle = parsedHeader.title
                currentTags = parsedHeader.techStackTags.toMutableList()
            } else if (isBullet) {
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

        val lines = skillsText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val detectedCategories = linkedMapOf<String, MutableList<String>>()
        var currentCategory: String? = null
        val flatSkills = mutableListOf<String>()

        fun cleanSkillItem(item: String): String {
            return item.replace(Regex("^[•\\-*·•\\s]+|[•\\-*·•\\s]+$"), "").trim()
        }

        for (line in lines) {
            // Check if this line defines a category header (e.g. "Mobile Development:" or "Mobile Development: Flutter, Android")
            if (line.contains(":")) {
                val colonIdx = line.indexOf(':')
                val potentialCat = line.substring(0, colonIdx).trim()
                val remainder = line.substring(colonIdx + 1).trim()

                // A valid category header name is typically short (<= 45 chars) and doesn't look like a URL/email
                if (potentialCat.length in 2..45 && !potentialCat.startsWith("http") && !potentialCat.contains("@")) {
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
                val skills = line.split(Regex("[,|•·]"))
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
                lower.contains("framework") || lower.contains("mobile") || lower.contains("architecture") || lower.contains("state") || lower.contains("core") -> frameworks.addAll(skills)
                lower.contains("firebase") || lower.contains("cloud") || lower.contains("devops") || lower.contains("database") || lower.contains("storage") -> cloudDevOps.addAll(skills)
                else -> toolsMethodologies.addAll(skills)
            }
        }

        if (cleanedCategories.isEmpty() && cleanedFlat.isNotEmpty()) {
            frameworks.addAll(cleanedFlat)
        }

        return SkillCategories(
            sectionTitle = normalizedTitle,
            languages = languages.distinct().ifEmpty { if (cleanedCategories.isEmpty()) listOf("Kotlin", "Dart", "Java") else emptyList() },
            frameworks = frameworks.distinct().ifEmpty { if (cleanedCategories.isEmpty()) listOf("Android", "Flutter", "Jetpack Compose") else emptyList() },
            cloudDevOps = cloudDevOps.distinct().ifEmpty { if (cleanedCategories.isEmpty()) listOf("Firebase", "Git") else emptyList() },
            toolsMethodologies = toolsMethodologies.distinct().ifEmpty { if (cleanedCategories.isEmpty()) listOf("MVVM", "REST APIs", "SQLite") else emptyList() },
            categorizedDomains = cleanedCategories,
            rawFlatSkills = cleanedFlat
        )
    }

    private fun parseEducation(eduText: String): List<EducationItem> {
        if (eduText.isBlank()) return emptyList()

        val lines = eduText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val datePattern = Regex(
            "(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s*)?\\d{4}\\s*(?:–|-|to)\\s*(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s*)?(?:\\d{4}|Present|Current)",
            RegexOption.IGNORE_CASE
        )
        val rangeMatch = datePattern.find(eduText)
        val formattedDuration = if (rangeMatch != null) {
            rangeMatch.value.trim()
        } else {
            val singleYearPattern = Regex("\\b(?:19|20)\\d{2}\\b")
            val singleYear = singleYearPattern.find(eduText)?.value
            if (singleYear != null) {
                val y = singleYear.toIntOrNull() ?: 2024
                "${y - 4} - $singleYear"
            } else {
                "2020 - 2024"
            }
        }

        fun cleanEduLine(line: String): String {
            return line.replace(datePattern, "")
                .replace(Regex("\\b(?:19|20)\\d{2}\\b"), "")
                .replace(Regex("\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^[|:–\\-\\s,]+|[|:–\\-\\s,]+$"), "")
                .trim()
        }

        fun isInstitutionLine(line: String): Boolean {
            val lower = line.lowercase()
            return lower.contains("college") || lower.contains("university") ||
                    lower.contains("institute") || lower.contains("school") ||
                    lower.contains("academy") || lower.contains("campus") ||
                    lower.contains("aktu") || lower.contains("board")
        }

        fun isDegreeLine(line: String): Boolean {
            val lower = line.lowercase()
            return lower.contains("bachelor") || lower.contains("b.tech") ||
                    lower.contains("btech") || lower.contains("master") ||
                    lower.contains("m.tech") || lower.contains("b.e.") ||
                    lower.contains("m.e.") || lower.contains("bca") ||
                    lower.contains("mca") || lower.contains("diploma") ||
                    lower.contains("degree") || lower.contains("ph.d") ||
                    lower.contains("b.sc") || lower.contains("m.sc") ||
                    lower.contains("bba") || lower.contains("mba")
        }

        val rawInstitution = lines.firstOrNull { isInstitutionLine(it) }
            ?: lines.firstOrNull { line -> !line.matches(Regex(".*\\b(?:19|20)\\d{2}\\b.*")) && line.length > 5 }
            ?: "University / College"

        val rawDegree = lines.firstOrNull { isDegreeLine(it) && it != rawInstitution }
            ?: lines.firstOrNull { it != rawInstitution && !it.matches(Regex(".*\\b(?:19|20)\\d{2}\\b.*")) && !isInstitutionLine(it) }
            ?: lines.getOrNull(1)
            ?: "Bachelor of Technology"

        // Grade / CGPA / GPA / Percentage detection
        val gradePattern = Regex(
            "(?:[|•·–\\-\\s]+)?\\b(?:CGPA|GPA|Percentage|Score|Grade)\\s*[:=]?\\s*([0-9.]+(?:/[0-9.]+)?%?)",
            RegexOption.IGNORE_CASE
        )
        var detectedGrade = ""
        val gradeMatch = gradePattern.find(rawInstitution) ?: gradePattern.find(rawDegree) ?: gradePattern.find(eduText)
        if (gradeMatch != null) {
            detectedGrade = gradeMatch.value.trim().replace(Regex("^[|•·–\\-\\s,]+|[|•·–\\-\\s,]+$"), "").trim()
        }

        // Clean lines and strip grade token from institution
        var cleanedInstitution = cleanEduLine(rawInstitution.replace(gradePattern, ""))
        var cleanedDegree = cleanEduLine(rawDegree.replace(gradePattern, ""))

        if (detectedGrade.isNotBlank() && !cleanedDegree.contains(detectedGrade, ignoreCase = true)) {
            cleanedDegree = if (cleanedDegree.isNotBlank()) "$cleanedDegree | $detectedGrade" else detectedGrade
        }

        if (cleanedInstitution.isBlank() && cleanedDegree.isBlank()) {
            return emptyList()
        }

        return listOf(
            EducationItem(
                institution = toTitleCase(cleanedInstitution),
                degree = toTitleCase(cleanedDegree),
                graduationYear = formattedDuration
            )
        )
    }

    private fun parseDynamicSections(rawText: String, annotationLinks: List<String> = emptyList()): List<DynamicSection> {
        val list = mutableListOf<DynamicSection>()

        val openSourceRegex = Regex(
            "(?:OPEN[- ]SOURCE CONTRIBUTIONS|OPEN[- ]SOURCE)[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:TECHNICAL SKILLS|SKILLS|EDUCATION|ACADEMICS|PROJECTS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val openSourceText = openSourceRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim()

        if (!openSourceText.isNullOrBlank()) {
            val openSourceLines = openSourceText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val rawFirstLine = openSourceLines.firstOrNull() ?: "Open Source Package"

            // Strip link keywords, domains, and action words (pub.dev, github.com, etc.)
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

            list.add(
                DynamicSection(
                    sectionTitle = "Open Source Contributions",
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
            )
        }

        return list
    }
}

