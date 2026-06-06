package com.securepay.agent.data.model

/**
 * A predefined financing plan offered to a customer at enrollment time.
 *
 * @param id stable identifier used on the wire.
 * @param name human-readable plan name.
 * @param totalLoanAmount full device price financed under the plan.
 * @param suggestedDownPayment default down payment pre-filled in the UI.
 * @param dailyRate amount the customer pays per day.
 * @param termDays number of days in the financing term.
 */
data class Plan(
    val id: String,
    val name: String,
    val totalLoanAmount: Double,
    val suggestedDownPayment: Double,
    val dailyRate: Double,
    val termDays: Int
)

/**
 * Catalog of plans the field agent can select from. In a production build this
 * would be fetched from the backend; here it is a static, deterministic list.
 */
object PlanCatalog {
    val plans: List<Plan> = listOf(
        Plan(
            id = "plan_lite",
            name = "Lite 90",
            totalLoanAmount = 12000.0,
            suggestedDownPayment = 2000.0,
            dailyRate = 120.0,
            termDays = 90
        ),
        Plan(
            id = "plan_standard",
            name = "Standard 180",
            totalLoanAmount = 24000.0,
            suggestedDownPayment = 3000.0,
            dailyRate = 130.0,
            termDays = 180
        ),
        Plan(
            id = "plan_premium",
            name = "Premium 365",
            totalLoanAmount = 45000.0,
            suggestedDownPayment = 5000.0,
            dailyRate = 140.0,
            termDays = 365
        )
    )

    fun firstOrNull(): Plan? = plans.firstOrNull()
}
