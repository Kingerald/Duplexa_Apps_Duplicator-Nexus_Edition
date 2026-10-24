package com.duplexa.duplicator.nexus

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * High-performance Dual-Mode Adapter (Detailed Card View vs Tactical Grid View)
 * for Duplexa Apps Duplicator - Nexus Edition.
 */
class DuplicatedLibraryAdapter(
    private val context: Context,
    private var appList: List<DuplicatedApp>,
    private var isGridMode: Boolean,
    private val onOpenClicked: (DuplicatedApp) -> Unit,
    private val onMenuClicked: (DuplicatedApp, View) -> Unit,
    private val onCardClicked: (DuplicatedApp) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_CARD = 1
        private const val VIEW_TYPE_GRID = 2
    }

    fun updateData(newList: List<DuplicatedApp>, gridMode: Boolean) {
        appList = newList
        isGridMode = gridMode
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            GridViewHolder(createGridTileView(parent.context))
        } else {
            CardViewHolder(createCardTileView(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val app = appList[position]
        val cloneManager = DuplexaCloneManager(context)
        val badgedIcon = cloneManager.loadBadgedIconDrawable(app)

        if (holder is CardViewHolder) {
            holder.bind(app, badgedIcon, context, onOpenClicked, onMenuClicked, onCardClicked)
        } else if (holder is GridViewHolder) {
            holder.bind(app, badgedIcon, context, onMenuClicked, onCardClicked)
        }
    }

    override fun getItemCount(): Int = appList.size

    // =========================================================================
    // VIEW BUILDER: DETAILED CARD VIEW (1-COLUMN)
    // =========================================================================
    private fun createCardTileView(ctx: Context): View {
        val pad16 = DuplexaUiComponents.dpToPx(ctx, 16f)
        val pad12 = DuplexaUiComponents.dpToPx(ctx, 12f)
        val pad8 = DuplexaUiComponents.dpToPx(ctx, 8f)

        val root = LinearLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = DuplexaUiComponents.dpToPx(ctx, 12f)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = DuplexaUiComponents.createCardSurfaceDrawable(ctx, 16f)
            setPadding(pad16, pad16, pad12, pad16)
            isClickable = true
            isFocusable = true
        }

        val iconSize = DuplexaUiComponents.dpToPx(ctx, 56f)
        val ivIcon = ImageView(ctx).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = pad16
            }
        }

        val centerCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = pad8
            }
        }

        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(ctx).apply {
            id = View.generateViewId()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
        }

        val tvBadge = TextView(ctx).apply {
            id = View.generateViewId()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            background = DuplexaUiComponents.createPillBadgeDrawable(ctx)
            val pH = DuplexaUiComponents.dpToPx(ctx, 8f)
            val pV = DuplexaUiComponents.dpToPx(ctx, 2f)
            setPadding(pH, pV, pH, pV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = pad8
            }
            layoutParams = lp
        }

        titleRow.addView(tvTitle)
        titleRow.addView(tvBadge)

        val tvPackage = TextView(ctx).apply {
            id = View.generateViewId()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            maxLines = 1
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(ctx, 3f)
            }
            layoutParams = lp
        }

        val tvMetadata = TextView(ctx).apply {
            id = View.generateViewId()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            maxLines = 1
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(ctx, 4f)
            }
            layoutParams = lp
        }

        centerCol.addView(titleRow)
        centerCol.addView(tvPackage)
        centerCol.addView(tvMetadata)

        val rightActionCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnOpen = TextView(ctx).apply {
            id = View.generateViewId()
            text = ctx.getString(R.string.action_open_duplicate)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = DuplexaUiComponents.createPrimaryButtonDrawable(ctx, 8f)
            val pH = DuplexaUiComponents.dpToPx(ctx, 12f)
            val pV = DuplexaUiComponents.dpToPx(ctx, 6f)
            setPadding(pH, pV, pH, pV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = DuplexaUiComponents.dpToPx(ctx, 4f)
            }
            layoutParams = lp
        }

        val btnMenu = ImageView(ctx).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_more)
            val size = DuplexaUiComponents.dpToPx(ctx, 36f)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding(
                DuplexaUiComponents.dpToPx(ctx, 6f),
                DuplexaUiComponents.dpToPx(ctx, 6f),
                DuplexaUiComponents.dpToPx(ctx, 6f),
                DuplexaUiComponents.dpToPx(ctx, 6f)
            )
        }

        rightActionCol.addView(btnOpen)
        rightActionCol.addView(btnMenu)

        root.addView(ivIcon)
        root.addView(centerCol)
        root.addView(rightActionCol)

        root.setTag(R.id.tag_view_holder_data, CardViewHolder(root))
        return root
    }

    // =========================================================================
    // VIEW BUILDER: TACTICAL GRID TILE (2-COLUMN)
    // =========================================================================
    private fun createGridTileView(ctx: Context): View {
        val pad16 = DuplexaUiComponents.dpToPx(ctx, 16f)
        val pad12 = DuplexaUiComponents.dpToPx(ctx, 12f)

        val root = LinearLayout(ctx).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    DuplexaUiComponents.dpToPx(ctx, 4f),
                    DuplexaUiComponents.dpToPx(ctx, 4f),
                    DuplexaUiComponents.dpToPx(ctx, 4f),
                    DuplexaUiComponents.dpToPx(ctx, 8f)
                )
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = DuplexaUiComponents.createCardSurfaceDrawable(ctx, 16f)
            setPadding(pad12, pad16, pad12, pad16)
            isClickable = true
            isFocusable = true
        }

        val topHeader = RelativeLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvBadge = TextView(ctx).apply {
            id = View.generateViewId()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            background = DuplexaUiComponents.createPillBadgeDrawable(ctx)
            val pH = DuplexaUiComponents.dpToPx(ctx, 8f)
            val pV = DuplexaUiComponents.dpToPx(ctx, 2f)
            setPadding(pH, pV, pH, pV)
            val lp = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            layoutParams = lp
        }

        val btnMenu = ImageView(ctx).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_more)
            val size = DuplexaUiComponents.dpToPx(ctx, 32f)
            val lp = RelativeLayout.LayoutParams(size, size).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            layoutParams = lp
            setPadding(4, 4, 4, 4)
        }

        topHeader.addView(tvBadge)
        topHeader.addView(btnMenu)

        val iconSize = DuplexaUiComponents.dpToPx(ctx, 64f)
        val ivIcon = ImageView(ctx).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                topMargin = DuplexaUiComponents.dpToPx(ctx, 12f)
            }
        }

        val tvTitle = TextView(ctx).apply {
            id = View.generateViewId()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(ctx, 10f)
            }
            layoutParams = lp
        }

        val tvMetadata = TextView(ctx).apply {
            id = View.generateViewId()
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            maxLines = 1
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DuplexaUiComponents.dpToPx(ctx, 4f)
            }
            layoutParams = lp
        }

        root.addView(topHeader)
        root.addView(ivIcon)
        root.addView(tvTitle)
        root.addView(tvMetadata)

        return root
    }

    // =========================================================================
    // VIEW HOLDERS
    // =========================================================================
    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(
            app: DuplicatedApp,
            badgedIcon: Drawable?,
            context: Context,
            onOpenClicked: (DuplicatedApp) -> Unit,
            onMenuClicked: (DuplicatedApp, View) -> Unit,
            onCardClicked: (DuplicatedApp) -> Unit
        ) {
            val textPrimary = ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaTextPrimary)
            val textSecondary = ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaTextSecondary)
            val accentPrimary = ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaAccentPrimary)
            val badgeTextColor = ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaBadgeTextColor)

            val ivIcon = itemView.findViewById<ImageView>(itemView.childCount - 3) // Approximate index search or tag-based
            // Better yet, traverse children safely:
            val linear = itemView as LinearLayout
            val iv = linear.getChildAt(0) as ImageView
            val centerCol = linear.getChildAt(1) as LinearLayout
            val rightCol = linear.getChildAt(2) as LinearLayout

            val titleRow = centerCol.getChildAt(0) as LinearLayout
            val tvTitle = titleRow.getChildAt(0) as TextView
            val tvBadge = titleRow.getChildAt(1) as TextView
            val tvPackage = centerCol.getChildAt(1) as TextView
            val tvMetadata = centerCol.getChildAt(2) as TextView

            val btnOpen = rightCol.getChildAt(0) as TextView
            val btnMenu = rightCol.getChildAt(1) as ImageView

            if (badgedIcon != null) iv.setImageDrawable(badgedIcon)
            else iv.setImageResource(R.drawable.ic_duplexa_logo_placeholder)

            tvTitle.text = app.displayName
            tvTitle.setTextColor(textPrimary)

            tvBadge.text = app.dvBadgeLabel
            tvBadge.setTextColor(badgeTextColor)

            tvPackage.text = app.duplicatedPackageName
            tvPackage.setTextColor(textSecondary)

            tvMetadata.text = "v${app.versionName} • ${app.getFormattedApkSize()} • Signed"
            tvMetadata.setTextColor(accentPrimary)

            btnOpen.setTextColor(badgeTextColor)

            btnOpen.setOnClickListener { onOpenClicked(app) }
            btnMenu.setOnClickListener { onMenuClicked(app, it) }
            itemView.setOnClickListener { onCardClicked(app) }
        }
    }

    class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(
            app: DuplicatedApp,
            badgedIcon: Drawable?,
            context: Context,
            onMenuClicked: (DuplicatedApp, View) -> Unit,
            onCardClicked: (DuplicatedApp) -> Unit
        ) {
            val textPrimary = ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaTextPrimary)
            val textSecondary = ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaTextSecondary)
            val accentPrimary = ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaAccentPrimary)
            val badgeTextColor = ThemePreferenceManager.resolveThemeColor(context, R.attr.duplexaBadgeTextColor)

            val root = itemView as LinearLayout
            val topHeader = root.getChildAt(0) as RelativeLayout
            val tvBadge = topHeader.getChildAt(0) as TextView
            val btnMenu = topHeader.getChildAt(1) as ImageView
            val ivIcon = root.getChildAt(1) as ImageView
            val tvTitle = root.getChildAt(2) as TextView
            val tvMetadata = root.getChildAt(3) as TextView

            if (badgedIcon != null) ivIcon.setImageDrawable(badgedIcon)
            else ivIcon.setImageResource(R.drawable.ic_duplexa_logo_placeholder)

            tvBadge.text = app.dvBadgeLabel
            tvBadge.setTextColor(badgeTextColor)

            tvTitle.text = app.displayName
            tvTitle.setTextColor(textPrimary)

            tvMetadata.text = app.getFormattedApkSize()
            tvMetadata.setTextColor(accentPrimary)

            btnMenu.setOnClickListener { onMenuClicked(app, it) }
            itemView.setOnClickListener { onCardClicked(app) }
        }
    }
}
