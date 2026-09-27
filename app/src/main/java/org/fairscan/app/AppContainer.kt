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

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.fairscan.app.data.FileLogger
import org.fairscan.app.data.FileManager
import org.fairscan.app.data.LogRepository
import org.fairscan.app.data.OcrLanguageRepository
import org.fairscan.app.domain.ImageSegmentationService
import org.fairscan.app.domain.OcrService
import org.fairscan.app.platform.AndroidImageLoader
import org.fairscan.app.platform.AndroidPdfWriter
import org.fairscan.app.ui.screens.camera.CameraViewModel
import org.fairscan.app.ui.screens.settings.SettingsRepository
import org.fairscan.app.ui.screens.settings.SettingsViewModel
import java.io.File

const val THUMBNAIL_SIZE_DP = 120

private val Context.dataStore by preferencesDataStore(name = "fairscan_settings")

/**
 * The manifest <meta-data> key that specifies the subdirectory name under filesDir/cacheDir
 * which FairScan can use for file storage
 */
const val STORAGE_SUBDIR_KEY = "org.fairscan.app.STORAGE_SUBDIR"

/**
 * Resolve the FairScan storage subdirectory name from the manifest <meta-data> value, or throw if
 * absent. An empty string is valid, meaning FairScan may use filesDir/cacheDir directly.
 */
internal fun resolveStorageSubdir(metaDataValue: String?): String =
    metaDataValue ?: throw IllegalStateException(
        "FairScan requires a <meta-data android:name=\"$STORAGE_SUBDIR_KEY\" " +
            "android:value=\"...\" /> entry in your app's <application> manifest tag - the " +
            "subdirectory FairScan may use under your filesDir/cacheDir, or \"\" to use them " +
            "directly. See README.md."
    )

internal fun storageRoot(dir: File, subdir: String): File =
    if (subdir.isEmpty()) dir else File(dir, subdir)

private fun readStorageSubdirMetaData(context: Context): String? =
    context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData?.getString(STORAGE_SUBDIR_KEY)

class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val storageSubdir = resolveStorageSubdir(readStorageSubdirMetaData(context))

    // mkdirs() unconditionally (a no-op when unprefixed, since filesDir/cacheDir already exist):
    // some consumers, e.g. LogRepository, write into these roots without creating them first.
    val filesRoot = storageRoot(context.filesDir, storageSubdir).apply { mkdirs() }
    private val cacheDir = storageRoot(context.cacheDir, storageSubdir).apply { mkdirs() }

    private val dataStore = context.dataStore
    val preparationDir = File(cacheDir, "pdfs")
    val ocrLanguageRepository =
        OcrLanguageRepository(dataStore, File(filesRoot, "tesseract/tessdata"))
    val ocrService = OcrService(ocrLanguageRepository, scope)
    val fileManager = FileManager(
        preparationDir,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        AndroidPdfWriter(ocrService, context.assets)
    )
    val logRepository = LogRepository(File(filesRoot, "logs.txt"))
    val logger = FileLogger(logRepository)
    val imageSegmentationService = ImageSegmentationService(context, logger)
    val imageLoader = AndroidImageLoader(context.contentResolver)
    val settingsRepository = SettingsRepository(context, dataStore, scope)

    private var scanningInitialized = false

    // Loading the LiteRT segmentation model here is real, and occasionally failing, work - it
    // must only run when the user is actually about to scan (MainActivity.onCreate), not
    // whenever AppContainer is constructed. A host app embedding :app as a library constructs
    // this at its own process start (see AppContainerInitializer), long before - if ever - the
    // user opens the scanning UI.
    fun ensureScanningInitialized() {
        if (scanningInitialized) return
        scanningInitialized = true
        scope.launch { imageSegmentationService.initialize() }
        scope.launch { ocrService.initialize() }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified VM : ViewModel> viewModelFactory(
        crossinline create: (AppContainer) -> VM
    ) = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return create(this@AppContainer) as T
        }
    }

    val cameraViewModelFactory = viewModelFactory { CameraViewModel(it) }
    val settingsViewModelFactory = viewModelFactory { SettingsViewModel(it) }

    // Runs off the calling thread: called from AppContainerInitializer.create(), which runs
    // synchronously on the main thread as part of a host app's own process start.
    fun cleanOrphanSessions() {
        scope.launch(Dispatchers.IO) {
            val sessionsRoot = sessionsRoot()
            if (!sessionsRoot.exists()) return@launch

            val now = System.currentTimeMillis()

            sessionsRoot.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { dir ->
                    if (isOldSession(dir, now)) {
                        dir.deleteRecursively()
                    }
                }
        }
    }

    fun sessionsRoot(): File = File(cacheDir, "sessions")

    private fun isOldSession(dir: File, now: Long): Boolean {
        val lastModified = dir.lastModified()
        return now - lastModified > 24 * 60 * 60 * 1000 // 24h
    }
}

class SessionViewModelFactory(
    private val application: Application,
    private val launchMode: LaunchMode,
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SessionViewModel(application, launchMode, appContainer) as T
    }
}
