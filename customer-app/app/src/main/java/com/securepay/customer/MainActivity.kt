package com.securepay.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.securepay.customer.data.MockLeaseRepository
import com.securepay.customer.policy.DevicePolicyController
import com.securepay.customer.ui.CustomerApp
import com.securepay.customer.ui.theme.SecurePayCustomerTheme
import com.securepay.customer.viewmodel.CustomerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CustomerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CustomerViewModel(MockLeaseRepository()) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val policyController = DevicePolicyController(this)
        setContent {
            SecurePayCustomerTheme {
                CustomerApp(
                    viewModel = viewModel,
                    policyController = policyController
                )
            }
        }
    }
}
