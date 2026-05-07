package com.travelsouvenirs.main.di

import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.auth.FirebaseAuthRepository
import com.travelsouvenirs.main.auth.GoogleSignInHelper
import com.travelsouvenirs.main.data.CategoryRepository
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.data.buildItemDatabase
import com.travelsouvenirs.main.domain.usecase.DeleteItemUseCase
import com.travelsouvenirs.main.domain.usecase.FilterItemsUseCase
import com.travelsouvenirs.main.domain.usecase.SaveItemUseCase
import com.travelsouvenirs.main.sync.CategorySyncService
import com.travelsouvenirs.main.sync.ImageSyncHelper
import com.travelsouvenirs.main.sync.ImageSyncService
import com.travelsouvenirs.main.sync.MetadataSyncService
import com.travelsouvenirs.main.sync.SyncCoordinator
import com.travelsouvenirs.main.ui.add.AddItemViewModel
import com.travelsouvenirs.main.ui.auth.SignInViewModel
import com.travelsouvenirs.main.ui.detail.ItemDetailViewModel
import com.travelsouvenirs.main.ui.list.ListViewModel
import com.travelsouvenirs.main.ui.main.AppViewModel
import com.travelsouvenirs.main.ui.map.MapViewModel
import com.travelsouvenirs.main.ui.settings.SettingsViewModel
import com.travelsouvenirs.main.ui.shared.CategoryFilterViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dataModule = module {
    single { buildItemDatabase() }
    single { get<com.travelsouvenirs.main.data.ItemDatabase>().itemDao() }
    single { get<com.travelsouvenirs.main.data.ItemDatabase>().categoryDao() }
    single { ItemRepository(get()) }
    single { CategoryRepository(get()) }
}

val syncModule = module {
    single { Firebase.firestore }
    single { ImageSyncHelper(Firebase.storage) }
    single { MetadataSyncService(get(), get(), get()) }
    single { ImageSyncService(get(), get()) }
    single { CategorySyncService(get(), get(), get()) }
    single {
        SyncCoordinator(
            imageSyncHelper = get(),
            authRepository = get(),
            appSettings = get(),
            networkMonitor = get(),
            metadataSync = get(),
            imageSync = get(),
            categorySync = get(),
        )
    }
}

val authModule = module {
    single<AuthRepository> { FirebaseAuthRepository() }
}

val useCaseModule = module {
    factory { DeleteItemUseCase(get(), get()) }
    factory { SaveItemUseCase(get()) }
    single { FilterItemsUseCase }
}

val viewModelModule = module {
    viewModel { AppViewModel() }
    viewModel { ListViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { CategoryFilterViewModel(get()) }
    viewModel { MapViewModel(get()) }
    viewModel { SignInViewModel(get(), get()) }
    viewModel { (editId: Long?) ->
        AddItemViewModel(get(), get(), get(), editId, get())
    }
    viewModel { (itemId: Long) ->
        ItemDetailViewModel(get(), itemId, get())
    }
}
