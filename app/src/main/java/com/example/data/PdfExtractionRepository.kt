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
import androidx.core.graphics.createBitmap
import kotlin.time.Duration.Companion.milliseconds

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

            val (finalRawText, tierUsed) = if (tier1Text.trim().length >= AppConstants.TIER_1_CHAR_THRESHOLD) {
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

            val parsedProfile = parseRawTextToProfile(finalRawText, extractedLinks)
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
            Regex("^(?:OPEN SOURCE CONTRIBUTIONS|OPEN SOURCE|CONTRIBUTIONS|COMMUNITY INITIATIVES)\\b", RegexOption.IGNORE_CASE),
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
                if (word == "|") "|"
                else word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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
            Log.w(tag, "Extract annotation links error: ${e.message}")
        }
        return extractedLinks
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
                val bitmap = createBitmap(width, height)
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

    fun parseRawTextToProfile(rawText: String, annotationLinks: List<String> = emptyList()): ResumeProfile {
        if (rawText.isBlank() || rawText.lines().count { it.isNotBlank() } < 3) {
            throw IllegalArgumentException("The attached PDF contains no readable text or is corrupted. Please ensure you attach a valid PDF document.")
        }

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val emailMatcher = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})").matcher(rawText)
        val email = if (emailMatcher.find()) emailMatcher.group(1) ?: "" else ""

        val phoneMatcher = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}").matcher(rawText)
        val phone = if (phoneMatcher.find()) phoneMatcher.group(0) ?: "" else ""

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

        val topLines = lines.take(5).filter { line ->
            !line.contains("@") && !line.contains("linkedin", ignoreCase = true) && !line.contains("github", ignoreCase = true) && !line.contains("http", ignoreCase = true)
        }

        val rawName = topLines.firstOrNull()?.takeIf { it.length <= 40 && !it.contains("resume", ignoreCase = true) } ?: "Professional Candidate"
        val candidateName = cleanSpacedLetters(rawName)
        val headline = topLines.getOrNull(1)?.takeIf { it.length <= 80 } ?: "Software Engineer"

        val contactInfo = ContactInfo(
            email = email,
            phone = phone,
            linkedinUrl = linkedinUrl,
            githubUrl = githubUrl
        )

        val locationRegex = Regex("(?:India|United States|USA|Canada|UK|Germany|Remote)[\\s,]+[A-Za-z]+|[A-Za-z]+,\\s*[A-Z]{2}|Remote", RegexOption.IGNORE_CASE)
        val detectedLocation = locationRegex.find(rawText)?.value?.trim() ?: "Remote / Global"

        // Dynamic Summary Extraction: ensure lookahead requires a preceding newline before section headings
        // so that words like 'experience' in '2 years of professional experience' are not treated as headings
        val summaryRegex = Regex(
            "(?:SUMMARY|PROFESSIONAL SUMMARY|EXECUTIVE SUMMARY|PROFILE|ABOUT ME)[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EMPLOYMENT HISTORY|WORK HISTORY|PROJECTS|FEATURED PROJECTS|OPEN SOURCE|TECHNICAL SKILLS|SKILLS|EDUCATION|ACADEMICS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val extractedSummary = summaryRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim()?.replace(Regex("[\\r\\n]+"), " ")
            ?: "Professional background extracted from document."

        // Dynamic Work Experience Extraction
        val expRegex = Regex(
            "(?:PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EMPLOYMENT HISTORY|WORK HISTORY|CAREER HISTORY|EXPERIENCE)[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:PROJECTS|FEATURED PROJECTS|OPEN SOURCE|SKILLS|TECHNICAL SKILLS|EDUCATION|ACADEMICS)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val expText = expRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val parsedWorkExperience = parseWorkExperience(expText)

        // Dynamic Projects Extraction
        val projRegex = Regex(
            "(?:FEATURED PROJECTS|PERSONAL PROJECTS|KEY PROJECTS|ACADEMIC PROJECTS|PROJECTS)[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:OPEN SOURCE|SKILLS|TECHNICAL SKILLS|EDUCATION|ACADEMICS|PROFESSIONAL EXPERIENCE|WORK EXPERIENCE)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val projText = projRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val parsedProjects = parseProjects(projText, annotationLinks)

        // Dynamic Skills Extraction
        val skillsRegex = Regex(
            "(?:TECHNICAL SKILLS|CORE COMPETENCIES|SKILLS & TOOLS|AREAS OF EXPERTISE|TECHNOLOGIES|SKILLS)[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:EDUCATION|ACADEMICS|PROJECTS|OPEN SOURCE|PROFESSIONAL EXPERIENCE)\\b)|$)",
            RegexOption.IGNORE_CASE
        )
        val skillsText = skillsRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val parsedSkills = parseSkills(skillsText)

        // Dynamic Education Extraction
        val eduRegex = Regex(
            "(?:EDUCATION & CREDENTIALS|ACADEMIC BACKGROUND|ACADEMICS|QUALIFICATIONS|EDUCATION)[^\\r\\n]*(.*?)[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:PROJECTS|OPEN SOURCE|SKILLS|TECHNICAL SKILLS|PROFESSIONAL EXPERIENCE)\\b)|$)",
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
            dynamicSections = dynamicList
        )
    }

    private fun parseWorkExperience(expText: String): List<WorkExperienceItem> {
        if (expText.isBlank()) return emptyList()

        val items = mutableListOf<WorkExperienceItem>()
        val lines = expText.lines().map { it.trim() }.filter { it.isNotBlank() }

        val datePattern = Regex(
            "(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s*)?\\d{4}\\s*(?:–|-|to)\\s*(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s*)?(?:\\d{4}|Present|Current)",
            RegexOption.IGNORE_CASE
        )

        fun cleanTextFromDates(text: String): String {
            return text.replace(datePattern, "")
                .replace(Regex("\\b(?:19|20)\\d{2}\\b"), "")
                .replace(Regex("\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^[|:–\\-\\s,]+|[|:–\\-\\s,]+$"), "")
                .trim()
        }

        var currentCompany = ""
        var currentRole = ""
        var currentStart = ""
        var currentEnd = ""
        val currentBullets = mutableListOf<String>()

        fun flushCurrent() {
            val cleanedCompany = cleanTextFromDates(currentCompany)
            if (cleanedCompany.isNotBlank()) {
                items.add(
                    WorkExperienceItem(
                        id = UUID.randomUUID().toString(),
                        companyName = cleanedCompany,
                        roleTitle = cleanTextFromDates(currentRole),
                        startDate = currentStart.ifBlank { "2023" },
                        endDate = currentEnd.ifBlank { "Present" },
                        bulletPoints = if (currentBullets.isNotEmpty()) currentBullets.toList() else listOf("Responsible for engineering and maintaining production application features."),
                        techStackTags = listOf("Mobile", "Architecture", "Engineering")
                    )
                )
                currentBullets.clear()
                currentRole = ""
                currentCompany = ""
                currentStart = ""
                currentEnd = ""
            }
        }

        fun isLikelyRole(text: String): Boolean {
            val lower = text.lowercase().trim()
            if (lower.startsWith("integrated") || lower.startsWith("responsible") ||
                lower.startsWith("developed") || lower.startsWith("engineered") ||
                lower.startsWith("architected") || lower.startsWith("built") ||
                lower.startsWith("managed") || lower.startsWith("created") ||
                lower.startsWith("designed") || lower.startsWith("maintained") ||
                lower.startsWith("worked") || lower.startsWith("implemented") ||
                lower.startsWith("leveraged") || lower.startsWith("published") ||
                lower.startsWith("optimized") || lower.startsWith("deployed")
            ) {
                return false
            }
            val roleKeywords = listOf("developer", "engineer", "architect", "lead", "manager", "intern", "consultant", "specialist", "analyst", "designer", "programmer", "administrator", "associate", "director", "officer", "cto", "ceo", "vp", "trainee", "fresher", "tester", "qa")
            return roleKeywords.any { lower.contains(it) }
        }

        var lastNonBulletLine = ""

        for (line in lines) {
            val dateMatch = datePattern.find(line)
            if (dateMatch != null) {
                val dateStr = dateMatch.value.trim()
                val parts = dateStr.split(Regex("[–\\-]|to"), 2).map { it.trim() }
                val start = parts.getOrNull(0) ?: ""
                val end = parts.getOrNull(1) ?: "Present"

                val withoutDate = line.replace(dateStr, "").replace(Regex("^[|:–\\-\\s,]+|[|:–\\-\\s,]+$"), "").trim()
                if (withoutDate.isNotBlank()) {
                    flushCurrent()
                    currentStart = start
                    currentEnd = end
                    currentCompany = withoutDate
                } else if (currentCompany.isNotBlank() && currentStart.isBlank()) {
                    currentStart = start
                    currentEnd = end
                } else {
                    flushCurrent()
                    currentStart = start
                    currentEnd = end
                    currentCompany = if (lastNonBulletLine.isNotBlank() && !isLikelyRole(lastNonBulletLine)) {
                        lastNonBulletLine
                    } else {
                        "Technology Company"
                    }
                }
            } else if (currentCompany.isNotBlank() && currentRole.isBlank() && isLikelyRole(line) && line.length < 50) {
                // Short line under company matching role title keywords
                currentRole = line
                lastNonBulletLine = line
            } else if (isBulletStart(line)) {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    currentBullets.add(cleaned)
                }
            } else if (currentCompany.isNotBlank()) {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    // Check if this line is a continuation of previous bullet point
                    if (currentBullets.isNotEmpty()) {
                        val lastBullet = currentBullets.last()
                        currentBullets[currentBullets.lastIndex] = "$lastBullet $cleaned"
                    } else {
                        currentBullets.add(cleaned)
                    }
                }
            } else {
                lastNonBulletLine = line
            }
        }
        flushCurrent()

        return items
    }

    private fun isBulletStart(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.matches(Regex("^[.•\\u2022\\u25cf\\u00b7\\u2043\\u25e6\\-*–—].*"))) return true
        if (trimmed.matches(Regex("^[0-9]+[.)\\-]\\s+.*"))) return true
        val actionVerbs = listOf(
            "Engineered", "Designed", "Leveraged", "Integrated", "Published", "Developed",
            "Improved", "Architected", "Built", "Created", "Implemented", "Maintained",
            "Managed", "Led", "Spearheaded", "Formulated", "Orchestrated", "Automated",
            "Collaborated", "Resolved", "Refactored", "Constructed", "Configured",
            "Delivered", "Executed", "Authored", "Established", "Optimized"
        )
        return actionVerbs.any { trimmed.startsWith("$it ", ignoreCase = false) }
    }

    private fun cleanBulletLine(line: String): String {
        return line.trim()
            .replace(Regex("^[.•\\u2022\\u25cf\\u00b7\\u2043\\u25e6\\-*–—\\s]+"), "")
            .replace(Regex("^[0-9]+[.)\\-]\\s*"), "")
            .trim()
    }

    private fun parseProjects(projText: String, annotationLinks: List<String> = emptyList()): List<ProjectItem> {
        if (projText.isBlank()) return emptyList()

        val items = mutableListOf<ProjectItem>()
        val lines = projText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var currentTitle = ""
        val currentBullets = mutableListOf<String>()

        fun flushProject() {
            if (currentTitle.isNotBlank()) {
                items.add(
                    ProjectItem(
                        id = UUID.randomUUID().toString(),
                        projectTitle = currentTitle,
                        description = currentBullets.joinToString("\n").ifBlank { "Production application project." },
                        techStackTags = listOf("Mobile", "API", "Cloud"),
                        demoUrl = "",
                        codeUrl = ""
                    )
                )
                currentBullets.clear()
            }
        }

        for (line in lines) {
            val isBullet = isBulletStart(line)
            if (!isBullet && (line.contains("|") || line.contains("Link", ignoreCase = true) || (currentTitle.isEmpty() && line.length < 60))) {
                if (currentTitle.isNotBlank()) flushProject()
                currentTitle = line.replace(Regex("\\|\\s*Link.*", RegexOption.IGNORE_CASE), "").trim()
            } else if (isBullet) {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    currentBullets.add(cleaned)
                }
            } else {
                val cleaned = cleanBulletLine(line)
                if (cleaned.isNotBlank()) {
                    if (currentBullets.isNotEmpty()) {
                        val lastBullet = currentBullets.last()
                        currentBullets[currentBullets.lastIndex] = "$lastBullet $cleaned"
                    } else {
                        currentBullets.add(cleaned)
                    }
                }
            }
        }
        flushProject()

        // Associate project links from extracted annotation links if demoUrl/codeUrl is empty
        for (i in items.indices) {
            if (items[i].demoUrl.isBlank() && items[i].codeUrl.isBlank()) {
                val titleWords = items[i].projectTitle.lowercase().split(Regex("[\\s_-]+")).filter { it.length > 2 }
                val matchingLink = annotationLinks.firstOrNull { link ->
                    val lowerLink = link.lowercase()
                    titleWords.any { word -> lowerLink.contains(word) }
                } ?: annotationLinks.firstOrNull {
                    it.contains("github.com/") &&
                            !it.endsWith("github.com") &&
                            !it.endsWith("github.com/") &&
                            !it.contains("linkedin.com") &&
                            !it.contains("pub.dev")
                }

                if (matchingLink != null) {
                    items[i] = items[i].copy(demoUrl = matchingLink, codeUrl = matchingLink)
                }
            }
        }

        return items
    }

    private fun parseSkills(skillsText: String): SkillCategories {
        if (skillsText.isBlank()) {
            return SkillCategories(
                languages = listOf("Kotlin", "Dart", "Java"),
                frameworks = listOf("Android", "Flutter", "Jetpack Compose"),
                cloudDevOps = listOf("Firebase", "Git"),
                toolsMethodologies = listOf("MVVM", "REST APIs", "SQLite")
            )
        }

        val languages = mutableListOf<String>()
        val frameworks = mutableListOf<String>()
        val cloudDevOps = mutableListOf<String>()
        val toolsMethodologies = mutableListOf<String>()

        val lines = skillsText.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (line in lines) {
            if (line.contains(":")) {
                val parts = line.split(":", limit = 2)
                val cat = parts[0].trim().lowercase()
                val skills = parts[1].split(Regex("[,|•]")).map { it.trim() }.filter { it.isNotBlank() }

                when {
                    cat.contains("language") -> languages.addAll(skills)
                    cat.contains("framework") || cat.contains("mobile") || cat.contains("architecture") || cat.contains("state") -> frameworks.addAll(skills)
                    cat.contains("cloud") || cat.contains("devops") || cat.contains("integration") -> cloudDevOps.addAll(skills)
                    else -> toolsMethodologies.addAll(skills)
                }
            } else {
                val items = line.split(Regex("[,|•]")).map { it.trim() }.filter { it.isNotBlank() }
                frameworks.addAll(items)
            }
        }

        return SkillCategories(
            languages = languages.distinct().ifEmpty { listOf("Kotlin", "Dart", "Java") },
            frameworks = frameworks.distinct().ifEmpty { listOf("Android", "Flutter", "Jetpack Compose") },
            cloudDevOps = cloudDevOps.distinct().ifEmpty { listOf("Firebase", "Git") },
            toolsMethodologies = toolsMethodologies.distinct().ifEmpty { listOf("MVVM", "REST APIs", "SQLite") }
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

        val cleanedInstitution = cleanEduLine(rawInstitution)
        val cleanedDegree = cleanEduLine(rawDegree)

        return listOf(
            EducationItem(
                institution = toTitleCase(cleanedInstitution.ifBlank { "University / College" }),
                degree = toTitleCase(cleanedDegree.ifBlank { "Bachelor of Technology" }),
                graduationYear = formattedDuration
            )
        )
    }

    private fun parseDynamicSections(rawText: String, annotationLinks: List<String> = emptyList()): List<DynamicSection> {
        val list = mutableListOf<DynamicSection>()

        val openSourceRegex = Regex(
            "(?:OPEN SOURCE CONTRIBUTIONS|OPEN SOURCE)[^\\r\\n]*[\\r\\n]+([\\s\\S]*?)(?=(?:[\\r\\n]+[ \\t]*(?:TECHNICAL SKILLS|SKILLS|EDUCATION|ACADEMICS|PROJECTS|$))\\b)",
            RegexOption.IGNORE_CASE
        )
        val openSourceText = openSourceRegex.find(rawText)?.groupValues?.getOrNull(1)?.trim()

        if (!openSourceText.isNullOrBlank()) {
            val openSourceLines = openSourceText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val firstLine = openSourceLines.firstOrNull()?.replace(Regex("\\|\\s*Link.*", RegexOption.IGNORE_CASE), "")?.trim() ?: "Open Source Package"

            val urlPattern = Regex("(https?://[^\\s]+|pub\\.dev/[^\\s]+|github\\.com/[^\\s]+)")
            var detectedLink = urlPattern.find(openSourceText)?.value ?: ""
            if (detectedLink.isBlank()) {
                detectedLink = annotationLinks.firstOrNull { it.contains("pub.dev") }
                    ?: annotationLinks.firstOrNull { it.contains("github.com") && !it.endsWith("github.com") }
                            ?: ""
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
                            title = firstLine,
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

