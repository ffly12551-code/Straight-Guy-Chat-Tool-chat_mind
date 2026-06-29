package com.chatmind.chat_mind

import android.content.Context
import android.content.SharedPreferences

class StorageService {
    private val PREFS_NAME = "ChatMindPrefs"
    private val KEY_SCENE = "currentScene"

    fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var currentScene: String
        get() = try {
            getSharedPreferences(AppContext.context).getString(KEY_SCENE, "职场") ?: "职场"
        } catch (_: Exception) {
            "职场"
        }
        set(value) {
            try {
                getSharedPreferences(AppContext.context).edit().putString(KEY_SCENE, value).apply()
            } catch (_: Exception) {}
        }
}

object AppContext {
    lateinit var context: Context
}
