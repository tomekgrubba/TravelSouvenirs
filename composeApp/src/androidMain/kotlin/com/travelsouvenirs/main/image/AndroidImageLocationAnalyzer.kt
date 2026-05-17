package com.travelsouvenirs.main.image

import android.graphics.BitmapFactory
import android.os.Build
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerationConfig
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.ModelConfig
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidImageLocationAnalyzer : ImageLocationAnalyzer {

    private val model by lazy {
        val mc = ModelConfig.builder().apply { preference = ModelPreference.FULL }.build()
        val config = GenerationConfig.builder().apply { modelConfig = mc }.build()
        Generation.getClient(config)
    }

    override suspend fun analyze(imagePath: String, lat: Double?, lng: Double?): String? = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext null
        try {
            when (model.checkStatus()) {
                FeatureStatus.UNAVAILABLE -> return@withContext null
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> return@withContext null
                FeatureStatus.AVAILABLE -> Unit
                else -> return@withContext null
            }
            val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@withContext null
            val builder = GenerateContentRequest.Builder(ImagePart(bitmap), TextPart(PROMPT))
            builder.maxOutputTokens = 64
            val result = StringBuilder()
            model.generateContent(builder.build()) { text -> result.append(text) }
            bitmap.recycle()
            val text = result.toString().trim()
            if (text.isBlank() || text.equals("unknown", ignoreCase = true)) null else text
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun isDownloadable(): Boolean = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext false
        try { model.checkStatus() == FeatureStatus.DOWNLOADABLE } catch (_: Exception) { false }
    }

    override suspend fun download(): Boolean = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext false
        try {
            var ok = false
            model.download().collect { ds ->
                if (ds.javaClass.simpleName == "DownloadCompleted") ok = true
            }
            ok
        } catch (_: Exception) { false }
    }

    companion object {
        private const val PROMPT =
            "Look at this photo. Identify the city, landmark, or country visible based on " +
            "architecture, signs, or recognizable features. Reply with just the place name " +
            "(e.g. 'Paris', 'Mount Fuji') or 'unknown' if you cannot tell."
    }
}
