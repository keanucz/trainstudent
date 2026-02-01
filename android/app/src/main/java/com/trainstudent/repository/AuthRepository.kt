package com.trainstudent.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trainstudent.network.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth")

data class AuthState(
    val isLoggedIn: Boolean = false,
    val token: String? = null,
    val userId: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val emailVerified: Boolean = false,
    val hasRailcard1625: Boolean = false,
    val quidcoLinked: Boolean = false,
    val topcashbackLinked: Boolean = false,
    val onboardingComplete: Boolean = false
)

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TrainStudentApi
) {
    private object Keys {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val FIRST_NAME = stringPreferencesKey("first_name")
        val LAST_NAME = stringPreferencesKey("last_name")
        val EMAIL_VERIFIED = booleanPreferencesKey("email_verified")
        val HAS_RAILCARD = booleanPreferencesKey("has_railcard_1625")
        val QUIDCO_LINKED = booleanPreferencesKey("quidco_linked")
        val TOPCASHBACK_LINKED = booleanPreferencesKey("topcashback_linked")
        val AUTH_ONBOARDING_COMPLETE = booleanPreferencesKey("auth_onboarding_complete")
    }
    
    val authState: Flow<AuthState> = context.authDataStore.data.map { prefs ->
        AuthState(
            isLoggedIn = prefs[Keys.IS_LOGGED_IN] ?: false,
            token = prefs[Keys.TOKEN],
            userId = prefs[Keys.USER_ID],
            email = prefs[Keys.EMAIL],
            firstName = prefs[Keys.FIRST_NAME],
            lastName = prefs[Keys.LAST_NAME],
            emailVerified = prefs[Keys.EMAIL_VERIFIED] ?: false,
            hasRailcard1625 = prefs[Keys.HAS_RAILCARD] ?: false,
            quidcoLinked = prefs[Keys.QUIDCO_LINKED] ?: false,
            topcashbackLinked = prefs[Keys.TOPCASHBACK_LINKED] ?: false,
            onboardingComplete = prefs[Keys.AUTH_ONBOARDING_COMPLETE] ?: false
        )
    }
    
    suspend fun getAuthState(): AuthState {
        return authState.first()
    }
    
    suspend fun getToken(): String? {
        return context.authDataStore.data.first()[Keys.TOKEN]
    }
    
    suspend fun isLoggedIn(): Boolean {
        return context.authDataStore.data.first()[Keys.IS_LOGGED_IN] ?: false
    }
    
    suspend fun isAuthOnboardingComplete(): Boolean {
        return context.authDataStore.data.first()[Keys.AUTH_ONBOARDING_COMPLETE] ?: false
    }
    
    // ============================================
    // AUTH API CALLS
    // ============================================
    
    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): Result<RegisterResponse> {
        return try {
            val response = api.register(
                RegisterRequest(
                    email = email,
                    password = password,
                    firstName = firstName,
                    lastName = lastName
                )
            )
            // Save email for verification step
            context.authDataStore.edit { prefs ->
                prefs[Keys.EMAIL] = email
                prefs[Keys.FIRST_NAME] = firstName
                prefs[Keys.LAST_NAME] = lastName
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            // Save auth state
            context.authDataStore.edit { prefs ->
                prefs[Keys.IS_LOGGED_IN] = true
                prefs[Keys.TOKEN] = response.token
                prefs[Keys.USER_ID] = response.userId
                prefs[Keys.EMAIL] = email
                prefs[Keys.EMAIL_VERIFIED] = true
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun verifyEmail(email: String, code: String): Result<VerifyEmailResponse> {
        return try {
            val response = api.verifyEmail(VerifyEmailRequest(email, code))
            if (response.status == "verified") {
                context.authDataStore.edit { prefs ->
                    prefs[Keys.EMAIL_VERIFIED] = true
                    response.token?.let { prefs[Keys.TOKEN] = it }
                    response.userId?.let { prefs[Keys.USER_ID] = it }
                    prefs[Keys.IS_LOGGED_IN] = true
                }
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getCurrentUser(): Result<UserResponse> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("Not logged in"))
            val response = api.getCurrentUser("Bearer $token")
            // Update local state
            context.authDataStore.edit { prefs ->
                prefs[Keys.FIRST_NAME] = response.firstName ?: ""
                prefs[Keys.LAST_NAME] = response.lastName ?: ""
                prefs[Keys.HAS_RAILCARD] = response.hasRailcard1625
                prefs[Keys.QUIDCO_LINKED] = response.quidcoLinked
                prefs[Keys.TOPCASHBACK_LINKED] = response.topcashbackLinked
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateProfile(
        hasRailcard1625: Boolean? = null,
        railcardNumber: String? = null
    ): Result<UpdateProfileResponse> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("Not logged in"))
            val response = api.updateProfile(
                "Bearer $token",
                UpdateProfileRequest(
                    hasRailcard1625 = hasRailcard1625,
                    railcardNumber = railcardNumber
                )
            )
            // Update local state
            if (hasRailcard1625 != null) {
                context.authDataStore.edit { prefs ->
                    prefs[Keys.HAS_RAILCARD] = hasRailcard1625
                }
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // CASHBACK LINKING
    // ============================================
    
    suspend fun linkCashbackAccount(
        provider: String,
        email: String,
        password: String
    ): Result<LinkCashbackResponse> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("Not logged in"))
            val userId = context.authDataStore.data.first()[Keys.USER_ID]
            val response = api.linkCashbackAccount(
                "Bearer $token",
                LinkCashbackRequest(
                    userId = userId,
                    provider = provider,
                    email = email,
                    password = password
                )
            )
            // Update local state
            context.authDataStore.edit { prefs ->
                when (provider.lowercase()) {
                    "quidco" -> prefs[Keys.QUIDCO_LINKED] = true
                    "topcashback" -> prefs[Keys.TOPCASHBACK_LINKED] = true
                }
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getSantanderOffer(): Result<SantanderOfferResponse> {
        return try {
            val response = api.getSantanderOffer()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // LOCAL STATE UPDATES
    // ============================================
    
    suspend fun setHasRailcard(has: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.HAS_RAILCARD] = has
        }
    }
    
    suspend fun setQuidcoLinked(linked: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.QUIDCO_LINKED] = linked
        }
    }
    
    suspend fun setTopcashbackLinked(linked: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.TOPCASHBACK_LINKED] = linked
        }
    }
    
    suspend fun updateLocalProfile(firstName: String, lastName: String) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.FIRST_NAME] = firstName
            prefs[Keys.LAST_NAME] = lastName
        }
    }
    
    suspend fun completeAuthOnboarding() {
        context.authDataStore.edit { prefs ->
            prefs[Keys.AUTH_ONBOARDING_COMPLETE] = true
        }
    }
    
    suspend fun logout() {
        context.authDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
