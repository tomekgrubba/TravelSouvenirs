package com.travelsouvenirs.main.sync

import com.travelsouvenirs.main.data.CategoryRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.util.AppSettings
import com.travelsouvenirs.main.util.nowEpochMillis
import dev.gitlive.firebase.firestore.FirebaseFirestore

/** Bidirectional sync of custom categories with Firestore. Wins go to whichever side is newer. */
class CategorySyncService(
    private val firestore: FirebaseFirestore,
    private val categoryRepository: CategoryRepository,
    private val appSettings: AppSettings,
) {
    suspend fun sync(userId: String) {
        val remoteRef = firestore
            .collection("users").document(userId)
            .collection("settings").document("categories")
        val localUpdatedAt = appSettings.categoriesUpdatedAt

        try {
            val snapshot = remoteRef.get()
            if (snapshot.exists) {
                val remoteUpdatedAt = snapshot.get<Long>("updatedAtMillis")
                val remoteList = snapshot.get<List<String>>("list")
                val remoteListFiltered = remoteList.filter { it != DEFAULT_CATEGORY }
                val localCategories = categoryRepository.getAll()

                if (localCategories == remoteListFiltered) {
                    appSettings.categoriesUpdatedAt = remoteUpdatedAt
                    return
                }

                if (remoteUpdatedAt > localUpdatedAt) {
                    categoryRepository.setAll(remoteListFiltered)
                    appSettings.categoriesUpdatedAt = remoteUpdatedAt
                    return
                }
            }
        } catch (e: Exception) {
            // Propagate network/auth errors to avoid overwriting remote categories with local state
            throw e
        }

        val localCategories = categoryRepository.getAll()
        val now = nowEpochMillis()
        remoteRef.set(
            mapOf(
                "list" to listOf(DEFAULT_CATEGORY) + localCategories,
                "updatedAtMillis" to now,
            )
        )
        appSettings.categoriesUpdatedAt = now
    }
}
