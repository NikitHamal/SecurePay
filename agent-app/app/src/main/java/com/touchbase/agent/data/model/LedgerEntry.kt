package com.touchbase.agent.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LedgerEntry(
    val id: String = "",
    // The ledger endpoint serialises the owning account under the JSON key
    // `customerId` (see dealer-dashboard api/ledger). The Json decoder runs with
    // ignoreUnknownKeys = true, so without this alias `accountId` silently decoded
    // to "" and every entry collapsed into one bucket when grouped by customer.
    @SerialName("customerId")
    val accountId: String = "",
    val customerName: String = "",
    val imei: String = "",
    val amount: Int = 0,
    val dateEpochMillis: Long = 0L,
    val method: String = "",
    val reference: String = ""
)
