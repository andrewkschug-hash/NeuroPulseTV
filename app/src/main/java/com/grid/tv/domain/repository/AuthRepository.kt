package com.grid.tv.domain.repository

import com.grid.tv.domain.model.AuthAccount
import com.grid.tv.domain.model.OAuthProviderId
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<Boolean>

    fun hasSkippedSignIn(): Boolean

    fun setSkippedSignIn(skipped: Boolean)

    suspend fun getCurrentAccount(): AuthAccount?

    suspend fun ensureCloudProfile()

    suspend fun signInWithOAuth(provider: OAuthProviderId)

    suspend fun handleOAuthCallback(url: String): Boolean

    suspend fun signOut()
}
