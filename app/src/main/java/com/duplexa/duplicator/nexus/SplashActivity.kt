package com.duplexa.duplicator.nexus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bootup & Initialization Splash Interface for Duplexa Apps Duplicator - Nexus Edition.
 *
 * Responsibilities:
 * 1. Applies the user's active Dual-Theme (Theme 1 or Theme 2) and Display Mode (Night or Day).
 * 2. Renders the Loki Vessel Crest (`ic_duplexa_splash_crest_placeholder`), Duplexa title,
 *    `NEXUS EDITION` pill badge, and initialization progress card.
 * 3. Pre-initializes the standalone APK vault directories and warms up the D_V# library registry.
 * 4. Transitions cleanly into MainActivity.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var layoutCenterBrand: LinearLayout
    private lateinit var tvSplashStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePreferenceManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        buildSplashInterface()
        runEntryAnimation()
        executeBootInitializationSequence()
    }

    /**
     * Constructs the Nordic-minimalist Splash Interface bound dynamically to the active
     * Theme 1 / Theme 2 and Night / Day semantic attributes.
     */
    private fun buildSplashInterface() {
        val bgPrimary = ThemePreferenceManager.resolveThemeColor(
            this,
            R.attr.duplexaBackgroundPrimary
        )
        val textPrimary = ThemePreferenceManager.resolveThemeColor(
            this,
            R.attr.duplexaTextPrimary
        )
        val textSecondary = ThemePreferenceManager.resolveThemeColor(
            this,
            R.attr.duplexaTextSecondary
        )
        val accentPrimary = ThemePreferenceManager.resolveThemeColor(
            this,
            R.attr.duplexaAccentPrimary
        )
        val badgeTextColor = ThemePreferenceManager.resolveThemeColor(
            this,
            R.attr.duplexaBadgeTextColor
        )

        val pad24 = DuplexaUiComponents.dpToPx(this, 24f)

        val rootLayout = RelativeLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(bgPrimary)
            setPadding(pad24, pad24, pad24, pad24)
        }

        // 1. Center Crest & Brand Identity Block
        layoutCenterBrand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            layoutParams = params
        }

        val crestSize = DuplexaUiComponents.dpToPx(this, 136f)
        val ivSplashCrest = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(crestSize, crestSize)
            setImageResource(R.drawable.ic_duplexa_splash_crest_placeholder)
            contentDescription = getString(R.string.app_name)
        }

        val tvSplashAppName = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(this@SplashActivity, 20f)
            }
            text = getString(R.string.app_name)
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
        }

        val tvSplashEditionBadge = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(this@SplashActivity, 8f)
            }
            text = getString(R.string.app_edition_badge)
            setTextColor(badgeTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.12f
            background = DuplexaUiComponents.createPillBadgeDrawable(this@SplashActivity)
            val padH = DuplexaUiComponents.dpToPx(this@SplashActivity, 14f)
            val padV = DuplexaUiComponents.dpToPx(this@SplashActivity, 4f)
            setPadding(padH, padV, padH, padV)
        }

        val tvSplashTagline = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(this@SplashActivity, 12f)
            }
            text = getString(R.string.app_tagline)
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
        }

        layoutCenterBrand.addView(ivSplashCrest)
        layoutCenterBrand.addView(tvSplashAppName)
        layoutCenterBrand.addView(tvSplashEditionBadge)
        layoutCenterBrand.addView(tvSplashTagline)

        // 2. Bottom Initialization Status Card & Version Footer
        val layoutBottomStatus = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
            layoutParams = params
        }

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = DuplexaUiComponents.createCardSurfaceDrawable(this@SplashActivity, 16f)
            val padH = DuplexaUiComponents.dpToPx(this@SplashActivity, 20f)
            val padV = DuplexaUiComponents.dpToPx(this@SplashActivity, 16f)
            setPadding(padH, padV, padH, padV)
        }

        val progressSplashInit = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DuplexaUiComponents.dpToPx(this@SplashActivity, 6f)
            )
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(accentPrimary)
        }

        tvSplashStatus = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(this@SplashActivity, 10f)
            }
            text = getString(R.string.splash_initializing_engine)
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }

        statusCard.addView(progressSplashInit)
        statusCard.addView(tvSplashStatus)

        val tvSplashVersion = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(this@SplashActivity, 14f)
            }
            text = getString(R.string.app_version_tag)
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
        }

        layoutBottomStatus.addView(statusCard)
        layoutBottomStatus.addView(tvSplashVersion)

        rootLayout.addView(layoutCenterBrand)
        rootLayout.addView(layoutBottomStatus)

        setContentView(rootLayout)
    }

    /**
     * Smoothly fades and scales in the central crest and brand identity block.
     */
    private fun runEntryAnimation() {
        layoutCenterBrand.apply {
            alpha = 0f
            scaleX = 0.88f
            scaleY = 0.88f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /**
     * Executes background verification of Duplexa's standalone APK vault and library registry
     * while updating the live status text on the splash screen.
     */
    private fun executeBootInitializationSequence() {
        lifecycleScope.launch {
            tvSplashStatus.text = getString(R.string.splash_initializing_engine)
            delay(500L)

            tvSplashStatus.text = getString(R.string.splash_verifying_vault)
            withContext(Dispatchers.IO) {
                val cloneManager = DuplexaCloneManager(applicationContext)
                cloneManager.getDuplicatedAppsLibrary()
            }
            delay(450L)

            val dashboardIntent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(dashboardIntent)
            finish()
        }
    }
}
