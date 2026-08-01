package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountRequest(
    val customerName: String,
    val nationalId: String,
    val phoneNumber: String,
    val imei: String,
    val planId: String? = null,
    val dailyRate: Int? = null,
    val totalAmount: Int? = null,
    val termDays: Int? = null,
    val downPayment: Int? = null,
    val customerPhoto: String? = null,
    val nationalIdFront: String? = null,
    val nationalIdBack: String? = null,
    val idType: String? = null,
    val nextOfKinName: String? = null,
    val nextOfKinPhone: String? = null,
    val nextOfKinRelation: String? = null,
    val refereeName: String? = null,
    val refereePhone: String? = null,
    val guarantorName: String? = null,
    val guarantorPhone: String? = null,
    val guarantorIdNumber: String? = null,
    val guarantorRelation: String? = null,
    val consentTerms: Boolean = false,
    val consentData: Boolean = false,
    val customerSignature: String? = null,
    // M-KOPA style application profile
    val surname: String? = null,
    val otherPhone: String? = null,
    val dateOfBirth: String? = null,
    val maritalStatus: String? = null,
    val employmentStatus: String? = null,
    val gender: String? = null,
    val isCustomerUser: Boolean? = null,
    val region: String? = null,
    val district: String? = null,
    val physicalAddress: String? = null,
    val preferredLanguage: String? = null,
    /** The exact agreement text the customer signed (for the record). */
    val agreementText: String? = null,
    /** Anti-fraud GPS fix captured on the agent's phone when the application was submitted. */
    val enrollmentLat: Double? = null,
    val enrollmentLng: Double? = null,
    val enrollmentAccuracy: Float? = null
)

@Serializable
data class AddDeviceRequest(
    val imei: String,
    val model: String,
    /** Where the phone physically was when it was registered (anti-fraud). */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null
)

/** Company-wide duplicate check result for a national ID (Ghana Card etc.). */
@Serializable
data class CheckNationalIdResponse(
    val duplicate: Boolean = false,
    val matches: List<NationalIdMatch> = emptyList()
)

@Serializable
data class NationalIdMatch(
    val accountId: String = "",
    val customerName: String = "",
    val deviceModel: String = "",
    val enrolledByName: String? = null,
    val createdAt: Long = 0L,
    val outstandingBalance: Int = 0,
    val fullyPaid: Boolean = false
)

@Serializable
data class UpdateAccountRequest(
    val customerName: String? = null,
    val nationalId: String? = null,
    val phoneNumber: String? = null,
    val dailyRate: Int? = null,
    val totalLoanAmount: Int? = null,
    val termDays: Int? = null,
    val customerPhoto: String? = null,
    val nationalIdFront: String? = null,
    val nationalIdBack: String? = null,
    val isStolen: Boolean? = null
)

@Serializable
data class ReleaseAccountRequest(
    val allowEarlyRelease: Boolean,
    val note: String
)

@kotlinx.serialization.Serializable
data class AppUpdateResponse(
    val available: Boolean = false,
    val url: String = "",
    val sha256Base64: String = "",
    val signatureChecksumBase64: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val minSupportedVersionCode: Int = 0,
    val serverTime: Long = 0L
)
