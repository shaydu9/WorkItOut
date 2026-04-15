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
    entities = [SavedDeviceEntity::class],
    version = 4,
    exportSchema = false
)
abstract class WorkItOutDatabase : RoomDatabase() {

    abstract fun savedDeviceDao(): SavedDeviceDao

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

        fun getDatabase(context: Context): WorkItOutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkItOutDatabase::class.java,
                    "workitout_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
