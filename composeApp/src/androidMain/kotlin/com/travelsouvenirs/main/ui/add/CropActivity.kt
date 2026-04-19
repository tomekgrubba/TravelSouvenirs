package com.travelsouvenirs.main.ui.add

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.yalantis.ucrop.UCropActivity

/** Thin wrapper around UCropActivity that opts into edge-to-edge window insets. */
class CropActivity : UCropActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
    }
}
