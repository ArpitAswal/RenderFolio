package com.example.data

import android.util.Log
import com.example.model.ResumeProfile
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cloud repository handling anonymous authentication, uploading compiled resumes
 * to Cloud Firestore with a 7-day TTL, and retrieving profiles via deep link IDs.
 */
class ResumeCloudRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    companion object {
        const val BASE_DEEP_LINK_URL = "https://lightweight-firebase-projects.web.app/r"
        private const val COLLECTION_RESUMES = "resumes"
        private const val TTL_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000 // 7-day TTL
    }

    /**
     * Authenticates anonymously if needed and stores the resume JSON under resumes/{uid}.
     * Returns the full shareable deep link URL.
     */
    suspend fun uploadResume(profile: ResumeProfile): Result<String> = runCatching {
        // 1. Ensure authenticated user session (Anonymous Auth)
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: throw IllegalStateException("Failed to establish authenticated session.")

        val uid = user.uid

        // 2. Generate unique share identifier tailored to this compilation
        // Uses short user ID prefix + unique token to guarantee user ownership namespace and avoid collisions
        val shortUid = if (uid.length >= 8) uid.substring(0, 8) else uid
        val uniqueToken = java.util.UUID.randomUUID().toString().replace("-", "").take(6)
        val shareId = "${shortUid}_$uniqueToken"

        // 3. Prepare Firestore document with metadata and 7-day TTL
        val expiresAtDate = Date(System.currentTimeMillis() + TTL_DAYS_MILLIS)
        val targetCompany = profile.hero.targetCompany.trim()
        val payload = hashMapOf<String, Any>(
            "shareId" to shareId,
            "ownerUid" to uid,
            "candidateName" to profile.hero.fullName,
            "headline" to profile.hero.headlineTitle,
            "targetCompany" to targetCompany,
            "profileJson" to profile.toJson(),
            "createdAt" to FieldValue.serverTimestamp(),
            "expireAt" to Timestamp(expiresAtDate)
        )

        // 4. Remove any older document for this user tailored to the SAME target company
        // to prevent obsolete duplicate documents for the same application in Firestore
        try {
            val existing = firestore.collection(COLLECTION_RESUMES)
                .whereEqualTo("ownerUid", uid)
                .get()
                .await()
            for (doc in existing.documents) {
                val docCompany = doc.getString("targetCompany")?.trim() ?: ""
                val docHeadline = doc.getString("headline")?.trim() ?: ""
                val isMatch = if (targetCompany.isNotBlank()) {
                    docCompany.equals(targetCompany, ignoreCase = true)
                } else {
                    docHeadline.equals(profile.hero.headlineTitle.trim(), ignoreCase = true)
                }
                if (isMatch) {
                    doc.reference.delete().await()
                }
            }
        } catch (e: Exception) {
            Log.w("ResumeCloudRepository", "Could not cleanup previous duplicate doc: ${e.message}")
        }

        // 5. Save document: independent tailored showcase under resumes/{shareId}
        firestore.collection(COLLECTION_RESUMES)
            .document(shareId)
            .set(payload)
            .await()

        "$BASE_DEEP_LINK_URL/$shareId"
    }.onFailure {
        Log.e("ResumeCloudRepository", "Upload failed: ${it.message}", it)
    }

    /**
     * Queries Cloud Firestore for all active showcases belonging to the authenticated user.
     * Automatically filters out expired showcases (> 7 days) and sorts oldest first (expiring soonest).
     */
    suspend fun fetchUserShowcases(): Result<List<com.example.model.SavedShowcase>> = runCatching {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: return@runCatching emptyList()

        val snapshot = firestore.collection(COLLECTION_RESUMES)
            .whereEqualTo("ownerUid", user.uid)
            .get()
            .await()

        val now = Date()
        val list = mutableListOf<com.example.model.SavedShowcase>()
        for (doc in snapshot.documents) {
            val expireAt = doc.getTimestamp("expireAt")
            if (expireAt != null && expireAt.toDate().before(now)) {
                continue
            }
            val shareId = doc.getString("shareId") ?: doc.id
            val candidateName = doc.getString("candidateName") ?: ""
            val headline = doc.getString("headline") ?: ""
            val profileJson = doc.getString("profileJson") ?: continue
            val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
            val expireAtMillis = expireAt?.toDate()?.time ?: (createdAt + TTL_DAYS_MILLIS)

            try {
                val parsedProfile = ResumeProfile.fromJson(profileJson)
                val targetCompany = doc.getString("targetCompany")?.trim() ?: parsedProfile.hero.targetCompany.trim()
                list.add(
                    com.example.model.SavedShowcase(
                        shareId = shareId,
                        shareUrl = "$BASE_DEEP_LINK_URL/$shareId",
                        candidateName = candidateName.ifBlank { parsedProfile.hero.fullName },
                        headline = headline.ifBlank { parsedProfile.hero.headlineTitle.ifBlank { "Professional Resume" } },
                        profile = parsedProfile,
                        targetCompany = targetCompany,
                        createdAtMillis = createdAt,
                        expireAtMillis = expireAtMillis
                    )
                )
            } catch (e: Exception) {
                Log.w("ResumeCloudRepository", "Skipping malformed profile in doc ${doc.id}", e)
            }
        }
        list.sortedBy { it.createdAtMillis }
    }.onFailure {
        Log.e("ResumeCloudRepository", "Fetch user showcases failed: ${it.message}", it)
    }

    /**
     * Deletes a specific resume document from Cloud Firestore.
     */
    suspend fun deleteResume(shareId: String): Result<Unit> = runCatching {
        if (shareId.isBlank()) return@runCatching
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        firestore.collection(COLLECTION_RESUMES)
            .document(shareId)
            .delete()
            .await()
        Log.d("ResumeCloudRepository", "Deleted showcase from Cloud Firestore: $shareId")
    }.onFailure {
        Log.e("ResumeCloudRepository", "Delete failed for showcase $shareId: ${it.message}", it)
    }

    /**
     * Retrieves the resume document from Cloud Firestore and parses the profile.
     */
    suspend fun fetchResume(shareId: String): Result<ResumeProfile> = runCatching {
        if (shareId.isBlank()) {
            throw IllegalArgumentException("Invalid showcase identifier.")
        }

        // Ensure anonymous session for Firestore rules requiring auth
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }

        val snapshot = firestore.collection(COLLECTION_RESUMES)
            .document(shareId)
            .get()
            .await()

        if (!snapshot.exists()) {
            throw NoSuchElementException("Showcase has expired or is unavailable.")
        }

        // Enforce 7-day expiration check in software
        val expireAt = snapshot.getTimestamp("expireAt")
        if (expireAt != null && expireAt.toDate().before(Date())) {
            throw NoSuchElementException("Showcase has expired or is unavailable.")
        }

        val profileJson = snapshot.getString("profileJson")
            ?: throw IllegalStateException("Showcase data is malformed.")

        ResumeProfile.fromJson(profileJson)
    }.onFailure {
        Log.e("ResumeCloudRepository", "Fetch failed: ${it.message}", it)
    }

    /**
     * Queries and deletes all resumes older than 7 days (expireAt < now) from Cloud Firestore.
     * Keeps the database clean without requiring a Google Cloud billing account.
     */
    suspend fun purgeExpiredResumes(): Result<Int> = runCatching {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }

        val now = Timestamp.now()
        val snapshot = firestore.collection(COLLECTION_RESUMES)
            .whereLessThan("expireAt", now)
            .get()
            .await()

        if (snapshot.isEmpty) {
            return@runCatching 0
        }

        val batch = firestore.batch()
        for (doc in snapshot.documents) {
            batch.delete(doc.reference)
        }
        batch.commit().await()

        Log.d("ResumeCloudRepository", "Cleaned up ${snapshot.size()} expired resumes from Cloud Firestore.")
        snapshot.size()
    }.onFailure {
        Log.w("ResumeCloudRepository", "Purge expired resumes skipped/failed: ${it.message}")
    }

    /**
     * Lightweight coroutine bridge for Play Services Tasks without extra dependencies.
     */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}
