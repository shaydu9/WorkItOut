package com.cycling.workitout.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main Room database for WorkItOut app.
 *
 * v4 dropped the profiles/equipment_profiles tables — the app no longer
 * has the profile concept. Saved devices are kept so paired trainer + HR
 * auto-reconnect on startup.
 */
@Database(
    entities = [SavedDeviceEntity::class, CompletedRideEntity::class, SavedWorkoutEntity::class],
    version = 7,
    exportSchema = false
)
abstract class WorkItOutDatabase : RoomDatabase() {

    abstract fun savedDeviceDao(): SavedDeviceDao
    abstract fun completedRideDao(): CompletedRideDao
    abstract fun savedWorkoutDao(): SavedWorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: WorkItOutDatabase? = null

        /** v1 → v2: add displayOrder to equipment_profiles. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE equipment_profiles ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3: split profileId off of saved_devices into a junction table. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS device_profile_cross_ref (
                        macAddress TEXT NOT NULL,
                        profileId TEXT NOT NULL,
                        PRIMARY KEY(macAddress, profileId),
                        FOREIGN KEY(macAddress) REFERENCES saved_devices(macAddress) ON DELETE CASCADE,
                        FOREIGN KEY(profileId) REFERENCES equipment_profiles(profileId) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_device_profile_cross_ref_macAddress ON device_profile_cross_ref(macAddress)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_device_profile_cross_ref_profileId ON device_profile_cross_ref(profileId)")
                db.execSQL("""
                    INSERT INTO device_profile_cross_ref (macAddress, profileId)
                    SELECT macAddress, profileId FROM saved_devices
                    WHERE profileId IS NOT NULL
                """)
                db.execSQL("""
                    CREATE TABLE saved_devices_new (
                        macAddress TEXT PRIMARY KEY NOT NULL,
                        manufacturerName TEXT NOT NULL,
                        customName TEXT,
                        deviceType TEXT NOT NULL,
                        firstConnectedTimestamp INTEGER NOT NULL,
                        lastConnectedTimestamp INTEGER NOT NULL,
                        connectionCount INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO saved_devices_new
                    SELECT macAddress, manufacturerName, customName, deviceType,
                           firstConnectedTimestamp, lastConnectedTimestamp, connectionCount
                    FROM saved_devices
                """)
                db.execSQL("DROP TABLE saved_devices")
                db.execSQL("ALTER TABLE saved_devices_new RENAME TO saved_devices")
            }
        }

        /**
         * v3 → v4: profiles are gone. Drop equipment_profiles and
         * device_profile_cross_ref. saved_devices stays as-is.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS device_profile_cross_ref")
                db.execSQL("DROP TABLE IF EXISTS equipment_profiles")
            }
        }

        /** v4 → v5: add completed_rides table for workout history. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS completed_rides (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        startedAtMillis INTEGER NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        avgPowerWatts INTEGER NOT NULL,
                        maxPowerWatts INTEGER NOT NULL,
                        avgHeartRate INTEGER NOT NULL,
                        maxHeartRate INTEGER NOT NULL,
                        avgCadence INTEGER NOT NULL,
                        normalizedPowerWatts INTEGER NOT NULL,
                        ftpWatts INTEGER NOT NULL,
                        dataPointsJson TEXT NOT NULL
                    )
                """)
            }
        }

        /**
         * v6 → v7: track Strava upload per ride. Two new columns on completed_rides:
         *   - stravaActivityId   — the activity ID Strava returned (null = never uploaded)
         *   - stravaUploadedAtMillis — wall-clock timestamp of a successful upload
         * Both nullable so existing rows survive untouched.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completed_rides ADD COLUMN stravaActivityId INTEGER")
                db.execSQL("ALTER TABLE completed_rides ADD COLUMN stravaUploadedAtMillis INTEGER")
            }
        }

        /** v5 → v6: add saved_workouts table for the workout library. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS saved_workouts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workoutId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        totalDurationSeconds INTEGER NOT NULL,
                        savedAtMillis INTEGER NOT NULL,
                        intervalsJson TEXT NOT NULL
                    )
                """)
            }
        }

        fun getDatabase(context: Context): WorkItOutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkItOutDatabase::class.java,
                    "workitout_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    // IMPORTANT: do NOT add fallbackToDestructiveMigration() here.
                    // It silently wipes the user's ride history (completed_rides) on any
                    // schema bump without a matching migration. Every version jump must
                    // have an explicit Migration registered above.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
