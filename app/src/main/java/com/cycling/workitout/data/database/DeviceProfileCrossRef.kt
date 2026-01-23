package com.cycling.workitout.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table for many-to-many relationship between devices and profiles
 * A device can be assigned to multiple profiles
 */
@Entity(
    tableName = "device_profile_cross_ref",
    primaryKeys = ["macAddress", "profileId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedDeviceEntity::class,
            parentColumns = ["macAddress"],
            childColumns = ["macAddress"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EquipmentProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["macAddress"]), Index(value = ["profileId"])]
)
data class DeviceProfileCrossRef(
    val macAddress: String,
    val profileId: String
)
