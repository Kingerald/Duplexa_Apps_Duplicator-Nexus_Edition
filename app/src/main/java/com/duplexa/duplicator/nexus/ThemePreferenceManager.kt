package com.duplexa.duplicator.nexus

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes

/**
 * Manages the Dual-Theme and Day/Night Display Mode engine for Duplexa - Nexus Edition.
 *
 * Strictly enforces two themes:
 * - Theme 1 (Main & Primary): Obsidian Void / Alabaster Mist with Emerald Illusion & Trickster Gold
 * - Theme 2 (Secondary): Midnight Slate / Warm Parchment with Trickster Gold & Emerald
 *
 * Both themes support Night Display Mode and Day Display Mode.
 */
object ThemePreferenceManager {

    private const val PREFS_NAME = "duplexa_nexus_theme_prefs"
    private const val KEY_SELECTED_THEME = "selected_theme_id"
    private const val KEY_DISPLAY_MODE = "selected_display_mode_id"

    enum class DuplexaTheme(val id: Int) {
        THEME_1_PRIMARY(1),
        THEME_2_SECONDARY(2);

        companion object {
            fun fromId(id: Int): DuplexaTheme {
                return entries.firstOrNull { it.id == id } ?: THEME_1_PRIMARY
            }
        }
    }

    enum class DisplayMode(val id: Int) {
        NIGHT(1),
        DAY(2);

        companion object {
            fun fromId(id: Int): DisplayMode {
                return entries.firstOrNull { it.id == id } ?: NIGHT
            }
        }
    }

    /**
     * Retrieves the currently active DuplexaTheme (defaults to THEME_1_PRIMARY).
     */
    fun getSelectedTheme(context: Context): DuplexaTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_SELECTED_THEME, DuplexaTheme.THEME_1_PRIMARY.id)
        return DuplexaTheme.fromId(id)
    }

    /**
     * Retrieves the currently active DisplayMode (defaults to NIGHT).
     */
    fun getDisplayMode(context: Context): DisplayMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_DISPLAY_MODE, DisplayMode.NIGHT.id)
        return DisplayMode.fromId(id)
    }

    /**
     * Persists a new theme and display mode combination.
     */
    fun setThemeAndMode(context: Context, theme: DuplexaTheme, mode: DisplayMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_THEME, theme.id)
            .putInt(KEY_DISPLAY_MODE, mode.id)
            .apply()
    }

    /**
     * Toggles between Theme 1 (Primary) and Theme 2 (Secondary) while keeping the current Day/Night mode.
     */
    fun toggleTheme(context: Context): DuplexaTheme {
        val currentTheme = getSelectedTheme(context)
        val currentMode = getDisplayMode(context)
        val nextTheme = if (currentTheme == DuplexaTheme.THEME_1_PRIMARY) {
            DuplexaTheme.THEME_2_SECONDARY
        } else {
            DuplexaTheme.THEME_1_PRIMARY
        }
        setThemeAndMode(context, nextTheme, currentMode)
        return nextTheme
    }

    /**
     * Toggles between Night Display Mode and Day Display Mode while keeping the current Theme.
     */
    fun toggleDisplayMode(context: Context): DisplayMode {
        val currentTheme = getSelectedTheme(context)
        val currentMode = getDisplayMode(context)
        val nextMode = if (currentMode == DisplayMode.NIGHT) {
            DisplayMode.DAY
        } else {
            DisplayMode.NIGHT
        }
        setThemeAndMode(context, currentTheme, nextMode)
        return nextMode
    }

    /**
     * Resolves the exact XML style resource ID matching the active Theme (1 or 2) and Display Mode (Night or Day).
     */
    @StyleRes
    fun getActiveThemeStyleRes(context: Context): Int {
        val theme = getSelectedTheme(context)
        val mode = getDisplayMode(context)

        return when (theme) {
            DuplexaTheme.THEME_1_PRIMARY -> {
                if (mode == DisplayMode.NIGHT) {
                    R.style.Theme_Duplexa_Theme1_Night
                } else {
                    R.style.Theme_Duplexa_Theme1_Day
                }
            }
            DuplexaTheme.THEME_2_SECONDARY -> {
                if (mode == DisplayMode.NIGHT) {
                    R.style.Theme_Duplexa_Theme2_Night
                } else {
                    R.style.Theme_Duplexa_Theme2_Day
                }
            }
        }
    }

    /**
     * Applies the user's selected Theme and Day/Night mode to the given Activity.
     * Must be called inside Activity.onCreate() prior to setContentView().
     */
    fun applyTheme(activity: Activity) {
        activity.setTheme(getActiveThemeStyleRes(activity))
    }

    /**
     * Dynamically resolves a semantic color attribute (e.g., R.attr.duplexaAccentPrimary)
     * from the currently applied theme.
     */
    @ColorInt
    fun resolveThemeColor(context: Context, @AttrRes attrResId: Int): Int {
        val typedValue = TypedValue()
        val resolved = context.theme.resolveAttribute(attrResId, typedValue, true)
        return if (resolved) {
            typedValue.data
        } else {
            0xFF10B981.toInt() // Fallback Emerald Illusion
        }
    }

    /**
     * Returns a human-readable summary of the active theme and display mode for UI headers/dialogs.
     */
    fun getActiveThemeSummary(context: Context): String {
        val themeLabel = when (getSelectedTheme(context)) {
            DuplexaTheme.THEME_1_PRIMARY -> context.getString(R.string.theme_option_primary)
            DuplexaTheme.THEME_2_SECONDARY -> context.getString(R.string.theme_option_secondary)
        }
        val modeLabel = when (getDisplayMode(context)) {
            DisplayMode.NIGHT -> context.getString(R.string.mode_option_night)
            DisplayMode.DAY -> context.getString(R.string.mode_option_day)
        }
        return "$themeLabel • $modeLabel"
    }
}
