package com.cycling.workitout

import android.app.Application
import com.cycling.workitout.data.database.WorkItOutDatabase
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.repository.DeviceRepository
import com.cycling.workitout.data.repository.ProfileRepository
import com.cycling.workitout.logging.PrettyTimberTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class WorkItOutApplication : Application() {
    
    companion object {
        lateinit var database: WorkItOutDatabase
            private set
        
        lateinit var deviceRepository: DeviceRepository
            private set
        
        lateinit var profileRepository: ProfileRepository
            private set
        
        lateinit var themePreferences: ThemePreferences
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for elegant logging (always enabled for development)
        Timber.plant(PrettyTimberTree())
        Timber.i("🚴 WorkItOut application started")
        
        // Initialize Room database using singleton
        database = WorkItOutDatabase.getDatabase(applicationContext)
        
        // Initialize repositories
        deviceRepository = DeviceRepository(
            database.savedDeviceDao(),
            database.deviceProfileCrossRefDao()
        )
        profileRepository = ProfileRepository(
            database.equipmentProfileDao(),
            database.deviceProfileCrossRefDao()
        )
        
        // Initialize preferences
        themePreferences = ThemePreferences(applicationContext)
        
        // Create demo profile on first launch
        CoroutineScope(Dispatchers.IO).launch {
            profileRepository.ensureDemoProfileExists()
        }
    }
}
