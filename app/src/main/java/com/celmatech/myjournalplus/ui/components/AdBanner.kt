package com.celmatech.myjournalplus.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.celmatech.myjournalplus.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Banner for free users. Uses the unit id from BuildConfig.
 * Google test banner (safe while developing): ca-app-pub-3940256099942544/6300978111
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val unitId = try {
        BuildConfig.ADMOB_BANNER_ID.ifBlank {
            "ca-app-pub-3940256099942544/6300978111"
        }
    } catch (_: Exception) {
        "ca-app-pub-3940256099942544/6300978111"
    }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(50.dp),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = unitId
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { adView ->
            if (adView.adUnitId.isNullOrBlank()) {
                adView.adUnitId = unitId
            }
        }
    )
}
