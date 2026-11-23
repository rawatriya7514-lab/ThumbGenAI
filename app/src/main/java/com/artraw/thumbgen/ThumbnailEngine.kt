package com.artraw.thumbgen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class AspectRatio(val width: Int, val height: Int, val label: String) {
    RATIO_16_9(1280, 720, "16:9 (YouTube Video)"),
    RATIO_9_16(720, 1280, "9:16 (Shorts / Reel)"),
    RATIO_1_1(1024, 1024, "1:1 (Post / Square)")
}

enum class Niche(val displayName: String, val promptModifier: String, val hookKeywords: List<String>) {
    COOKING(
        "Cooking / Food",
        "mouthwatering sizzling food close-up, glistening oil, hot rising steam, rich aromatic textures, warm golden rim lighting, dark moody kitchen bokeh, 8k photorealistic culinary photography, ultra sharp focus, appetizing colors",
        listOf("ASLI DESI TASTE!", "SECRET RECIPE REVEALED", "DHAABA CHEF SECRET", "NEVER COOK LIKE THIS!")
    ),
    TECH(
        "Tech",
        "ultra-sleek premium gadget product photography, matte dark background, dramatic neon edge reflections, cinematic teal and orange rim lighting, futuristic studio depth of field, 8k hyper-detailed",
        listOf("DON'T BUY THIS!", "INSANE PERFORMANCE!", "WORTH THE HYPE?", "100X BETTER!")
    ),
    VLOG(
        "Vlog",
        "dynamic cinematic lifestyle photography, high-contrast golden hour sunlight, dramatic emotional expression, clean bokeh depth of field, vibrant hyper-realistic travel aesthetics",
        listOf("IT FINALLY HAPPENED!", "WE DIDN'T EXPECT THIS", "WORST MISTAKE EVER?", "CAN'T BELIEVE THIS!")
    ),
    GAMING(
        "Gaming",
        "intense dynamic high-octane battle scene, glowing cyan and crimson RGB rim lighting, explosive cinematic atmosphere, volumetric light rays, hyper-detailed photorealistic render",
        listOf("NEW WORLD RECORD!", "UNSTOPPABLE BEAST!", "1V4 CLUTCH GOD!", "THEY BANNED THIS!")
    )
}

data class StrategyResult(
    val imagePrompt: String,
    val textHook: String
)

class ThumbnailEngine(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Step 1: Generate AI Prompt & Text Hook Strategy
     * Calls OpenRouter or Groq, or provides a rich heuristic strategy if no key or offline.
     */
    suspend fun generateStrategy(
        topic: String,
        niche: Niche,
        apiKeys: ApiKeysConfig,
        aspectRatio: AspectRatio
    ): StrategyResult = withContext(Dispatchers.IO) {
        val trimmedTopic = topic.trim().ifEmpty { "Viral ${niche.displayName} Story" }
        val llmKey = apiKeys.llmKey.trim()

        if (llmKey.isNotEmpty()) {
            try {
                val isGroq = llmKey.startsWith("gsk_")
                val url = if (isGroq) {
                    "https://api.groq.com/openai/v1/chat/completions"
                } else {
                    "https://openrouter.ai/api/v1/chat/completions"
                }
                val model = if (isGroq) {
                    "llama-3.3-70b-versatile"
                } else {
                    "meta-llama/llama-3.3-70b-instruct:free"
                }

                val systemPrompt = """
                    You are an expert YouTube Thumbnail Strategist and AI Prompt Engineer.
                    Your goal is to produce:
                    1. 'image_prompt': A photorealistic image description for a 16:9 or vertical background. Include camera angle, lighting (e.g. rim light, dark moody bokeh background), textures, and subject focus.
                    CRITICAL RULE: STRICTLY DO NOT include ANY text, words, labels, letters, or numbers in the image_prompt.
                    2. 'text_hook': A punchy 2 to 4 word curiosity-driven text hook in Hinglish or English that creates extreme FOMO or curiosity.
                    Return ONLY a valid JSON object with keys 'image_prompt' and 'text_hook'.
                """.trimIndent()

                val userMessage = "Topic: '$trimmedTopic', Niche: '${niche.displayName}', Target Resolution: ${aspectRatio.width}x${aspectRatio.height}."

                val jsonPayload = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0.7)
                    put("response_format", JSONObject().put("type", "json_object"))
                    put("messages", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userMessage)
                        })
                    })
                }

                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $llmKey")
                    .addHeader("Content-Type", "application/json")

                if (!isGroq) {
                    requestBuilder.addHeader("HTTP-Referer", "https://artraw.thumbgen")
                    requestBuilder.addHeader("X-Title", "ThumbGen AI")
                }

                val request = requestBuilder
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val respObj = JSONObject(body)
                    val choices = respObj.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val messageObj = choices.getJSONObject(0).optJSONObject("message")
                        val content = messageObj?.optString("content").orEmpty()
                        val parsed = JSONObject(content.trim())
                        val prompt = parsed.optString("image_prompt").trim()
                        val hook = parsed.optString("text_hook").trim()
                        if (prompt.isNotEmpty() && hook.isNotEmpty()) {
                            return@withContext StrategyResult(prompt, hook.take(30))
                        }
                    }
                }
            } catch (e: Exception) {
                // Fall through to heuristic fallback
            }
        }

        // Heuristic fallback strategy
        val cleanPrompt = "Cinematic dramatic close-up of $trimmedTopic, ${niche.promptModifier}, studio rim lighting, dark moody blurred bokeh background, 8k photorealistic, high contrast, vivid colors"
        val fallbackHook = niche.hookKeywords.random()
        return@withContext StrategyResult(cleanPrompt, fallbackHook)
    }

    /**
     * Step 2: Multi-Provider Free Image Generation with Fallback
     * Cloudflare Workers AI -> NVIDIA NIM -> Pollinations.ai -> Offline Procedural Bitmap
     */
    suspend fun generateImage(
        prompt: String,
        aspectRatio: AspectRatio,
        apiKeys: ApiKeysConfig,
        onStatus: (String) -> Unit
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = aspectRatio.width
        val height = aspectRatio.height

        // 1. Try Cloudflare Workers AI (FLUX-1-schnell)
        if (apiKeys.cfAccountId.isNotEmpty() && apiKeys.cfApiToken.isNotEmpty()) {
            try {
                onStatus("Rendering with Cloudflare FLUX-1...")
                val cfUrl = "https://api.cloudflare.com/client/v4/accounts/${apiKeys.cfAccountId}/ai/run/@cf/black-forest-labs/flux-1-schnell"
                val payload = JSONObject().apply {
                    put("prompt", prompt)
                    put("num_steps", 6)
                }
                val request = Request.Builder()
                    .url(cfUrl)
                    .addHeader("Authorization", "Bearer ${apiKeys.cfApiToken}")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val resObj = JSONObject(body).optJSONObject("result")
                    val b64 = resObj?.optString("image").orEmpty()
                    if (b64.isNotEmpty()) {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            return@withContext Bitmap.createScaledBitmap(bmp, width, height, true)
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next provider
            }
        }

        // 2. Try NVIDIA NIM (FLUX.1-dev)
        if (apiKeys.nvidiaKey.isNotEmpty()) {
            try {
                onStatus("Rendering with NVIDIA NIM FLUX.1-dev...")
                val nvUrl = "https://ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-dev"
                val payload = JSONObject().apply {
                    put("prompt", prompt)
                    put("width", width)
                    put("height", height)
                    put("steps", 25)
                    put("seed", 0)
                }
                val request = Request.Builder()
                    .url(nvUrl)
                    .addHeader("Authorization", "Bearer ${apiKeys.nvidiaKey}")
                    .addHeader("Accept", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val resObj = JSONObject(body)
                    val artifacts = resObj.optJSONArray("artifacts")
                    if (artifacts != null && artifacts.length() > 0) {
                        val b64 = artifacts.getJSONObject(0).optString("base64")
                        if (b64.isNotEmpty()) {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                return@withContext Bitmap.createScaledBitmap(bmp, width, height, true)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next provider
            }
        }

        // 3. Try Pollinations.ai FLUX (Free Open Access)
        try {
            onStatus("Rendering with Pollinations FLUX...")
            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
            val polUrl = "https://gen.pollinations.ai/image/$encodedPrompt?model=flux&width=$width&height=$height&nologo=true"
            val reqBuilder = Request.Builder().url(polUrl)
            if (apiKeys.pollinationsKey.isNotEmpty()) {
                reqBuilder.addHeader("Authorization", "Bearer ${apiKeys.pollinationsKey}")
            }
            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        return@withContext Bitmap.createScaledBitmap(bmp, width, height, true)
                    }
                }
            }
        } catch (e: Exception) {
            // Fall through to procedural engine
        }

        // 4. Procedural Studio Background Fallback
        onStatus("Generating studio visual base...")
        return@withContext createProceduralBackground(width, height)
    }

    /**
     * Extracts subject using Google ML Kit Subject Segmentation
     */
    suspend fun removeBackground(inputBitmap: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
            val segmenter = SubjectSegmentation.getClient(options)
            val inputImage = InputImage.fromBitmap(inputBitmap, 0)
            val result = segmenter.process(inputImage).await()
            result.foregroundBitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Step 3: Composite base background + cutout subject (without text).
     * This base composite can be cached so the user can edit text in real time!
     */
    suspend fun createBaseComposite(
        bgBitmap: Bitmap,
        referenceUri: Uri?,
        removeBg: Boolean,
        onStatus: (String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = bgBitmap.width
        val height = bgBitmap.height

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // Draw background
        canvas.drawBitmap(bgBitmap, 0f, 0f, null)

        // Draw subtle dark vignette on left side to guarantee text readability
        val vignettePaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f,
                width * 0.55f, 0f,
                intArrayOf(Color.argb(190, 10, 10, 10), Color.argb(110, 15, 15, 15), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width * 0.65f, height.toFloat(), vignettePaint)

        // If reference image provided, process and place on the right
        if (referenceUri != null) {
            onStatus("Compositing reference cutout...")
            val refBitmap = loadBitmapFromUri(referenceUri, width, height)
            if (refBitmap != null) {
                val subjectBitmap: Bitmap = if (removeBg) {
                    removeBackground(refBitmap) ?: refBitmap
                } else {
                    refBitmap
                }

                // Scale subject cleanly to fit ~85% of thumbnail height
                val targetSubjectHeight = (height * 0.88f).toInt()
                val scale = targetSubjectHeight.toFloat() / subjectBitmap.height.toFloat()
                val targetSubjectWidth = (subjectBitmap.width * scale).toInt()

                val scaledSubject = Bitmap.createScaledBitmap(subjectBitmap, targetSubjectWidth, targetSubjectHeight, true)

                // Position on right-third
                val posX = (width - targetSubjectWidth - (width * 0.04f)).coerceAtLeast(width * 0.40f)
                val posY = (height - targetSubjectHeight).toFloat()

                // Draw rim glow behind subject
                val glowPaint = Paint().apply {
                    color = Color.argb(120, 255, 255, 255)
                    maskFilter = android.graphics.BlurMaskFilter(24f, android.graphics.BlurMaskFilter.Blur.OUTER)
                }
                canvas.drawBitmap(scaledSubject.extractAlpha(), posX, posY, glowPaint)

                // Draw subject cutout
                canvas.drawBitmap(scaledSubject, posX, posY, null)
            }
        }

        resultBitmap
    }

    /**
     * Renders high-CTR Text Hook on top of the base composite.
     * Features: Bold Sans-Serif, thick black stroke outline, drop shadow, yellow or white fill.
     */
    fun renderTextOnComposite(
        baseComposite: Bitmap,
        textHook: String,
        isYellow: Boolean
    ): Bitmap {
        val width = baseComposite.width
        val height = baseComposite.height

        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        canvas.drawBitmap(baseComposite, 0f, 0f, null)

        val cleanHook = textHook.trim().uppercase()
        if (cleanHook.isEmpty()) return finalBitmap

        val words = cleanHook.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val lines = if (words.size <= 2) {
            listOf(cleanHook)
        } else if (words.size == 3) {
            listOf(words[0], "${words[1]} ${words[2]}")
        } else {
            listOf("${words[0]} ${words[1]}", words.subList(2, words.size).joinToString(" "))
        }

        val baseFontSize = (height * 0.125f).coerceIn(48f, 130f)
        val typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = baseFontSize
            this.color = Color.BLACK
            this.style = Paint.Style.STROKE
            this.strokeWidth = baseFontSize * 0.22f
            this.strokeJoin = Paint.Join.ROUND
            this.strokeCap = Paint.Cap.ROUND
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = baseFontSize
            this.color = if (isYellow) Color.parseColor("#FFE600") else Color.WHITE
            this.style = Paint.Style.FILL
            setShadowLayer(baseFontSize * 0.16f, 6f, 8f, Color.argb(220, 0, 0, 0))
        }

        // Draw pill backdrop for maximum contrast
        val startX = width * 0.06f
        var currentY = height * 0.26f
        val lineSpacing = baseFontSize * 1.18f

        for (line in lines) {
            val textBounds = Rect()
            fillPaint.getTextBounds(line, 0, line.length, textBounds)

            val pillRect = RectF(
                startX - 18f,
                currentY - textBounds.height() - 16f,
                startX + textBounds.width() + 32f,
                currentY + 20f
            )
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, 0, 0, 0)
            }
            canvas.drawRoundRect(pillRect, 16f, 16f, pillPaint)

            // Draw outer black stroke
            canvas.drawText(line, startX, currentY, strokePaint)
            // Draw inner vibrant fill
            canvas.drawText(line, startX, currentY, fillPaint)

            currentY += lineSpacing
        }

        return finalBitmap
    }

    /**
     * Saves the final thumbnail directly to the Android Gallery under Pictures/ThumbGen
     */
    suspend fun saveToGallery(bitmap: Bitmap, topic: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val filename = "ThumbGen_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ThumbGen")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri).use { outStream ->
                    if (outStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                return@withContext imageUri
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun loadBitmapFromUri(uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            var scale = 1
            while (options.outWidth / scale > maxWidth * 1.5 || options.outHeight / scale > maxHeight * 1.5) {
                scale *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val secondStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(secondStream, null, decodeOptions)
            secondStream?.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun createProceduralBackground(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.parseColor("#121212"),
                Color.parseColor("#250D11"),
                Color.parseColor("#0F1E2A")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Decorative studio bokeh spots
        val bokehPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, 240, 68, 68)
        }
        canvas.drawCircle(width * 0.75f, height * 0.35f, width * 0.28f, bokehPaint)

        bokehPaint.color = Color.argb(30, 0, 229, 255)
        canvas.drawCircle(width * 0.88f, height * 0.75f, width * 0.22f, bokehPaint)

        return bitmap
    }
}
