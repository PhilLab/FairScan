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
package org.fairscan.app.ui

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Fixed, FairScan-owned authority - not applicationId-derived - so it can't collide with a host
 * app's own FileProvider. Must be kept in sync with the <provider> declaration in
 * standalone/src/main/AndroidManifest.xml
 */
const val FILE_PROVIDER_AUTHORITY = "org.fairscan.app.fileprovider"

fun uriForFile(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
}
