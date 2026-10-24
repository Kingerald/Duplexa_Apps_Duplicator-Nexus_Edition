package com.duplexa.duplicator.nexus

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Loki Vessel Vault Dashboard & Duplicated App Library for Duplexa - Nexus Edition.
 *
 * Features:
 * 1. Nordic Command Center Header with Theme 1 / Theme 2, Night / Day, and Card / Grid toggles.
 * 2. Live Vault Telemetry HUD (Active Vessels count, Storage Footprint, V1+V2 Signing Status).
 * 3. Instant Search Bar & Smart Filter Chips (All, Multi-Instance D_V2+, Installed on OS).
 * 4. Scrollable App Library RecyclerView supporting both detailed cards and tactical grids.
 * 5. Tactical Vessel Control Sheet (3 Core Options: Open, Install/Download, Delete + Quick Utilities).
 * 6. Thumb-Friendly Bottom Command Bar to trigger App Selection.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var cloneManager: DuplexaCloneManager
    private lateinit var libraryAdapter: DuplicatedLibraryAdapter
    private lateinit var rvLibrary: RecyclerView
    private lateinit var tvEmptyState: LinearLayout
    private lateinit var tvTelemetryVessels: TextView
    private lateinit var tvTelemetryStorage: TextView
    private lateinit var etSearchLibrary: EditText

    private var currentFilterMode = FilterMode.ALL
    private var isGridMode = false
    private val allDuplicatedApps = mutableListOf<DuplicatedApp>()

    enum class FilterMode {
        ALL,
        MULTI_INSTANCE,
        INSTALLED_OS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePreferenceManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        cloneManager = DuplexaCloneManager(this)
        buildDashboardInterface()
        refreshLibraryData()
    }

    override fun onResume() {
        super.onResume()
        refreshLibraryData()
    }

    /**
     * Constructs the complete Dashboard UI programmatically using native views
     * and dynamic semantic attributes.
     */
    @SuppressLint("SetTextI18n")
    private fun buildDashboardInterface() {
        val bgPrimary = ThemePreferenceManager.resolveThemeColor(this, R.attr.duplexaBackgroundPrimary)
        val textPrimary = ThemePreferenceManager.resolveThemeColor(this, R.attr.duplexaTextPrimary)
        val textSecondary = ThemePreferenceManager.resolveThemeColor(this, R.attr.duplexaTextSecondary)
        val accentPrimary = ThemePreferenceManager.resolveThemeColor(this, R.attr.duplexaAccentPrimary)
        val badgeTextColor = ThemePreferenceManager.resolveThemeColor(this, R.attr.duplexaBadgeTextColor)

        val pad16 = DuplexaUiComponents.dpToPx(this, 16f)
        val pad12 = DuplexaUiComponents.dpToPx(this, 12f)

        val rootLayout = RelativeLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(bgPrimary)
        }

        // =====================================================================
        // 1. NORDIC TOP HEADER BAR
        // =====================================================================
        val headerLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad16, pad16, pad16, pad12)
        }

        val logoSize = DuplexaUiComponents.dpToPx(this, 40f)
        val ivHeaderLogo = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(logoSize, logoSize).apply {
                marginEnd = pad12
            }
            setImageResource(R.drawable.ic_duplexa_logo_placeholder)
            contentDescription = getString(R.string.app_name)
        }

        val headerTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvHeaderTitle = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.05f
        }

        val tvHeaderSubtitle = TextView(this).apply {
            text = ThemePreferenceManager.getActiveThemeSummary(this@MainActivity)
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
        }

        headerTitleCol.addView(tvHeaderTitle)
        headerTitleCol.addView(tvHeaderSubtitle)

        // Header Action Controls (Theme Toggle, Mode Toggle, Grid Toggle)
        val headerControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnToggleTheme = TextView(this).apply {
            text = "T1/T2"
            setTextColor(badgeTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            background = DuplexaUiComponents.createPillBadgeDrawable(this@MainActivity)
            val pH = DuplexaUiComponents.dpToPx(this@MainActivity, 8f)
            val pV = DuplexaUiComponents.dpToPx(this@MainActivity, 4f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                ThemePreferenceManager.toggleTheme(this@MainActivity)
                recreate()
            }
        }

        val btnToggleMode = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_day_calendar)
            val size = DuplexaUiComponents.dpToPx(this@MainActivity, 32f)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = DuplexaUiComponents.dpToPx(this@MainActivity, 8f)
            }
            setOnClickListener {
                ThemePreferenceManager.toggleDisplayMode(this@MainActivity)
                recreate()
            }
        }

        val btnToggleGrid = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            val size = DuplexaUiComponents.dpToPx(this@MainActivity, 32f)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = DuplexaUiComponents.dpToPx(this@MainActivity, 8f)
            }
            setOnClickListener {
                isGridMode = !isGridMode
                updateLibraryLayoutManager()
                libraryAdapter.updateData(getFilteredList(etSearchLibrary.text.toString()), isGridMode)
            }
        }

        headerControls.addView(btnToggleTheme)
        headerControls.addView(btnToggleMode)
        headerControls.addView(btnToggleGrid)

        headerLayout.addView(ivHeaderLogo)
        headerLayout.addView(headerTitleCol)
        headerLayout.addView(headerControls)

        // =====================================================================
        // 2. BOTTOM COMMAND BAR (THUMB-FRIENDLY DUPLICATE BUTTON)
        // =====================================================================
        val bottomCommandBar = LinearLayout(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
            orientation = LinearLayout.VERTICAL
            background = DuplexaUiComponents.createCardSurfaceDrawable(this@MainActivity, 0f).apply {
                // Solid surface fill for bottom bar
                setColor(ThemePreferenceManager.resolveThemeColor(this@MainActivity, R.attr.duplexaSurfaceCard))
            }
            setPadding(pad16, pad12, pad16, pad16)
        }

        val btnForgeNew = TextView(this).apply {
            text = "+ " + getString(R.string.btn_create_first_duplicate)
            setTextColor(badgeTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = DuplexaUiComponents.createPrimaryButtonDrawable(this@MainActivity, 14f)
            val pH = DuplexaUiComponents.dpToPx(this@MainActivity, 20f)
            val pV = DuplexaUiComponents.dpToPx(this@MainActivity, 14f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener {
                openAppSelectionInterface()
            }
        }
        bottomCommandBar.addView(btnForgeNew)

        // =====================================================================
        // 3. SCROLLABLE CONTAINER (TELEMETRY HUD, SEARCH, CHIPS & RECYCLERVIEW)
        // =====================================================================
        val mainScrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            ).apply {
                addRule(RelativeLayout.ABOVE, bottomCommandBar.id)
                addRule(RelativeLayout.BELOW, headerLayout.id)
            }
            layoutParams = params
            setPadding(pad16, 0, pad16, 0)
        }

        // A. Live Vault Telemetry HUD Card
        val telemetryCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = DuplexaUiComponents.createCardSurfaceDrawable(this@MainActivity, 16f)
            val pH = DuplexaUiComponents.dpToPx(this@MainActivity, 16f)
            val pV = DuplexaUiComponents.dpToPx(this@MainActivity, 14f)
            setPadding(pH, pV, pH, pV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = DuplexaUiComponents.dpToPx(this@MainActivity, 12f)
            }
            layoutParams = lp
        }

        tvTelemetryVessels = createTelemetryMetricView("04", "VESSELS")
        val divider1 = createTelemetryDivider()
        tvTelemetryStorage = createTelemetryMetricView("0.0 MB", "FOOTPRINT")
        val divider2 = createTelemetryDivider()
        val tvTelemetryEngine = createTelemetryMetricView("V1+V2", "SIGNING")

        telemetryCard.addView(tvTelemetryVessels)
        telemetryCard.addView(divider1)
        telemetryCard.addView(tvTelemetryStorage)
        telemetryCard.addView(divider2)
        telemetryCard.addView(tvTelemetryEngine)

        mainScrollContent.addView(telemetryCard)

        // B. Instant Search Bar
        etSearchLibrary = EditText(this).apply {
            hint = getString(R.string.search_library_hint)
            setTextColor(textPrimary)
            setHintTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = DuplexaUiComponents.createOutlinedControlDrawable(this@MainActivity, 12f)
            val pH = DuplexaUiComponents.dpToPx(this@MainActivity, 16f)
            val pV = DuplexaUiComponents.dpToPx(this@MainActivity, 12f)
            setPadding(pH, pV, pH, pV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = DuplexaUiComponents.dpToPx(this@MainActivity, 10f)
            }
            layoutParams = lp
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    libraryAdapter.updateData(getFilteredList(s.toString()), isGridMode)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        mainScrollContent.addView(etSearchLibrary)

        // C. Smart Filter Chips Row
        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = DuplexaUiComponents.dpToPx(this@MainActivity, 12f)
            }
            layoutParams = lp
        }

        val chipAll = createFilterChip("All Vessels", true) {
            currentFilterMode = FilterMode.ALL
            libraryAdapter.updateData(getFilteredList(etSearchLibrary.text.toString()), isGridMode)
        }
        val chipMulti = createFilterChip("Multi-D_V", false) {
            currentFilterMode = FilterMode.MULTI_INSTANCE
            libraryAdapter.updateData(getFilteredList(etSearchLibrary.text.toString()), isGridMode)
        }
        val chipOs = createFilterChip("Installed OS", false) {
            currentFilterMode = FilterMode.INSTALLED_OS
            libraryAdapter.updateData(getFilteredList(etSearchLibrary.text.toString()), isGridMode)
        }

        chipsRow.addView(chipAll)
        chipsRow.addView(chipMulti)
        chipsRow.addView(chipOs)
        mainScrollContent.addView(chipsRow)

        // D. Library RecyclerView & Empty State Container
        val libraryContainer = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        rvLibrary = RecyclerView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            clipToPadding = false
            setPadding(0, 0, 0, DuplexaUiComponents.dpToPx(this@MainActivity, 16f))
        }

        libraryAdapter = DuplicatedLibraryAdapter(
            context = this,
            appList = emptyList(),
            isGridMode = isGridMode,
            onOpenClicked = { app -> cloneManager.openDuplicatedAppInDuplexa(this, app) },
            onMenuClicked = { app, anchor -> showDuplicateActionsMenu(app) },
            onCardClicked = { app -> cloneManager.openDuplicatedAppInDuplexa(this, app) }
        )
        updateLibraryLayoutManager()
        rvLibrary.adapter = libraryAdapter

        // Empty State View
        tvEmptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            layoutParams = params
            visibility = View.GONE
            setPadding(pad16, pad16, pad16, pad16)
        }

        val emptyIconSize = DuplexaUiComponents.dpToPx(this, 80f)
        val ivEmpty = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(emptyIconSize, emptyIconSize).apply {
                bottomMargin = DuplexaUiComponents.dpToPx(this@MainActivity, 16f)
            }
            setImageResource(R.drawable.ic_empty_vessels_placeholder)
        }

        val tvEmptyTitle = TextView(this).apply {
            text = getString(R.string.empty_library_title)
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val tvEmptySub = TextView(this).apply {
            text = getString(R.string.empty_library_subtitle)
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(this@MainActivity, 8f)
                bottomMargin = DuplexaUiComponents.dpToPx(this@MainActivity, 20f)
            }
            layoutParams = lp
        }

        val btnEmptyAction = TextView(this).apply {
            text = getString(R.string.btn_create_first_duplicate)
            setTextColor(badgeTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = DuplexaUiComponents.createPrimaryButtonDrawable(this@MainActivity, 12f)
            val pH = DuplexaUiComponents.dpToPx(this@MainActivity, 20f)
            val pV = DuplexaUiComponents.dpToPx(this@MainActivity, 12f)
            setPadding(pH, pV, pH, pV)
            setOnClickListener { openAppSelectionInterface() }
        }

        tvEmptyState.addView(ivEmpty)
        tvEmptyState.addView(tvEmptyTitle)
        tvEmptyState.addView(tvEmptySub)
        tvEmptyState.addView(btnEmptyAction)

        libraryContainer.addView(rvLibrary)
        libraryContainer.addView(tvEmptyState)
        mainScrollContent.addView(libraryContainer)

        rootLayout.addView(headerLayout)
        rootLayout.addView(bottomCommandBar)
        rootLayout.addView(mainScrollContent)

        setContentView(rootLayout)
    }

    private fun updateLibraryLayoutManager() {
        if (isGridMode) {
            rvLibrary.layoutManager = GridLayoutManager(this, 2)
        } else {
            rvLibrary.layoutManager = LinearLayoutManager(this)
        }
    }

    private fun createTelemetryMetricView(value: String, label: String): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvVal = TextView(this).apply {
            text = value
            setTextColor(ThemePreferenceManager.resolveThemeColor(this@MainActivity, R.attr.duplexaAccentPrimary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val tvLbl = TextView(this).apply {
            text = label
            setTextColor(ThemePreferenceManager.resolveThemeColor(this@MainActivity, R.attr.duplexaTextSecondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2
            }
            layoutParams = lp
        }
        col.addView(tvVal)
        col.addView(tvLbl)
        return col
    }

    private fun createTelemetryDivider(): View {
        val div = View(this).apply {
            val w = DuplexaUiComponents.dpToPx(this@MainActivity, 1f)
            val h = DuplexaUiComponents.dpToPx(this@MainActivity, 28f)
            layoutParams = LinearLayout.LayoutParams(w, h).apply {
                setMargins(DuplexaUiComponents.dpToPx(this@MainActivity, 8f), 0, DuplexaUiComponents.dpToPx(this@MainActivity, 8f), 0)
            }
            setBackgroundColor(ThemePreferenceManager.resolveThemeColor(this@MainActivity, R.attr.duplexaBorderColor))
        }
        return div
    }

    private fun createFilterChip(title: String, isSelected: Boolean, onClick: () -> Unit): TextView {
        val chip = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            val pH = DuplexaUiComponents.dpToPx(this@MainActivity, 14f)
            val pV = DuplexaUiComponents.dpToPx(this@MainActivity, 8f)
            setPadding(pH, pV, pH, pV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = DuplexaUiComponents.dpToPx(this@MainActivity, 8f)
            }
            layoutParams = lp
            background = if (isSelected) {
                DuplexaUiComponents.createPrimaryButtonDrawable(this@MainActivity, 8f)
            } else {
                DuplexaUiComponents.createOutlinedControlDrawable(this@MainActivity, 8f)
            }
            setTextColor(
                if (isSelected) ThemePreferenceManager.resolveThemeColor(this@MainActivity, R.attr.duplexaBadgeTextColor)
                else ThemePreferenceManager.resolveThemeColor(this@MainActivity, R.attr.duplexaTextPrimary)
            )
            setOnClickListener {
                onClick()
            }
        }
        return chip
    }

    /**
     * Refreshes the library dataset from DuplexaCloneManager and updates telemetry metrics.
     */
    @SuppressLint("SetTextI18n")
    fun refreshLibraryData() {
        allDuplicatedApps.clear()
        allDuplicatedApps.addAll(cloneManager.getDuplicatedAppsLibrary())

        // Update telemetry HUD metrics
        val totalVessels = allDuplicatedApps.size
        tvTelemetryVessels.text = String.format("%02d", totalVessels)

        val totalBytes = allDuplicatedApps.sumOf { it.apkSizeBytes }
        val totalMb = totalBytes / (1024.0 * 1024.0)
        tvTelemetryStorage.text = if (totalMb >= 1000.0) {
            String.format("%.1f GB", totalMb / 1024.0)
        } else {
            String.format("%.1f MB", totalMb)
        }

        val query = etSearchLibrary.text?.toString() ?: ""
        val filtered = getFilteredList(query)
        libraryAdapter.updateData(filtered, isGridMode)

        if (allDuplicatedApps.isEmpty()) {
            rvLibrary.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
        } else {
            rvLibrary.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }
    }

    private fun getFilteredList(query: String): List<DuplicatedApp> {
        val baseList = when (currentFilterMode) {
            FilterMode.ALL -> allDuplicatedApps
            FilterMode.MULTI_INSTANCE -> allDuplicatedApps.filter { app ->
                allDuplicatedApps.count { it.originalPackageName == app.originalPackageName } > 1
            }
            FilterMode.INSTALLED_OS -> allDuplicatedApps.filter { app ->
                try {
                    packageManager.getLaunchIntentForPackage(app.duplicatedPackageName) != null
                } catch (_: Exception) {
                    false
                }
            }
        }

        if (query.isBlank()) return baseList
        val lower = query.lowercase()
        return baseList.filter {
            it.displayName.lowercase().contains(lower) ||
                it.duplicatedPackageName.lowercase().contains(lower) ||
                it.dvBadgeLabel.lowercase().contains(lower)
        }
    }

    /**
     * Toggles the Tactical Vessel Control Sheet (3-Option Menu + Quick Utilities).
     */
    fun showDuplicateActionsMenu(duplicatedApp: DuplicatedApp) {
        val ctx = this
        val dialogView = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = DuplexaUiComponents.createCardSurfaceDrawable(ctx, 20f)
            val pad = DuplexaUiComponents.dpToPx(ctx, 20f)
            setPadding(pad, pad, pad, pad)
        }

        val textPrimary = ThemePreferenceManager.resolveThemeColor(ctx, R.attr.duplexaTextPrimary)
        val textSecondary = ThemePreferenceManager.resolveThemeColor(ctx, R.attr.duplexaTextSecondary)

        val tvTitle = TextView(ctx).apply {
            text = duplicatedApp.displayName
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = DuplexaUiComponents.dpToPx(ctx, 4f)
            }
            layoutParams = lp
        }

        val tvSubtitle = TextView(ctx).apply {
            text = "${duplicatedApp.duplicatedPackageName} • ${duplicatedApp.getFormattedApkSize()}"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = DuplexaUiComponents.dpToPx(ctx, 16f)
            }
            layoutParams = lp
        }

        dialogView.addView(tvTitle)
        dialogView.addView(tvSubtitle)

        // 1. Open Action
        val actionOpen = createMenuActionRow(
            ctx,
            android.R.drawable.ic_media_play,
            getString(R.string.action_open_duplicate),
            getString(R.string.action_open_duplicate_desc)
        ) {
            dialogInstance?.dismiss()
            cloneManager.openDuplicatedAppInDuplexa(ctx, duplicatedApp)
        }

        // 2. Install / Download Action
        val actionInstallDownload = createMenuActionRow(
            ctx,
            android.R.drawable.ic_menu_save,
            getString(R.string.action_install_or_download_apk),
            getString(R.string.action_install_or_download_apk_desc)
        ) {
            dialogInstance?.dismiss()
            showInstallOrDownloadSubDialog(duplicatedApp)
        }

        // 3. Pin Home Shortcut Action
        val actionPin = createMenuActionRow(
            ctx,
            android.R.drawable.ic_menu_send,
            "Pin D_V# to Home Screen",
            "Place badged shortcut icon directly on launcher"
        ) {
            dialogInstance?.dismiss()
            val pinned = cloneManager.pinShortcutToHomeScreen(duplicatedApp)
            if (pinned) {
                Toast.makeText(ctx, "Shortcut request sent!", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Delete Action
        val actionDelete = createMenuActionRow(
            ctx,
            android.R.drawable.ic_menu_delete,
            getString(R.string.action_delete_duplicate),
            getString(R.string.action_delete_duplicate_desc),
            isDanger = true
        ) {
            dialogInstance?.dismiss()
            confirmAndDeleteDuplicate(duplicatedApp)
        }

        dialogView.addView(actionOpen)
        dialogView.addView(actionInstallDownload)
        dialogView.addView(actionPin)
        dialogView.addView(actionDelete)

        dialogInstance = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        dialogInstance?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogInstance?.show()
    }

    private var dialogInstance: AlertDialog? = null

    private fun createMenuActionRow(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String,
        isDanger: Boolean = false
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val pV = DuplexaUiComponents.dpToPx(context, 12f)
            val pH = DuplexaUiComponents.dpToPx(context, 8f)
            setPadding(pH, pV, pH, pV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = DuplexaUiComponents.dpToPx(context, 6f)
            }
            layoutParams = lp
            background = DuplexaUiComponents.createOutlinedControlDrawable(context, 10f)
        }

        val iconSize = DuplexaUiComponents.dpToPx(context, 24f)
        val iv = ImageView(context).apply {
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = DuplexaUiComponents.dpToPx(context, 14f)
            }
            if (isDanger) {
                setColorFilter(Color.parseColor("#EF4444"))
            } else {
                setColorFilter(ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaAccentPrimary))
            }
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(context).apply {
            text = title
            setTextColor(
                if (isDanger) Color.parseColor("#EF4444")
                else ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaTextPrimary)
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val tvSub = TextView(context).apply {
            text = subtitle
            setTextColor(ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaTextSecondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }

        textCol.addView(tvTitle)
        textCol.addView(tvSub)

        row.addView(iv)
        row.addView(textCol)
        return row
    }

    /**
     * Sub-dialog for Install / Download choices.
     */
    private fun showInstallOrDownloadSubDialog(duplicatedApp: DuplicatedApp) {
        val ctx = this
        val dialogView = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = DuplexaUiComponents.createCardSurfaceDrawable(ctx, 20f)
            val pad = DuplexaUiComponents.dpToPx(ctx, 20f)
            setPadding(pad, pad, pad, pad)
        }

        val textPrimary = ThemePreferenceManager.resolveThemeColor(ctx, R.attr.duplexaTextPrimary)

        val tvTitle = TextView(ctx).apply {
            text = getString(R.string.dialog_install_download_title, duplicatedApp.displayName)
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = DuplexaUiComponents.dpToPx(ctx, 16f)
            }
            layoutParams = lp
        }

        val optionInstall = createMenuActionRow(
            ctx,
            android.R.drawable.ic_menu_view,
            getString(R.string.option_chooser_install),
            "Trigger system installer / app chooser via FileProvider"
        ) {
            subDialogInstance?.dismiss()
            cloneManager.launchSystemInstallChooser(ctx, duplicatedApp)
        }

        val optionExport = createMenuActionRow(
            ctx,
            android.R.drawable.ic_menu_save,
            getString(R.string.option_export_downloads),
            "Save standalone .apk directly to Downloads/Duplexa_APKs/"
        ) {
            subDialogInstance?.dismiss()
            lifecycleScope.launch {
                val savedName = withContext(Dispatchers.IO) {
                    cloneManager.exportStandaloneApkToDownloads(duplicatedApp)
                }
                if (!savedName.isNullOrBlank()) {
                    Toast.makeText(
                        ctx,
                        getString(R.string.toast_apk_exported_success, savedName),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(ctx, "Failed to export APK to Downloads.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialogView.addView(tvTitle)
        dialogView.addView(optionInstall)
        dialogView.addView(optionExport)

        subDialogInstance = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        subDialogInstance?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        subDialogInstance?.show()
    }

    private var subDialogInstance: AlertDialog? = null

    private fun confirmAndDeleteDuplicate(duplicatedApp: DuplicatedApp) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${duplicatedApp.displayName}?")
            .setMessage("This will permanently remove the standalone APK, badged icon, and isolated vessel storage directory from Duplexa.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        cloneManager.deleteDuplicatedApp(duplicatedApp.id)
                    }
                    if (success) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.toast_duplicate_deleted, duplicatedApp.displayName),
                            Toast.LENGTH_SHORT
                        ).show()
                        refreshLibraryData()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Routes to App Selection interface (Stage 4.3).
     */
    private fun openAppSelectionInterface() {
        Toast.makeText(this, "Opening App Selection & Duplication Forge...", Toast.LENGTH_SHORT).show()
        // Stage 4.3 will launch the InstalledAppPickerActivity or bottom sheet here.
    }
}
