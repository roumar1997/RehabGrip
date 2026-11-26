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
import android.content.pm.PackageManager
import com.rehabgrip.android.platform.AndroidCuePlayer
import com.rehabgrip.android.platform.AndroidReminderScheduler
import com.rehabgrip.core.domain.port.CuePlayer
import com.rehabgrip.core.domain.port.ExportSharer
import com.rehabgrip.core.domain.port.ReminderScheduler
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
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return request(perms)
    }

    private suspend fun requestNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else request(arrayOf(Manifest.permission.POST_NOTIFICATIONS))

    private suspend fun request(perms: Array<String>): Boolean {
        if (perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) return true
        val deferred = CompletableDeferred<Boolean>()
        pendingPermission = deferred
        permissionLauncher.launch(perms)
        return deferred.await()
    }

    private fun shareCsv(fileName: String, content: String) {
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        // The CSV already starts with the BOM (Excel then reads the accents correctly).
        val file = File(dir, fileName).apply { writeText(content) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Rehagrip · datos de fuerza")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Exportar datos"))
    }

    /** Process-wide services; activity-bound calls go to the live activity. */
    private class AndroidPlatform(private val appContext: Context) : PlatformServices {
        override val driverFactory = DriverFactory(appContext)
        override val isDebug = BuildConfig.DEBUG
        override val exportSharer = ExportSharer { name, content -> currentActivity?.shareCsv(name, content) }
        override val cuePlayer: CuePlayer = AndroidCuePlayer(appContext)
        override val reminderScheduler: ReminderScheduler =
            AndroidReminderScheduler(appContext) { currentActivity?.requestNotificationPermission() ?: false }
        override fun realForceSource(): ForceSource = AndroidCraneForceSource(appContext)
        override fun advertisementScanner(): com.rehabgrip.core.ble.AdvertisementScanner =
            com.rehabgrip.core.ble.AndroidAdvertisementScanner(appContext)
        override suspend fun ensureBluetoothPermission(): Boolean = currentActivity?.requestBlePermission() ?: false
    }

    private companion object {
        var sharedContainer: AppContainer? = null
        var currentActivity: MainActivity? = null
    }
}
