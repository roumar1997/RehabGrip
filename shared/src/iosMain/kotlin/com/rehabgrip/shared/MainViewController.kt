package com.rehabgrip.shared

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.rehabgrip.core.ble.ForceSource
import com.rehabgrip.core.ble.IosCraneForceSource
import com.rehabgrip.core.data.DriverFactory
import com.rehabgrip.core.domain.port.ExportSharer
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private class IosPlatform : PlatformServices {
    override val driverFactory = DriverFactory()
    override val isDebug: Boolean = kotlin.native.Platform.isDebugBinary
    override fun realForceSource(): ForceSource = IosCraneForceSource()

    /** CoreBluetooth asks for permission itself on first scan. */
    override suspend fun ensureBluetoothPermission() = true

    @Suppress("CAST_NEVER_SUCCEEDS")
    override val exportSharer = ExportSharer { fileName, content ->
        val path = NSTemporaryDirectory() + fileName
        (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        val controller = UIActivityViewController(listOf(NSURL.fileURLWithPath(path)), null)
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(controller, true, null)
    }
}

private val container by lazy { AppContainer(IosPlatform()) }

/** Entry point used from SwiftUI (iosApp/iosApp/ContentView.swift). */
@Suppress("FunctionName", "unused")
fun MainViewController() = ComposeUIViewController {
    val navigator = remember { Navigator() }
    App(container, navigator)
}
