package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import com.touchbase.agent.ui.enrollment.GhanaGeo

/**
 * M-KOPA "Customer location details": Region dropdown, District dropdown
 * (depends on the region), Physical Address, Preferred Language dropdown.
 */
@Composable
fun LocationStep(
    state: EnrollmentUiState,
    onRegionChange: (String) -> Unit,
    onDistrictChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        WizardDropdown(
            label = "Region",
            value = draft.region,
            options = GhanaGeo.REGION_NAMES,
            onSelect = onRegionChange
        )

        WizardDropdown(
            label = "District",
            value = draft.district,
            options = GhanaGeo.districtsFor(draft.region),
            onSelect = onDistrictChange,
            enabled = draft.region.isNotBlank()
        )

        WizardTextField(
            label = "Physical Address",
            value = draft.physicalAddress,
            onValueChange = onAddressChange,
            placeholder = "e.g. Market",
            isError = draft.physicalAddress.isNotEmpty() && !state.isAddressValid,
            supportingText = "House / landmark / street"
        )

        WizardDropdown(
            label = "Preferred Language",
            value = draft.preferredLanguage,
            options = GhanaGeo.LANGUAGES,
            onSelect = onLanguageChange
        )
    }
}
