package com.securepay.agent.data.model

/**
 * Ordered steps of the enrollment wizard. [index] is used by the step indicator
 * to determine completion state and by the view model for navigation bounds.
 */
enum class WizardStep(val index: Int, val title: String) {
    KYC(0, "Customer"),
    SCANNER(1, "Device"),
    PAYMENT(2, "Plan");

    companion object {
        val ordered: List<WizardStep> = entries.sortedBy { it.index }
        val last: WizardStep = PAYMENT

        fun fromIndex(index: Int): WizardStep =
            ordered.firstOrNull { it.index == index } ?: KYC
    }
}
