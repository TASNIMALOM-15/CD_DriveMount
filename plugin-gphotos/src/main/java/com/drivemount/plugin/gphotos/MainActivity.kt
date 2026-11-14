package com.drivemount.plugin.gphotos

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** ধাপ ২: Micro-G থেকে Google অ্যাকাউন্ট বেছে নেওয়া ও টোকেন পাওয়া যায় কি না পরীক্ষা */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var logView: TextView
    private val log = StringBuilder()

    // প্রথমটি Micro-G ঠিকঠাক কাজ করছে কি না দেখার জন্য; বাকিগুলো Photos-সংক্রান্ত স্কোপ
    private val scopes = listOf(
        "oauth2:https://www.googleapis.com/auth/userinfo.email",
        "oauth2:https://www.googleapis.com/auth/photos.native",
        "oauth2:https://www.googleapis.com/auth/photoslibrary",
        "oauth2:https://www.googleapis.com/auth/mobileapps.native",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "DriveMount Google Photos Plugin v${BuildConfig.VERSION_NAME}"
            textSize = 18f
        })
        status = TextView(this).apply { textSize = 14f; setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "1. Choose Google account"
            setOnClickListener { chooseAccount() }
        })
        root.addView(Button(this).apply {
            text = "Show account providers"
            setOnClickListener { listAuthenticators() }
        })
        root.addView(Button(this).apply {
            text = "2. Test token access"
            setOnClickListener { testTokens() }
        })
        root.addView(Button(this).apply {
            text = "Copy log"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("log", log.toString()))
                Toast.makeText(this@MainActivity, "Log copied", Toast.LENGTH_SHORT).show()
            }
        })
        logView = TextView(this).apply { textSize = 12f; setTextIsSelectable(true) }
        root.addView(ScrollView(this).apply { addView(logView) })
        setContentView(root)
        refreshStatus()
    }

    private fun refreshStatus() {
        val a = AccountStore.load(this)
        status.text = if (a == null) "No account selected" else "Account: ${a.name}"
    }

    private fun addLog(line: String) {
        runOnUiThread {
            log.append(line).append('\n')
            logView.text = log.toString()
        }
    }

    /** সিস্টেমে নিবন্ধিত সব অ্যাকাউন্ট-প্রদানকারী (type → package) লগে দেখায় */
    private fun listAuthenticators(): List<Pair<String, String>> {
        val all = AccountManager.get(this).authenticatorTypes.map { it.type to it.packageName }
        addLog("Account providers on this phone:")
        all.forEach { (type, pkg) -> addLog("  $type  ←  $pkg") }
        return all
    }

    private fun isMicroG(pkg: String) =
        pkg.contains("revanced", true) || pkg.contains("microg", true)

    private fun chooseAccount() {
        try {
            val all = listAuthenticators()
            val microG = all.filter { isMicroG(it.second) }.map { it.first }.distinct()
            val types = if (microG.isEmpty()) {
                addLog("No Micro-G account provider found. Showing accounts from all providers (including Play Services).")
                null
            } else {
                addLog("Using Micro-G account type(s): ${microG.joinToString()}")
                microG.toTypedArray()
            }
            val intent = AccountManager.newChooseAccountIntent(
                null, null, types, null, null, null, null,
            )
            startActivityForResult(intent, REQ_ACCOUNT)
        } catch (e: Exception) {
            addLog("Account chooser failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_ACCOUNT) return
        val name = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val type = data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        if (resultCode == RESULT_OK && name != null && type != null) {
            AccountStore.save(this, Account(name, type))
            addLog("Selected account: $name ($type)")
        } else {
            addLog("No account selected (result=$resultCode). If the list was empty, add a Google account in Micro-G first.")
        }
        refreshStatus()
    }

    private fun testTokens() {
        val account = AccountStore.load(this)
        if (account == null) {
            addLog("Choose an account first.")
            return
        }
        val am = AccountManager.get(this)
        Thread {
            for (scope in scopes) {
                addLog("→ $scope")
                try {
                    val result = am.getAuthToken(account, scope, null, this, null, null).result
                    val token = result.getString(AccountManager.KEY_AUTHTOKEN)
                    if (token.isNullOrEmpty()) {
                        addLog("   no token (keys: ${result.keySet().joinToString()})")
                    } else {
                        addLog("   OK, token length ${token.length}, starts ${token.take(4)}…")
                    }
                } catch (e: Exception) {
                    addLog("   FAILED: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            addLog("Done.")
        }.start()
    }

    companion object {
        private const val REQ_ACCOUNT = 100
    }
}
