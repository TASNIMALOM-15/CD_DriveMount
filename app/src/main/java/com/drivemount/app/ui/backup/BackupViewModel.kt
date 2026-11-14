package com.drivemount.app.ui.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R
import com.drivemount.app.backup.BackupCrypto
import com.drivemount.app.security.TotpGate
import com.drivemount.app.security.VaultKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ফলাফলের বার্তা; isError হলে লাল রঙে দেখানো হয় */
data class BackupMessage(val text: String, val isError: Boolean)

class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as DriveMountApp
    private val manager = appRef.backupManager
    private val gate = TotpGate(app)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message

    private val _gateEnabled = MutableStateFlow(gate.isEnabled())
    val gateEnabled: StateFlow<Boolean> = _gateEnabled

    /** বাছাই করা ফাইলের সংস্করণ (1 = পুরোনো, 2 = নতুন); ফাইল বাছা না থাকলে null */
    private val _importFormat = MutableStateFlow<Int?>(null)
    val importFormat: StateFlow<Int?> = _importFormat

    private var importBlob: ByteArray? = null

    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    fun showError(text: String) {
        _message.value = BackupMessage(text, true)
    }

    // ---------------- Authenticator গেট ----------------

    fun verifyGate(code: String): TotpGate.Result = if (gate.isEnabled()) gate.verify(code) else TotpGate.Result.OK

    fun newGateSecret(): String = gate.newSecret()

    fun gateUri(secret: String): String = gate.otpauthUri(secret)

    fun enableGate(secret: String, code: String): Boolean {
        val ok = gate.enable(secret, code)
        _gateEnabled.value = gate.isEnabled()
        return ok
    }

    fun disableGate() {
        gate.disable()
        _gateEnabled.value = false
    }

    // ---------------- Export ----------------

    /** নতুন Backup Key (দেখানোর জন্য, ABCD-EFGH-... আকারে) */
    fun newBackupKey(): String = VaultKeys.generateRecoveryCode()

    fun export(uri: Uri, password1: String, password2: String, backupKey: String) {
        if (_busy.value) return
        val key = VaultKeys.normalizeRecoveryCode(backupKey) ?: return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            _message.value = try {
                val (bytes, count) = manager.export(password1, password2, key)
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("Cannot write to the chosen location")
                    out.use { it.write(bytes) }
                }
                BackupMessage(str(R.string.backup_exported, count), false)
            } catch (t: Throwable) {
                BackupMessage(t.message ?: t.toString(), true)
            }
            _busy.value = false
        }
    }

    // ---------------- Import ----------------

    /** ফাইল পড়ে তার সংস্করণ জানা, যাতে ঠিক কোন কোন তথ্য চাইতে হবে বোঝা যায় */
    fun prepareImport(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                val blob = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Cannot read the chosen file")
                }
                val format = if (blob.size > MAX_BYTES) null else BackupCrypto.formatOf(blob)
                if (format == null) {
                    _message.value = BackupMessage(str(R.string.backup_not_backup), true)
                } else {
                    importBlob = blob
                    _importFormat.value = format
                }
            } catch (t: Throwable) {
                _message.value = BackupMessage(t.message ?: t.toString(), true)
            }
            _busy.value = false
        }
    }

    fun cancelImport() {
        importBlob = null
        _importFormat.value = null
    }

    fun importBackup(password1: String, password2: String, backupKey: String) {
        if (_busy.value) return
        val blob = importBlob ?: return
        val format = _importFormat.value
        val key = if (format == 2) {
            VaultKeys.normalizeRecoveryCode(backupKey) ?: run {
                _message.value = BackupMessage(str(R.string.backup_key_invalid), true)
                cancelImport()
                return
            }
        } else {
            ""
        }
        cancelImport()
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            _message.value = try {
                val r = manager.importBackup(blob, password1, password2, key)
                val summary = str(R.string.backup_imported, r.imported, r.skipped, r.failed.size)
                BackupMessage(
                    text = if (r.failed.isEmpty()) summary else summary + "\n" + r.failed.joinToString("\n"),
                    isError = r.failed.isNotEmpty() && r.imported == 0,
                )
            } catch (e: BackupCrypto.WrongPasswordException) {
                BackupMessage(str(R.string.backup_wrong_pw), true)
            } catch (e: BackupCrypto.NotBackupException) {
                BackupMessage(str(R.string.backup_not_backup), true)
            } catch (t: Throwable) {
                BackupMessage(t.message ?: t.toString(), true)
            }
            _busy.value = false
        }
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
