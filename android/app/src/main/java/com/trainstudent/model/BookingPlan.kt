package com.trainstudent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BookingPlan(
    val id: String,
    val netCost: Double,
    val originalCost: Double,
    val operatorCode: String,           // "TPE", "NORTHERN", "AVANTI", "GC"
    val operatorName: String,
    val departureTime: String,          // ISO 8601
    val arrivalTime: String,
    val durationMinutes: Int,
    val origin: String,
    val originCrs: String,
    val destination: String,
    val destinationCrs: String,
    val requiredProof: List<String>,    // ["16-25 Railcard"], [".ac.uk email"]
    val promoCode: String? = null,
    val cashbackNetwork: String? = null, // "TOPCASHBACK", "QUIDCO"
    val cashbackPercent: Double? = null,
    val bookingAction: BookingActionDto,
    val summary: String,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val discountType: String,           // "RAILCARD", "TPESTUDENT", "UNIDAYS", "NORTHERN50"
    val savingsPercent: Double
) {
    /**
     * Convert the DTO BookingAction to the sealed class version
     */
    fun getAction(): BookingAction {
        return when (bookingAction.type) {
            "deeplink" -> BookingAction.DeepLink(bookingAction.url ?: "")
            "webview" -> BookingAction.WebView(bookingAction.url ?: "", bookingAction.promoCode)
            "native" -> BookingAction.NativeFlow(bookingAction.flowId ?: "", emptyMap())
            "northern_app_automation" -> BookingAction.NorthernAppAutomation(
                promoCode = bookingAction.promoCode ?: promoCode ?: "",
                origin = bookingAction.origin ?: origin,
                originCrs = bookingAction.originCrs ?: originCrs,
                destination = bookingAction.destination ?: destination,
                destinationCrs = bookingAction.destinationCrs ?: destinationCrs,
                departureTime = bookingAction.departureTime ?: departureTime
            )
            else -> BookingAction.DeepLink("")
        }
    }
}

/**
 * DTO class matching the JSON structure from the backend
 */
@Serializable
data class BookingActionDto(
    val type: String,  // "deeplink", "webview", "native", "northern_app_automation"
    val url: String? = null,
    val promoCode: String? = null,
    val flowId: String? = null,
    // Northern App Automation specific fields
    val origin: String? = null,
    val originCrs: String? = null,
    val destination: String? = null,
    val destinationCrs: String? = null,
    val departureTime: String? = null
)

/**
 * Sealed class for type-safe action handling in the UI
 */
sealed class BookingAction {
    data class DeepLink(val url: String) : BookingAction()
    
    data class WebView(val url: String, val promoCode: String? = null) : BookingAction()
    
    data class NativeFlow(val flowId: String, val params: Map<String, String> = emptyMap()) : BookingAction()
    
    /**
     * Northern App Automation action
     * Opens Northern app and automates promo code entry + search
     */
    data class NorthernAppAutomation(
        val promoCode: String,
        val origin: String,
        val originCrs: String,
        val destination: String,
        val destinationCrs: String,
        val departureTime: String
    ) : BookingAction()
}

@Serializable
enum class RiskLevel {
    LOW,      // Guaranteed discount
    MEDIUM,   // Usually works, occasional issues
    HIGH      // Experimental, may not work
}
