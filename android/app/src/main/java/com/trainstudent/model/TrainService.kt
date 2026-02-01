package com.trainstudent.model

import kotlinx.serialization.Serializable

@Serializable
data class TrainService(
    val serviceId: String,
    val operator: String,
    val operatorCode: String,
    val origin: String,
    val destination: String,
    val scheduledDeparture: String,
    val scheduledArrival: String,
    val expectedDeparture: String? = null,
    val expectedArrival: String? = null,
    val platform: String? = null,
    val status: ServiceStatus = ServiceStatus.ON_TIME,
    val duration: Int = 0,
    val delayMinutes: Int = 0,
    val changes: Int = 0,
    val price: Double? = null,
    val discountedPrice: Double? = null,
    val hasDiscount: Boolean = false,
    val callingPoints: List<CallingPoint> = emptyList()
)

@Serializable
data class Station(
    val name: String,
    val crs: String
)

@Serializable
data class CallingPoint(
    val station: Station,
    val scheduledTime: String,
    val expectedTime: String? = null,
    val platform: String? = null
)

@Serializable
enum class ServiceStatus {
    ON_TIME,
    DELAYED,
    CANCELLED,
    EARLY,
    NO_REPORT
}
