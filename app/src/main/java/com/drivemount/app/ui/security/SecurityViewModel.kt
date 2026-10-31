package com.drivemount.app.ui.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.security.SecuritySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SecurityViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as DriveMountApp

    val settings: StateFlow<SecuritySettings> = appRef.securityRepository.settings

    private val _configProtected = MutableStateFlow(appRef.configProtector.isProtected())
    val configProtected: StateFlow<Boolean> = _configProtected

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun showMessage(text: String?) {
        _message.value = text
    }

    fun setAppLock(enabled: Boolean) {
        appRef.securityRepository.setAppLock(enabled)
        if (!enabled) appRef.appLock.unlocked()
        _message.value = null
    }

    fun enableConfigProtection() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            appRef.configProtector.ensureEnabled().fold(
                onSuccess = {
                    _configProtected.value = true
                    _message.value = null
                },
                onFailure = { _message.value = it.message ?: it.toString() },
            )
            _busy.value = false
        }
    }
}
