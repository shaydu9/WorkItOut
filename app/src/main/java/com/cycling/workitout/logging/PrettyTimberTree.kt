package com.cycling.workitout.logging

import android.util.Log
import timber.log.Timber

// Timber tree that prefixes tags with component emojis and log-level icons.
class PrettyTimberTree : Timber.DebugTree() {

    companion object {
        private const val CALL_STACK_INDEX = 5
        private const val MAX_TAG_LENGTH = 23
        private const val MAX_LOG_LENGTH = 4000

        private const val COLOR_VERBOSE = "[37m"
        private const val COLOR_DEBUG = "[36m"
        private const val COLOR_INFO = "[32m"
        private const val COLOR_WARN = "[33m"
        private const val COLOR_ERROR = "[31m"
        private const val COLOR_RESET = "[0m"

        private const val EMOJI_VERBOSE = "💬"
        private const val EMOJI_DEBUG = "🔍"
        private const val EMOJI_INFO = "✓"
        private const val EMOJI_WARN = "⚠️"
        private const val EMOJI_ERROR = "❌"

        private val componentEmojis = mapOf(
            "BleManager" to "📡",
            "MockDataGenerator" to "🎮",
            "SettingsViewModel" to "⚙️",
            "HomeViewModel" to "🏠",
            "FirstRunPairingVM" to "🔗",
            "WorkoutViewModel" to "🏋️",
            "AiWorkoutService" to "🤖",
            "DeviceRepository" to "💾",
            "WorkItOutDatabase" to "🗄️"
        )
    }

    override fun createStackElementTag(element: StackTraceElement): String {
        val tag = super.createStackElementTag(element) ?: "WorkItOut"
        val emoji = componentEmojis.entries.find {
            tag.contains(it.key, ignoreCase = true)
        }?.value ?: ""
        val full = if (emoji.isNotEmpty()) "$emoji $tag" else tag
        return full.take(MAX_TAG_LENGTH)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val emoji = when (priority) {
            Log.VERBOSE -> EMOJI_VERBOSE
            Log.DEBUG -> EMOJI_DEBUG
            Log.INFO -> EMOJI_INFO
            Log.WARN -> EMOJI_WARN
            Log.ERROR -> EMOJI_ERROR
            else -> ""
        }
        val formattedMessage = "$emoji $message"

        if (formattedMessage.length > MAX_LOG_LENGTH) {
            val chunks = formattedMessage.chunked(MAX_LOG_LENGTH)
            chunks.forEachIndexed { index, chunk ->
                val prefix = if (index > 0) "   ↳ " else ""
                super.log(priority, tag, prefix + chunk, null)
            }
            if (t != null) {
                super.log(priority, tag, "", t)
            }
        } else {
            super.log(priority, tag, formattedMessage, t)
        }
    }
}
