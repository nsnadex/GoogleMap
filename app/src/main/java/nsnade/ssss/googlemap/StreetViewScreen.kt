package nsnade.ssss.googlemap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.LatLng
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
    var requestCount by remember { mutableStateOf(0) }

    // センサー自動連動の状態管理
    var isTrackingEnabled by remember { mutableStateOf(true) }
    
    // 再開直後の誤判定を防ぐガード（1秒間のクールダウン）
    var isGestureCooldownActive by remember { mutableStateOf(false) }

    // 過去画像情報
    var selectedHistoricalInfo by remember { mutableStateOf<PanoInfo?>(null) }
    var isSearchingHistorical by remember { mutableStateOf(false) }

    var streetViewPanorama by remember { mutableStateOf<StreetViewPanorama?>(null) }
    var panoramaAvailable by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("ストリートビュー読み込み中...") }
    var lastSetPosition by remember { mutableStateOf<LatLng?>(null) }

    val streetViewPanoramaView = remember {
        StreetViewPanoramaView(context).apply {
            onCreate(Bundle())
        }
    }

    // 連動再開（リセット）処理の共通関数
    fun resumeTrackingWithCooldown() {
        isTrackingEnabled = true
        selectedHistoricalInfo = null
        isGestureCooldownActive = true

        val loc = currentLocation
        val panorama = streetViewPanorama
        if (loc != null && panorama != null) {
            val targetLatLng = LatLng(loc.latitude, loc.longitude)
            lastSetPosition = targetLatLng
            panorama.setPosition(targetLatLng, 100, StreetViewSource.DEFAULT)

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
    DisposableEffect(lifecycleOwner, streetViewPanoramaView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> streetViewPanoramaView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    streetViewPanoramaView.onResume()
                    sensorHelper.startListening()
                    locationHelper.startLocationUpdates()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sensorHelper.stopListening()
                    locationHelper.stopLocationUpdates()
                    streetViewPanoramaView.onPause()
                }
                Lifecycle.Event.ON_STOP -> streetViewPanoramaView.onStop()
                Lifecycle.Event.ON_DESTROY -> streetViewPanoramaView.onDestroy()
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

    // Native SDK 非同期準備
    LaunchedEffect(streetViewPanoramaView) {
        streetViewPanoramaView.getStreetViewPanoramaAsync { panorama ->
            streetViewPanorama = panorama
            panorama.isStreetNamesEnabled = true
            panorama.isUserNavigationEnabled = true

            panorama.setOnStreetViewPanoramaChangeListener { location ->
                requestCount++
                if (location != null && location.links != null && location.links.isNotEmpty()) {
                    panoramaAvailable = true
                    statusText = if (selectedHistoricalInfo != null) {
                        "過去画像表示中 (${selectedHistoricalInfo?.dateText})"
                    } else if (isTrackingEnabled) {
                        "連動中"
                    } else {
                        "手動操作中（連動停止中）"
                    }
                } else {
                    panoramaAvailable = false
                    statusText = "100m以内に画像がありません"
                }
            }

            val defaultLatLng = LatLng(35.681236, 139.767125)
            panorama.setPosition(defaultLatLng, 100, StreetViewSource.DEFAULT)
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

    // GPS移動時の自動更新
    LaunchedEffect(currentLocation, streetViewPanorama, isTrackingEnabled, selectedHistoricalInfo) {
        val loc = currentLocation
        val panorama = streetViewPanorama
        if (isTrackingEnabled && selectedHistoricalInfo == null && loc != null && panorama != null) {
            val newLatLng = LatLng(loc.latitude, loc.longitude)
            if (lastSetPosition == null || distanceBetween(lastSetPosition!!, newLatLng) > 2.0) {
                lastSetPosition = newLatLng
                panorama.setPosition(newLatLng, 100, StreetViewSource.DEFAULT)
            }
        }
    }

    // 方位・仰俯角のセンサー連動
    LaunchedEffect(bearing, tilt, streetViewPanorama, isTrackingEnabled) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // メインコンポーネント
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { streetViewPanoramaView }
        )

        if (!panoramaAvailable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "現在地の周辺（100m以内）にストリートビュー画像が存在しません。",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "公道や屋外に移動すると自動的に読み込まれます。",
                        color = Color.Yellow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 過去画像モード表示中の通知バッジ
        AnimatedVisibility(
            visible = selectedHistoricalInfo != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFE65100))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "📜 過去写真を表示中: ${selectedHistoricalInfo?.dateText} 撮影",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // 上部コントロールバー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = !isTrackingEnabled || selectedHistoricalInfo != null,
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

            if (isTrackingEnabled && selectedHistoricalInfo == null) {
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (showMenuPanel) Color.Yellow else Color.Black.copy(alpha = 0.8f))
                    .clickable { showMenuPanel = !showMenuPanel }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (showMenuPanel) "⚙️ 設定を隠す" else "⚙️ 設定・情報",
                    color = if (showMenuPanel) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // 設定＆ステータスパネル
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙️ ストリートビュー設定",
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E88E5))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "📊 APIロード回数: ${requestCount} 回",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // メニュー内コントロールボタン群
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
                                containerColor = if (isTrackingEnabled) Color(0xFFE53935) else Color(0xFF43A047)
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (isTrackingEnabled) "⏸️ センサー連動を停止" else "▶️ センサー連動を再開",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // ② 現在地に戻るボタン
                        Button(
                            onClick = { resumeTrackingWithCooldown() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(text = "📍 最新位置へ戻る", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // ③ 過去（最古）の Pano ID 検索＆表示ボタン
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSearchingHistorical = true
                                Toast.makeText(context, "現在地の過去パノラマ（Pano ID）を検索中...", Toast.LENGTH_SHORT).show()
                                val info = HistoricalPanoHelper.fetchOldestPanoInfo(lat, lng)
                                isSearchingHistorical = false
                                if (info != null) {
                                    selectedHistoricalInfo = info
                                    streetViewPanorama?.setPosition(info.panoId)
                                    Toast.makeText(context, "過去の画像を表示しました: ${info.dateText}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "この場所には過去の撮影データが見つかりませんでした", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSearchingHistorical,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = if (isSearchingHistorical) "検索中..." else "📜 過去(最古)ストリートビューを表示",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "連動モード: " + if (selectedHistoricalInfo != null) {
                        "📜 過去写真モード (${selectedHistoricalInfo?.dateText})"
                    } else if (isTrackingEnabled) {
                        "🔴 センサー連動中"
                    } else {
                        "⏸️ 手動操作中（連動停止中）"
                    },
                    color = if (selectedHistoricalInfo != null) Color(0xFFFF9800) else if (isTrackingEnabled) Color.Green else Color.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "方位: ${bearing.roundToInt()}° / 見上げ角: ${tilt.roundToInt()}°",
                    color = Color.White,
                    fontSize = 12.sp
                )
                if (currentLocation != null) {
                    Text(
                        text = "現在地 緯度: %.5f / 経度: %.5f".format(lat, lng),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = "GPS位置情報取得中...",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
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
