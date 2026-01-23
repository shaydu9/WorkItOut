package com.cycling.workitout.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for equipment profiles
 */
@Dao
interface EquipmentProfileDao {
    
    /**
     * Get all profiles as a Flow (reactive updates)
     * Ordered by displayOrder (custom order), with demo profile always last
     */
    @Query("SELECT * FROM equipment_profiles ORDER BY displayOrder ASC, createdTimestamp ASC")
    fun getAllProfiles(): Flow<List<EquipmentProfileEntity>>
    
    /**
     * Get active profile
     */
    @Query("SELECT * FROM equipment_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfile(): Flow<EquipmentProfileEntity?>
    
    /**
     * Get active profile (suspend)
     */
    @Query("SELECT * FROM equipment_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfileSync(): EquipmentProfileEntity?
    
    /**
     * Get profile by ID
     */
    @Query("SELECT * FROM equipment_profiles WHERE profileId = :profileId")
    suspend fun getProfileById(profileId: String): EquipmentProfileEntity?
    
    /**
     * Insert a profile
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: EquipmentProfileEntity)
    
    /**
     * Update profile name and icon
     */
    @Query("UPDATE equipment_profiles SET name = :name, icon = :icon WHERE profileId = :profileId")
    suspend fun updateProfile(profileId: String, name: String, icon: String)
    
    /**
     * Set active profile (deactivate all others first)
     */
    @Transaction
    suspend fun setActiveProfile(profileId: String) {
        deactivateAllProfiles()
        activateProfile(profileId)
        updateLastUsed(profileId, System.currentTimeMillis())
    }
    
    @Query("UPDATE equipment_profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()
    
    @Query("UPDATE equipment_profiles SET isActive = 1 WHERE profileId = :profileId")
    suspend fun activateProfile(profileId: String)
    
    @Query("UPDATE equipment_profiles SET lastUsedTimestamp = :timestamp WHERE profileId = :profileId")
    suspend fun updateLastUsed(profileId: String, timestamp: Long)
    
    /**
     * Delete a profile
     */
    @Delete
    suspend fun deleteProfile(profile: EquipmentProfileEntity)
    
    /**
     * Delete profile by ID (also unassigns all devices)
     */
    @Query("DELETE FROM equipment_profiles WHERE profileId = :profileId")
    suspend fun deleteProfileById(profileId: String)
    
    /**
     * Update profile display order
     */
    @Query("UPDATE equipment_profiles SET displayOrder = :order WHERE profileId = :profileId")
    suspend fun updateProfileOrder(profileId: String, order: Int)
}
