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
data class WorkExperienceItem(
    val id: String = UUID.randomUUID().toString(),
    val companyName: String = "",
    val roleTitle: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val bulletPoints: List<String> = emptyList(),
    val techStackTags: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "companyName" to companyName,
        "roleTitle" to roleTitle,
        "startDate" to startDate,
        "endDate" to endDate,
        "bulletPoints" to bulletPoints,
        "techStackTags" to techStackTags
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
                techStackTags = (map["techStackTags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
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

@Serializable
data class ResumeProfile(
    val hero: ProfileHero = ProfileHero(),
    val coreSections: CoreSections = CoreSections(),
    val dynamicSections: List<DynamicSection> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "hero" to hero.toMap(),
        "coreSections" to coreSections.toMap(),
        "dynamicSections" to dynamicSections.map { it.toMap() }
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

            return ResumeProfile(
                hero = ProfileHero.fromMap(map["hero"] as? Map<String, Any?>),
                coreSections = CoreSections.fromMap(map["coreSections"] as? Map<String, Any?>),
                dynamicSections = dynamicList
            )
        }

        fun sampleProfile(): ResumeProfile {
            return ResumeProfile(
                hero = ProfileHero(
                    fullName = "Alex Mercer",
                    headlineTitle = "Senior Staff Android & Distributed Systems Engineer",
                    location = "San Francisco, CA · Remote",
                    contactInfo = ContactInfo(
                        email = "alex.mercer.dev@gmail.com",
                        phone = "+1 (415) 890-4321",
                        linkedinUrl = "https://linkedin.com/in/alexmercer-dev",
                        githubUrl = "https://github.com/alexmercer",
                        websiteUrl = "https://alexmercer.design"
                    )
                ),
                coreSections = CoreSections(
                    profileSummary = "Product-minded Mobile Architect with 8+ years building high-concurrency mobile systems, reactive Jetpack Compose architectures, and privacy-first local ML pipelines. Passionate about developer tooling, fluid UI interactions, and high-performance cross-platform bridges.",
                    workExperience = listOf(
                        WorkExperienceItem(
                            id = "exp-1",
                            companyName = "Apex Mobile Technologies",
                            roleTitle = "Lead Mobile Architect",
                            startDate = "2022",
                            endDate = "Present",
                            bulletPoints = listOf(
                                "Spearheaded migration of legacy Android multi-module architecture to Jetpack Compose and Coroutines Flow, cutting UI render lag by 42%.",
                                "Engineered on-device document intelligence pipeline delivering zero-latency text parsing with local ML Kit and native C++ decoding.",
                                "Mentored 14 mobile engineers across Android & iOS, standardizing design system tokens and automated screenshot testing."
                            ),
                            techStackTags = listOf("Kotlin", "Jetpack Compose", "Coroutines", "Room", "Hilt", "ML Kit")
                        ),
                        WorkExperienceItem(
                            id = "exp-2",
                            companyName = "CloudStream Systems",
                            roleTitle = "Senior Android Engineer",
                            startDate = "2019",
                            endDate = "2022",
                            bulletPoints = listOf(
                                "Architected real-time WebSocket syncing layer supporting 1.2M daily active users with offline conflict-free replicated data types.",
                                "Reduced cold-start app launch duration from 1.8s to 420ms through baseline profiles and dependency graph pruning."
                            ),
                            techStackTags = listOf("Kotlin", "RxJava", "OkHttp", "SQLite", "Biometrics", "CI/CD")
                        )
                    ),
                    projects = listOf(
                        ProjectItem(
                            id = "proj-1",
                            projectTitle = "NeuralDoc - Smart PDF Scanner",
                            description = "Open-source Android scanner app utilizing edge detection and ML Kit text recognition to extract clean markdown from handwritten and typed documents.",
                            techStackTags = listOf("Jetpack Compose", "ML Kit", "CameraX", "DataStore"),
                            demoUrl = "https://neuraldoc.dev/demo",
                            codeUrl = "https://github.com/alexmercer/neuraldoc"
                        )
                    ),
                    skills = SkillCategories(
                        sectionTitle = "TECHNICAL SKILLS",
                        languages = listOf("Kotlin", "Java", "Python", "Rust", "SQL"),
                        frameworks = listOf("Jetpack Compose", "Android SDK", "Coroutines & Flow", "Ktor", "Room"),
                        cloudDevOps = listOf("Firebase", "Google Cloud Platform", "GitHub Actions", "Docker"),
                        toolsMethodologies = listOf("Clean Architecture", "MVVM / MVI", "TDD & Robolectric", "Git"),
                        categorizedDomains = linkedMapOf(
                            "Languages & Core" to listOf("Kotlin", "Java", "Python", "Rust", "SQL"),
                            "Frameworks & SDKs" to listOf("Jetpack Compose", "Android SDK", "Coroutines & Flow", "Ktor", "Room"),
                            "Cloud & DevOps" to listOf("Firebase", "Google Cloud Platform", "GitHub Actions", "Docker"),
                            "Tools & Methodologies" to listOf("Clean Architecture", "MVVM / MVI", "TDD & Robolectric", "Git")
                        )
                    ),
                    education = listOf(
                        EducationItem(
                            institution = "University of California, Berkeley",
                            degree = "B.S. in Electrical Engineering and Computer Sciences (EECS)",
                            graduationYear = "2017"
                        )
                    )
                ),
                dynamicSections = listOf(
                    DynamicSection(
                        sectionTitle = "Certifications & Honors",
                        items = listOf(
                            DynamicItem(
                                title = "Google Associate Android Developer (AAD)",
                                subtitle = "Google Developers Certification",
                                dateOrPeriod = "2021",
                                description = "Mastery of Android core architecture and async processing."
                            )
                        )
                    )
                )
            )
        }
    }
}
