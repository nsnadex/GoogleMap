package nsnade.ssss.googlemap

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HistoricalStreetViewWebView(
    latitude: Double,
    longitude: Double,
    heading: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Web版 Google Maps ストリートビューのURL
    // data=!3m4!1e1 パラメータによりストリートビューモードで読み込まれ、Web上の「他の日付を見る」UIが有効になります
    val url = remember(latitude, longitude, heading) {
        "https://www.google.com/maps/@$latitude,$longitude,3a,75y,$heading" + "h,90t/data=!3m4!1e1"
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadUrl(url)
            }
        },
        update = { webView ->
            // 位置情報が変わった際に最新URLを再読み込み（頻繁な再読み込みを防ぐため必要に応じて制御）
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        }
    )
}
