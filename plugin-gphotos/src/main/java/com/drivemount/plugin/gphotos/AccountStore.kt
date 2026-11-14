package com.drivemount.plugin.gphotos

import android.accounts.Account
import android.content.Context

/** বেছে নেওয়া Google অ্যাকাউন্টের নাম মনে রাখে (টোকেন নয়) */
object AccountStore {
    private fun prefs(c: Context) = c.getSharedPreferences("plugin", Context.MODE_PRIVATE)

    fun save(c: Context, account: Account) {
        prefs(c).edit().putString("name", account.name).putString("type", account.type).apply()
    }

    fun load(c: Context): Account? {
        val p = prefs(c)
        val n = p.getString("name", null) ?: return null
        return Account(n, p.getString("type", "com.google") ?: "com.google")
    }
}
