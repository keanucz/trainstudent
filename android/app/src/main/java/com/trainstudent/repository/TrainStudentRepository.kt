package com.trainstudent.repository

import com.trainstudent.model.*
import com.trainstudent.network.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainStudentRepository @Inject constructor(
    private val api: TrainStudentApi
) {

    suspend fun searchDiscounts(
        from: ParsedStation,
        to: ParsedStation,
        datetime: ParsedDateTime,
        railcard: String? = null
    ): Result<List<BookingPlan>> {
        return try {
            val dateTimeString = datetime.isoString
            val response = api.searchDiscounts(
                SearchRequest(
                    from = from.crs,
                    to = to.crs,
                    datetime = dateTimeString,
                    railcard = railcard
                )
            )
            Result.success(response.bookingPlans)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateNorthernCode(
        firstName: String,
        lastName: String,
        email: String
    ): Result<NorthernCodeResponse> {
        return try {
            val response = api.generateNorthernCode(
                NorthernCodeRequest(firstName, lastName, email)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOffers(
        operator: String? = null,
        railcard: String? = null,
        discountType: String? = null
    ): Result<List<Offer>> {
        return try {
            val response = api.getOffers(operator, railcard, discountType)
            Result.success(response.offers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchAvantiWithUnidays(
        from: ParsedStation,
        to: ParsedStation,
        datetime: ParsedDateTime,
        useUnidays: Boolean = true
    ): Result<AvantiSearchResponse> {
        return try {
            val response = api.searchAvantiUnidays(
                AvantiSearchRequest(
                    from = from.name,
                    fromNlc = from.crs.hashCode() % 10000, // Simplified NLC
                    to = to.name,
                    toNlc = to.crs.hashCode() % 10000,
                    datetime = datetime.isoString,
                    useUnidays = useUnidays
                )
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitGrandCentralStudent(
        firstName: String,
        lastName: String,
        email: String
    ): Result<GrandCentralResponse> {
        return try {
            // Validate .ac.uk email
            if (!email.lowercase().endsWith(".ac.uk")) {
                return Result.failure(IllegalArgumentException("Email must be a .ac.uk address"))
            }
            val response = api.submitGrandCentralStudent(
                GrandCentralRequest(firstName, lastName, email)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getLiveTrains(
        from: String,
        to: String,
        datetime: Instant? = null
    ): Flow<Result<List<TrainService>>> = flow {
        try {
            val response = api.getLiveTrains(
                from = from,
                to = to,
                datetime = datetime?.toString()
            )
            emit(Result.success(response.services))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun refreshLiveTrains(
        from: String,
        to: String
    ): Result<List<TrainService>> {
        return try {
            val response = api.getLiveTrains(from, to)
            Result.success(response.services)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
