package com.pocketterminal.core

import android.content.Context

class PersistentSettings(context: Context) {
    private val preferences = context.getSharedPreferences(
        "terminal_preferences",
        Context.MODE_PRIVATE
    )

    var darkTheme: Boolean
        get() = preferences.getBoolean("dark_theme", true)
        set(value) = preferences.edit().putBoolean("dark_theme", value).apply()

    var fontSize: Float
        get() = preferences.getFloat("font_size", 13f)
        set(value) = preferences.edit().putFloat("font_size", value).apply()

    var grantedTree: String?
        get() = preferences.getString("tree_uri", null)
        set(value) = preferences.edit().putString("tree_uri", value).apply()
}