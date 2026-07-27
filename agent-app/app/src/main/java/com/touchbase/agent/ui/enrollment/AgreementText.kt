package com.touchbase.agent.ui.enrollment

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full Touch Base Device Financing Agreement — the formal contract generated
 * from the application data. It is shown in the agent app before the customer
 * signs, stored on the server at enrolment, and re-rendered on the customer
 * detail screens (app + dashboard) exactly as signed.
 *
 * Covers: customer identity, guarantor/next-of-kin/referee pages, product and
 * loan terms, money handling & payment channels, device lock policy, data
 * protection (Ghana Act 843), default & recovery, and signature blocks.
 */
object AgreementText {

    fun money(cents: Int): String {
        val formatted = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(cents / 100.0)
        return "GH₵ $formatted"
    }

    fun today(): String = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(Date())

    data class Parties(
        val firstName: String = "",
        val surname: String = "",
        val idType: String = "",
        val idNumber: String = "",
        val phone: String = "",
        val otherPhone: String = "",
        val dateOfBirth: String = "",
        val gender: String = "",
        val maritalStatus: String = "",
        val employmentStatus: String = "",
        val region: String = "",
        val district: String = "",
        val physicalAddress: String = "",
        val preferredLanguage: String = "",
        val customerName: String = "",
        // Device & loan
        val deviceModel: String = "",
        val imei: String = "",
        val planName: String = "",
        val totalLoanAmountCents: Int = 0,
        val downPaymentCents: Int = 0,
        val dailyRateCents: Int = 0,
        val termDays: Int = 0,
        // People who vouch for the customer
        val kinName: String = "",
        val kinRelation: String = "",
        val kinPhone: String = "",
        val refereeName: String = "",
        val refereePhone: String = "",
        val guarantorName: String = "",
        val guarantorRelation: String = "",
        val guarantorPhone: String = "",
        val guarantorId: String = ""
    )

    fun build(p: Parties, date: String = today()): String {
        val customer = listOf(p.firstName, p.surname).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { p.customerName.ifBlank { "__________________" } }
        val idDesc = when {
            p.idType.isNotBlank() && p.idNumber.isNotBlank() -> "${p.idType} number ${p.idNumber}"
            p.idNumber.isNotBlank() -> "ID number ${p.idNumber}"
            else -> "the national ID on file"
        }
        val outstanding = (p.totalLoanAmountCents - p.downPaymentCents).coerceAtLeast(0)

        fun row(label: String, value: String) = "$label: ${value.ifBlank { "—" }}"

        return buildString {
            appendLine("TOUCH BASE")
            appendLine("DEVICE FINANCING AGREEMENT")
            appendLine()
            appendLine("I. CUSTOMER ID")
            appendLine(row("First Name", p.firstName) + "        " + row("Last Name", p.surname))
            appendLine(row("ID Type", p.idType) + "        " + row("ID Number", p.idNumber))
            appendLine(row("Mobile #", p.phone) + "        " + row("Other #", p.otherPhone))
            appendLine(row("Date of Birth", p.dateOfBirth) + "        " + row("Gender", p.gender))
            appendLine(row("Marital Status", p.maritalStatus) + "        " + row("Employment", p.employmentStatus))
            appendLine(row("Region", p.region) + "        " + row("District", p.district))
            appendLine(row("Physical Address", p.physicalAddress))
            appendLine(row("Preferred Language", p.preferredLanguage))
            appendLine()
            appendLine("II. PARTIES AND PURPOSE")
            appendLine("1. This Device Financing Agreement (\"the Agreement\") is entered into on $date between Touch Base (\"the Company\", represented by its authorised agent) and $customer, holder of $idDesc (\"the Customer\").")
            appendLine("2. The Company agrees to sell the Product described in Section III to the Customer on a financed, pay-as-you-go basis, and the Customer agrees to pay for the Product in the instalments described in Sections IV and V.")
            appendLine()
            appendLine("III. PRODUCT AND OFFER")
            appendLine(row("1. Product", p.deviceModel))
            appendLine(row("2. Device IMEI", p.imei))
            appendLine(row("3. Offer", p.planName.ifBlank { "Custom terms" }))
            appendLine("4. The Customer acknowledges that the Product carries the Company's device-management software, which protects the Company's interest until the loan is fully repaid.")
            appendLine()
            appendLine("IV. LOAN DETAILS")
            appendLine(row("1. Total loan amount", money(p.totalLoanAmountCents)))
            appendLine(row("2. Initial payment (deposit)", money(p.downPaymentCents)) + ", payable on signing this Agreement.")
            appendLine(row("3. Daily repayment rate", money(p.dailyRateCents)) + " every day for ${p.termDays} days.")
            appendLine("4. Repayment period: ${p.termDays} days, starting on $date.")
            appendLine(row("5. Outstanding balance at signing", money(outstanding)))
            appendLine()
            appendLine("V. MONEY HANDLING AND PAYMENTS")
            appendLine("1. The Customer shall pay each instalment through the Company's approved payment channels: MTN Mobile Money, Telecel Cash, the Touch Base customer app, or cash paid to an authorised agent of the Company against an official receipt.")
            appendLine("2. Payments are due every day without demand. A payment counts as made only when confirmed in the Company's systems.")
            appendLine("3. The Customer may pay more than the daily rate, or settle the full outstanding balance, at any time without penalty.")
            appendLine("4. Ownership of the Product remains with the Company until the total loan amount is paid in full. The Customer shall not sell, pawn, gift, or otherwise transfer the Product while this Agreement is active.")
            appendLine()
            appendLine("VI. DEVICE SECURITY, LOCKING AND TRACKING")
            appendLine("1. The Product is protected by the Company's device-management software. The Customer shall not remove, disable, or attempt to bypass it.")
            appendLine("2. If a payment is overdue, the Product locks automatically and unlocks once the overdue amount is paid. Continued default may bring further restrictions (calls, data and app access).")
            appendLine("3. The Customer consents to the Company recording the Product's location for fraud prevention, theft recovery and financing protection.")
            appendLine("4. After full payment of the total loan amount, the lock is permanently removed and full ownership passes to the Customer.")
            appendLine()
            appendLine("VII. REFEREES, NEXT OF KIN AND GUARANTOR")
            appendLine(row("Next of Kin", p.kinName) + (if (p.kinRelation.isNotBlank()) " (${p.kinRelation})" else "") + (if (p.kinPhone.isNotBlank()) " — ${p.kinPhone}" else ""))
            appendLine(row("Referee", p.refereeName) + (if (p.refereePhone.isNotBlank()) " — ${p.refereePhone}" else ""))
            appendLine(row("Guarantor (co-signer)", p.guarantorName) + (if (p.guarantorRelation.isNotBlank()) " (${p.guarantorRelation})" else "") + (if (p.guarantorPhone.isNotBlank()) " — ${p.guarantorPhone}" else "") + (if (p.guarantorId.isNotBlank()) ", ID ${p.guarantorId}" else ""))
            appendLine("1. The Guarantor co-signs this Agreement and guarantees the Customer's obligations. If the Customer defaults or cannot be reached, the Guarantor accepts responsibility for helping the Company contact the Customer and for settling outstanding amounts.")
            appendLine("2. The Customer confirms that the next of kin, referee and guarantor named above have agreed to be contacted by the Company in relation to this Agreement.")
            appendLine()
            appendLine("VIII. CONSENT FOR COLLECTION AND PROCESSING OF PERSONAL DATA AND PRODUCT INFORMATION")
            appendLine("1. Privacy Policy. The Customer acknowledges receiving (or being able to access) the Company's Customer Privacy Policy, which explains how the Company collects, uses, stores, processes, transfers and shares personal and Product information in furtherance of this Agreement and under applicable law, including the Data Protection Act, 2012 (Act 843) of the Republic of Ghana.")
            appendLine("2. Where the Product is a phone, the Customer understands that the Company shall have access to information on the phone, including the applications run on the phone, the SIM/ICCID number, the phone's IMEI number, software crash reports, status and history (collectively \"Phone Information\"). Phone Information may be shared with mobile network operators and phone manufacturers to troubleshoot, detect fraud, run analytics and improve the quality of services provided to phone customers.")
            appendLine("3. Data Transfer. The Customer consents to personal data and Product information being collected by, used by, stored with, processed by, transferred to, or shared with entities affiliated with the Company; business partners, suppliers and sub-contractors engaged for the performance of this Agreement; professional advisers, auditors, insurers and service providers; and any party where required by law, regulation, court order or other court proceedings.")
            appendLine("4. Credit Checks. The Customer consents to the Company retrieving, analysing and processing the Customer's credit history, credit scoring and similar personal information from third parties such as credit reference bureaus and mobile network providers, in order to assess the Customer's credit profile and score, and to the Company reporting the Customer's repayment performance to such bodies.")
            appendLine()
            appendLine("IX. DEFAULT AND RECOVERY")
            appendLine("1. If the Customer remains in default, the Company may demand immediate payment of the full outstanding balance, repossess the Product, and report the default to credit reference bureaus.")
            appendLine("2. On default the Customer shall, on demand, return the Product to the Company in good working condition.")
            appendLine()
            appendLine("X. DECLARATIONS")
            appendLine("1. The Customer declares that all information provided in connection with this Agreement is true and accurate.")
            appendLine("2. The Customer confirms that this Agreement, the privacy policy and the terms and conditions were read over and explained in a language the Customer understands best, and the Customer agrees to the terms and conditions contained herein in relation to the Product.")
            appendLine()
            appendLine("XI. GOVERNING LAW")
            appendLine("1. This Agreement is governed by the laws of the Republic of Ghana.")
            appendLine()
            appendLine("XII. SIGNATURES")
            appendLine("Customer: $customer    Date: $date")
            appendLine("Signature: (signed digitally in the Touch Base agent app)")
            appendLine("Guarantor: ${p.guarantorName.ifBlank { "—" }}    Date: $date")
            appendLine("For the Company: Touch Base authorised agent    Date: $date")
        }.trimEnd()
    }
}
