package com.cycling.workitout.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

// Forwards Timber W/E to Crashlytics. Errors with throwables become non-fatals;
// errors without throwables and warnings become breadcrumb logs so they show up
// as context on the next real crash.
class CrashlyticsTree : Timber.Tree() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.WARN
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val prefix = if (tag != null) "[$tag] " else ""
        crashlytics.log("$prefix$message")

        if (priority == Log.ERROR) {
            crashlytics.recordException(t ?: RuntimeException(message))
        }
    }
}
