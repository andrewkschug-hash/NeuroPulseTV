package com.neuropulse.tv.domain.model

data class UserProfile(
    val id: Long,
    val name: String,
    val avatarColor: String,
    val hasPin: Boolean,
    val isParental: Boolean
)
