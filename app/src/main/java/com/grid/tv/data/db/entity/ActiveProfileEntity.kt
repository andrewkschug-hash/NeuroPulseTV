package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_profile")
data class ActiveProfileEntity(
    @PrimaryKey val singletonId: Int = 1,
    val profileId: Long
)
