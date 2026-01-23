package com.cycling.workitout.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main Room database for WorkItOut app
 */
@Database(
    entities = [
        SavedDeviceEntity::class,
        EquipmentProfileEntity::class,
        DeviceProfileCrossRef::class
    ],
    version = 3,
    exportSchema = false
)
abstract class WorkItOutDatabase : RoomDatabase() {
    
    abstract fun savedDeviceDao(): SavedDeviceDao
    abstract fun equipmentProfileDao(): EquipmentProfileDao
    abstract fun deviceProfileCrossRefDao(): DeviceProfileCrossRefDao
    
    companion object {
        @Volatile
        private var INSTANCE: WorkItOutDatabase? = null
        
        /**
         * Migration from version 1 to 2: Add displayOrder to equipment_profiles
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE equipment_profiles ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        /**
         * Migration from version 2 to 3: 
         * Remove profileId from saved_devices and create junction table
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create junction table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS device_profile_cross_ref (
                        macAddress TEXT NOT NULL,
                        profileId TEXT NOT NULL,
                        PRIMARY KEY(macAddress, profileId),
                        FOREIGN KEY(macAddress) REFERENCES saved_devices(macAddress) ON DELETE CASCADE,
                        FOREIGN KEY(profileId) REFERENCES equipment_profiles(profileId) ON DELETE CASCADE
                    )
                """)
                
                database.execSQL("CREATE INDEX IF NOT EXISTS index_device_profile_cross_ref_macAddress ON device_profile_cross_ref(macAddress)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_device_profile_cross_ref_profileId ON device_profile_cross_ref(profileId)")
                
                // Migrate existing profileId data to junction table
                database.execSQL("""
                    INSERT INTO device_profile_cross_ref (macAddress, profileId)
                    SELECT macAddress, profileId FROM saved_devices 
                    WHERE profileId IS NOT NULL
                """)
                
                // Create new saved_devices table without profileId
                database.execSQL("""
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
                
                // Copy data
                database.execSQL("""
                    INSERT INTO saved_devices_new 
                    SELECT macAddress, manufacturerName, customName, deviceType, 
                           firstConnectedTimestamp, lastConnectedTimestamp, connectionCount
                    FROM saved_devices
                """)
                
                // Drop old table and rename new one
                database.execSQL("DROP TABLE saved_devices")
                database.execSQL("ALTER TABLE saved_devices_new RENAME TO saved_devices")
            }
        }
        
        fun getDatabase(context: Context): WorkItOutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkItOutDatabase::class.java,
                    "workitout_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
