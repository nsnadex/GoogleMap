package nsnade.ssss.googlemap

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
     * 指定された緯度・経度の周辺の過去ストリートビューパノラマメタデータを取得し、
     * 最も古い撮影日の PanoInfo (panoId, dateText) を返します。
     */
    suspend fun fetchOldestPanoInfo(latitude: Double, longitude: Double): PanoInfo? = withContext(Dispatchers.IO) {
        try {
            // Google Street View 内部パノラマメタデータ検索エンドポイント
            val urlString = "https://cbk0.google.com/cbk?output=json&ll=$latitude,$longitude"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                if (responseText.isNotBlank()) {
                    val json = JSONObject(responseText)
                    
                    val oldestPano = findOldestPanoFromJson(json)
                    if (oldestPano != null) {
                        return@withContext oldestPano
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun findOldestPanoFromJson(json: JSONObject): PanoInfo? {
        val list = mutableListOf<PanoInfo>()

        // 現在のパノラマ情報
        val locationObj = json.optJSONObject("Location")
        val currentPanoId = locationObj?.optString("panoId")
        val currentDate = locationObj?.optString("original_date") // 例: "2023-05"

        if (!currentPanoId.isNullOrEmpty() && !currentDate.isNullOrEmpty()) {
            list.add(PanoInfo(currentPanoId, currentDate))
        }

        // 過去のパノラマ情報配列 (Ancients / Panos)
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

        // 全パノラマ文字列から regex パターン検索（保険処理）
        if (list.isEmpty()) {
            val jsonString = json.toString()
            val matcher = Pattern.compile("\"id\":\"([^\"]+)\"[^}]*\"date\":\"([^\"]+)\"").matcher(jsonString)
            while (matcher.find()) {
                val pId = matcher.group(1) ?: continue
                val dText = matcher.group(2) ?: continue
                list.add(PanoInfo(pId, dText))
            }
        }

        // 日付順にソート（最も古い日付を選択）
        return list.distinctBy { it.panoId }.minByOrNull { it.dateText }
    }
}
