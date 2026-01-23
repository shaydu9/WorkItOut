package com.cycling.workitout.data.repository

import com.cycling.workitout.data.database.DeviceProfileCrossRefDao
import com.cycling.workitout.data.database.EquipmentProfileDao
import com.cycling.workitout.data.database.EquipmentProfileEntity
import com.cycling.workitout.data.database.SavedDeviceDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Repository for managing equipment profiles
 */
class ProfileRepository(
    private val profileDao: EquipmentProfileDao,
    private val crossRefDao: DeviceProfileCrossRefDao
) {
    
    companion object {
        const val DEMO_PROFILE_ID = "demo_profile_00000000"
    }
    
    /**
     * Get all profiles
     */
    fun getAllProfiles(): Flow<List<EquipmentProfileEntity>> {
        return profileDao.getAllProfiles()
    }
    
    /**
     * Get active profile
     */
    fun getActiveProfile(): Flow<EquipmentProfileEntity?> {
        return profileDao.getActiveProfile()
    }
    
    /**
     * Get active profile (suspend)
     */
    suspend fun getActiveProfileSync(): EquipmentProfileEntity? {
        return profileDao.getActiveProfileSync()
    }
    
    /**
     * Create a new profile
     */
    suspend fun createProfile(name: String, icon: String = "🚴"): String {
        val profileId = UUID.randomUUID().toString()
        val profile = EquipmentProfileEntity(
            profileId = profileId,
            name = name,
            icon = icon,
            createdTimestamp = System.currentTimeMillis(),
            lastUsedTimestamp = System.currentTimeMillis(),
            isActive = false
        )
        profileDao.insertProfile(profile)
        return profileId
    }
    
    /**
     * Update profile
     */
    suspend fun updateProfile(profileId: String, name: String, icon: String) {
        // Prevent editing of demo profile
        if (isDemoProfile(profileId)) {
            return
        }
        
        profileDao.updateProfile(profileId, name, icon)
    }
    
    /**
     * Set active profile
     */
    suspend fun setActiveProfile(profileId: String) {
        profileDao.setActiveProfile(profileId)
    }
    
    /**
     * Deactivate all profiles (no active profile)
     */
    suspend fun deactivateAllProfiles() {
        profileDao.deactivateAllProfiles()
    }
    
    /**
     * Delete a profile and unassign all its devices
     */
    suspend fun deleteProfile(profileId: String) {
        // Prevent deletion of demo profile
        if (isDemoProfile(profileId)) {
            return
        }
        
        // Delete all device-profile assignments for this profile (handled by CASCADE)
        // Then delete the profile
        profileDao.deleteProfileById(profileId)
    }
    
    /**
     * Get profile by ID
     */
    suspend fun getProfileById(profileId: String): EquipmentProfileEntity? {
        return profileDao.getProfileById(profileId)
    }
    
    /**
     * Create demo profile if it doesn't exist
     */
    suspend fun ensureDemoProfileExists() {
        val existing = profileDao.getProfileById(DEMO_PROFILE_ID)
        if (existing == null) {
            val demoProfile = EquipmentProfileEntity(
                profileId = DEMO_PROFILE_ID,
                name = "Demo Mode",
                icon = "🎮",
                createdTimestamp = System.currentTimeMillis(),
                lastUsedTimestamp = System.currentTimeMillis(),
                isActive = false,
                displayOrder = 999999 // Always at the bottom
            )
            profileDao.insertProfile(demoProfile)
        }
    }
    
    /**
     * Check if a profile is the demo profile
     */
    fun isDemoProfile(profileId: String): Boolean {
        return profileId == DEMO_PROFILE_ID
    }
    
    /**
     * Reorder profiles (excluding demo profile)
     * @param orderedProfileIds List of profile IDs in desired order
     */
    suspend fun reorderProfiles(orderedProfileIds: List<String>) {
        orderedProfileIds.forEachIndexed { index, profileId ->
            if (!isDemoProfile(profileId)) {
                profileDao.updateProfileOrder(profileId, index)
            }
        }
    }
}
