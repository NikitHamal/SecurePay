package com.touchbase.agent.ui.tracking

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.touchbase.agent.data.model.LocationResponse
import com.touchbase.agent.data.remote.SecurePayRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LIVE_LOCATION_REFRESH_MS = 15_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingMapScreen(
    accountId: String,
    repository: SecurePayRepository?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var location by remember(accountId) { mutableStateOf<LocationResponse?>(null) }
    var isLoading by remember(accountId) { mutableStateOf(true) }
    var isRefreshing by remember(accountId) { mutableStateOf(false) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }

    suspend fun loadLatestLocation(showInitialLoader: Boolean) {
        if (showInitialLoader && location == null) {
            isLoading = true
        } else {
            isRefreshing = true
        }

        val result = repository?.getLocation(accountId)
        if (result == null) {
            error = "Location service is not available in this build."
        } else {
            result.fold(
                onSuccess = { latest ->
                    location = latest
                    error = null
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "No location data available yet."
                    error = if (location == null) message else "Last refresh failed: $message"
                }
            )
        }
        isLoading = false
        isRefreshing = false
    }

    LaunchedEffect(accountId) {
        while (true) {
            loadLatestLocation(showInitialLoader = location == null)
            delay(LIVE_LOCATION_REFRESH_MS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { loadLatestLocation(showInitialLoader = false) } },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading && location == null -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )

                location != null -> LiveLocationContent(
                    location = location!!,
                    error = error,
                    onRefresh = { scope.launch { loadLatestLocation(showInitialLoader = false) } },
                    onOpenMaps = {
                        val current = location
                        val lat = current?.resolvedLatitude
                        val lng = current?.resolvedLongitude
                        if (lat != null && lng != null) {
                            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(SecurePay%20Device)")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            runCatching { context.startActivity(intent) }
                        }
                    }
                )

                else -> EmptyLocationState(
                    message = error ?: "No location data available yet.",
                    onRefresh = { scope.launch { loadLatestLocation(showInitialLoader = false) } }
                )
            }
        }
    }
}

@Composable
private fun LiveLocationContent(
    location: LocationResponse,
    error: String?,
    onRefresh: () -> Unit,
    onOpenMaps: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                OpenStreetMapView(
                    latitude = location.resolvedLatitude ?: 0.0,
                    longitude = location.resolvedLongitude ?: 0.0,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Latest stolen-device location", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Auto-refreshes every ${LIVE_LOCATION_REFRESH_MS / 1000}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                InfoLine("Latitude", location.resolvedLatitude?.toString() ?: "Unknown")
                InfoLine("Longitude", location.resolvedLongitude?.toString() ?: "Unknown")
                InfoLine("Accuracy", location.accuracy?.let { "±${String.format(Locale.US, "%.1f", it)} m" } ?: "Unknown")
                InfoLine("Battery", location.resolvedBattery?.let { "$it%" } ?: "Unknown")
                InfoLine("Last updated", location.timestamp?.let { formatLocationTime(it) } ?: "Unknown")

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(360.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh")
                    }
                    Button(
                        onClick = onOpenMaps,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(360.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Maps")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLocationState(
    message: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Waiting for phone location",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(360.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Check Again")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OpenStreetMapView(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    val html = remember(latitude, longitude) { buildOpenStreetMapHtml(latitude, longitude) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://www.openstreetmap.org/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

private fun buildOpenStreetMapHtml(latitude: Double, longitude: Double): String {
    val lat = latitude.coerceIn(-90.0, 90.0)
    val lng = longitude.coerceIn(-180.0, 180.0)
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <style>
            html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #f3f4f6; }
            .leaflet-control-attribution { font-size: 10px; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <script>
            const lat = $lat;
            const lng = $lng;
            const map = L.map('map', { zoomControl: true }).setView([lat, lng], 16);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: '&copy; OpenStreetMap contributors'
            }).addTo(map);
            L.marker([lat, lng]).addTo(map).bindPopup('Latest device location').openPopup();
            L.circle([lat, lng], { radius: 25, color: '#2563eb', fillOpacity: 0.12 }).addTo(map);
            setTimeout(() => map.invalidateSize(), 250);
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun formatLocationTime(timestamp: Long): String {
    val millis = if (timestamp > 10_000_000_000L) timestamp else timestamp * 1000L
    return SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
