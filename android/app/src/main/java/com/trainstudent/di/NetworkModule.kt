package com.trainstudent.di

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trainstudent.BuildConfig
import com.trainstudent.network.TrainStudentApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth")

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Backend API URL Configuration
     * 
     * For LOCAL DEVELOPMENT with physical device (Samsung phone):
     * 1. Find your computer's local IP: `hostname -I | awk '{print $1}'` or check network settings
     * 2. Update the debug URL below with your IP (e.g., "http://192.168.1.100:8080/")
     * 3. Make sure your phone is on the same WiFi network as your computer
     * 4. Run the backend: `cd backend && go run .`
     * 
     * For EMULATOR: Use "http://10.0.2.2:8080/" (special alias for host localhost)
     */
    private val BASE_URL: String
        get() = if (BuildConfig.DEBUG) {
            // TODO: Replace with your computer's local IP for physical device testing
            // Use `hostname -I` on Linux or `ipconfig` on Windows to find your IP
            "http://100.110.185.18:8080/"  // Your local IP (found via hostname -I)
        } else {
            BuildConfig.API_BASE_URL.ifEmpty { "https://api.trainstudent.com/" }
        }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }
    
    @Provides
    @Singleton
    fun provideAuthInterceptor(@ApplicationContext context: Context): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            
            // Skip adding auth header if it's already present (for explicit auth calls)
            if (originalRequest.header("Authorization") != null) {
                return@Interceptor chain.proceed(originalRequest)
            }
            
            // Try to get token from DataStore
            val token = runBlocking {
                try {
                    context.authDataStore.data.first()[stringPreferencesKey("auth_token")]
                } catch (e: Exception) {
                    null
                }
            }
            
            val request = if (token != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
            
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Only log in debug builds to prevent sensitive data exposure
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTrainStudentApi(retrofit: Retrofit): TrainStudentApi {
        return retrofit.create(TrainStudentApi::class.java)
    }
}
