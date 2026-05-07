package com.travelsouvenirs.main.di

import androidx.compose.runtime.compositionLocalOf
import com.travelsouvenirs.main.ui.shared.CategoryFilterViewModel
import com.travelsouvenirs.main.ui.main.AppViewModel

val LocalAppViewModel = compositionLocalOf<AppViewModel> {
    error("LocalAppViewModel not provided")
}
val LocalCategoryFilter = compositionLocalOf<CategoryFilterViewModel> {
    error("LocalCategoryFilter not provided")
}
