package com.neuropulse.tv.domain.model

data class UserProfile(
    val id: Long,
    val name: String,
    val avatarColor: String,
    val hasPin: Boolean,
    val isParental: Boolean,
    val allowedStartMinutes: Int = 0,
    val allowedEndMinutes: Int = 1439
)
