package com.touchbase.agent.ui.dashboard

import android.app.Activity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.OverscrollConfiguration
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.agent.R
import com.touchbase.agent.data.model.KpiSummary
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.components.SecurePayBottomNavBar
import com.touchbase.agent.ui.theme.Poppins
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.isLight
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: SecurePayRepository?,
    onNavigateToCustomers: () -> Unit,
    onNavigateToEnrollment: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    previewKpis: KpiSummary? = null
) {
    val isPreview = LocalInspectionMode.current
    var kpis by remember {
        mutableStateOf<KpiSummary?>(previewKpis)
    }
    var isLoading by remember { mutableStateOf(previewKpis == null && !isPreview) }
    var error by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val backgroundColor = MaterialTheme.colorScheme.background

    if (!isPreview) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = backgroundColor.isLight()
        }
    }

    val dealerName by (repository?.dealerName ?: MutableStateFlow<String?>(null)).collectAsState()

    LaunchedEffect(Unit) {
        if (isPreview || previewKpis != null) return@LaunchedEffect
        isLoading = true
        val result = repository?.getKpis()
        isLoading = false
        result?.fold(
            onSuccess = { kpis = it },
            onFailure = { error = it.message }
        )
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = backgroundColor,
            topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF10B981),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("TB Agent", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    scrolledContainerColor = backgroundColor
                )
            )
        },
        bottomBar = {
            SecurePayBottomNavBar(
                selectedTab = 0,
                onHomeClick = {},
                onCustomersClick = onNavigateToCustomers,
                onInventoryClick = onNavigateToInventory,
                onLedgerClick = onNavigateToLedger
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToEnrollment,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Enrollment", modifier = Modifier.size(28.dp))
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.End
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null && kpis == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(
                        state = rememberScrollState()
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                kpis?.let { kpi ->
                    val today = remember { LocalDate.now(ZoneOffset.UTC) }
                    var selectedIndex by remember { mutableStateOf(today.dayOfMonth - 1) }

                    val context = LocalContext.current
                    DateSelectorCard(
                        currentOutstanding = kpi.totalOutstanding,
                        selectedIndex = selectedIndex,
                        onDateSelected = { selectedIndex = it },
                        onNavigateToLedger = onNavigateToLedger
                    )

                    CollectionOverviewCard(
                        collectedToday = kpi.collectedToday,
                        onSyncData = {
                            Toast.makeText(context, "Data synchronized", Toast.LENGTH_SHORT).show()
                        },
                        onNewRecord = onNavigateToEnrollment
                    )

                    WidgetsSection(kpi = kpi)

                    CollectionAreaChart(data = kpi.collectionHistory, collectedToday = kpi.collectedToday)

                    OutstandingSection(kpi = kpi)
                }
            }
        }
    }
}
}

@Composable
fun OutstandingSection(
    kpi: KpiSummary,
    modifier: Modifier = Modifier
) {
    if (kpi.outstandingHistory.isEmpty()) {
        return
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(kpi.outstandingHistory) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Total unpaid balance",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatAmount(kpi.totalOutstanding),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xFFFDA4AF).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Text(
                    text = "High Risk",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFDA4AF),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val maxVal = kpi.outstandingHistory.maxOrNull()?.toFloat() ?: 1f
            val minVal = kpi.outstandingHistory.minOrNull()?.toFloat() ?: 0f
            val range = (maxVal - minVal).coerceAtLeast(1f)

            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(text = formatAmount(maxVal.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = formatAmount(((maxVal + minVal) / 2).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = formatAmount(minVal.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val primaryColor = MaterialTheme.colorScheme.primary
            val onBackgroundColor = MaterialTheme.colorScheme.onBackground

            Canvas(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                val width = size.width
                val height = size.height
                val spacing = width / (kpi.outstandingHistory.size - 1).coerceAtLeast(1)

                // Draw Grid
                val gridColor = onBackgroundColor.copy(alpha = 0.05f)
                val horizontalLines = 4
                for (i in 0..horizontalLines) {
                    val yLine = (height / horizontalLines) * i
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, yLine), end = androidx.compose.ui.geometry.Offset(width, yLine), strokeWidth = 1.dp.toPx())
                }
                kpi.outstandingHistory.forEachIndexed { index, _ ->
                    val xLine = index * spacing
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(xLine, 0f), end = androidx.compose.ui.geometry.Offset(xLine, height), strokeWidth = 1.dp.toPx())
                }

                val path = Path()
                val fillPath = Path()

                kpi.outstandingHistory.forEachIndexed { index, value ->
                    val x = index * spacing
                    val targetY = height - ((value - minVal) / range * height)
                    val y = height - ((height - targetY) * animationProgress.value)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (index - 1) * spacing
                        val prevVal = kpi.outstandingHistory[index - 1]
                        val prevTargetY = height - ((prevVal - minVal) / range * height)
                        val prevY = height - ((height - prevTargetY) * animationProgress.value)

                        val controlPoint1 = (prevX + x) / 2
                        path.cubicTo(controlPoint1, prevY, controlPoint1, y, x, y)
                        fillPath.cubicTo(controlPoint1, prevY, controlPoint1, y, x, y)
                    }
                }

                fillPath.lineTo(width, height)
                fillPath.lineTo(0f, height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.2f),
                            primaryColor.copy(alpha = 0.0f)
                        )
                    )
                )

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                kpi.outstandingHistory.forEachIndexed { index, value ->
                    val x = index * spacing
                    val targetY = height - ((value - minVal) / range * height)
                    val y = height - ((height - targetY) * animationProgress.value)

                    drawCircle(
                        color = onBackgroundColor.copy(alpha = animationProgress.value),
                        radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = animationProgress.value),
                        radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 45.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(text = day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun WidgetsSection(
    kpi: KpiSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Your Widgets",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WidgetCard(
                title = "Active",
                count = kpi.activeCount,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            WidgetCard(
                title = "Warning",
                count = kpi.warningCount,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WidgetCard(
                title = "Paid Off",
                count = kpi.paidCount,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )
            WidgetCard(
                title = "Locked",
                count = kpi.lockedCount,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun WidgetCard(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(count) {
        startAnimation = true
    }

    val animatedCount by animateIntAsState(
        targetValue = if (startAnimation) count else 0,
        animationSpec = tween(durationMillis = 1000),
        label = "countAnimation"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = animatedCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun DateSelectorCard(
    currentOutstanding: Int,
    selectedIndex: Int,
    onDateSelected: (Int) -> Unit,
    onNavigateToLedger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now(ZoneOffset.UTC) }
    val days = remember {
        val firstDayOfMonth = today.withDayOfMonth(1)
        (0 until today.lengthOfMonth()).map { i ->
            val date = firstDayOfMonth.plusDays(i.toLong())
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) to
                    date.dayOfMonth.toString()
        }
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        val itemWidthPx = 160
        val centerOffset = 500
        val scrollPosition = (selectedIndex * itemWidthPx) - centerOffset
        scrollState.animateScrollTo(scrollPosition.coerceAtLeast(0))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sales Overview",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.clickable { onNavigateToLedger() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View Ledger",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View Ledger",
                        tint = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.primary else Color(0xFF004B30),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        state = scrollState
                    ),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                days.forEachIndexed { index, item ->
                    val selected = index == selectedIndex

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(360.dp)
                            )
                            .clickable {
                                onDateSelected(index)
                            }
                            .padding(
                                horizontal = 4.dp,
                                vertical = 8.dp
                            )
                    ) {
                        Text(
                            text = item.first,
                            color = if (selected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.second,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionAreaChart(
    data: List<Int>,
    collectedToday: Int = 0,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No collection data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        // Animation State
        val animationProgress = remember { Animatable(0f) }
        LaunchedEffect(data) {
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000)
            )
        }

        Column(modifier = modifier.padding(horizontal = 16.dp)) {
            // New Header Details
            Text(
                text = "Collections today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatAmount(collectedToday),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                val maxVal = data.maxOrNull()?.toFloat() ?: 1f
                Column(
                    modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(text = formatAmount(maxVal.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = formatAmount((maxVal / 2).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val primaryColor = MaterialTheme.colorScheme.primary
                val onBackgroundColor = MaterialTheme.colorScheme.onBackground

                // The Chart
                Canvas(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    val minVal = 0f
                    val range = maxVal.coerceAtLeast(1f)
                    val width = size.width
                    val height = size.height
                    val spacing = width / (data.size - 1).coerceAtLeast(1)

                    val gridColor = onBackgroundColor.copy(alpha = 0.05f)
                    val numberOfLines = 5
                    for (i in 0..numberOfLines) {
                        val y = (height / numberOfLines) * i
                        drawLine(
                            color = gridColor,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    data.forEachIndexed { index, _ ->
                        val x = index * spacing
                        drawLine(
                            color = gridColor,
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val path = Path()
                    val fillPath = Path()

                    data.forEachIndexed { index, value ->
                        val x = index * spacing
                        val targetY = height - ((value - minVal) / range * height)
                        val y = height - ((height - targetY) * animationProgress.value)

                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            val prevX = (index - 1) * spacing
                            val prevY = height - ((data[index - 1] - minVal) / range * height)
                            val controlPoint1 = (prevX + x) / 2
                            path.cubicTo(controlPoint1, prevY, controlPoint1, y, x, y)
                            fillPath.cubicTo(controlPoint1, prevY, controlPoint1, y, x, y)
                        }
                    }

                    fillPath.lineTo(width, height)
                    fillPath.lineTo(0f, height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.2f),
                                primaryColor.copy(alpha = 0.0f)
                            )
                        )
                    )

                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    data.forEachIndexed { index, value ->
                        val x = index * spacing
                        val targetY = height - ((value - minVal) / range * height)
                        val y = height - ((height - targetY) * animationProgress.value)

                        drawCircle(
                            color = onBackgroundColor.copy(alpha = animationProgress.value),
                            radius = 4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                        drawCircle(
                            color = primaryColor.copy(alpha = animationProgress.value),
                            radius = 4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x, y),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 45.dp, top = 8.dp), 
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SoldPhonesHistogram(
    data: List<Int>,
    title: String = "Phones sold (Weekly)",
    labels: List<String> = listOf("M", "T", "W", "T", "F", "S", "S"),
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    // Animation State
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${data.sum()} total",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val maxVal = data.maxOrNull()?.toFloat() ?: 1f
            Column(
                modifier = Modifier.width(40.dp).fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(text = maxVal.toInt().toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = (maxVal / 2).toInt().toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val primaryColor = MaterialTheme.colorScheme.primary
            val onBackgroundColor = MaterialTheme.colorScheme.onBackground

            Canvas(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                val range = maxVal.coerceAtLeast(1f)
                val width = size.width
                val height = size.height
                val barWidth = width / (data.size * 2f)

                // Draw Grid
                val gridColor = onBackgroundColor.copy(alpha = 0.05f)
                val numberOfLines = 4
                for (i in 0..numberOfLines) {
                    val y = (height / numberOfLines) * i
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(width, y), strokeWidth = 1.dp.toPx())
                }

                data.forEachIndexed { index, value ->
                    val x = index * (width / data.size) + (width / data.size) / 2f - barWidth / 2f
                    val targetHeight = (value / range) * height
                    val animatedHeight = targetHeight * animationProgress.value
                    val y = height - animatedHeight

                    drawRoundRect(
                        color = primaryColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, animatedHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(40.dp)) // Match Y-axis width
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                labels.forEach { label ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionOverviewCard(
    collectedToday: Int,
    onSyncData: () -> Unit,
    onNewRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val currencyValue = collectedToday / 100.0
    val formattedAmount = String.format(Locale.US, "%,.2f", currencyValue)

    val cardBg = if (isDark) {
        Brush.horizontalGradient(colors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)))
    } else {
        Brush.horizontalGradient(colors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(cardBg)
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ledger),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Total Collected Today",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "↗",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "+12%",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "KES",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onSyncData,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Sync",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Button(
                        onClick = onNewRecord,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.AddCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Record",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardLoadingPreview() {
    SecurePayAgentTheme {
        DashboardScreen(
            repository = null,
            onNavigateToCustomers = {},
            onNavigateToEnrollment = {},
            onNavigateToInventory = {},
            onNavigateToLedger = {},
            onLogout = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardDataPreview() {
    val previewKpi = KpiSummary(
        activeCount = 45,
        lockedCount = 12,
        warningCount = 15,
        paidCount = 10,
        totalOutstanding = 125000,
        collectedToday = 1500,
        totalAccounts = 70,
        collectionHistory = listOf(1200, 1800, 900, 2500, 1500, 2100, 1500),
        outstandingHistory = listOf(140000, 135000, 132000, 130000, 128000, 126000, 125000)
    )
    SecurePayAgentTheme {
        DashboardScreen(
            repository = null,
            previewKpis = previewKpi,
            onNavigateToCustomers = {},
            onNavigateToEnrollment = {},
            onNavigateToInventory = {},
            onNavigateToLedger = {},
            onLogout = {},
            onNavigateToSettings = {}
        )
    }
}
