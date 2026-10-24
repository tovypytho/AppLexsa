package com.tovypytho.applexsa

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tovypytho.applexsa.automation.LexsaService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @Test fun launchesAndSwitchesAllPages() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.homePage).visibility)
            }
            screenshot("01-beranda")
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.settingsTab).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settingsPage).visibility)
            }
            screenshot("02-pengaturan")
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.diagnosticsTab).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.diagnosticsPage).visibility)
            }
            screenshot("03-diagnostik")
            scenario.recreate()
            scenario.onActivity { activity -> assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.diagnosticsPage).visibility) }
        }
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val file = File(instrumentation.targetContext.cacheDir, "screenshots/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun manifestDoesNotRequestNetworkOrContactsAndServiceIsProtected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        assertFalse(info.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
        assertFalse(info.requestedPermissions.orEmpty().contains(Manifest.permission.READ_CONTACTS))
        val service = context.packageManager.getServiceInfo(ComponentName(context, LexsaService::class.java), PackageManager.ComponentInfoFlags.of(0))
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, service.permission)
    }
}
