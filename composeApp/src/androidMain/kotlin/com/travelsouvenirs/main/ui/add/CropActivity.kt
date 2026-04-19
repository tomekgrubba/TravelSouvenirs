package com.travelsouvenirs.main.ui.add

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.yalantis.ucrop.UCropActivity

class CropActivity : UCropActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
    }
}
