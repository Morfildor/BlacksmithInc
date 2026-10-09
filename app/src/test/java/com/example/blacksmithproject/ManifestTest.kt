package com.example.blacksmithproject

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The game is offline: it asks for no permission and ships no network, ads, billing or telemetry library. */
class ManifestTest {
    @Test
    fun noPermissionsAndNoNetworkLibraries() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("manifest not found at ${manifest.absolutePath}", manifest.isFile)
        assertFalse("the manifest must not request a permission", manifest.readText().contains("<uses-permission"))

        val forbidden = listOf(
            "okhttp3.OkHttpClient",
            "retrofit2.Retrofit",
            "io.ktor.client.HttpClient",
            "com.android.volley.RequestQueue",
            "com.google.firebase.FirebaseApp",
            "com.google.android.gms.common.GoogleApiAvailability",
            "com.google.android.gms.ads.MobileAds",
            "com.android.billingclient.api.BillingClient",
        )
        val present = forbidden.filter { runCatching { Class.forName(it) }.isSuccess }
        assertTrue("libraries that must not be on the runtime classpath: $present", present.isEmpty())
    }
}
