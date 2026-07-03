package com.travelsouvenirs.main.data
 
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.sync.SyncStatus
import com.travelsouvenirs.main.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
 
class CategoryRepository(
    private val dao: CategoryDao,
    private val itemDao: ItemDao,
) {
 
    // "Default" lives in the DB for FK integrity but is never exposed — callers prepend it manually.
    val categories: Flow<List<String>> = dao.getAllCategories()
        .map { list -> list.map { it.name }.filter { it != DEFAULT_CATEGORY } }
 
    suspend fun getAll(): List<String> = dao.getAllNames().filter { it != DEFAULT_CATEGORY }
 
    suspend fun add(name: String) = dao.insertCategory(CategoryEntity(name, nowEpochMillis()))

    suspend fun rename(oldName: String, newName: String): Boolean {
        val trimmedNew = newName.trim()
        if (trimmedNew.isBlank() || trimmedNew.contains(',') || trimmedNew == DEFAULT_CATEGORY) {
            return false
        }
        val ts = nowEpochMillis()
        val allNames = dao.getAllNames()
        if (trimmedNew in allNames) {
            return false
        }

        dao.insertCategory(CategoryEntity(trimmedNew, ts))
        itemDao.reassignCategory(oldName, trimmedNew, ts)
        dao.deleteCategory(oldName)
        return true
    }

    suspend fun delete(name: String) {
        val ts = nowEpochMillis()
        itemDao.deleteCategoryAndReassignItems(name, DEFAULT_CATEGORY, ts)
    }
 
    suspend fun setAll(names: List<String>) {
        val ts = nowEpochMillis()
        val currentNames = dao.getAllNames().filter { it != DEFAULT_CATEGORY }
        val namesToKeep = names.filter { it != DEFAULT_CATEGORY }
        val namesToDelete = currentNames.filter { it !in namesToKeep }

        // 1. Bulk delete old categories in a single transaction (N-M1 & N-H1)
        if (namesToDelete.isNotEmpty()) {
            itemDao.deleteCategoriesAndReassignItems(namesToDelete, DEFAULT_CATEGORY, ts)
        }

        // 2. Insert new or update timestamps of kept categories (N-M2) without delete cascades
        namesToKeep.forEach { name ->
            dao.insertOrUpdateCategory(CategoryEntity(name, ts))
        }
    }
}
