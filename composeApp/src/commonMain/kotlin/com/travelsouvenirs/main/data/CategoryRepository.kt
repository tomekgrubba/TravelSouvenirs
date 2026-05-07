package com.travelsouvenirs.main.data

import com.travelsouvenirs.main.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(private val dao: CategoryDao) {

    val categories: Flow<List<String>> = dao.getAllCategories().map { list -> list.map { it.name } }

    suspend fun getAll(): List<String> = dao.getAllNames()

    suspend fun add(name: String) = dao.insertCategory(CategoryEntity(name, nowEpochMillis()))

    suspend fun delete(name: String) = dao.deleteCategory(name)

    suspend fun setAll(names: List<String>) {
        val ts = nowEpochMillis()
        dao.deleteAll()
        names.forEach { dao.insertCategory(CategoryEntity(it, ts)) }
    }

    suspend fun isEmpty(): Boolean = dao.count() == 0
}
