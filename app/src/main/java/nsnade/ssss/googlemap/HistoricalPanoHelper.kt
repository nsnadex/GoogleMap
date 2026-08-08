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
     * 現在地の周辺（半径500m以内）にある最寄りの過去パノラマ情報を自動検索し、
     * 最も古い撮影日の PanoInfo (panoId, dateText) を返します。
     */
    suspend fun fetchOldestPanoInfo(context: Context, latitude: Double, longitude: Double): PanoInfo? = withContext(Dispatchers.IO) {
        val apiKey = getApiKeyFromManifest(context)
        
        // 1. Google Official Street View Metadata API (radius=500m) による最寄り検索
        if (!apiKey.isNullOrEmpty() && apiKey != "YOUR_API_KEY_HERE") {
            val officialPano = fetchFromOfficialMetadataApi(latitude, longitude, apiKey)
            if (officialPano != null) {
                return@withContext officialPano
            }
        }

        // 2. 広域グリッド探索（現在地および周辺100m以内の5地点を走査）
        val offsets = listOf(
            Pair(0.0, 0.0),
            Pair(0.0005, 0.0005),   // 約50m北東
            Pair(-0.0005, -0.0005), // 約50m南西
            Pair(0.0008, -0.0008), // 約80m北西
            Pair(-0.0008, 0.0008)  // 約80m南東
        )

        val candidates = mutableListOf<PanoInfo>()

        for (offset in offsets) {
            val searchLat = latitude + offset.first
            val searchLng = longitude + offset.second
            val pano = fetchFromCbkApi(searchLat, searchLng)
            if (pano != null) {
                candidates.add(pano)
            }
        }

        // 日付順にソート（最も古い日付のパノラマを選択）
        return@withContext candidates.distinctBy { it.panoId }.minByOrNull { it.dateText }
    }

    private fun fetchFromOfficialMetadataApi(lat: Double, lng: Double, apiKey: String): PanoInfo? {
        try {
            val urlString = "https://maps.googleapis.com/maps/api/streetview/metadata?location=$lat,$lng&radius=500&key=$apiKey"
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
                        val date = json.optString("date")
                        if (panoId.isNotEmpty()) {
                            return PanoInfo(panoId, date.ifEmpty { "過去データ" })
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun fetchFromCbkApi(lat: Double, lng: Double): PanoInfo? {
        try {
            val urlString = "https://cbk0.google.com/cbk?output=json&ll=$lat,$lng"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                if (responseText.isNotBlank()) {
                    val json = JSONObject(responseText)
                    return findOldestPanoFromJson(json)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun findOldestPanoFromJson(json: JSONObject): PanoInfo? {
        val list = mutableListOf<PanoInfo>()

        val locationObj = json.optJSONObject("Location")
        val currentPanoId = locationObj?.optString("panoId")
        val currentDate = locationObj?.optString("original_date")

        if (!currentPanoId.isNullOrEmpty() && !currentDate.isNullOrEmpty()) {
            list.add(PanoInfo(currentPanoId, currentDate))
        }

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

        if (list.isEmpty()) {
            val jsonString = json.toString()
            val matcher = Pattern.compile("\"id\":\"([^\"]+)\"[^}]*\"date\":\"([^\"]+)\"").matcher(jsonString)
            while (matcher.find()) {
                val pId = matcher.group(1) ?: continue
                val dText = matcher.group(2) ?: continue
                list.add(PanoInfo(pId, dText))
            }
        }

        return list.distinctBy { it.panoId }.minByOrNull { it.dateText }
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
