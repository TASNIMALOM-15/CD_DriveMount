package com.drivemount.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.engine.RcloneEngine
import com.drivemount.app.engine.RcloneVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface EngineUiState {
    data object Loading : EngineUiState
    data class Ready(val version: RcloneVersion, val remoteCount: Int) : EngineUiState
    data class Error(val message: String) : EngineUiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<EngineUiState>(EngineUiState.Loading)
    val state: StateFlow<EngineUiState> = _state

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = try {
                val config = File(getApplication<Application>().filesDir, "rclone.conf")
                RcloneEngine.initialize(config.absolutePath)
                EngineUiState.Ready(
                    version = RcloneEngine.version(),
                    remoteCount = RcloneEngine.listRemotes().size,
                )
            } catch (t: Throwable) {
                EngineUiState.Error(t.message ?: t.toString())
            }
        }
    }
}
