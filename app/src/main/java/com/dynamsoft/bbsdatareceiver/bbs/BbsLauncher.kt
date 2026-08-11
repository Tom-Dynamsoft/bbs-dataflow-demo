package com.dynamsoft.bbsdatareceiver.bbs

import android.content.Intent
import android.net.Uri

/**
 * Builds the Intent to launch the BBS App via its public deep link.
 * Ported from the original Java BbsAppLauncher.
 */
object BbsLauncher {

    private const val SCHEME = "dynamsoftbbs.android"
    private const val HOST = "open.home"
    private const val QUERY_PARAM_SCENARIO = "scenario"
    private const val QUERY_PARAM_RETURN_RESULT = "returnResult"

    enum class Scenario(val value: String) {
        FOV_SCAN("FOV Scan"),
        SNAP_AND_SCAN("Snap & Scan"),
        PANORAMA_SCAN("Panorama")
    }

    fun buildIntent(scenario: Scenario): Intent {
        val uri = Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter(QUERY_PARAM_SCENARIO, scenario.value)
            .appendQueryParameter(QUERY_PARAM_RETURN_RESULT, "true")
            .build()

        return Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }
}
