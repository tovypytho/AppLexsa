package com.tovypytho.applexsa

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tovypytho.applexsa.automation.LexsaService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @Test fun launchesAndSwitchesAllPages() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.homePage).visibility)
                activity.findViewById<Button>(R.id.settingsTab).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settingsPage).visibility)
                activity.findViewById<Button>(R.id.diagnosticsTab).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.diagnosticsPage).visibility)
            }
            scenario.recreate()
            scenario.onActivity { activity -> assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.diagnosticsPage).visibility) }
        }
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
