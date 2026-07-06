package com.touchbase.agent.ui.tracking

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Marker
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingMapScreen(
    accountId: String,
    repository: SecurePayRepository?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var location by remember { mutableStateOf<LatLng?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accountId) {
        try {
            val result = repository?.getLocation(accountId)
            result?.fold(
                onSuccess = { loc ->
                    location = LatLng(loc.latitude, loc.longitude)
                    isLoading = false
                },
                onFailure = {
                    error = it.message
                    isLoading = false
                }
            ) ?: run { isLoading = false }
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Tracking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (error != null) {
                Text(error!!, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
            } else if (location != null) {
                GoogleMap(
                    latLng = location!!,
                    onMapReady = { map ->
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(location!!, 15f))
                        map.addMarker(MarkerOptions().position(location!!).title("Stolen Device"))
                    }
                )
            } else {
                Text("No location available", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun GoogleMap(
    latLng: LatLng,
    onMapReady: (GoogleMap) -> Unit
) {
    AndroidView(
        factory = { context ->
            com.google.android.gms.maps.MapView(context).apply {
                onCreate(Bundle())
                getMapAsync { googleMap ->
                    onMapReady(googleMap)
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
