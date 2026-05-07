package com.travelsouvenirs.main.data

import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(private val dao: CategoryDao) {

    // "Default" lives in the DB for FK integrity but is never exposed — callers prepend it manually.
    val categories: Flow<List<String>> = dao.getAllCategories()
        .map { list -> list.map { it.name }.filter { it != DEFAULT_CATEGORY } }

    suspend fun getAll(): List<String> = dao.getAllNames().filter { it != DEFAULT_CATEGORY }

    suspend fun add(name: String) = dao.insertCategory(CategoryEntity(name, nowEpochMillis()))

    suspend fun delete(name: String) = dao.deleteCategory(name)

    suspend fun setAll(names: List<String>) {
        val ts = nowEpochMillis()
        dao.deleteAllCustom(DEFAULT_CATEGORY)
        names.filter { it != DEFAULT_CATEGORY }.forEach { dao.insertCategory(CategoryEntity(it, ts)) }
    }
}
