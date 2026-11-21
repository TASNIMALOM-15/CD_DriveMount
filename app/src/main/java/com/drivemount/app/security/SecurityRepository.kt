package com.drivemount.app.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.securityDataStore by preferencesDataStore(name = "security_settings")

private val KEY_APP_LOCK = booleanPreferencesKey("app_lock")

data class SecuritySettings(val appLock: Boolean = false)

/** নিরাপত্তা সেটিং (এখন শুধু অ্যাপ লক); অ্যাপ খোলার প্রথম মুহূর্তেই জানা দরকার, তাই একবার সিঙ্ক্রোনাসভাবে পড়া হয় */
class SecurityRepository(context: Context) {

    private val dataStore = context.applicationContext.securityDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SecuritySettings> = _settings.asStateFlow()

    private fun load(): SecuritySettings = try {
        val prefs = runBlocking { dataStore.data.first() }
        SecuritySettings(appLock = prefs[KEY_APP_LOCK] ?: false)
    } catch (e: Exception) {
        SecuritySettings()
    }

    fun setAppLock(enabled: Boolean) {
        _settings.value = _settings.value.copy(appLock = enabled)
        scope.launch { dataStore.edit { it[KEY_APP_LOCK] = enabled } }
    }
}
