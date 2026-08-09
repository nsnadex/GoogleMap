package nsnade.ssss.googlemap

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class PanoInfo(
    val panoId: String,
    val dateText: String
)

object HistoricalPanoHelper {

    /**
     * 現在地または指定された panoId から、すべての過去撮影年代の PanoInfo リストを取得します。
     * 日付昇順（最古 -> 最新）でソートして返します。
     */
    suspend fun fetchAllPanoHistory(
        context: Context,
        latitude: Double,
        longitude: Double,
        currentPanoId: String? = null
    ): List<PanoInfo> = withContext(Dispatchers.IO) {
        val apiKey = getApiKeyFromManifest(context)
        val allCandidatePanos = mutableListOf<PanoInfo>()

        // 1. Pano ID が直接指定されている場合、Pano ID 直接クエリで CBK を検索（最も確実）
        if (!currentPanoId.isNullOrEmpty()) {
            val panosFromPanoId = fetchFromCbkApiByPanoId(currentPanoId)
            allCandidatePanos.addAll(panosFromPanoId)
        }

        // 2. Official Metadata API で最寄りパノラマの Pano ID と正確な座標を取得
        val (exactLat, exactLng, metaPanoId) = if (!apiKey.isNullOrEmpty() && apiKey != "YOUR_API_KEY_HERE") {
            fetchLocationFromOfficialMetadataApi(latitude, longitude, apiKey)
        } else {
            Triple(latitude, longitude, null)
        }

        if (!metaPanoId.isNullOrEmpty()) {
            val panosFromMetaPanoId = fetchFromCbkApiByPanoId(metaPanoId)
            allCandidatePanos.addAll(panosFromMetaPanoId)
        }

        // 3. 座標指定による CBK 検索（フォールバック）
        val searchLat = exactLat ?: latitude
        val searchLng = exactLng ?: longitude
        val offsets = listOf(
            Pair(0.0, 0.0),
            Pair(0.0003, 0.0003),
            Pair(-0.0003, -0.0003),
            Pair(0.0005, -0.0005),
            Pair(-0.0005, 0.0005)
        )

        for (offset in offsets) {
            val lat = searchLat + offset.first
            val lng = searchLng + offset.second
            val panos = fetchAllFromCbkApiByLatLng(lat, lng)
            allCandidatePanos.addAll(panos)
        }

        // 重複を除外し、日付（昇順: 最古 -> 最新）でソート
        return@withContext allCandidatePanos
            .filter { it.panoId.isNotEmpty() && it.dateText.isNotEmpty() }
            .distinctBy { it.panoId }
            .sortedBy { it.dateText }
    }

    /**
     * Pano ID を直接指定して CBK サービスから過去履歴を取得
     */
    private fun fetchFromCbkApiByPanoId(panoId: String): List<PanoInfo> {
        val urlString = "https://cbk0.google.com/cbk?output=json&panoid=$panoId"
        return executeCbkRequest(urlString)
    }

    /**
     * 緯度経度を指定して CBK サービスから過去履歴を取得
     */
    private fun fetchAllFromCbkApiByLatLng(lat: Double, lng: Double): List<PanoInfo> {
        val urlString = "https://cbk0.google.com/cbk?output=json&ll=$lat,$lng"
        return executeCbkRequest(urlString)
    }

    private fun executeCbkRequest(urlString: String): List<PanoInfo> {
        val list = mutableListOf<PanoInfo>()
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                if (responseText.isNotBlank()) {
                    val json = JSONObject(responseText)
                    list.addAll(extractAllPanosFromJson(json))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun fetchLocationFromOfficialMetadataApi(lat: Double, lng: Double, apiKey: String): Triple<Double?, Double?, String?> {
        try {
            val urlString = "https://maps.googleapis.com/maps/api/streetview/metadata?location=$lat,$lng&radius=1000&key=$apiKey"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                if (responseText.isNotBlank()) {
                    val json = JSONObject(responseText)
                    val status = json.optString("status")
                    if (status == "OK") {
                        val panoId = json.optString("pano_id")
                        val locationObj = json.optJSONObject("location")
                        val resLat = locationObj?.optDouble("lat")
                        val resLng = locationObj?.optDouble("lng")
                        return Triple(resLat, resLng, panoId)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Triple(null, null, null)
    }

    private fun extractAllPanosFromJson(json: JSONObject): List<PanoInfo> {
        val list = mutableListOf<PanoInfo>()

        // 1. Location オブジェクトからの現在/最寄りパノラマ情報
        val locationObj = json.optJSONObject("Location")
        val currentPanoId = locationObj?.optString("panoId")
        val currentDate = locationObj?.optString("original_date")

        if (!currentPanoId.isNullOrEmpty() && !currentDate.isNullOrEmpty()) {
            list.add(PanoInfo(currentPanoId, currentDate))
        }

        // 2. 過去のパノラマ配列 (Panos / Ancients / historical_panoramas)
        val panosArray = json.optJSONArray("Panos") ?: json.optJSONArray("Ancients")
        if (panosArray != null) {
            for (i in 0 until panosArray.length()) {
                val item = panosArray.optJSONObject(i) ?: continue
                val panoId = item.optString("id") ?: item.optString("panoId") ?: ""
                val date = item.optString("date") ?: item.optString("original_date") ?: ""
                if (panoId.isNotEmpty() && date.isNotEmpty()) {
                    list.add(PanoInfo(panoId, date))
                }
            }
        }

        // 3. Regex によるテキスト全体全探索（JSONの構造揺れ対策）
        val jsonString = json.toString()
        val matcher = Pattern.compile("\"(?:id|panoId)\":\"([^\"]+)\"[^}]*\"(?:date|original_date)\":\"([^\"]+)\"").matcher(jsonString)
        while (matcher.find()) {
            val pId = matcher.group(1) ?: continue
            val dText = matcher.group(2) ?: continue
            if (pId.length > 5 && dText.contains("-")) {
                list.add(PanoInfo(pId, dText))
            }
        }

        return list
    }

    private fun getApiKeyFromManifest(context: Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY")
        } catch (e: Exception) {
            null
        }
    }
}
