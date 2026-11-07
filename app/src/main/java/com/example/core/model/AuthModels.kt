package com.example.core.model

data class User(
    val id: String,
    val email: String,
    val fullName: String,
    val role: CustomerRole = CustomerRole.CUSTOMER,
    val tenantId: String,
    val mfaEnabled: Boolean = false,
    val createdAt: String
)

enum class CustomerRole {
    CUSTOMER,
    SUPPORT_AGENT,
    ADMINISTRATOR,
    SUPER_ADMINISTRATOR
}

data class Tenant(
    val id: String,
    val name: String,
    val ownerUserId: String,
    val maxAccounts: Int = 10,
    val maxEaInstallations: Int = 25,
    val plan: String = "Enterprise Gold"
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long = 3600,
    val user: User
)

data class ApiSettings(
    val isDemoMode: Boolean = false,
    val customApiBaseUrl: String = "https://xau-pilot-api.onrender.com/api/v1",
    val webSocketUrl: String = "wss://localhost/ws",
    val autoRefreshIntervalSec: Int = 10
)
