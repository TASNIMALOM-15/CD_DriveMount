package com.drivemount.app.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

private val KEY_MODE = stringPreferencesKey("mode")
private val KEY_SOURCE = stringPreferencesKey("source")
private val KEY_SEED = intPreferencesKey("seed")
private val KEY_ORIGINAL_ICONS = booleanPreferencesKey("original_icons")
private val KEY_THUMBNAILS = booleanPreferencesKey("thumbnails")
private val KEY_GRID = booleanPreferencesKey("grid_view")

/**
 * Theme-এর একমাত্র source of truth।
 * - update() দিলে UI সঙ্গে সঙ্গে বদলায় (StateFlow)
 * - ডিস্কে save হয় একটু দেরিতে (debounce), যাতে slider টানার সময় বারবার লেখা না হয়
 */
@OptIn(FlowPreview::class)
class ThemeRepository(context: Context) {

    private val dataStore = context.applicationContext.themeDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(loadBlocking())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    init {
        scope.launch {
            _settings.drop(1).debounce(250).collect { save(it) }
        }
    }

    fun update(transform: (ThemeSettings) -> ThemeSettings) {
        _settings.update(transform)
    }

    // অ্যাপ খোলার প্রথম frame-এই সঠিক theme দেখাতে একবার সিঙ্ক্রোনাসভাবে পড়া হয় (ফাইল খুবই ছোট)
    private fun loadBlocking(): ThemeSettings = try {
        runBlocking { dataStore.data.first() }.toSettings()
    } catch (e: Exception) {
        ThemeSettings()
    }

    private suspend fun save(s: ThemeSettings) {
        dataStore.edit {
            it[KEY_MODE] = s.mode.name
            it[KEY_SOURCE] = s.source.name
            it[KEY_SEED] = s.seedColor
            it[KEY_ORIGINAL_ICONS] = s.originalIcons
            it[KEY_THUMBNAILS] = s.thumbnails
            it[KEY_GRID] = s.gridView
        }
    }

    private fun Preferences.toSettings() = ThemeSettings(
        mode = this[KEY_MODE]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        source = this[KEY_SOURCE]
            ?.let { runCatching { ColorSource.valueOf(it) }.getOrNull() }
            ?: ColorSource.DEFAULT,
        seedColor = this[KEY_SEED] ?: DEFAULT_SEED,
        originalIcons = this[KEY_ORIGINAL_ICONS] ?: false,
        thumbnails = this[KEY_THUMBNAILS] ?: true,
        gridView = this[KEY_GRID] ?: false,
    )
}
