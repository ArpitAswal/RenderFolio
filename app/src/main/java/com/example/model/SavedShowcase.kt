package com.example.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Represents a saved tailored showcase in local storage.
 * Encapsulates the unique share identifier, target role headline, full profile,
 * and expiration timestamp.
 */
@Serializable
data class SavedShowcase(
    val shareId: String,
    val shareUrl: String,
    val candidateName: String,
    val headline: String,
    val profile: ResumeProfile,
    val targetCompany: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val expireAtMillis: Long = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000,
    val isSyncPending: Boolean = false
) {
    val isLocalOnly: Boolean
        get() = isSyncPending || shareId.startsWith("local")

    val displayHeadline: String
        get() = if (targetCompany.isNotBlank()) {
            "$headline ($targetCompany)"
        } else {
            headline
        }

    val daysRemaining: Int
        get() {
            val diff = expireAtMillis - System.currentTimeMillis()
            val days = (diff / (24 * 60 * 60 * 1000)).toInt()
            return days.coerceAtLeast(0)
        }

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expireAtMillis

    fun toJson(): String = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String): SavedShowcase = Json.decodeFromString(json)
    }
}

