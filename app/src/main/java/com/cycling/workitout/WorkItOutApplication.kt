package com.cycling.workitout

import android.app.Application
import com.cycling.workitout.data.database.WorkItOutDatabase
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.repository.DeviceRepository
import com.cycling.workitout.data.strava.HistoryStravaUploader
import com.cycling.workitout.data.strava.StravaRepository
import com.cycling.workitout.logging.PrettyTimberTree
import timber.log.Timber

class WorkItOutApplication : Application() {

    companion object {
        lateinit var database: WorkItOutDatabase
            private set

        lateinit var deviceRepository: DeviceRepository
            private set

        lateinit var themePreferences: ThemePreferences
            private set

        lateinit var stravaRepository: StravaRepository
            private set

        lateinit var historyStravaUploader: HistoryStravaUploader
            private set
    }

    override fun onCreate() {
        super.onCreate()

        Timber.plant(PrettyTimberTree())
        Timber.i("🚴 WorkItOut application started")

        database = WorkItOutDatabase.getDatabase(applicationContext)
        deviceRepository = DeviceRepository(database.savedDeviceDao())
        themePreferences = ThemePreferences(applicationContext)
        stravaRepository = StravaRepository(applicationContext)
        historyStravaUploader = HistoryStravaUploader(
            appContext = applicationContext,
            rideDao = database.completedRideDao(),
            stravaRepository = stravaRepository,
            themePreferences = themePreferences
        )

        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        Timber.i("Firebase ready: project=${com.google.firebase.FirebaseApp.getInstance().options.projectId}, currentUser=${auth.currentUser?.uid ?: "null"}")
    }
}
