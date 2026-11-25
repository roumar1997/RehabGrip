package com.rehabgrip.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import com.rehabgrip.core.ble.AndroidCraneForceSource
import com.rehabgrip.core.ble.ForceSource
import com.rehabgrip.core.data.DriverFactory
import com.rehabgrip.core.domain.port.ExportSharer
import com.rehabgrip.shared.App
import com.rehabgrip.shared.AppContainer
import com.rehabgrip.shared.Navigator
import com.rehabgrip.shared.PlatformServices
import kotlinx.coroutines.CompletableDeferred
import java.io.File

class MainActivity : ComponentActivity() {
    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pendingPermission?.complete(result.values.all { it })
        pendingPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        currentActivity = this
        val container = sharedContainer ?: AppContainer(AndroidPlatform(applicationContext)).also { sharedContainer = it }
        setContent {
            val navigator = remember { Navigator() }
            BackHandler(enabled = navigator.canPop) { navigator.pop() }
            App(container, navigator)
        }
    }

    override fun onDestroy() {
        if (currentActivity === this) currentActivity = null
        super.onDestroy()
    }

    private suspend fun requestBlePermission(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingPermission = deferred
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionLauncher.launch(perms)
        return deferred.await()
    }

    private fun shareCsv(fileName: String, content: String) {
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        // BOM so Excel opens accents correctly.
        val file = File(dir, fileName).apply { writeText("﻿" + content) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RehabGrip · datos de fuerza")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Exportar datos"))
    }

    /** Process-wide services; activity-bound calls go to the live activity. */
    private class AndroidPlatform(private val appContext: Context) : PlatformServices {
        override val driverFactory = DriverFactory(appContext)
        override val isDebug = BuildConfig.DEBUG
        override val exportSharer = ExportSharer { name, content -> currentActivity?.shareCsv(name, content) }
        override fun realForceSource(): ForceSource = AndroidCraneForceSource(appContext)
        override suspend fun ensureBluetoothPermission(): Boolean = currentActivity?.requestBlePermission() ?: false
    }

    private companion object {
        var sharedContainer: AppContainer? = null
        var currentActivity: MainActivity? = null
    }
}
