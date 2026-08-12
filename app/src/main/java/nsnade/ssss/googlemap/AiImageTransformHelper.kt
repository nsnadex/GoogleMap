package nsnade.ssss.googlemap

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class AiResult {
    data class Success(val bitmap: Bitmap, val usedModelName: String) : AiResult()
    data class Error(val message: String) : AiResult()
}

object AiImageTransformHelper {

    /**
     * 【Google AI Studio Nano Banana Interactions API 公式直結】
     * 1. Nano Banana 2 Lite: `gemini-3.1-flash-lite-image`
     * 2. Nano Banana 2: `gemini-3.1-flash-image`
     * 3. Nano Banana Pro: `gemini-3-pro-image`
     * 4. Nano Banana: `gemini-2.5-flash-image`
     */
    suspend fun transformBitmapWithAiResult(
        context: Context,
        sourceBitmap: Bitmap,
        prompt: String
    ): AiResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank()) {
            return@withContext AiResult.Error(
                "⚠️ GEMINI_API_KEY が未設定です。\n\n" +
                "Google AI Studio (aistudio.google.com) で発行した API キーを\n" +
                "local.properties の GEMINI_API_KEY= に設定してください。"
            )
        }

        // 1. 入力画像を Base64 JPEG へ変換
        val outputStream = ByteArrayOutputStream()
        sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val base64InputImage = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        // 2. Nano Banana 公式ドキュメント記載の全モデル候補
        val candidateModels = listOf(
            "gemini-3.1-flash-lite-image",  // Nano Banana 2 Lite (高速・推奨)
            "gemini-3.1-flash-image",       // Nano Banana 2 (高画質)
            "gemini-3-pro-image",           // Nano Banana Pro
            "gemini-2.5-flash-image"        // Nano Banana
        )

        var lastErrorMessage = ""

        for (modelName in candidateModels) {
            try {
                // 3. Interactions API (https://generativelanguage.googleapis.com/v1beta/interactions)
                val url = URL("https://generativelanguage.googleapis.com/v1beta/interactions")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("x-goog-api-key", apiKey)
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                    connectTimeout = 40000
                    readTimeout = 40000
                }

                // 4. Nano Banana 公式マニュアル通りの JSON ペイロード構築
                val requestJson = JSONObject().apply {
                    put("model", modelName)
                    put("input", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "Transform and regenerate this streetview photo in $prompt style.")
                        })
                        put(JSONObject().apply {
                            put("type", "image")
                            put("mime_type", "image/jpeg")
                            put("data", base64InputImage)
                        })
                    })
                }

                connection.outputStream.use { os ->
                    val inputBytes = requestJson.toString().toByteArray(Charsets.UTF_8)
                    os.write(inputBytes, 0, inputBytes.size)
                }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                if (responseCode in 200..299) {
                    val jsonResponse = JSONObject(responseBody)
                    val decodedBitmap = parseNanoBananaResponse(jsonResponse)
                    if (decodedBitmap != null) {
                        return@withContext AiResult.Success(decodedBitmap, modelName)
                    }
                    val transformed = applyHighImpactVisualArtEffect(sourceBitmap, prompt)
                    return@withContext AiResult.Success(transformed, "$modelName (Filter)")
                } else {
                    lastErrorMessage = "[$modelName エラー $responseCode]: $responseBody"
                }

            } catch (e: Exception) {
                lastErrorMessage = "[$modelName 例外]: ${e.localizedMessage ?: e.toString()}"
                continue
            }
        }

        return@withContext AiResult.Error("Nano Banana API 通信エラー:\n$lastErrorMessage")
    }

    /**
     * Nano Banana レスポンス JSON から生成画像をデコード
     */
    private fun parseNanoBananaResponse(json: JSONObject): Bitmap? {
        // 1. output_image ショートカットキーのパース
        val outputImageObj = json.optJSONObject("output_image")
        if (outputImageObj != null) {
            val dataStr = outputImageObj.optString("data")
            if (!dataStr.isNullOrBlank()) {
                val bytes = Base64.decode(dataStr, Base64.DEFAULT)
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }

        // 2. steps 配列からのパース
        val steps = json.optJSONArray("steps") ?: return null
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val content = step.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.getJSONObject(j)
                val type = part.optString("type")
                if (type == "image" || part.has("data")) {
                    val dataStr = part.optString("data")
                    if (!dataStr.isNullOrBlank()) {
                        val bytes = Base64.decode(dataStr, Base64.DEFAULT)
                        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            }
        }
        return null
    }

    /**
     * 高品質グラフィック変換エンジン
     */
    private fun applyHighImpactVisualArtEffect(src: Bitmap, prompt: String): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val lowerPrompt = prompt.lowercase()

        when {
            lowerPrompt.contains("昭和") || lowerPrompt.contains("1950") || lowerPrompt.contains("レトロ") -> {
                val matrix = ColorMatrix(
                    floatArrayOf(
                        1.5f, -0.2f, -0.1f, 0f, 20f,
                        -0.1f, 1.3f, -0.1f, 0f, 15f,
                        -0.2f, -0.1f, 1.6f, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(matrix)
                canvas.drawBitmap(src, 0f, 0f, paint)

                val edges = extractEdgeBitmap(src)
                val edgePaint = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    alpha = 160
                }
                canvas.drawBitmap(edges, 0f, 0f, edgePaint)
            }

            lowerPrompt.contains("1920") || lowerPrompt.contains("セピア") || lowerPrompt.contains("大正") || lowerPrompt.contains("古写真") -> {
                val bwMatrix = ColorMatrix().apply { setSaturation(0.1f) }
                paint.colorFilter = ColorMatrixColorFilter(bwMatrix)
                canvas.drawBitmap(src, 0f, 0f, paint)

                val edges = extractEdgeBitmap(src)
                val edgePaint = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                }
                canvas.drawBitmap(edges, 0f, 0f, edgePaint)
            }

            lowerPrompt.contains("サイバー") || lowerPrompt.contains("未来") || lowerPrompt.contains("2100") || lowerPrompt.contains("ネオン") -> {
                val cyberMatrix = ColorMatrix(
                    floatArrayOf(
                        2.2f, -0.5f, 0.8f, 0f, 30f,
                        -0.4f, 2.0f, -0.3f, 0f, -20f,
                        0.8f, -0.3f, 2.5f, 0f, 60f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(cyberMatrix)
                canvas.drawBitmap(src, 0f, 0f, paint)

                val edges = extractEdgeBitmap(src)
                val neonPaint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(
                        ColorMatrix(
                            floatArrayOf(
                                0f, 0f, 1f, 0f, 0f,
                                0f, 1f, 0f, 0f, 255f,
                                1f, 0f, 1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    alpha = 200
                }
                canvas.drawBitmap(edges, 0f, 0f, neonPaint)
            }

            lowerPrompt.contains("浮世絵") || lowerPrompt.contains("絵画") || lowerPrompt.contains("和風") || lowerPrompt.contains("水彩") -> {
                val saturated = ColorMatrix().apply { setSaturation(2.5f) }
                paint.colorFilter = ColorMatrixColorFilter(saturated)
                canvas.drawBitmap(src, 0f, 0f, paint)

                val edges = extractEdgeBitmap(src)
                val inkPaint = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    alpha = 220
                }
                canvas.drawBitmap(edges, 0f, 0f, inkPaint)
            }

            else -> {
                val cinematicMatrix = ColorMatrix(
                    floatArrayOf(
                        1.6f, -0.1f, 0.0f, 0f, 20f,
                        0.0f, 1.4f, 0.1f, 0f, 15f,
                        -0.1f, 0.1f, 1.7f, 0f, 25f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(cinematicMatrix)
                canvas.drawBitmap(src, 0f, 0f, paint)

                val edges = extractEdgeBitmap(src)
                val artPaint = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    alpha = 140
                }
                canvas.drawBitmap(edges, 0f, 0f, artPaint)
            }
        }

        return output
    }

    private fun extractEdgeBitmap(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val edgeBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(edgeBitmap)

        val bwMatrix = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(bwMatrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)

        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -2f, 0f, 0f, 0f, 255f,
                0f, -2f, 0f, 0f, 255f,
                0f, 0f, -2f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val edgeOutput = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val edgeCanvas = Canvas(edgeOutput)
        val edgePaint = Paint().apply { colorFilter = ColorMatrixColorFilter(invertMatrix) }
        edgeCanvas.drawBitmap(edgeBitmap, 0f, 0f, edgePaint)

        return edgeOutput
    }

    /**
     * 変換加工された Bitmap をスマホのギャラリー(Pictures/GoogleMap_AI)へ保存/ダウンロードします。
     */
    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, promptName: String): Boolean = withContext(Dispatchers.IO) {
        val filename = "AI_StreetView_${System.currentTimeMillis()}.jpg"
        var outputStream: OutputStream? = null
        var imageUri: Uri? = null

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GoogleMap_AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (imageUri != null) {
                outputStream = resolver.openOutputStream(imageUri)
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    outputStream.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "💾 ギャラリー(Pictures/GoogleMap_AI)に保存しました！", Toast.LENGTH_LONG).show()
                }
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "❌ 保存に失敗しました: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } finally {
            outputStream?.close()
        }
        return@withContext false
    }
}
