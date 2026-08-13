package nsnade.ssss.googlemap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.google.android.gms.maps.model.StreetViewSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun StreetViewScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (!hasLocationPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Google Maps ストリートビューを表示するには位置情報の許可が必要です。",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) {
                    Text("権限を許可する")
                }
            }
        }
    } else {
        StreetViewMainContent(context = context, lifecycleOwner = lifecycleOwner)
    }
}

@Composable
private fun StreetViewMainContent(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val coroutineScope = rememberCoroutineScope()
    val sensorHelper = remember { SensorManagerHelper(context) }
    val locationHelper = remember { LocationHelper(context) }

    val bearing by sensorHelper.bearing.collectAsState()
    val tilt by sensorHelper.tilt.collectAsState()
    val currentLocation by locationHelper.locationState.collectAsState()

    var showMenuPanel by remember { mutableStateOf(false) }
    var showMinimap by remember { mutableStateOf(true) }
    var requestCount by remember { mutableStateOf(0) }

    // センサー自動連動の状態管理
    var isTrackingEnabled by remember { mutableStateOf(true) }
    var isGestureCooldownActive by remember { mutableStateOf(false) }

    // AIスタイル変換の状態
    var showAiDialog by remember { mutableStateOf(false) }
    var aiPromptInput by remember { mutableStateOf("昭和30年代の日本のレトロな街並み風") }
    var isGeneratingAiImage by remember { mutableStateOf(false) }
    var generatedAiBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var full360PanoramaBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var inputParamBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeAiPromptText by remember { mutableStateOf<String?>(null) }
    var errorMessageText by remember { mutableStateOf<String?>(null) }

    var streetViewPanorama by remember { mutableStateOf<StreetViewPanorama?>(null) }
    var panoramaAvailable by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("ストリートビュー読み込み中...") }
    var lastSetPosition by remember { mutableStateOf<LatLng?>(null) }

    // 2D ミニマップ用の状態
    var googleMapInstance by remember { mutableStateOf<GoogleMap?>(null) }
    var minimapMarker by remember { mutableStateOf<Marker?>(null) }

    val streetViewPanoramaView = remember {
        StreetViewPanoramaView(context).apply {
            onCreate(Bundle())
        }
    }

    val minimapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    // Google Maps アプリを起動する関数
    fun openGoogleMapsApp() {
        val lat = currentLocation?.latitude ?: 35.681236
        val lng = currentLocation?.longitude ?: 139.767125
        try {
            val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(現在地)")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                context.startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Google Maps アプリの起動に失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    // 連動再開（リセット）処理の共通関数
    fun resumeTrackingWithCooldown() {
        isTrackingEnabled = true
        generatedAiBitmap = null
        activeAiPromptText = null
        isGestureCooldownActive = true

        val loc = currentLocation
        val panorama = streetViewPanorama
        if (loc != null && panorama != null) {
            val targetLatLng = LatLng(loc.latitude, loc.longitude)
            lastSetPosition = targetLatLng
            panorama.setPosition(targetLatLng, 500, StreetViewSource.OUTDOOR)

            val updatedCamera = StreetViewPanoramaCamera.Builder(panorama.panoramaCamera)
                .bearing(bearing)
                .tilt(tilt)
                .build()
            panorama.animateTo(updatedCamera, 200)
        }

        coroutineScope.launch {
            delay(1000)
            isGestureCooldownActive = false
        }
    }

    // ライフサイクルの完全転送
    DisposableEffect(lifecycleOwner, streetViewPanoramaView, minimapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    streetViewPanoramaView.onStart()
                    minimapView.onStart()
                }
                Lifecycle.Event.ON_RESUME -> {
                    streetViewPanoramaView.onResume()
                    minimapView.onResume()
                    sensorHelper.startListening()
                    locationHelper.startLocationUpdates()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sensorHelper.stopListening()
                    locationHelper.stopLocationUpdates()
                    streetViewPanoramaView.onPause()
                    minimapView.onPause()
                }
                Lifecycle.Event.ON_STOP -> {
                    streetViewPanoramaView.onStop()
                    minimapView.onStop()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    streetViewPanoramaView.onDestroy()
                    minimapView.onDestroy()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            sensorHelper.stopListening()
            locationHelper.stopLocationUpdates()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Native StreetView 非同期準備
    LaunchedEffect(streetViewPanoramaView) {
        streetViewPanoramaView.getStreetViewPanoramaAsync { panorama ->
            streetViewPanorama = panorama
            panorama.isStreetNamesEnabled = true
            panorama.isUserNavigationEnabled = true

            panorama.setOnStreetViewPanoramaChangeListener { location ->
                requestCount++
                if (location != null && location.links != null && location.links.isNotEmpty()) {
                    panoramaAvailable = true
                    statusText = if (generatedAiBitmap != null) {
                        "✨ AIスタイル変換表示中 ($activeAiPromptText)"
                    } else if (isTrackingEnabled) {
                        "連動中"
                    } else {
                        "手動操作中（連動停止中）"
                    }
                } else {
                    panoramaAvailable = false
                    statusText = "1km以内に最寄りの画像がありません"
                }
            }

            val defaultLatLng = LatLng(35.681236, 139.767125)
            panorama.setPosition(defaultLatLng, 500, StreetViewSource.OUTDOOR)
        }
    }

    // 2D ミニマップ 非同期準備
    LaunchedEffect(minimapView) {
        minimapView.getMapAsync { map ->
            googleMapInstance = map
            map.uiSettings.apply {
                isZoomControlsEnabled = false
                isCompassEnabled = false
                isMapToolbarEnabled = false
                isMyLocationButtonEnabled = false
            }
            map.setOnMapClickListener {
                openGoogleMapsApp()
            }
            val defaultLatLng = LatLng(35.681236, 139.767125)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 16.5f))

            val arrowBitmap = createDirectionArrowBitmap()
            val markerOptions = MarkerOptions()
                .position(defaultLatLng)
                .icon(BitmapDescriptorFactory.fromBitmap(arrowBitmap))
                .anchor(0.5f, 0.5f)
                .flat(true)
            minimapMarker = map.addMarker(markerOptions)
        }
    }

    // タッチイベントの監視
    DisposableEffect(streetViewPanoramaView, isTrackingEnabled, isGestureCooldownActive) {
        streetViewPanoramaView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE && isTrackingEnabled && !isGestureCooldownActive) {
                isTrackingEnabled = false
            }
            false
        }
        onDispose {
            streetViewPanoramaView.setOnTouchListener(null)
        }
    }

    // GPS位置更新時の連動
    LaunchedEffect(currentLocation, streetViewPanorama, googleMapInstance, minimapMarker, isTrackingEnabled, generatedAiBitmap) {
        val loc = currentLocation
        if (loc != null) {
            val newLatLng = LatLng(loc.latitude, loc.longitude)

            googleMapInstance?.moveCamera(CameraUpdateFactory.newLatLng(newLatLng))
            minimapMarker?.position = newLatLng

            val panorama = streetViewPanorama
            if (isTrackingEnabled && generatedAiBitmap == null && panorama != null) {
                if (lastSetPosition == null || distanceBetween(lastSetPosition!!, newLatLng) > 2.0) {
                    lastSetPosition = newLatLng
                    panorama.setPosition(newLatLng, 500, StreetViewSource.OUTDOOR)
                }
            }
        }
    }

    // 方位・仰俯角の連動
    LaunchedEffect(bearing, tilt, streetViewPanorama, minimapMarker, isTrackingEnabled) {
        minimapMarker?.rotation = bearing

        val panorama = streetViewPanorama
        if (isTrackingEnabled && panorama != null) {
            val currentCamera = panorama.panoramaCamera
            val updatedCamera = StreetViewPanoramaCamera.Builder(currentCamera)
                .bearing(bearing)
                .tilt(tilt)
                .build()
            panorama.animateTo(updatedCamera, 150)
        }
    }

    val lat = currentLocation?.latitude ?: 35.681236
    val lng = currentLocation?.longitude ?: 139.767125

    val presets = listOf(
        "昭和30年代の日本のレトロな街並み風",
        "1920年代 大正ロマン・モノクロセピア古写真風",
        "2100年 サイバーパンク未来都市風",
        "江戸時代の浮世絵・和風絵画調",
        "水彩画・ファンタジーアニメの背景風"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // メイン 3D ストリートビュー
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { streetViewPanoramaView }
        )

        // ✨ AI変換された静止画写真のフル画面鑑賞 ＆ 端末ダウンロード保存オーバーレイ
        if (generatedAiBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1000f)
                    .background(Color.Black)
            ) {
                // 変換画像本体（高精細プレビュー）
                Image(
                    bitmap = generatedAiBitmap!!.asImageBitmap(),
                    contentDescription = "AI Transformed Image Result",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // 綺麗に配置された上部コントロールバー（白を基調とした洗練されたデザイン）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左上: AIスタイル名バッジ
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.94f))
                            .border(1.5.dp, Color(0xFFE1BEE7), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "✨ AIスタイル: ${activeAiPromptText?.take(14)}",
                            color = Color(0xFF6A1B9A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }

                    // 右上: 洗練された丸型「✖ 閉じる」ボタン
                    Button(
                        onClick = {
                            generatedAiBitmap = null
                            activeAiPromptText = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFEBEE),
                            contentColor = Color(0xFFC62828)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Text("✖ 閉じる", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // 下部アクションバー (白を基調とした洗練されたフローティング保存ボタン)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                AiImageTransformHelper.saveBitmapToGallery(
                                    context = context,
                                    bitmap = generatedAiBitmap!!,
                                    promptName = activeAiPromptText ?: "AI"
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.96f),
                            contentColor = Color(0xFF1B5E20)
                        ),
                        shape = RoundedCornerShape(30.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(
                                width = 2.dp,
                                color = Color(0xFF00E676),
                                shape = RoundedCornerShape(30.dp)
                            ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 10.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Text(
                            text = "💾 このAI加工写真をダウンロード保存する",
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // ✨ AI生成中の美しいローディング画面 (最前面 zIndex 指定)
        if (isGeneratingAiImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2000f)
                    .background(Color.Black.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFD500F9),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "✨ Google AI Studio (Nano Banana) で景色を生成加工中...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "※AI生成には約15秒〜30秒かかります。そのままお待ちください。",
                        color = Color.Yellow,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (!panoramaAvailable && generatedAiBitmap == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "最寄りの道路（1km以内）にストリートビューが見つかりませんでした。",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "公道付近に移動すると自動的に読み込まれます。",
                        color = Color.Yellow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

    var showFullMapSelectionDialog by remember { mutableStateOf(false) }
    var selectedMapLocation by remember { mutableStateOf<LatLng?>(null) }

    // 右下: 2D ミニマップ
    AnimatedVisibility(
        visible = showMinimap,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = if (showMenuPanel) 240.dp else 24.dp, end = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
                .border(2.5.dp, Color.Yellow, RoundedCornerShape(18.dp))
                .clickable { showFullMapSelectionDialog = true }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { minimapView }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showFullMapSelectionDialog = true }
            )
        }
    }

        // 通常時の上部コントロールバー
        if (generatedAiBitmap == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = !isTrackingEnabled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Button(
                        onClick = { resumeTrackingWithCooldown() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "🔄 連動を再開",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                if (isTrackingEnabled) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (showMenuPanel) Color.White else Color.Black.copy(alpha = 0.8f))
                        .border(
                            width = 1.5.dp,
                            color = if (showMenuPanel) Color(0xFF1E88E5) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { showMenuPanel = !showMenuPanel }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (showMenuPanel) "⚙️ 設定を隠す" else "⚙️ 設定・情報",
                        color = if (showMenuPanel) Color(0xFF1E88E5) else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 白を基調としたおしゃれな Glassmorphism コントロールパネル
        AnimatedVisibility(
            visible = showMenuPanel,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .border(1.5.dp, Color(0xFFE3F2FD), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙️ ストリートビュー設定",
                        color = Color(0xFF1A237E),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE3F2FD))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "📊 APIロード回数: ${requestCount} 回",
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ① センサー連動の停止 / 再開トグルボタン
                        Button(
                            onClick = {
                                if (isTrackingEnabled) {
                                    isTrackingEnabled = false
                                } else {
                                    resumeTrackingWithCooldown()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTrackingEnabled) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                contentColor = if (isTrackingEnabled) Color(0xFFC62828) else Color(0xFF2E7D32)
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isTrackingEnabled) "⏸️ センサー連動を停止" else "▶️ センサー連動を再開",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // ② ミニマップ表示ON/OFF切り替えボタン
                        Button(
                            onClick = { showMinimap = !showMinimap },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showMinimap) Color(0xFFE8EAF6) else Color(0xFFF5F5F5),
                                contentColor = if (showMinimap) Color(0xFF283593) else Color(0xFF616161)
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (showMinimap) "🗺️ ミニマップ: ON" else "🗺️ ミニマップ: OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ③ 最新位置へ戻るボタン
                        Button(
                            onClick = { resumeTrackingWithCooldown() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1976D2),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "📍 最新位置へ戻る", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // ④ ✨ AIでこの景色を変換加工ボタン
                        Button(
                            onClick = {
                                captureRealStreetViewPhoto(streetViewPanoramaView) { realPhoto ->
                                    inputParamBitmap = realPhoto
                                    showAiDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8E24AA),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "✨ AI景色変換加工",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE0E0E0))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "連動モード: " + if (generatedAiBitmap != null) {
                        "✨ AIスタイル加工中 ($activeAiPromptText)"
                    } else if (isTrackingEnabled) {
                        "🟢 センサー連動中"
                    } else {
                        "⏸️ 手動操作中（連動停止中）"
                    },
                    color = if (generatedAiBitmap != null) Color(0xFFAB47BC) else if (isTrackingEnabled) Color(0xFF2E7D32) else Color(0xFFE65100),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ✨ AIプロンプト変換入力モーダルダイアログ (白を基調とした洗練されたデザイン)
        if (showAiDialog) {
            AlertDialog(
                onDismissRequest = { showAiDialog = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "✨ Google AI Studio / AI 景色加工",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF6A1B9A),
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 📸 切り取ったストリートビュー景色のプレビュー表示
                        if (inputParamBitmap != null) {
                            Text(
                                text = "📸 切り取り景色のプレビュー:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F8E9))
                                    .border(1.5.dp, Color(0xFF81C784), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = inputParamBitmap!!.asImageBitmap(),
                                    contentDescription = "Cutout View Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Text(
                            text = "現在のストリートビュー画面をAIでプロンプト通りのスタイル写真に変換加工します。",
                            fontSize = 12.sp,
                            color = Color(0xFF424242)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "🌟 人気のプロンプトプリセット:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presets) { preset ->
                                FilterChip(
                                    selected = aiPromptInput == preset,
                                    onClick = { aiPromptInput = preset },
                                    label = {
                                        Text(
                                            text = preset,
                                            fontSize = 11.sp,
                                            fontWeight = if (aiPromptInput == preset) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFF3E5F5),
                                        selectedLabelColor = Color(0xFF7B1FA2),
                                        containerColor = Color(0xFFF5F5F5),
                                        labelColor = Color(0xFF616161)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = aiPromptInput == preset,
                                        selectedBorderColor = Color(0xFFAB47BC),
                                        borderColor = Color(0xFFE0E0E0)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = aiPromptInput,
                            onValueChange = { aiPromptInput = it },
                            label = { Text("プロンプト（加工スタイル）を入力", color = Color(0xFF757575)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF212121),
                                unfocusedTextColor = Color(0xFF212121),
                                focusedBorderColor = Color(0xFF8E24AA),
                                unfocusedBorderColor = Color(0xFFBDBDBD),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAiDialog = false
                            coroutineScope.launch {
                                generatedAiBitmap = null
                                activeAiPromptText = null
                                isGeneratingAiImage = true
                                Toast.makeText(context, "✨ 今見ている景色をAIで変換加工中...", Toast.LENGTH_SHORT).show()

                                coroutineScope.launch {
                                    try {
                                        val realBitmap = inputParamBitmap ?: createFallbackColorBitmap()
                                        val result = AiImageTransformHelper.transformBitmapWithAiResult(
                                            context = context,
                                            sourceBitmap = realBitmap,
                                            prompt = aiPromptInput
                                        )
                                        isGeneratingAiImage = false
                                        when (result) {
                                            is AiResult.Success -> {
                                                generatedAiBitmap = result.bitmap
                                                activeAiPromptText = "${result.usedModelName}: $aiPromptInput"
                                                Toast.makeText(context, "✨ Google AI Studio (${result.usedModelName}) での変換が完了しました！", Toast.LENGTH_SHORT).show()
                                            }
                                            is AiResult.Error -> {
                                                errorMessageText = result.message
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isGeneratingAiImage = false
                                        errorMessageText = "予期せぬエラーが発生しました:\n${e.localizedMessage ?: e.toString()}"
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                    ) {
                        Text("✨ 景色を変換加工する", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAiDialog = false }) {
                        Text("キャンセル", color = Color(0xFF757575), fontWeight = FontWeight.Medium)
                    }
                }
            )
        }

        // 🗺️ インタラクティブ・マップ選択ダイアログ (白を基調とした大画面フルスクリーンデザイン)
        if (showFullMapSelectionDialog) {
            var pickerMapInstance by remember { mutableStateOf<GoogleMap?>(null) }
            var pickedMarker by remember { mutableStateOf<Marker?>(null) }
            var mapSearchQuery by remember { mutableStateOf("") }
            val pickerMapView = remember {
                MapView(context).apply { onCreate(Bundle()) }
            }

            fun executeSearchLocation() {
                val query = mapSearchQuery.trim()
                val map = pickerMapInstance
                if (query.isNotEmpty() && map != null) {
                    try {
                        val geocoder = android.location.Geocoder(context, java.util.Locale.JAPAN)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            geocoder.getFromLocationName(query, 1) { addresses ->
                                if (addresses.isNotEmpty()) {
                                    val addr = addresses[0]
                                    val targetLatLng = LatLng(addr.latitude, addr.longitude)
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        selectedMapLocation = targetLatLng
                                        pickedMarker?.position = targetLatLng
                                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, 16f), 400, null)
                                        Toast.makeText(context, "📍 「$query」へ移動しました！", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "🔍 「$query」の場所が見つかりませんでした", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocationName(query, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                val targetLatLng = LatLng(addr.latitude, addr.longitude)
                                selectedMapLocation = targetLatLng
                                pickedMarker?.position = targetLatLng
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, 16f), 400, null)
                                Toast.makeText(context, "📍 「$query」へ移動しました！", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "🔍 「$query」の場所が見つかりませんでした", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "❌ 検索エラーが発生しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            Dialog(
                onDismissRequest = { showFullMapSelectionDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212))
                ) {
                    // 1. メイン領域: 全画面フルスクリーン Google Map
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            pickerMapView.apply {
                                setOnTouchListener { v, event ->
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                            v.parent?.requestDisallowInterceptTouchEvent(false)
                                        }
                                    }
                                    false
                                }
                            }
                        }
                    )

                    // 2. 上部固定コントロールパネル（白を基調としたおしゃれなヘッダー）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(12.dp)
                            .align(Alignment.TopCenter)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.94f))
                            .border(1.5.dp, Color(0xFFE3F2FD), RoundedCornerShape(20.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🗺️ ストリートビュー移動スポットを選択",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color(0xFF1565C0)
                            )
                            Button(
                                onClick = { showFullMapSelectionDialog = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFEBEE),
                                    contentColor = Color(0xFFC62828)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("✖ 閉じる", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = mapSearchQuery,
                                onValueChange = { mapSearchQuery = it },
                                placeholder = { Text("地名・住所・施設名で検索", fontSize = 11.sp, color = Color(0xFF757575)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF212121),
                                    unfocusedTextColor = Color(0xFF212121),
                                    focusedBorderColor = Color(0xFF1976D2),
                                    unfocusedBorderColor = Color(0xFFBDBDBD),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { executeSearchLocation() },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                Text("🔍 検索", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // 3. 下部固定アクションバー (白を基調とした洗練されたフローティングボタン)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        Button(
                            onClick = {
                                val targetPos = selectedMapLocation
                                if (targetPos != null && streetViewPanorama != null) {
                                    isTrackingEnabled = false
                                    lastSetPosition = targetPos
                                    streetViewPanorama?.setPosition(targetPos, 500, StreetViewSource.OUTDOOR)
                                    generatedAiBitmap = null
                                    activeAiPromptText = null
                                    Toast.makeText(context, "📍 選択したスポットに移動しました (連動は停止中)", Toast.LENGTH_SHORT).show()
                                }
                                showFullMapSelectionDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.96f),
                                contentColor = Color(0xFF1B5E20)
                            ),
                            shape = RoundedCornerShape(30.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .border(
                                    width = 2.dp,
                                    color = Color(0xFF00E676),
                                    shape = RoundedCornerShape(30.dp)
                                ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 10.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Text(
                                text = "📍 この場所へ移動する",
                                color = Color(0xFF1B5E20),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // 🚀 MapView ライフサイクルの同期
                    DisposableEffect(pickerMapView) {
                        pickerMapView.onStart()
                        pickerMapView.onResume()
                        onDispose {
                            pickerMapView.onPause()
                            pickerMapView.onStop()
                            pickerMapView.onDestroy()
                        }
                    }

                    LaunchedEffect(pickerMapView) {
                        pickerMapView.getMapAsync { map ->
                            pickerMapInstance = map
                            val startPos = streetViewPanorama?.location?.position
                                ?: lastSetPosition
                                ?: currentLocation?.let { LatLng(it.latitude, it.longitude) }
                                ?: LatLng(35.681236, 139.767125)
                            
                            map.isBuildingsEnabled = false
                            map.isIndoorEnabled = false
                            map.uiSettings.isZoomControlsEnabled = true
                            map.uiSettings.isRotateGesturesEnabled = false
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(startPos, 16f))

                            val initialMarker = map.addMarker(
                                MarkerOptions()
                                    .position(startPos)
                                    .title("📍 選択中のスポット")
                            )
                            pickedMarker = initialMarker
                            selectedMapLocation = startPos

                            map.setOnMapClickListener { clickedLatLng ->
                                selectedMapLocation = clickedLatLng
                                pickedMarker?.position = clickedLatLng
                                map.animateCamera(CameraUpdateFactory.newLatLng(clickedLatLng), 300, null)
                            }
                        }
                    }
                }
            }
        }

        // ⚠️ エラー詳細表示ダイアログ (高コントラスト・極めて読みやすいデザイン)
        if (errorMessageText != null) {
            AlertDialog(
                onDismissRequest = { errorMessageText = null },
                containerColor = Color(0xFF1E1E2C),
                title = {
                    Text(
                        text = "⚠️ 処理エラー通知",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFFFF5252)
                    )
                },
                text = {
                    Text(
                        text = errorMessageText!!,
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { errorMessageText = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OK (閉じる)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

private fun createDirectionArrowBitmap(): Bitmap {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#E6000000")
        isAntiAlias = true
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, bgPaint)

    val arrowPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#00E676")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val path = Path().apply {
        moveTo(size / 2f, 10f)
        lineTo(size - 16f, size - 14f)
        lineTo(size / 2f, size - 24f)
        lineTo(16f, size - 14f)
        close()
    }
    canvas.drawPath(path, arrowPaint)

    return bitmap
}

private fun distanceBetween(point1: LatLng, point2: LatLng): Float {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        point1.latitude, point1.longitude,
        point2.latitude, point2.longitude,
        results
    )
    return results[0]
}

private fun captureRealStreetViewPhoto(view: android.view.View, onCaptured: (Bitmap) -> Unit) {
    // 1. TextureView 探索（液晶画面に映っている本物のストリートビュー写真をそのままダイレクト取得！）
    val textureView = findTextureView(view)
    if (textureView != null) {
        val w = if (view.width > 0) view.width else 640
        val h = if (view.height > 0) view.height else 480
        val bmp = textureView.getBitmap(w, h)
        if (bmp != null) {
            onCaptured(bmp)
            return
        }
    }

    // 2. SurfaceView 直接 PixelCopy
    val surfaceView = findSurfaceView(view)
    if (surfaceView != null && surfaceView.holder.surface.isValid) {
        val w = if (surfaceView.width > 0) surfaceView.width else 640
        val h = if (surfaceView.height > 0) surfaceView.height else 480
        val surfaceBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            android.view.PixelCopy.request(
                surfaceView,
                surfaceBitmap,
                { result ->
                    if (result == android.view.PixelCopy.SUCCESS) {
                        onCaptured(surfaceBitmap)
                    } else {
                        val fallbackBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(fallbackBmp)
                        view.draw(canvas)
                        onCaptured(fallbackBmp)
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 3. Activity Window PixelCopy
    val activity = view.context as? android.app.Activity
    if (activity != null && view.width > 0 && view.height > 0) {
        val pixelCopyBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect = android.graphics.Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )

        try {
            android.view.PixelCopy.request(
                activity.window,
                rect,
                pixelCopyBitmap,
                { result ->
                    if (result == android.view.PixelCopy.SUCCESS) {
                        onCaptured(pixelCopyBitmap)
                    } else {
                        val fallbackBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(fallbackBitmap)
                        view.draw(canvas)
                        onCaptured(fallbackBitmap)
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 4. View.draw
    val width = if (view.width > 0) view.width else 640
    val height = if (view.height > 0) view.height else 480
    val fallbackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(fallbackBitmap)
    view.draw(canvas)
    onCaptured(fallbackBitmap)
}

private fun findTextureView(view: android.view.View): android.view.TextureView? {
    if (view is android.view.TextureView) return view
    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = findTextureView(view.getChildAt(i))
            if (child != null) return child
        }
    }
    return null
}

private fun findSurfaceView(view: android.view.View): android.view.SurfaceView? {
    if (view is android.view.SurfaceView) return view
    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = findSurfaceView(view.getChildAt(i))
            if (child != null) return child
        }
    }
    return null
}

private fun createFallbackColorBitmap(): Bitmap {
    val width = 640
    val height = 480
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val skyPaint = Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, height * 0.55f, android.graphics.Color.parseColor("#4FC3F7"), android.graphics.Color.parseColor("#E0F7FA"), android.graphics.Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height * 0.55f, skyPaint)

    val groundPaint = Paint().apply {
        shader = android.graphics.LinearGradient(0f, height * 0.55f, 0f, height.toFloat(), android.graphics.Color.parseColor("#81C784"), android.graphics.Color.parseColor("#388E3C"), android.graphics.Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, height * 0.55f, width.toFloat(), height.toFloat(), groundPaint)
    return bitmap
}
