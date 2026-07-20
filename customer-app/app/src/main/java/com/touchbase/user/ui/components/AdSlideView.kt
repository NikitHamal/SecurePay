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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Ics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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

/**
 * A horizontal slide view for displaying advertisements.
 * Shows up to 3 ads in a carousel format.
 */
@Composable
fun AdSlideView(
    ads: List<AdModel>,
    modifier: Modifier = Modifier,
    autoScroll: Boolean = true,
    scrollInterval: Long = 5000L
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0)
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
        pagerState.interactionSource.collect { interaction ->
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
            count = ads.size,
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
 * Individual ad card display.
 */
@Composable
private fun AdCard(
    ad: AdModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.12f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ad icon/placeholder
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
                
                // Ad title
                Text(
                    text = ad.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                
                // Ad description
                Text(
                    text = ad.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                
                // Link indicator if URL is present
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
