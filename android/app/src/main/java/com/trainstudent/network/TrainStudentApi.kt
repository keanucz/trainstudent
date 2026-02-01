package com.trainstudent.network

import com.trainstudent.model.*
import kotlinx.serialization.Serializable
import retrofit2.http.*

interface TrainStudentApi {

    // ============================================
    // AUTH ENDPOINTS
    // ============================================
    
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse
    
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    
    @POST("api/auth/verify")
    suspend fun verifyEmail(@Body request: VerifyEmailRequest): VerifyEmailResponse
    
    @GET("api/auth/me")
    suspend fun getCurrentUser(@Header("Authorization") token: String): UserResponse
    
    @PUT("api/auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): UpdateProfileResponse
    
    // ============================================
    // CASHBACK ENDPOINTS
    // ============================================
    
    @GET("api/cashback/providers")
    suspend fun getCashbackProviders(): CashbackProvidersResponse
    
    @GET("api/cashback/operator/{code}")
    suspend fun getCashbackForOperator(@Path("code") operatorCode: String): OperatorCashbackResponse
    
    @POST("api/cashback/link-account")
    suspend fun linkCashbackAccount(
        @Header("Authorization") token: String,
        @Body request: LinkCashbackRequest
    ): LinkCashbackResponse
    
    @GET("api/cashback/santander/offer")
    suspend fun getSantanderOffer(): SantanderOfferResponse

    // ============================================
    // SEARCH ENDPOINTS
    // ============================================

    @POST("api/search")
    suspend fun searchDiscounts(@Body request: SearchRequest): SearchResponse

    @POST("api/codes/northern")
    suspend fun generateNorthernCode(@Body request: NorthernCodeRequest): NorthernCodeResponse

    @GET("api/offers")
    suspend fun getOffers(
        @Query("operator") operator: String? = null,
        @Query("railcard") railcard: String? = null,
        @Query("discountType") discountType: String? = null
    ): OffersResponse

    @POST("api/avanti/search")
    suspend fun searchAvantiUnidays(@Body request: AvantiSearchRequest): AvantiSearchResponse

    @POST("api/grandcentral/student")
    suspend fun submitGrandCentralStudent(@Body request: GrandCentralRequest): GrandCentralResponse

    @GET("api/trains/live")
    suspend fun getLiveTrains(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("datetime") datetime: String? = null
    ): LiveTrainsResponse
}

@Serializable
data class SearchRequest(
    val from: String,
    val to: String,
    val datetime: String,
    val railcard: String? = null
)

@Serializable
data class SearchResponse(
    val bookingPlans: List<BookingPlan>,
    val cheapestIndex: Int
)

@Serializable
data class NorthernCodeRequest(
    val firstName: String,
    val lastName: String,
    val email: String
)

@Serializable
data class NorthernCodeResponse(
    val code: String,
    val expires: String,
    val message: String
)

@Serializable
data class OffersResponse(
    val offers: List<Offer>
)

@Serializable
data class Offer(
    val id: String,
    val operator: String,
    val title: String,
    val description: String,
    val discountPercent: Int,
    val discountType: String,
    val requiredRailcard: String?,
    val validFrom: String,
    val validTo: String,
    val link: String
)

@Serializable
data class AvantiSearchRequest(
    val from: String,
    val fromNlc: Int,
    val to: String,
    val toNlc: Int,
    val datetime: String,
    val adults: Int = 1,
    val children: Int = 0,
    val useUnidays: Boolean = true
)

@Serializable
data class AvantiSearchResponse(
    val fares: List<AvantiFare>,
    val unidaysApplied: Boolean
)

@Serializable
data class AvantiFare(
    val price: Double,
    val ticketType: String,
    val ticketClass: String,
    val departureTime: String,
    val arrivalTime: String,
    val duration: String,
    val changes: Int
)

@Serializable
data class GrandCentralRequest(
    val firstName: String,
    val lastName: String,
    val email: String // Must be .ac.uk
)

@Serializable
data class GrandCentralResponse(
    val status: String,
    val message: String
)

@Serializable
data class LiveTrainsResponse(
    val services: List<TrainService>
)

// ============================================
// AUTH DATA CLASSES
// ============================================

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String
)

@Serializable
data class RegisterResponse(
    val status: String,
    val message: String,
    val email: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val userId: String,
    val expiresAt: String
)

@Serializable
data class VerifyEmailRequest(
    val email: String,
    val code: String
)

@Serializable
data class VerifyEmailResponse(
    val status: String,
    val token: String? = null,
    val userId: String? = null
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val gmailConnected: Boolean = false,
    val gmailEmail: String? = null,
    val quidcoLinked: Boolean = false,
    val topcashbackLinked: Boolean = false,
    val hasRailcard1625: Boolean = false,
    val studentVerified: Boolean = false,
    val uniEmail: String? = null,
    val createdAt: String
)

@Serializable
data class UpdateProfileRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val hasRailcard1625: Boolean? = null,
    val railcardNumber: String? = null,
    val uniEmail: String? = null
)

@Serializable
data class UpdateProfileResponse(
    val status: String,
    val message: String
)

// ============================================
// CASHBACK DATA CLASSES
// ============================================

@Serializable
data class CashbackProvider(
    val id: String,
    val name: String,
    val description: String,
    val logoUrl: String,
    val cashbackRate: String
)

@Serializable
data class CashbackProvidersResponse(
    val providers: List<CashbackProvider>
)

@Serializable
data class CashbackInfo(
    val available: Boolean,
    val rate: String,
    val trackingUrl: String
)

@Serializable
data class OperatorCashbackResponse(
    val operator: String? = null,
    val operatorCode: String,
    val quidco: CashbackInfo? = null,
    val topcashback: CashbackInfo? = null
)

@Serializable
data class LinkCashbackRequest(
    val userId: String? = null,
    val provider: String,
    val email: String,
    val password: String
)

@Serializable
data class LinkCashbackResponse(
    val status: String,
    val userId: String? = null,
    val provider: String,
    val message: String
)

@Serializable
data class SantanderOfferResponse(
    val offerName: String,
    val cashbackRate: String,
    val affiliateLink: String
)
