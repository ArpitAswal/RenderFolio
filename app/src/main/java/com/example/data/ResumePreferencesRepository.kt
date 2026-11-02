package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ResumeProfile
import com.example.model.SavedShowcase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.core.content.edit

/**
 * Local DataStore / SharedPreferences repository caching the compiled ResumeProfiles
 * and active cloud showcases. Supports multiple tailored showcases with 7-day lifespans,
 * ordered by earliest expiration (oldest first).
 */
class ResumePreferencesRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("resume_prefs", Context.MODE_PRIVATE)

    private val _showcasesFlow = MutableStateFlow<List<SavedShowcase>>(emptyList())
    val showcasesFlow: Flow<List<SavedShowcase>> = _showcasesFlow.asStateFlow()

    private val _profileFlow = MutableStateFlow<ResumeProfile?>(null)
    val profileFlow: Flow<ResumeProfile?> = _profileFlow.asStateFlow()

    private val _activeShareIdFlow = MutableStateFlow<String?>(null)
    val activeShareIdFlow: Flow<String?> = _activeShareIdFlow.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_SAVED_SHOWCASES -> {
                _showcasesFlow.value = loadSavedShowcases()
                _profileFlow.value = loadSavedProfile()
            }
            KEY_SAVED_PROFILE -> _profileFlow.value = loadSavedProfile()
            KEY_ACTIVE_SHARE_ID -> _activeShareIdFlow.value = prefs.getString(KEY_ACTIVE_SHARE_ID, null)
        }
    }

    init {
        _showcasesFlow.value = loadSavedShowcases()
        _profileFlow.value = loadSavedProfile()
        _activeShareIdFlow.value = prefs.getString(KEY_ACTIVE_SHARE_ID, null)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun refresh() {
        _showcasesFlow.value = loadSavedShowcases()
        _profileFlow.value = loadSavedProfile()
        _activeShareIdFlow.value = prefs.getString(KEY_ACTIVE_SHARE_ID, null)
    }

    /**
     * Loads saved tailored showcases.
     * Automatically filters out expired showcases, dummy placeholders, and demo entries.
     * Deduplicates by shareId and by (candidateName, headline), keeping the newest version.
     * Orders by createdAtMillis ASCENDING (oldest first = expiring soonest).
     */
    fun loadSavedShowcases(): List<SavedShowcase> {
        val rawJson = prefs.getString(KEY_SAVED_SHOWCASES, null)
        val list = if (!rawJson.isNullOrBlank()) {
            try {
                Json.decodeFromString<List<SavedShowcase>>(rawJson)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Filter out expired items, demo profiles, and dummy placeholders ("showcase_1", "local")
        val validList = list.filter {
            !it.isExpired &&
            !it.candidateName.equals("Alex Mercer", ignoreCase = true) &&
            it.shareId != "showcase_1" &&
            it.shareId != "local"
        }

        // Deduplicate: If multiple items share the same candidate and target company,
        // retain only the newest entry to prevent duplicate cards for the same application.
        val deduplicated = validList
            .groupBy {
                val keyCompany = it.targetCompany.trim().lowercase()
                val keyHeadline = it.headline.trim().lowercase()
                val keyCandidate = it.candidateName.trim().lowercase()
                if (keyCompany.isNotBlank()) {
                    "$keyCandidate:::$keyCompany"
                } else {
                    "$keyCandidate:::$keyHeadline"
                }
            }
            .map { (_, group) ->
                group.maxByOrNull { it.createdAtMillis }!!
            }
            .distinctBy { it.shareId }
            .sortedBy { it.createdAtMillis }

        // If stale, dummy, or duplicate items were purged, update persistent storage
        if (deduplicated.size != list.size) {
            persistShowcases(deduplicated)
        }

        return deduplicated
    }

    private fun persistShowcases(showcases: List<SavedShowcase>) {
        val serialized = Json.encodeToString(showcases)
        prefs.edit { putString(KEY_SAVED_SHOWCASES, serialized) }
        _showcasesFlow.value = showcases
    }

    fun saveShowcase(showcase: SavedShowcase) {
        // Never persist demo showcase profile to user's active showcases
        if (showcase.candidateName.equals("Alex Mercer", ignoreCase = true)) {
            return
        }

        // Deduplicate against existing showcases:
        // 1. Remove dummy IDs ("showcase_1", "local")
        // 2. Remove identical shareId
        // 3. If a showcase already exists for the same candidate and target company,
        //    replace it with this newly compiled version rather than creating duplicate cards.
        val current = loadSavedShowcases().filter {
            !it.isExpired &&
            it.shareId != showcase.shareId &&
            it.shareId != "showcase_1" &&
            it.shareId != "local" &&
            !(it.candidateName.equals(showcase.candidateName, ignoreCase = true) &&
              ((showcase.targetCompany.isNotBlank() && it.targetCompany.trim().equals(showcase.targetCompany.trim(), ignoreCase = true)) ||
               (showcase.targetCompany.isBlank() && it.headline.trim().equals(showcase.headline.trim(), ignoreCase = true))))
        }.toMutableList()

        current.add(showcase)
        val sorted = current.sortedBy { it.createdAtMillis }
        persistShowcases(sorted)

        // Keep legacy single fields in sync with latest added showcase
        saveProfile(showcase.profile)
        saveActiveShareId(showcase.shareId)
    }

    /**
     * Reconciles and synchronizes local storage with verified cloud showcases from Cloud Firestore.
     * Purges orphaned local entries that do not exist in the cloud.
     */
    fun syncFromCloud(cloudShowcases: List<SavedShowcase>) {
        val validList = cloudShowcases.filter {
            !it.isExpired &&
            !it.candidateName.equals("Alex Mercer", ignoreCase = true) &&
            it.shareId != "showcase_1" &&
            it.shareId != "local"
        }

        val deduplicated = validList
            .groupBy {
                val keyCompany = it.targetCompany.trim().lowercase()
                val keyHeadline = it.headline.trim().lowercase()
                val keyCandidate = it.candidateName.trim().lowercase()
                if (keyCompany.isNotBlank()) {
                    "$keyCandidate:::$keyCompany"
                } else {
                    "$keyCandidate:::$keyHeadline"
                }
            }
            .map { (_, group) ->
                group.maxByOrNull { it.createdAtMillis }!!
            }
            .distinctBy { it.shareId }
            .sortedBy { it.createdAtMillis }

        persistShowcases(deduplicated)
        deduplicated.lastOrNull()?.let { latest ->
            saveProfile(latest.profile)
            saveActiveShareId(latest.shareId)
        }
    }

    fun deleteShowcase(shareId: String) {
        val updated = loadSavedShowcases().filter { it.shareId != shareId }
        persistShowcases(updated)
        if (updated.isEmpty()) {
            clearProfile()
        }
    }

    fun getShowcases(): List<SavedShowcase> = _showcasesFlow.value

    private fun loadSavedProfile(): ResumeProfile? {
        val json = prefs.getString(KEY_SAVED_PROFILE, null) ?: return null
        return try {
            val profile = ResumeProfile.fromJson(json)
            // Auto-clean any pre-loaded demo profile ("Alex Mercer") that was inadvertently cached in earlier builds
            if (profile.hero.fullName.equals("Alex Mercer", ignoreCase = true)) {
                prefs.edit { remove(KEY_SAVED_PROFILE).remove(KEY_ACTIVE_SHARE_ID) }
                null
            } else {
                profile
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveProfile(profile: ResumeProfile) {
        // Never persist demo showcase profile to user's active showcase preferences
        if (profile.hero.fullName.equals("Alex Mercer", ignoreCase = true)) {
            return
        }
        prefs.edit().putString(KEY_SAVED_PROFILE, profile.toJson()).apply()
        _profileFlow.value = profile
    }

    fun saveActiveShareId(shareId: String) {
        prefs.edit().putString(KEY_ACTIVE_SHARE_ID, shareId).apply()
        _activeShareIdFlow.value = shareId
    }

    fun getActiveShareId(): String? = _activeShareIdFlow.value

    fun clearProfile() {
        prefs.edit()
            .remove(KEY_SAVED_PROFILE)
            .remove(KEY_ACTIVE_SHARE_ID)
            .remove(KEY_SAVED_SHOWCASES)
            .apply()
        _profileFlow.value = null
        _activeShareIdFlow.value = null
        _showcasesFlow.value = emptyList()
    }

    fun getProfile(): ResumeProfile? = _profileFlow.value ?: _showcasesFlow.value.lastOrNull()?.profile

    companion object {
        private const val KEY_SAVED_PROFILE = "key_saved_profile"
        private const val KEY_ACTIVE_SHARE_ID = "key_active_share_id"
        private const val KEY_SAVED_SHOWCASES = "key_saved_showcases"

        @Volatile
        private var instance: ResumePreferencesRepository? = null

        fun getInstance(context: Context): ResumePreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: ResumePreferencesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
