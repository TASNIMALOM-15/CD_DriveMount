package com.drivemount.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.drivemount.app.DriveMountApp
import com.drivemount.app.ui.theme.ColorSource
import com.drivemount.app.ui.theme.DEFAULT_SEED
import com.drivemount.app.ui.theme.ThemeMode
import com.drivemount.app.ui.theme.ThemeSettings
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DriveMountApp).themeRepository

    val settings: StateFlow<ThemeSettings> = repo.settings

    fun setMode(mode: ThemeMode) {
        repo.update { it.copy(mode = mode) }
    }

    fun setMaterialColor(argb: Int) {
        repo.update { it.copy(source = ColorSource.MATERIAL, seedColor = argb) }
    }

    fun setCustomColor(argb: Int) {
        repo.update { it.copy(source = ColorSource.CUSTOM, seedColor = argb) }
    }

    /** Clear/Reset: রং আবার ডিফল্ট (Material You) */
    fun resetColor() {
        repo.update { it.copy(source = ColorSource.DEFAULT, seedColor = DEFAULT_SEED) }
    }
}
