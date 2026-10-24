package com.duplexa.duplicator.nexus

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Direct Home-Screen Shortcut & External Intent Router for Duplexa Apps Duplicator - Nexus Edition.
 *
 * Routes incoming shortcut or external launch requests directly into the target D_V#
 * standalone native instance with zero WebKit frames and zero transition flicker.
 */
class CloneLauncherActivity : Activity() {

    companion object {
        const val ACTION_LAUNCH_DUPLICATE = "com.duplexa.duplicator.nexus.ACTION_LAUNCH_DUPLICATE"
        const val EXTRA_FALLBACK_DUPLICATE_ID = "extra_duplicate_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val duplicateId = extractTargetDuplicateId(intent)
        val cloneManager = DuplexaCloneManager(this)

        if (duplicateId.isNullOrBlank()) {
            routeToMainDashboard()
            terminateZeroFlicker()
            return
        }

        val duplicatedApp = cloneManager.getDuplicatedAppById(duplicateId)
        if (duplicatedApp == null) {
            Toast.makeText(
                this,
                getString(R.string.toast_duplicate_deleted, "D_V Instance"),
                Toast.LENGTH_SHORT
            ).show()
            routeToMainDashboard()
            terminateZeroFlicker()
            return
        }

        routeToStandaloneDuplicate(cloneManager, duplicatedApp)
        terminateZeroFlicker()
    }

    /**
     * Extracts the target DuplicatedApp UUID from the launching Intent extras or data URI.
     */
    private fun extractTargetDuplicateId(launchIntent: Intent?): String? {
        if (launchIntent == null) return null

        val primaryExtra = launchIntent.getStringExtra(CloneSandboxActivity.EXTRA_DUPLICATE_ID)
        if (!primaryExtra.isNullOrBlank()) {
            return primaryExtra
        }

        val fallbackExtra = launchIntent.getStringExtra(EXTRA_FALLBACK_DUPLICATE_ID)
        if (!fallbackExtra.isNullOrBlank()) {
            return fallbackExtra
        }

        return launchIntent.data?.lastPathSegment
    }

    /**
     * Routes execution to the duplicated D_V# app in 100% full-screen standalone native mode.
     *
     * Priority 1: If the user has also installed the generated standalone D_V# APK onto the
     *             device OS, launch that standalone package directly in full screen.
     * Priority 2: Otherwise, open the duplicated app directly inside Duplexa's full-screen
     *             native standalone container (CloneSandboxActivity).
     */
    private fun routeToStandaloneDuplicate(
        cloneManager: DuplexaCloneManager,
        duplicatedApp: DuplicatedApp
    ) {
        val installedStandaloneIntent = packageManager.getLaunchIntentForPackage(
            duplicatedApp.duplicatedPackageName
        )

        if (installedStandaloneIntent != null) {
            installedStandaloneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            installedStandaloneIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            installedStandaloneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(installedStandaloneIntent)
                return
            } catch (_: Exception) {
                // Fall back to Duplexa's internal full-screen standalone sandbox container
            }
        }

        cloneManager.openDuplicatedAppInDuplexa(this, duplicatedApp)
    }

    /**
     * Opens the main Duplexa Dashboard if no valid duplicate ID was supplied or if the
     * target duplicate was previously deleted from the Duplexa Library.
     */
    private fun routeToMainDashboard() {
        val dashboardIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(dashboardIntent)
    }

    /**
     * Finishes the router activity immediately without window animation flicker.
     */
    private fun terminateZeroFlicker() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
