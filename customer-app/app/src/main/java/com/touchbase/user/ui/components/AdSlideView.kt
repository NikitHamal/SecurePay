package com.touchbase.user.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import kotlinx.coroutines.flow.collect
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.touchbase.user.data.model.AdModel
import com.touchbase.user.ui.theme.CharcoalElevated
import com.touchbase.user.ui.theme.Gold
import com.touchbase.user.ui.theme.TextPrimary
import com.touchbase.user.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.touchbase.user.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * A horizontal slide view for displaying advertisements.
 * Shows up to 3 ads in a carousel format.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdSlideView(
    ads: List<AdModel>,
    modifier: Modifier = Modifier,
    autoScroll: Boolean = true,
    scrollInterval: Long = 5000L
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0) { ads.size }
    var autoScrollEnabled by remember { mutableStateOf(autoScroll) }
    
    // Auto-scroll effect
    LaunchedEffect(pagerState, autoScrollEnabled) {
        if (autoScrollEnabled && ads.isNotEmpty()) {
            while (true) {
                delay(scrollInterval)
                val nextPage = (pagerState.currentPage + 1) % ads.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }
    
    // Pause auto-scroll when user interacts
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                    autoScrollEnabled = false
                }
                is androidx.compose.foundation.interaction.PressInteraction.Release -> {
                    autoScrollEnabled = true
                }
                else -> {}
            }
        }
    }
    
    if (ads.isEmpty()) {
        // Show placeholder when no ads
        AdPlaceholder(modifier = modifier)
        return
    }
    
    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val ad = ads[page]
            AdCard(
                ad = ad,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    ad.linkUrl?.let { url ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }
                }
            )
        }
        
        // Page indicators
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(ads.size) { index ->
                val isSelected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .background(
                            if (isSelected) Gold else Color(0xFF262629),
                            CircleShape
                        )
                )
            }
        }
    }
}

/**
 * Plain, interceptor-free HTTP client used only to fetch ad artwork. The public
 * /api/ads/{id}/image route is auth- and HMAC-free (it is not in the device-HMAC
 * allow-list and the JWT hook is additive), so unsigned GETs succeed even before
 * the device is provisioned. No HMAC signing, no bearer token.
 */
private val adImageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

/**
 * Resolve an ad image reference to a loadable URL. External http(s) links are
 * used verbatim. Uploaded assets are stored server-side as R2 object keys (i.e.
 * a non-http string) and are served by the public /api/ads/{id}/image route, so
 * for those we build the route URL from the ad id rather than the stored key.
 */
private fun resolveAdImageUrl(ad: AdModel): String? {
    val ref = ad.imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (ref.startsWith("http://", ignoreCase = true) || ref.startsWith("https://", ignoreCase = true)) {
        ref
    } else {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val path = if (base.endsWith("/api")) "/ads/" else "/api/ads/"
        base + path + ad.id + "/image"
    }
}

/**
 * Loads the ad artwork off the main thread. Returns null while loading or if the
 * fetch/decode fails, so the caller can fall back to the branded placeholder.
 */
@Composable
private fun rememberAdImage(ad: AdModel): Bitmap? {
    var bitmap by remember(ad.id, ad.imageUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ad.id, ad.imageUrl) {
        val url = resolveAdImageUrl(ad) ?: return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                adImageClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    BitmapFactory.decodeStream(body.byteStream())
                }
            } catch (_: Exception) {
                null
            }
        }
        bitmap = decoded
    }
    return bitmap
}

/**
 * Individual ad card display.
 */
@Composable
private fun AdCard(
    ad: AdModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bitmap = rememberAdImage(ad)
    Card(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.12f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                // Artwork fills the card; a bottom scrim keeps the copy legible.
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = ad.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = ad.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Start
                    )
                    if (!ad.description.isNullOrBlank()) {
                        Text(
                            text = ad.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Start,
                            maxLines = 2
                        )
                    }
                    if (!ad.linkUrl.isNullOrBlank()) {
                        Text(
                            text = "Tap to learn more",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gold,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // No artwork (or it failed to load): the original branded card.
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = ad.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        if (!ad.description.isNullOrBlank()) {
                            Text(
                                text = ad.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                        if (!ad.linkUrl.isNullOrBlank()) {
                            Text(
                                text = "Tap to learn more",
                                style = MaterialTheme.typography.labelSmall,
                                color = Gold,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Placeholder shown when no ads are available.
 */
@Composable
private fun AdPlaceholder(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Ads managed from dashboard",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
