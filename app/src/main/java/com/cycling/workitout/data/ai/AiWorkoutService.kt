package com.cycling.workitout.data.ai

import com.cycling.workitout.data.PowerZone
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef
import com.cycling.workitout.data.network.anthropic.AnthropicApi
import com.cycling.workitout.data.network.anthropic.dto.Message
import com.cycling.workitout.data.network.anthropic.dto.MessagesRequest
import com.cycling.workitout.data.network.anthropic.dto.SystemBlock
import com.cycling.workitout.data.network.core.NetworkModule
import com.cycling.workitout.ui.home.Difficulty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber
import kotlin.math.roundToInt

// Builds a WorkoutDefinition from Claude's JSON response — two modes: structured or freeform prompt.
class AiWorkoutService(
    private val anthropicApi: AnthropicApi = NetworkModule.anthropicApi
) {

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

    /**
     * Call the LLM up to twice. First attempt uses the plain prompt; if the
     * result fails hard validation (too few intervals, duration drift >10%),
     * the second attempt tacks on an explicit "your last answer was invalid"
     * nudge. Soft repairs (clamping out-of-range values, silent rescaling for
     * drift ≤10%) happen silently with a Timber warning.
     */
    private suspend fun callApi(
        userMessage: String,
        ftp: Int,
        expectedTotalSec: Int?
    ): WorkoutDefinition = withContext(Dispatchers.IO) {
        var lastError: String? = null
        for (attempt in 1..2) {
            val messageForThisAttempt = if (attempt == 1) userMessage else buildString {
                append(userMessage)
                append("\n\nYour previous response was invalid: ")
                append(lastError)
                append("\nReturn a corrected JSON object. The sum of durationSec MUST match the total.")
            }

            val dto = requestDto(messageForThisAttempt)
            if (dto == null) {
                lastError = "response was not valid JSON"
            } else {
                val result = buildWorkoutOrNull(dto, ftp, expectedTotalSec)
                if (result.workout != null) return@withContext result.workout
                lastError = result.rejectionReason
                Timber.tag("AI").w("AI workout attempt $attempt rejected: $lastError")
            }
        }
        throw IllegalStateException("Claude returned an invalid workout twice: $lastError")
    }

    private suspend fun requestDto(userMessage: String): AiWorkoutDto? {
        val request = MessagesRequest(
            model = "claude-sonnet-4-6",
            maxTokens = 2048,
            system = listOf(
                SystemBlock(
                    type = "text",
                    text = systemPrompt
                )
            ),
            messages = listOf(Message(role = "user", content = userMessage))
        )

        val response = try {
            anthropicApi.createMessage(request)
        } catch (e: HttpException) {
            val errorBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            Timber.tag("AI").w(e, "Anthropic request failed: HTTP ${e.code()} body=$errorBody")
            throw e
        }
        val text = response.content.firstOrNull { it.type == "text" }?.text.orEmpty()
        Timber.tag("AI").d("AI raw text: $text")

        val jsonText = extractJsonObject(text) ?: return null
        Timber.tag("AI").d("AI workout JSON: $jsonText")

        return try {
            json.decodeFromString(AiWorkoutDto.serializer(), jsonText)
        } catch (t: Throwable) {
            Timber.tag("AI").w(t, "AI JSON failed to decode")
            null
        }
    }

    // Strips markdown fences and returns the first balanced JSON object from the text.
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

    internal data class BuildResult(val workout: WorkoutDefinition?, val rejectionReason: String?)

    // Clamps out-of-range values (soft), rejects structurally invalid responses (hard — caller retries).
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
                targetPowerPercentFtp = targetPctClamped.toFloat(),
                targetPowerWatts = (targetPctClamped * ftp).roundToInt().coerceAtLeast(40),
                name = i.name,
                zone = parseZone(i.zone)
            )
        }.toMutableList()
        if (clampedTarget > 0) Timber.tag("AI").w("AI validation: clamped $clampedTarget out-of-range interval targetPct values")
        if (clampedDuration > 0) Timber.tag("AI").w("AI validation: clamped $clampedDuration out-of-range interval durations")

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
                    Timber.tag("AI").w("AI validation: rescaling ${sum}s → ${expectedTotalSec}s (drift ${(driftRatio * 100).roundToInt()}%)")
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

    companion object {
        internal const val MIN_TARGET_PCT = 0.30
        internal const val MAX_TARGET_PCT = 1.70
        internal const val MIN_INTERVAL_SEC = 10
        internal const val MAX_INTERVAL_SEC = 3600
        internal const val MIN_INTERVALS = 3
        internal const val HARD_DRIFT_RATIO = 0.10
    }
}
