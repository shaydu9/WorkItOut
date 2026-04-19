package com.cycling.workitout.data.ai

import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef
import com.cycling.workitout.ui.home.Difficulty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Calls the Anthropic Messages API to generate a structured cycling workout.
 * Two entry points:
 *  - [generateStructured] — duration + difficulty + FTP
 *  - [generateFromPrompt] — freeform user description
 */
class AiWorkoutService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val systemPrompt = """
        You are a cycling coach generating ERG-mode indoor trainer workouts.
        You output ONLY a single JSON object — no prose, no markdown fences.

        Schema:
        {
          "name": "<short workout name>",
          "description": "<one sentence>",
          "intervals": [
            { "name": "<interval name>", "durationSec": <int>, "targetPct": <float 0.40-1.50>, "zone": "Z1|Z2|Z3|Z4|Z5|Z6" }
          ]
        }

        Rules:
        - targetPct is a fraction of FTP (1.00 = FTP). Recovery is typically 0.45-0.55, endurance 0.60-0.75,
          tempo 0.76-0.87, sweet spot 0.88-0.93, threshold 0.95-1.05, VO2 1.10-1.20, anaerobic 1.25-1.50.
        - Always start with a warmup interval (~10-15% of total time) and end with a cooldown (~10% of total time).
        - The sum of all durationSec values MUST equal the requested total duration in seconds.
        - Map each interval to the correct zone label (Z1=recovery, Z2=endurance, Z3=tempo, Z4=threshold/sweet spot,
          Z5=VO2 max, Z6=anaerobic).
        - Keep interval names short and descriptive (e.g. "Sweet Spot 1", "Recovery", "VO2 #3").
        - Output JSON only.
    """.trimIndent()

    suspend fun generateStructured(
        durationMinutes: Int,
        difficulty: Difficulty,
        ftp: Int
    ): WorkoutDefinition {
        val difficultyHint = when (difficulty) {
            Difficulty.EASY -> "Easy aerobic ride — mostly Z2 endurance with light tempo touches."
            Difficulty.MODERATE -> "Moderate — sweet spot or tempo focus, ~88-93% FTP work."
            Difficulty.HARD -> "Hard threshold session — repeats around 100-105% FTP."
            Difficulty.VO2 -> "VO2 max session — 3-5 minute hard efforts at 110-120% FTP with equal recovery."
        }
        val userMessage = """
            Generate a ${durationMinutes}-minute indoor trainer workout.
            Difficulty: ${difficulty.label}. $difficultyHint
            My FTP is ${ftp}W. Total duration must equal exactly ${durationMinutes * 60} seconds.
        """.trimIndent()

        return callApi(userMessage, ftp, expectedTotalSec = durationMinutes * 60)
    }

    suspend fun generateFromPrompt(
        userPrompt: String,
        ftp: Int
    ): WorkoutDefinition {
        val userMessage = """
            My FTP is ${ftp}W. Build me a workout matching this request:
            "$userPrompt"

            Pick an appropriate total duration if I didn't specify one (default to 45 minutes).
            Remember: warmup, cooldown, and the sum of durationSec must match the total you choose.
        """.trimIndent()

        return callApi(userMessage, ftp, expectedTotalSec = null)
    }

    private suspend fun callApi(
        userMessage: String,
        ftp: Int,
        expectedTotalSec: Int?
    ): WorkoutDefinition = withContext(Dispatchers.IO) {
        val apiKey = readApiKey()
        require(apiKey.isNotBlank() && !apiKey.contains("PASTE_YOUR_KEY")) {
            "ANTHROPIC_API_KEY is not set in local.properties"
        }

        // Up to two tries: first attempt at the given prompt, second attempt tacks on
        // an explicit "your last answer was invalid" nudge. Hard validation failures
        // (too few intervals, drift >10%) trigger the retry; soft fixes (minor scale,
        // per-interval clamp) happen silently with a Timber warning.
        var lastError: String? = null
        for (attempt in 1..2) {
            val messageForThisAttempt = if (attempt == 1) userMessage else buildString {
                append(userMessage)
                append("\n\nYour previous response was invalid: ")
                append(lastError)
                append("\nReturn a corrected JSON object. The sum of durationSec MUST match the total.")
            }

            val dto = requestDto(apiKey, messageForThisAttempt)
            if (dto == null) {
                lastError = "response was not valid JSON"
            } else {
                val result = buildWorkoutOrNull(dto, ftp, expectedTotalSec)
                if (result.workout != null) return@withContext result.workout
                lastError = result.rejectionReason
                Timber.w("AI workout attempt $attempt rejected: $lastError")
            }
        }
        throw IllegalStateException("Claude returned an invalid workout twice: $lastError")
    }

    private fun requestDto(apiKey: String, userMessage: String): AiWorkoutDto? {
        val body = buildJsonObject {
            put("model", "claude-sonnet-4-5")  // bump to claude-sonnet-4-6 once verified available
            put("max_tokens", 2048)
            putJsonArray("system") {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", systemPrompt)
                    putJsonObject("cache_control") { put("type", "ephemeral") }
                })
            }
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            }
        }.toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Timber.e("Anthropic API error ${response.code}: $responseBody")
                throw IllegalStateException("Claude API ${response.code}: ${response.message}")
            }
            val text = extractText(responseBody)
            Timber.d("AI raw text: $text")
            val jsonText = extractJsonObject(text) ?: return null
            Timber.d("AI workout JSON: $jsonText")
            return try {
                json.decodeFromString(AiWorkoutDto.serializer(), jsonText)
            } catch (t: Throwable) {
                Timber.w(t, "AI JSON failed to decode")
                null
            }
        }
    }

    private fun extractText(responseBody: String): String {
        val root = json.parseToJsonElement(responseBody) as JsonObject
        val content = root["content"] as JsonArray
        val first = content.first() as JsonObject
        return (first["text"] as JsonPrimitive).content
    }

    /**
     * Extract the first balanced JSON object from arbitrary text.
     * Handles markdown fences, leading prose, and trailing commentary.
     */
    private fun extractJsonObject(raw: String): String? {
        val s = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until s.length) {
            val c = s[i]
            if (escape) { escape = false; continue }
            if (c == '\\') { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return s.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /** Outcome of validation: either a workout, or a human-readable rejection reason. */
    internal data class BuildResult(val workout: WorkoutDefinition?, val rejectionReason: String?)

    /**
     * Validate the DTO and either build a [WorkoutDefinition] or explain why we can't.
     *
     * Soft repairs (logged, not rejected):
     *  - targetPct clamped to [MIN_TARGET_PCT, MAX_TARGET_PCT] per interval.
     *  - per-interval durationSec clamped to [MIN_INTERVAL_SEC, MAX_INTERVAL_SEC].
     *  - Total-duration drift ≤ SOFT_DRIFT_RATIO is silently rescaled to hit the target.
     *
     * Hard rejections (caller retries):
     *  - Fewer than MIN_INTERVALS intervals.
     *  - Drift > HARD_DRIFT_RATIO from the requested total.
     */
    internal fun buildWorkoutOrNull(
        dto: AiWorkoutDto,
        ftp: Int,
        expectedTotalSec: Int?
    ): BuildResult {
        if (dto.intervals.size < MIN_INTERVALS) {
            return BuildResult(null, "only ${dto.intervals.size} intervals (need at least $MIN_INTERVALS)")
        }

        var clampedTarget = 0
        var clampedDuration = 0
        val intervals = dto.intervals.map { i ->
            val targetPctClamped = i.targetPct.coerceIn(MIN_TARGET_PCT, MAX_TARGET_PCT)
            if (targetPctClamped != i.targetPct) clampedTarget++

            val durClamped = i.durationSec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
            if (durClamped != i.durationSec) clampedDuration++

            WorkoutIntervalDef(
                durationSeconds = durClamped,
                targetPowerWatts = (targetPctClamped * ftp).roundToInt().coerceAtLeast(40),
                name = i.name,
                zone = parseZone(i.zone)
            )
        }.toMutableList()
        if (clampedTarget > 0) Timber.w("AI validation: clamped $clampedTarget out-of-range interval targetPct values")
        if (clampedDuration > 0) Timber.w("AI validation: clamped $clampedDuration out-of-range interval durations")

        // Duration-sum reconciliation (only when the caller asked for a specific total).
        if (expectedTotalSec != null) {
            val sum = intervals.sumOf { it.durationSeconds }
            if (sum <= 0) return BuildResult(null, "all intervals had zero duration")

            val driftRatio = kotlin.math.abs(sum - expectedTotalSec).toDouble() / expectedTotalSec
            when {
                driftRatio > HARD_DRIFT_RATIO -> {
                    return BuildResult(null, "total duration ${sum}s differs from requested ${expectedTotalSec}s by ${(driftRatio * 100).roundToInt()}%")
                }
                sum != expectedTotalSec -> {
                    Timber.w("AI validation: rescaling ${sum}s → ${expectedTotalSec}s (drift ${(driftRatio * 100).roundToInt()}%)")
                    val scale = expectedTotalSec.toDouble() / sum
                    for (idx in intervals.indices) {
                        intervals[idx] = intervals[idx].copy(
                            durationSeconds = (intervals[idx].durationSeconds * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                    val newSum = intervals.sumOf { it.durationSeconds }
                    val drift = expectedTotalSec - newSum
                    if (drift != 0) {
                        val last = intervals.last()
                        intervals[intervals.size - 1] = last.copy(
                            durationSeconds = (last.durationSeconds + drift).coerceAtLeast(1)
                        )
                    }
                }
            }
        }

        val workout = WorkoutDefinition(
            id = "ai_${System.currentTimeMillis()}",
            name = dto.name,
            description = dto.description,
            intervals = intervals
        )
        return BuildResult(workout, null)
    }

    companion object {
        internal const val MIN_TARGET_PCT = 0.30
        internal const val MAX_TARGET_PCT = 1.70
        internal const val MIN_INTERVAL_SEC = 10
        internal const val MAX_INTERVAL_SEC = 3600
        internal const val MIN_INTERVALS = 3
        internal const val HARD_DRIFT_RATIO = 0.10   // >10% drift triggers a retry; anything smaller is silently rescaled
    }

    /**
     * Read ANTHROPIC_API_KEY from BuildConfig via reflection.
     * Reflection bypasses the Kotlin static-final-String inlining trap: the value
     * read here is always whatever the freshly-generated BuildConfig.class holds,
     * so changing local.properties + a normal incremental build picks it up
     * without needing a clean.
     */
    private fun readApiKey(): String {
        return try {
            val cls = Class.forName("com.cycling.workitout.BuildConfig")
            cls.getField("ANTHROPIC_API_KEY").get(null) as? String ?: ""
        } catch (t: Throwable) {
            Timber.e(t, "Failed to read ANTHROPIC_API_KEY from BuildConfig")
            ""
        }
    }

    private fun parseZone(label: String): PowerZone {
        return when (label.trim().uppercase()) {
            "Z1" -> PowerZone.Z1_RECOVERY
            "Z2" -> PowerZone.Z2_ENDURANCE
            "Z3" -> PowerZone.Z3_TEMPO
            "Z4" -> PowerZone.Z4_THRESHOLD
            "Z5" -> PowerZone.Z5_VO2MAX
            "Z6" -> PowerZone.Z6_ANAEROBIC
            else -> PowerZone.Z2_ENDURANCE
        }
    }
}
