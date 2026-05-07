package com.travelsouvenirs.main.ui.add

import com.travelsouvenirs.main.image.ImageStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages photo copy lifecycle: tracks copied files and deletes orphans when
 * the user leaves without saving. Call [release] from ViewModel.onCleared().
 */
class ImagePickerHandler(
    private val imageStorage: ImageStorage,
    initialPhotoPath: String? = null,
) : AutoCloseable {

    private val cleanupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _photoPath = MutableStateFlow(initialPhotoPath)
    val photoPath: StateFlow<String?> = _photoPath.asStateFlow()

    // Tracks all copied files during this session so orphans are cleaned up.
    private val copiedPaths = mutableSetOf<String>()
    // The original path in edit mode — must never be deleted by this handler.
    var originalPhotoPath: String? = initialPhotoPath

    fun onPhotoSelected(sourcePath: String, onCopied: (String?) -> Unit) {
        cleanupScope.launch {
            val path = imageStorage.copyToInternalStorage(sourcePath)
            if (path != null) copiedPaths.add(path)
            _photoPath.value = path
            onCopied(path)
        }
    }

    fun cleanupOnSave(savedPath: String) {
        // Delete any copied photos that were replaced before saving.
        copiedPaths.filter { it != savedPath }.forEach { path ->
            cleanupScope.launch {
                runCatching { imageStorage.deleteImage(path) }
            }
        }
        copiedPaths.clear()
        // If editing and the user replaced the original photo, delete the original.
        val orig = originalPhotoPath
        if (orig != null && orig != savedPath) {
            cleanupScope.launch { runCatching { imageStorage.deleteImage(orig) } }
        }
    }

    override fun close() {
        cleanupScope.cancel()
    }

    fun cleanupOrphansAndClose() {
        if (copiedPaths.isNotEmpty()) {
            val paths = copiedPaths.toList()
            cleanupScope.launch {
                paths.forEach { runCatching { imageStorage.deleteImage(it) } }
                cleanupScope.cancel()
            }
        } else {
            cleanupScope.cancel()
        }
    }
}
