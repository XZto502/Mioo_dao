package com.mioo.dao.ui.screens.settings

import android.os.Build
import android.os.Bundle
import androidx.core.view.WindowCompat
import com.journeyapps.barcodescanner.CaptureActivity

class CustomCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val transparent = android.graphics.Color.TRANSPARENT
        window.statusBarColor = transparent
        window.navigationBarColor = transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = transparent
        }
    }
}
