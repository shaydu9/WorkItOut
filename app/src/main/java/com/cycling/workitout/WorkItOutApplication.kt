package com.cycling.workitout

import android.app.Application
import com.cycling.workitout.data.auth.AuthRepository
import com.cycling.workitout.data.database.WorkItOutDatabase
import com.cycling.workitout.data.firestore.RideRepository
import com.cycling.workitout.data.firestore.UserProfileRepository
import com.cycling.workitout.data.firestore.WorkoutRepository
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.repository.DeviceRepository
import com.cycling.workitout.data.strava.HistoryStravaUploader
import com.cycling.workitout.data.strava.StravaRepository
import com.cycling.workitout.logging.CrashlyticsTree
import com.cycling.workitout.logging.PrettyTimberTree
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

class WorkItOutApplication : Application() {

    companion object {
        lateinit var database: WorkItOutDatabase
            private set

        lateinit var authRepository: AuthRepository
            private set

        lateinit var deviceRepository: DeviceRepository
            private set

        lateinit var themePreferences: ThemePreferences
            private set

        lateinit var stravaRepository: StravaRepository
            private set

        lateinit var historyStravaUploader: HistoryStravaUploader
            private set

        lateinit var rideRepository: RideRepository
            private set

        lateinit var workoutRepository: WorkoutRepository
            private set

        lateinit var userProfileRepository: UserProfileRepository
            private set
    }

    override fun onCreate() {
        super.onCreate()

        Timber.plant(PrettyTimberTree())
        if (!BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            Timber.plant(CrashlyticsTree())
        }
        Timber.i("🚴 WorkItOut application started")

        database = WorkItOutDatabase.getDatabase(applicationContext)
        authRepository = AuthRepository()
        deviceRepository = DeviceRepository(database.savedDeviceDao())
        themePreferences = ThemePreferences(applicationContext)
        stravaRepository = StravaRepository(applicationContext)
        rideRepository = RideRepository()
        historyStravaUploader = HistoryStravaUploader(
            appContext = applicationContext,
            rideRepository = rideRepository,
            stravaRepository = stravaRepository,
            themePreferences = themePreferences
        )
        workoutRepository = WorkoutRepository()
        userProfileRepository = UserProfileRepository(themePreferences = themePreferences)

        Timber.i("Auth ready: currentUser=${authRepository.currentUser.value}")
    }
}
