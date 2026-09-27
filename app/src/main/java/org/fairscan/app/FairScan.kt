/*
 * Copyright 2025-2026 The FairScan authors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

// Entry point for host apps embedding :app as a library. MainActivity has no intent-filter of
// its own here (see AndroidManifest.xml) - only :standalone declares the SCAN_TO_PDF and
// GET_CONTENT filters, so that starting FairScan from within a host app can't turn into a
// disambiguation dialog against a separately installed, standalone FairScan. Launching by
// explicit intent (naming the activity directly, as scanToPdfIntent does) never shows a chooser.
object FairScan {

    const val ACTION_SCAN_TO_PDF = "org.fairscan.app.action.SCAN_TO_PDF"

    // Set by MainActivity.sendActivityResult instead of `data` when the caller is this same app
    // (embedded-library case) - see that function for why. Internal: host apps should only ever
    // go through pdfUriFromResult, which hides which transport was actually used.
    internal const val EXTRA_PDF_PATH = "org.fairscan.app.extra.PDF_PATH"

    // Launch with registerForActivityResult(ActivityResultContracts.StartActivityForResult()).
    // On Activity.RESULT_OK, pass the result to pdfUriFromResult to get the produced PDF's Uri.
    @JvmStatic
    fun scanToPdfIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).setAction(ACTION_SCAN_TO_PDF)

    // Returns null if the user cancelled instead of producing a PDF.
    @JvmStatic
    fun pdfUriFromResult(resultCode: Int, data: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        val path = data?.getStringExtra(EXTRA_PDF_PATH)
        if (path != null) return Uri.fromFile(File(path))
        return data?.data
    }
}
