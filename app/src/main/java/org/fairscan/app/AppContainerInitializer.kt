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

import android.content.Context
import androidx.startup.Initializer

// A host app embedding :app as a library owns the process's Application subclass, so this can't
// rely on a FairScanApp.onCreate() to build the container. androidx.startup runs regardless of
// which Application class the process ends up using - registered once, in AndroidManifest.xml,
// via the androidx.startup.InitializationProvider entry that this module's manifest declares.
object AppContainerHolder {
    lateinit var instance: AppContainer
        internal set
}

class AppContainerInitializer : Initializer<AppContainer> {
    override fun create(context: Context): AppContainer {
        val container = AppContainer(context.applicationContext)
        container.cleanOrphanSessions()
        AppContainerHolder.instance = container
        return container
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
