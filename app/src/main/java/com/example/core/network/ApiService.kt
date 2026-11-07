package com.example.core.network

import com.example.core.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {


    // Developer EA management endpoints.
    @POST("developer/register") suspend fun registerDeveloper(@Body body: Map<String, String>): Response<Map<String, Any>>
    @POST("developer/ea-products") suspend fun createDeveloperEaProduct(@Body body: Map<String, Any>): Response<Map<String, Any>>
    @GET("developer/ea-products") suspend fun getDeveloperEaProducts(): Response<List<Map<String, Any?>>>
    @POST("developer/ea-products/{productId}/performance-account") suspend fun connectPublisherPerformanceAccount(@Path("productId") productId: String, @Body body: Map<String, Any>): Response<Map<String, Any?>>
    @POST("developer/ea-products/{productId}/performance-account/rotate-token") suspend fun rotatePublisherPerformanceToken(@Path("productId") productId: String): Response<Map<String, Any?>>
    @POST("developer/ea-products/{productId}/versions") suspend fun createDeveloperEaVersion(@Path("productId") productId: String, @Body body: Map<String, String>): Response<Map<String, Any?>>
    @Multipart @POST("developer/ea-products/{productId}/versions/{versionId}/upload-binary") suspend fun uploadDeveloperEaBinary(@Path("productId") productId: String, @Path("versionId") versionId: String, @Part file: MultipartBody.Part): Response<Map<String, Any?>>
    @Multipart @POST("developer/ea-products/{productId}/versions/{versionId}/upload-manifest") suspend fun uploadDeveloperEaManifest(@Path("productId") productId: String, @Path("versionId") versionId: String, @Part file: MultipartBody.Part): Response<Map<String, Any?>>
    @POST("developer/ea-products/{productId}/versions/{versionId}/validate") suspend fun validateDeveloperEaVersion(@Path("productId") productId: String, @Path("versionId") versionId: String): Response<Map<String, Any?>>
    @POST("developer/ea-products/{productId}/versions/{versionId}/publish") suspend fun publishDeveloperEaVersion(@Path("productId") productId: String, @Path("versionId") versionId: String): Response<Map<String, Any?>>
    @GET("developer/ea-products/{productId}/versions/{versionId}/preview") suspend fun previewDeveloperEaSettings(@Path("productId") productId: String, @Path("versionId") versionId: String): Response<Map<String, Any?>>

    // Authentication
    @POST("auth/login")
    suspend fun login(@Body body: Map<String, String>): Response<AuthSession>

    @POST("auth/register")
    suspend fun register(@Body body: Map<String, String>): Response<AuthSession>

    @POST("auth/refresh")
    suspend fun refreshSession(@Body body: Map<String, String>): Response<AuthSession>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("users/me")
    suspend fun getCurrentUser(): Response<User>

    // Tenants
    @GET("tenants/current")
    suspend fun getCurrentTenant(): Response<Tenant>

    // Broker Accounts
    @GET("broker-accounts")
    suspend fun getBrokerAccounts(): Response<List<BrokerAccount>>

    @POST("broker-accounts")
    suspend fun addBrokerAccount(@Body account: Map<String, Any>): Response<BrokerAccount>

    @GET("broker-accounts/{id}")
    suspend fun getBrokerAccount(@Path("id") id: String): Response<BrokerAccount>

    @DELETE("broker-accounts/{id}")
    suspend fun deleteBrokerAccount(@Path("id") id: String): Response<Unit>

    @GET("broker-accounts/{id}/risk-profile")
    suspend fun getAccountRiskProfile(@Path("id") id: String): Response<AccountRiskProfile>

    @PUT("broker-accounts/{id}/risk-profile")
    suspend fun updateAccountRiskProfile(@Path("id") id: String, @Body risk: AccountRiskProfile): Response<AccountRiskProfile>

    // EA Marketplace
    @GET("ea-products")
    suspend fun getEaProducts(): Response<List<EaProduct>>

    @GET("ea-products/{id}")
    suspend fun getEaProduct(@Path("id") id: String): Response<EaProduct>

    // Public guest-readable EA performance feed; returns real owner MT5 telemetry only.
    @GET("marketplace/ea-products/{id}/performance")
    suspend fun getEaPublicPerformance(@Path("id") id: String): Response<Map<String, Any?>>

    // EA Licensing
    @GET("ea-licenses")
    suspend fun getLicenses(): Response<List<EaLicense>>

    @POST("ea-licenses/purchase")
    suspend fun purchaseLicense(@Body purchaseRequest: Map<String, String>): Response<EaLicense>

    @POST("ea-licenses/{id}/activate")
    suspend fun activateLicense(@Path("id") licenseId: String): Response<EaLicense>

    // EA Installations
    @GET("ea-installations")
    suspend fun getInstallations(): Response<List<EaInstallation>>

    @POST("ea-installations")
    suspend fun createInstallation(@Body request: Map<String, Any>): Response<EaInstallation>

    @POST("ea-installations/{id}/activate")
    suspend fun activateInstallation(@Path("id") id: String): Response<EaInstallation>

    @POST("ea-installations/{id}/uninstall")
    suspend fun uninstallInstallation(@Path("id") id: String): Response<Unit>

    // Provider-neutral VPS and MT5 hosting adapter
    @POST("hosting/installations/{id}/provision")
    suspend fun provisionInstallationHost(@Path("id") id: String): Response<Map<String, Any>>

    @GET("hosting/installations/{id}/jobs")
    suspend fun getHostingJobs(@Path("id") id: String): Response<List<Map<String, Any?>>>

    // EA Configuration & Remote Inputs
    @GET("ea-installations/{id}/inputs")
    suspend fun getInputDefinitions(@Path("id") id: String): Response<List<ParameterDefinition>>

    @GET("ea-installations/{id}/configuration")
    suspend fun getCurrentConfiguration(@Path("id") id: String): Response<Map<String, Any>>

    @PUT("ea-installations/{id}/configuration")
    suspend fun updateConfiguration(
        @Path("id") id: String,
        @Body values: Map<String, String>
    ): Response<ConfigAuditEntry>

    @GET("ea-installations/{id}/configuration/history")
    suspend fun getConfigurationHistory(@Path("id") id: String): Response<List<ConfigAuditEntry>>

    @GET("ea-installations/{id}/presets")
    suspend fun getPresets(@Path("id") id: String): Response<List<EaPreset>>

    @POST("ea-installations/{id}/presets")
    suspend fun savePreset(@Path("id") id: String, @Body preset: EaPreset): Response<EaPreset>

    @POST("ea-installations/{id}/presets/{preset_id}/apply")
    suspend fun applyPreset(@Path("id") id: String, @Path("preset_id") presetId: String): Response<ConfigAuditEntry>

    // Managed VPS Hosting
    @GET("hosting/managed-vps")
    suspend fun getVpsInstances(): Response<List<VpsInstance>>

    @POST("hosting/managed-vps/{id}/restart")
    suspend fun restartVps(@Path("id") id: String): Response<Unit>

    @POST("hosting/managed-vps/{id}/stop")
    suspend fun stopVps(@Path("id") id: String): Response<Unit>

    // Account Controls & Telemetry
    @POST("accounts/{id}/commands/pause")
    suspend fun pauseAccount(@Path("id") accountId: String): Response<RemoteCommand>

    @POST("accounts/{id}/commands/resume")
    suspend fun resumeAccount(@Path("id") accountId: String): Response<RemoteCommand>

    @POST("accounts/{id}/commands/emergency-stop")
    suspend fun emergencyStopAccount(
        @Path("id") accountId: String,
        @Body policy: Map<String, String>
    ): Response<RemoteCommand>

    @GET("accounts/{id}/positions")
    suspend fun getPositions(@Path("id") accountId: String): Response<List<Position>>

    @GET("accounts/{id}/trades")
    suspend fun getTradesHistory(@Path("id") accountId: String): Response<List<ClosedTrade>>


    // Admin and support API methods
@GET("admin/ea-review/pending") suspend fun getPendingEaReviews(): Response<List<Map<String, Any?>>>

@POST("admin/ea-review/{productId}/approve") suspend fun approveEaReview(@Path("productId") productId: String): Response<Map<String, Any?>>

@POST("admin/ea-review/{productId}/reject") suspend fun rejectEaReview(@Path("productId") productId: String, @Body body: Map<String, String>): Response<Map<String, Any?>>

@POST("auth/admin-users") suspend fun createRestrictedAdmin(@Body body: Map<String, String>): Response<Map<String, Any?>>

@GET("admin/users") suspend fun getPlatformUsers(): Response<List<Map<String, Any?>>>

@GET("admin/administrators") suspend fun getAdministrators(): Response<List<Map<String, Any?>>>

@PATCH("admin/administrators/{userId}/status") suspend fun setAdministratorStatus(@Path("userId") userId: String, @Body body: Map<String, Boolean>): Response<Map<String, Any?>>

@GET("support/conversations") suspend fun getSupportConversations(): Response<List<Map<String, Any?>>>

@POST("support/conversations") suspend fun createSupportConversation(@Body body: Map<String, String>): Response<Map<String, Any?>>

@GET("support/conversations/{conversationId}") suspend fun getSupportConversation(@Path("conversationId") conversationId: String): Response<Map<String, Any?>>

@POST("support/conversations/{conversationId}/messages") suspend fun sendSupportMessage(@Path("conversationId") conversationId: String, @Body body: Map<String, String>): Response<Map<String, Any?>>

@PATCH("support/conversations/{conversationId}/status") suspend fun setSupportStatus(@Path("conversationId") conversationId: String, @Body body: Map<String, String>): Response<Map<String, Any?>>


    // Developer EA management endpoints.
}
