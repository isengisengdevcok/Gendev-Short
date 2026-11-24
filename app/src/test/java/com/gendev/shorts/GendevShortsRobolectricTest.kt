package com.gendev.shorts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GendevShortsRobolectricTest {

    @Test
    fun `app name matches GENDEV Shorts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GENDEV Shorts", appName)
    }

    @Test
    fun `indonesian localization strings are available`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val config = android.content.res.Configuration(baseContext.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag("in"))
        }
        val localizedContext = baseContext.createConfigurationContext(config)
        val privacyTitle = localizedContext.getString(R.string.privacy_guarantee_title)
        assertTrue(privacyTitle.contains("Anonim", ignoreCase = true))
    }
}
