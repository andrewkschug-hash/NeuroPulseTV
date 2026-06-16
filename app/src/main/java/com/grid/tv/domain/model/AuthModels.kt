package com.grid.tv.domain.model

data class AuthAccount(
    val userId: String,
    val email: String?,
    val displayName: String?,
    val avatarUrl: String?
)

enum class OAuthProviderId(val providerName: String, val buttonLabel: String) {
    Google(providerName = "google", buttonLabel = "Continue with Google")
    // Future: Apple("apple", "Continue with Apple"), Discord("discord", "Continue with Discord")
}
