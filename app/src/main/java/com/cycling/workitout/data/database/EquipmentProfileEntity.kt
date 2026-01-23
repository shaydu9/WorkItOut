package com.cycling.workitout.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Database entity for equipment profiles (bike setups)
 */
@Entity(tableName = "equipment_profiles")
data class EquipmentProfileEntity(
    @PrimaryKey
    val profileId: String,                  // Unique ID (UUID)
    val name: String,                       // "Specialized Aethos", "Indoor Setup"
    val icon: String = "🚴",                // Emoji icon
    val createdTimestamp: Long,             // When created
    val lastUsedTimestamp: Long,            // When last selected
    val isActive: Boolean = false,          // Currently active profile
    val displayOrder: Int = 0               // Custom display order (lower = higher in list)
)

/**
 * Predefined profile templates
 */
object ProfileTemplates {
    const val ROAD_BIKE = "🚴 Road Bike"
    const val INDOOR_TRAINER = "🏠 Indoor Trainer"
    const val MOUNTAIN_BIKE = "🚵 Mountain Bike"
}
