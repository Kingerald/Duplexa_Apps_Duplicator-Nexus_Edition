package com.duplexa.duplicator.nexus

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.annotation.AttrRes

/**
 * Reusable Theme-Aware Native UI Drawable & Dimension Factory for Duplexa - Nexus Edition.
 *
 * Dynamically resolves semantic attributes from the active theme (Theme 1 / Theme 2
 * across Night / Day modes) to construct crisp Nordic-minimalist surfaces, pill badges,
 * and interactive buttons with zero hardcoded colors.
 */
object DuplexaUiComponents {

    /**
     * Converts dp to pixels for crisp layout sizing across all screen densities.
     */
    fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * Creates a rounded pill badge drawable (`999dp` corner radius) filled with the active
     * theme's `duplexaAccentSecondary` (Trickster Gold / Burnished Amber / Emerald) or custom attribute.
     */
    fun createPillBadgeDrawable(
        context: Context,
        @AttrRes fillAttr: Int = R.attr.duplexaAccentSecondary
    ): GradientDrawable {
        val fillColor = ThemePreferenceManager.resolveThemeColor(context, fillAttr)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 999f).toFloat()
            setColor(fillColor)
        }
    }

    /**
     * Creates a `16dp` rounded card surface with a `1dp` subtle hairline border using
     * `?attr/duplexaSurfaceCard` and `?attr/duplexaBorderColor`.
     */
    fun createCardSurfaceDrawable(
        context: Context,
        cornerRadiusDp: Float = 16f
    ): GradientDrawable {
        val surfaceColor = ThemePreferenceManager.resolveThemeColor(
            context,
            R.attr.duplexaSurfaceCard
        )
        val borderColor = ThemePreferenceManager.resolveThemeColor(
            context,
            R.attr.duplexaBorderColor
        )
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
            setColor(surfaceColor)
            setStroke(dpToPx(context, 1f).coerceAtLeast(1), borderColor)
        }
    }

    /**
     * Creates a rounded primary action button background using `?attr/duplexaAccentPrimary`.
     */
    fun createPrimaryButtonDrawable(
        context: Context,
        cornerRadiusDp: Float = 12f
    ): GradientDrawable {
        val accentPrimary = ThemePreferenceManager.resolveThemeColor(
            context,
            R.attr.duplexaAccentPrimary
        )
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
            setColor(accentPrimary)
        }
    }

    /**
     * Creates a subtle outlined button / chip surface for secondary controls and search bars.
     */
    fun createOutlinedControlDrawable(
        context: Context,
        cornerRadiusDp: Float = 12f
    ): GradientDrawable {
        val surfaceColor = ThemePreferenceManager.resolveThemeColor(
            context,
            R.attr.duplexaSurfaceCard
        )
        val borderColor = ThemePreferenceManager.resolveThemeColor(
            context,
            R.attr.duplexaBorderColor
        )
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
            setColor(surfaceColor)
            setStroke(dpToPx(context, 1.2f).coerceAtLeast(1), borderColor)
        }
    }
}
