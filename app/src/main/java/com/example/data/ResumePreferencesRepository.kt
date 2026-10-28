package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ResumeProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "resume_showcase_preferences")

class ResumePreferencesRepository(private val context: Context) {

    private val resumeJsonKey = stringPreferencesKey("saved_resume_profile_json")

    val profileFlow: Flow<ResumeProfile?> = context.dataStore.data.map { preferences ->
        val rawJson = preferences[resumeJsonKey]
        if (!rawJson.isNullOrBlank()) {
            try {
                ResumeProfile.fromJson(rawJson)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun saveProfile(profile: ResumeProfile) {
        val serialized = profile.toJson()
        context.dataStore.edit { preferences ->
            preferences[resumeJsonKey] = serialized
        }
    }

    suspend fun clearProfile() {
        context.dataStore.edit { preferences ->
            preferences.remove(resumeJsonKey)
        }
    }
}
