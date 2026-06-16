package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarColor: String,
    val pin: String? = null,
    val isParental: Boolean = false,
    val allowedStartMinutes: Int = 0,
    val allowedEndMinutes: Int = 1439
)
