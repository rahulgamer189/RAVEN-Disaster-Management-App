package com.raven.application.data

import android.content.Context

/** Local device preferences shared by the UI and Bluetooth runtime. */
object RavenPreferences {
    private const val FILE_NAME = "raven_preferences"
    private const val DISPLAY_NAME_KEY = "display_name"

    fun readDisplayName(context: Context): String = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getString(DISPLAY_NAME_KEY, "").orEmpty()

    fun writeDisplayName(context: Context, value: String) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit().putString(DISPLAY_NAME_KEY, value.trim()).apply()
    }
}
