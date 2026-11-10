package com.example.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ContactInfo(
    val email: String = "",
    val phone: String = "",
    val linkedinUrl: String = "",
    val githubUrl: String = "",
    val websiteUrl: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "email" to email,
        "phone" to phone,
        "linkedinUrl" to linkedinUrl,
        "githubUrl" to githubUrl,
        "websiteUrl" to websiteUrl
    )

    companion object {
        fun fromMap(map: Map<String, Any?>?): ContactInfo {
            if (map == null) return ContactInfo()
            return ContactInfo(
                email = map["email"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                linkedinUrl = map["linkedinUrl"] as? String ?: "",
                githubUrl = map["githubUrl"] as? String ?: "",
                websiteUrl = map["websiteUrl"] as? String ?: ""
            )
        }
    }
}

@Serializable
data class ProfileHero(
    val fullName: String = "",
    val headlineTitle: String = "",
    val location: String = "",
    val contactInfo: ContactInfo = ContactInfo(),
    val targetCompany: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "fullName" to fullName,
        "headlineTitle" to headlineTitle,
        "location" to location,
        "contactInfo" to contactInfo.toMap(),
        "targetCompany" to targetCompany
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>?): ProfileHero {
            if (map == null) return ProfileHero()
            return ProfileHero(
                fullName = map["fullName"] as? String ?: "",
                headlineTitle = map["headlineTitle"] as? String ?: "",
                location = map["location"] as? String ?: "",
                contactInfo = ContactInfo.fromMap(map["contactInfo"] as? Map<String, Any?>),
                targetCompany = map["targetCompany"] as? String ?: ""
            )
        }
    }
}

@Serializable
data class ClientAppItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "url" to url
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ClientAppItem {
            return ClientAppItem(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                name = map["name"] as? String ?: "",
                url = map["url"] as? String ?: ""
            )
        }
    }
}

@Serializable
data class WorkExperienceItem(
    val id: String = UUID.randomUUID().toString(),
    val companyName: String = "",
    val roleTitle: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val bulletPoints: List<String> = emptyList(),
    val techStackTags: List<String> = emptyList(),
    val clientApps: List<ClientAppItem> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "companyName" to companyName,
        "roleTitle" to roleTitle,
        "startDate" to startDate,
        "endDate" to endDate,
        "bulletPoints" to bulletPoints,
        "techStackTags" to techStackTags,
        "clientApps" to clientApps.map { it.toMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): WorkExperienceItem {
            return WorkExperienceItem(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                companyName = map["companyName"] as? String ?: "",
                roleTitle = map["roleTitle"] as? String ?: "",
                startDate = map["startDate"] as? String ?: "",
                endDate = map["endDate"] as? String ?: "",
                bulletPoints = (map["bulletPoints"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                techStackTags = (map["techStackTags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                clientApps = (map["clientApps"] as? List<*>)?.mapNotNull {
                    when (it) {
                        is Map<*, *> -> ClientAppItem.fromMap(it as Map<String, Any?>)
                        is String -> ClientAppItem(name = it)
                        else -> null
                    }
                } ?: emptyList()
            )
        }
    }
}

@Serializable
data class ProjectItem(
    val id: String = UUID.randomUUID().toString(),
    val projectTitle: String = "",
    val description: String = "",
    val techStackTags: List<String> = emptyList(),
    val demoUrl: String = "",
    val codeUrl: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "projectTitle" to projectTitle,
        "description" to description,
        "techStackTags" to techStackTags,
        "demoUrl" to demoUrl,
        "codeUrl" to codeUrl
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): ProjectItem {
            return ProjectItem(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                projectTitle = map["projectTitle"] as? String ?: "",
                description = map["description"] as? String ?: "",
                techStackTags = (map["techStackTags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                demoUrl = map["demoUrl"] as? String ?: "",
                codeUrl = map["codeUrl"] as? String ?: ""
            )
        }
    }
}

@Serializable
data class SkillCategories(
    val sectionTitle: String = "TECHNICAL SKILLS",
    val languages: List<String> = emptyList(),
    val frameworks: List<String> = emptyList(),
    val cloudDevOps: List<String> = emptyList(),
    val toolsMethodologies: List<String> = emptyList(),
    val categorizedDomains: Map<String, List<String>> = emptyMap(),
    val rawFlatSkills: List<String> = emptyList(),
    val customCategories: Map<String, List<String>> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "sectionTitle" to sectionTitle,
        "languages" to languages,
        "frameworks" to frameworks,
        "cloudDevOps" to cloudDevOps,
        "toolsMethodologies" to toolsMethodologies,
        "categorizedDomains" to categorizedDomains,
        "rawFlatSkills" to rawFlatSkills,
        "customCategories" to customCategories
    )

    val totalCount: Int
        get() {
            if (categorizedDomains.isNotEmpty()) {
                return categorizedDomains.values.sumOf { it.size }
            }
            if (rawFlatSkills.isNotEmpty()) {
                return rawFlatSkills.size
            }
            return languages.size + frameworks.size + cloudDevOps.size + toolsMethodologies.size + customCategories.values.sumOf { it.size }
        }

    val isCategorized: Boolean
        get() = categorizedDomains.isNotEmpty() || (listOf(languages, frameworks, cloudDevOps, toolsMethodologies).count { it.isNotEmpty() } > 1)

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>?): SkillCategories {
            if (map == null) return SkillCategories()
            val custom = (map["customCategories"] as? Map<*, *>)?.entries?.associate {
                (it.key as? String ?: "") to ((it.value as? List<*>)?.filterIsInstance<String>() ?: emptyList())
            }?.filterKeys { it.isNotBlank() } ?: emptyMap()

            val catDomains = (map["categorizedDomains"] as? Map<*, *>)?.entries?.associate {
                (it.key as? String ?: "") to ((it.value as? List<*>)?.filterIsInstance<String>() ?: emptyList())
            }?.filterKeys { it.isNotBlank() } ?: emptyMap()

            val flat = (map["rawFlatSkills"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            return SkillCategories(
                sectionTitle = map["sectionTitle"] as? String ?: "TECHNICAL SKILLS",
                languages = (map["languages"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                frameworks = (map["frameworks"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                cloudDevOps = (map["cloudDevOps"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                toolsMethodologies = (map["toolsMethodologies"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                categorizedDomains = catDomains,
                rawFlatSkills = flat,
                customCategories = custom
            )
        }
    }
}

@Serializable
data class EducationItem(
    val institution: String = "",
    val degree: String = "",
    val graduationYear: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "institution" to institution,
        "degree" to degree,
        "graduationYear" to graduationYear
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): EducationItem {
            return EducationItem(
                institution = map["institution"] as? String ?: "",
                degree = map["degree"] as? String ?: "",
                graduationYear = map["graduationYear"] as? String ?: ""
            )
        }
    }
}

@Serializable
data class CoreSections(
    val profileSummary: String = "",
    val workExperience: List<WorkExperienceItem> = emptyList(),
    val projects: List<ProjectItem> = emptyList(),
    val skills: SkillCategories = SkillCategories(),
    val education: List<EducationItem> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "profileSummary" to profileSummary,
        "workExperience" to workExperience.map { it.toMap() },
        "projects" to projects.map { it.toMap() },
        "skills" to skills.toMap(),
        "education" to education.map { it.toMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>?): CoreSections {
            if (map == null) return CoreSections()
            val expList = (map["workExperience"] as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()
                ?.map { WorkExperienceItem.fromMap(it) } ?: emptyList()
            val projList = (map["projects"] as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()
                ?.map { ProjectItem.fromMap(it) } ?: emptyList()
            val eduList = (map["education"] as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()
                ?.map { EducationItem.fromMap(it) } ?: emptyList()

            return CoreSections(
                profileSummary = map["profileSummary"] as? String ?: "",
                workExperience = expList,
                projects = projList,
                skills = SkillCategories.fromMap(map["skills"] as? Map<String, Any?>),
                education = eduList
            )
        }
    }
}

@Serializable
data class DynamicItem(
    val title: String = "",
    val subtitle: String = "",
    val dateOrPeriod: String = "",
    val description: String = "",
    val link: String = "",
    val bulletPoints: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "subtitle" to subtitle,
        "dateOrPeriod" to dateOrPeriod,
        "description" to description,
        "link" to link,
        "bulletPoints" to bulletPoints
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): DynamicItem {
            return DynamicItem(
                title = map["title"] as? String ?: "",
                subtitle = map["subtitle"] as? String ?: "",
                dateOrPeriod = map["dateOrPeriod"] as? String ?: "",
                description = map["description"] as? String ?: "",
                link = map["link"] as? String ?: "",
                bulletPoints = (map["bulletPoints"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }
}

@Serializable
data class DynamicSection(
    val sectionTitle: String = "",
    val items: List<DynamicItem> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "sectionTitle" to sectionTitle,
        "items" to items.map { it.toMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): DynamicSection {
            val itemList = (map["items"] as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()
                ?.map { DynamicItem.fromMap(it) } ?: emptyList()

            return DynamicSection(
                sectionTitle = map["sectionTitle"] as? String ?: "",
                items = itemList
            )
        }
    }
}

object ResumeSectionConstants {
    const val SECTION_PERSONAL = "section_personal"
    const val SECTION_SUMMARY = "section_summary"
    const val SECTION_EXPERIENCE = "section_experience"
    const val SECTION_PROJECTS = "section_projects"
    const val SECTION_SKILLS = "section_skills"
    const val SECTION_EDUCATION = "section_education"
    const val CUSTOM_PREFIX = "custom_"

    val DEFAULT_CORE_ORDER = listOf(
        SECTION_PERSONAL,
        SECTION_SUMMARY,
        SECTION_EXPERIENCE,
        SECTION_PROJECTS,
        SECTION_SKILLS,
        SECTION_EDUCATION
    )
}

@Serializable
data class ResumeProfile(
    val hero: ProfileHero = ProfileHero(),
    val coreSections: CoreSections = CoreSections(),
    val dynamicSections: List<DynamicSection> = emptyList(),
    val sectionOrder: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "hero" to hero.toMap(),
        "coreSections" to coreSections.toMap(),
        "dynamicSections" to dynamicSections.map { it.toMap() },
        "sectionOrder" to sectionOrder
    )

    fun toJson(): String {
        return jsonConfig.encodeToString(this)
    }

    companion object {
        val jsonConfig = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(jsonStr: String): ResumeProfile {
            return jsonConfig.decodeFromString(jsonStr)
        }

        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): ResumeProfile {
            val dynamicList = (map["dynamicSections"] as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()
                ?.map { DynamicSection.fromMap(it) } ?: emptyList()

            val orderList = (map["sectionOrder"] as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()

            return ResumeProfile(
                hero = ProfileHero.fromMap(map["hero"] as? Map<String, Any?>),
                coreSections = CoreSections.fromMap(map["coreSections"] as? Map<String, Any?>),
                dynamicSections = dynamicList,
                sectionOrder = orderList
            )
        }

        fun sampleProfile(): ResumeProfile {
            return ResumeProfile(
                hero = ProfileHero(
                    fullName = "Alex Mercer",
                    headlineTitle = "Staff Mobile Systems Architect | Android & Cross-Platform",
                    location = "San Francisco, CA · Remote",
                    targetCompany = "",
                    contactInfo = ContactInfo(
                        email = "alex.mercer.dev@example.com",
                        phone = "+1 (415) 555-0198",
                        linkedinUrl = "https://linkedin.com/in/alexmercer-dev",
                        githubUrl = "https://github.com/alexmercer",
                        websiteUrl = "https://alexmercer.design"
                    )
                ),
                coreSections = CoreSections(
                    profileSummary = "Product-minded Mobile Systems Architect with 8+ years architecting high-scale Android applications, reactive Kotlin Flow micro-features, and on-device ML pipelines. Specialized in fluid design systems, resilient offline data synchronization, and high-throughput modular architectures.",
                    workExperience = listOf(
                        WorkExperienceItem(
                            id = "exp-1",
                            companyName = "Apex Mobile Technologies",
                            roleTitle = "Staff Mobile Architect",
                            startDate = "Jan 2022",
                            endDate = "Present",
                            bulletPoints = listOf(
                                "Spearheaded modernization of multi-module Android architecture to Jetpack Compose and Coroutines Flow, reducing UI frame latency by 38%.",
                                "Architected high-throughput offline-first synchronization engine supporting 1.5M daily active users with automatic conflict resolution.",
                                "Established design system token architecture and automated visual regression testing pipeline across 12 distributed feature teams."
                            ),
                            clientApps = listOf(
                                ClientAppItem(
                                    name = "ApexPay",
                                    url = "https://play.google.com/store/apps/details?id=com.apex.pay.sample"
                                ),
                                ClientAppItem(
                                    name = "ApexFlow",
                                    url = "https://play.google.com/store/apps/details?id=com.apex.flow.sample"
                                )
                            ),
                            techStackTags = emptyList()
                        ),
                        WorkExperienceItem(
                            id = "exp-2",
                            companyName = "CloudStream Systems",
                            roleTitle = "Senior Android Engineer",
                            startDate = "Jun 2019",
                            endDate = "Dec 2021",
                            bulletPoints = listOf(
                                "Designed resilient bi-directional WebSocket client layer with automated backoff retry, achieving 99.98% connection reliability.",
                                "Optimized cold-start launch latency from 1.6s to 410ms through Android Baseline Profiles and modular dependency injection tuning."
                            ),
                            clientApps = emptyList(),
                            techStackTags = listOf("Kotlin", "Coroutines", "Room", "Hilt", "WebSockets", "CI/CD")
                        )
                    ),
                    projects = listOf(
                        ProjectItem(
                            id = "proj-1",
                            projectTitle = "NeuralDoc · On-Device Document Intelligence",
                            description = "• Engineered privacy-first document scanning engine with CameraX and on-device ML Kit text recognition.\n• Built reactive local persistence pipeline utilizing encrypted Room database and Proto DataStore.\n• Published open-source library on GitHub with 1.8k+ stars and automated semantic versioning.",
                            techStackTags = listOf("Jetpack Compose", "ML Kit", "CameraX", "Room"),
                            demoUrl = "https://github.com/alexmercer/neuraldoc",
                            codeUrl = "https://github.com/alexmercer/neuraldoc"
                        ),
                        ProjectItem(
                            id = "proj-2",
                            projectTitle = "PulseSync · Distributed Event Engine",
                            description = "• Built high-performance event stream aggregator capable of processing 40,000 live telemetry updates per second.\n• Implemented conflict-free replicated data types (CRDTs) to power peer-to-peer collaboration without central lock contention.",
                            techStackTags = listOf("Kotlin Multiplatform", "Ktor", "Flow", "SQLite"),
                            demoUrl = "https://play.google.com/store/apps/details?id=com.cloudstream.pulsesync",
                            codeUrl = "https://play.google.com/store/apps/details?id=com.cloudstream.pulsesync"
                        )
                    ),
                    skills = SkillCategories(
                        sectionTitle = "TECHNICAL SKILLS",
                        languages = listOf("Kotlin", "Java", "Dart", "Rust", "SQL"),
                        frameworks = listOf("Jetpack Compose", "Android SDK", "Coroutines & Flow", "Clean MVVM", "Hilt"),
                        cloudDevOps = listOf("Firebase", "Google Cloud", "GitHub Actions", "Docker", "CI/CD"),
                        toolsMethodologies = listOf("Clean Architecture", "MVI", "TDD & Robolectric", "Git"),
                        categorizedDomains = linkedMapOf(
                            "Programming Languages" to listOf("Kotlin", "Java", "Dart", "Rust", "SQL"),
                            "Mobile & Architecture" to listOf("Jetpack Compose", "Android SDK", "Coroutines & Flow", "Clean MVVM", "Hilt"),
                            "Networking & Storage" to listOf("Ktor", "Retrofit", "Room", "DataStore", "SQLite"),
                            "Cloud & DevOps" to listOf("Firebase", "Google Cloud", "GitHub Actions", "Docker", "CI/CD")
                        )
                    ),
                    education = listOf(
                        EducationItem(
                            institution = "Stanford University",
                            degree = "Master of Science - Computer Science | Systems & Architecture Track",
                            graduationYear = "2017 – 2019"
                        ),
                        EducationItem(
                            institution = "University of California, Berkeley",
                            degree = "Bachelor of Science - Electrical Engineering & Computer Sciences (EECS)",
                            graduationYear = "2013 – 2017"
                        )
                    )
                ),
                dynamicSections = listOf(
                    DynamicSection(
                        sectionTitle = "Open Source & Certifications",
                        items = listOf(
                            DynamicItem(
                                title = "compose_motion_transitions",
                                subtitle = "Creator & Lead Maintainer",
                                dateOrPeriod = "Active",
                                description = "• Engineered fluid physics-driven spring transition system for Jetpack Compose with zero frame drop.\n• Published to Maven Central with 800+ weekly downloads and interactive component documentation.",
                                bulletPoints = listOf(
                                    "Engineered fluid physics-driven spring transition system for Jetpack Compose with zero frame drop.",
                                    "Published to Maven Central with 800+ weekly downloads and interactive component documentation."
                                ),
                                link = "https://github.com/alexmercer/compose-motion-transitions"
                            ),
                            DynamicItem(
                                title = "Google Associate Android Developer (AAD)",
                                subtitle = "Google Developers Certification",
                                dateOrPeriod = "Verified",
                                description = "• Validated competency in Android architectural components, asynchronous processing, and UI accessibility.\n• Completed practical performance evaluation assessed by Google Developers certification board.",
                                bulletPoints = listOf(
                                    "Validated competency in Android architectural components, asynchronous processing, and UI accessibility.",
                                    "Completed practical performance evaluation assessed by Google Developers certification board."
                                ),
                                link = "https://www.credly.com/badges/sample-associate-android-developer"
                            )
                        )
                    )
                ),
                sectionOrder = ResumeSectionConstants.DEFAULT_CORE_ORDER + listOf("custom_Open Source Contributions")
            )
        }
    }
}
