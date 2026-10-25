package com.rancho.gestion.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    CLARO,
    OSCURO
}

class ThemeManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("rancho_theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isDarkMode = MutableStateFlow(_themeMode.value == AppThemeMode.OSCURO)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private fun loadThemeMode(): AppThemeMode {
        val modeStr = prefs.getString("key_app_theme", AppThemeMode.CLARO.name)
        return try {
            AppThemeMode.valueOf(modeStr ?: AppThemeMode.CLARO.name)
        } catch (e: Exception) {
            AppThemeMode.CLARO
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("key_app_theme", mode.name).apply()
        _themeMode.value = mode
        _isDarkMode.value = (mode == AppThemeMode.OSCURO)
    }

    fun toggleTheme() {
        val newMode = if (_themeMode.value == AppThemeMode.CLARO) AppThemeMode.OSCURO else AppThemeMode.CLARO
        setThemeMode(newMode)
    }
}
