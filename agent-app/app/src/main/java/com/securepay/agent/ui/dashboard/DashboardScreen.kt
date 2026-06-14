package com.securepay.agent.ui.dashboard

import android.app.Activity
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.securepay.agent.R
import com.securepay.agent.data.model.KpiSummary
import com.securepay.agent.data.model.formatAmount
import com.securepay.agent.data.remote.SecurePayRepository
import com.securepay.agent.ui.theme.Poppins
import com.securepay.agent.ui.theme.SecurePayAgentTheme
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
    modifier: Modifier = Modifier,
    previewKpis: KpiSummary? = null
) {
    var kpis by remember {
        mutableStateOf<KpiSummary?>(
            previewKpis ?: KpiSummary(
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
        )
    }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val isPreview = LocalInspectionMode.current
    val view = LocalView.current
    val backgroundColor = Color(0xFF212121) // Updated main background

    if (!isPreview) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
        }
    }

    val dealerName by (repository?.dealerName ?: MutableStateFlow(isPreview.let { if(it) "Demo Agent" else null })).collectAsState()

    LaunchedEffect(Unit) {
        // API loading disabled for now
        /*
        if (isPreview || kpis != null) return@LaunchedEffect
        isLoading = true
        val result = repository?.getKpis()
        isLoading = false
        result?.fold(
            onSuccess = { kpis = it },
            onFailure = { error = it.message }
        )
        */
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
                        Column {
                            Text("SecurePay", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                dealerName ?: "Agent",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_logout),
                            contentDescription = "Logout",
                            modifier = Modifier.size(23.dp),
                            tint = Color.Red
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
            Column {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.1f),
                    thickness = 0.5.dp
                )
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_dashboard), contentDescription = "Dashboard", modifier = Modifier.size(20.dp)) },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            indicatorColor = Color(0xFF10B981).copy(alpha = 0.1f)
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToCustomers,
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_customers), contentDescription = "Customers", modifier = Modifier.size(20.dp)) },
                        label = { Text("Customers") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToInventory,
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_inventory), contentDescription = "Inventory", modifier = Modifier.size(20.dp)) },
                        label = { Text("Inventory") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToLedger,
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_ledger), contentDescription = "Ledger", modifier = Modifier.size(20.dp)) },
                        label = { Text("Ledger") }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToEnrollment,
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
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
                    DateSelectorCard(currentOutstanding = kpi.totalOutstanding)

                    WidgetsSection(kpi = kpi)

                    SoldPhonesHistogram(data = listOf(8, 12, 19, 14, 25, 20, 31))

                    CollectionAreaChart(data = kpi.collectionHistory)

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
            color = Color.Gray
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
                color = Color.White
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "-1.24% vs last month",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = Poppins),
            color = Color(0xFF10B981) // Green for healthy decrease in debt
        )

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
                Text(text = formatAmount(maxVal.toInt()), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(text = formatAmount(((maxVal + minVal) / 2).toInt()), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(text = formatAmount(minVal.toInt()), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            Canvas(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                val width = size.width
                val height = size.height
                val spacing = width / (kpi.outstandingHistory.size - 1).coerceAtLeast(1)

                // Draw Grid
                val gridColor = Color.White.copy(alpha = 0.05f)
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
                            Color(0xFF10B981).copy(alpha = 0.2f),
                            Color(0xFF10B981).copy(alpha = 0.0f)
                        )
                    )
                )

                drawPath(
                    path = path,
                    color = Color(0xFF10B981),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                kpi.outstandingHistory.forEachIndexed { index, value ->
                    val x = index * spacing
                    val targetY = height - ((value - minVal) / range * height)
                    val y = height - ((height - targetY) * animationProgress.value)

                    drawCircle(
                        color = Color.White.copy(alpha = animationProgress.value),
                        radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = animationProgress.value),
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
                Text(text = day, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
            color = Color.White,
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
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            WidgetCard(
                title = "Warning",
                count = kpi.warningCount,
                color = Color(0xFFFDE047),
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
                color = Color(0xFF60A5FA),
                modifier = Modifier.weight(1f)
            )
            WidgetCard(
                title = "Locked",
                count = kpi.lockedCount,
                color = Color(0xFFFDA4AF),
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
            containerColor = Color(0xFF2A2A2A)
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
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

    var selectedIndex by remember {
        mutableStateOf(today.dayOfMonth - 1)
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
            containerColor = Color(0xFF2A2A2A)
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = "Sales Overview",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

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
                                color = if (selected) Color(0xFF10B981) else Color.Transparent,
                                shape = RoundedCornerShape(360.dp)
                            )
                            .clickable {
                                selectedIndex = index
                            }
                            .padding(
                                horizontal = 4.dp,
                                vertical = 8.dp
                            )
                    ) {
                        Text(
                            text = item.first,
                            color = if (selected)
                                Color.White
                            else
                                Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    color = if (selected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.second,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected)
                                    Color.Black
                                else
                                    Color.White.copy(alpha = 0.9f)
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
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No collection data", color = Color.Gray)
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
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "GH₵ 12,000",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "3.89% vs GH₵ 5,432.74 prev. 90 days",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Poppins),
                color = Color(0xFF10B981)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                val maxVal = data.maxOrNull()?.toFloat() ?: 1f
                Column(
                    modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(text = formatAmount(maxVal.toInt()), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(text = formatAmount((maxVal / 2).toInt()), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(text = "0", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                // The Chart
                Canvas(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    val minVal = 0f
                    val range = maxVal.coerceAtLeast(1f)
                    val width = size.width
                    val height = size.height
                    val spacing = width / (data.size - 1).coerceAtLeast(1)

                    val gridColor = Color.White.copy(alpha = 0.05f)
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
                                Color(0xFF10B981).copy(alpha = 0.2f),
                                Color(0xFF10B981).copy(alpha = 0.0f)
                            )
                        )
                    )

                    drawPath(
                        path = path,
                        color = Color(0xFF10B981),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    data.forEachIndexed { index, value ->
                        val x = index * spacing
                        val targetY = height - ((value - minVal) / range * height)
                        val y = height - ((height - targetY) * animationProgress.value)

                        drawCircle(
                            color = Color.White.copy(alpha = animationProgress.value),
                            radius = 4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                        drawCircle(
                            color = Color(0xFF10B981).copy(alpha = animationProgress.value),
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
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun SoldPhonesHistogram(
    data: List<Int>,
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
            text = "Phones sold (Weekly)",
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${data.sum()} total",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val maxVal = data.maxOrNull()?.toFloat() ?: 1f
            Column(
                modifier = Modifier.width(40.dp).fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(text = maxVal.toInt().toString(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(text = (maxVal / 2).toInt().toString(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(text = "0", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            Canvas(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                val range = maxVal.coerceAtLeast(1f)
                val width = size.width
                val height = size.height
                val barWidth = width / (data.size * 2f)

                // Draw Grid
                val gridColor = Color.White.copy(alpha = 0.05f)
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
                        color = Color(0xFF10B981),
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
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(text = day, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
            onLogout = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardDataPreview() {
    val mockKpi = KpiSummary(
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
            previewKpis = mockKpi,
            onNavigateToCustomers = {},
            onNavigateToEnrollment = {},
            onNavigateToInventory = {},
            onNavigateToLedger = {},
            onLogout = {}
        )
    }
}
