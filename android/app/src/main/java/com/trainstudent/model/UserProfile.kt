package com.trainstudent.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val hasRailcard1625: Boolean = false,
    val canVerifyStudent: Boolean = false,
    val studentVerificationMethod: StudentVerificationMethod = StudentVerificationMethod.NONE
)

@Serializable
enum class StudentVerificationMethod {
    NONE,
    AC_UK_EMAIL,
    UNIDAYS,
    STUDENT_BEANS,
    TOTUM
}
