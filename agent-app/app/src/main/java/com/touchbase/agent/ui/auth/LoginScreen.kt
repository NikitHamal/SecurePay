package com.touchbase.agent.ui.auth

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.agent.R
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repository: SecurePayRepository?,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()

    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.background
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFEAF5EE), // Light mint tint
                Color(0xFFF6FAF7)  // White-ish bottom
            )
        )
    }

    if (!isPreview) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (isDark) Color(0xFF121212).toArgb() else Color(0xFFEAF5EE).toArgb()
            window.navigationBarColor = if (isDark) Color(0xFF121212).toArgb() else Color(0xFFF6FAF7).toArgb()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Logo Container Card matching the reference design
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(24.dp),
                            clip = false
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    SecurePayLogo(
                        modifier = Modifier.size(54.dp),
                        isDark = false
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = if (isDark) Color.White else Color(0xFF004B30),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Sign in to access your account",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Main Form Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(32.dp),
                            clip = false
                        ),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E1E1E) else Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Email Field
                        Text(
                            text = "Email",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) Color(0xFFE5E7EB) else Color(0xFF4B5563),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                errorMessage = null
                            },
                            placeholder = { Text("Enter your email", color = Color(0xFF9CA3AF)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_email),
                                    contentDescription = null,
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            isError = errorMessage != null,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF3F4F6),
                                unfocusedContainerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF3F4F6),
                                disabledContainerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF3F4F6),
                                errorContainerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF3F4F6),
                                focusedBorderColor = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFFE5E7EB),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = if (isDark) Color.White else Color(0xFF111827),
                                unfocusedTextColor = if (isDark) Color.White else Color(0xFF111827),
                                focusedPlaceholderColor = Color(0xFF9CA3AF),
                                unfocusedPlaceholderColor = Color(0xFF9CA3AF)
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Password Field
                        Text(
                            text = "Password",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) Color(0xFFE5E7EB) else Color(0xFF4B5563),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            placeholder = { Text("Enter your password", color = Color(0xFF9CA3AF)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lock),
                                    contentDescription = null,
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible }
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (passwordVisible) R.drawable.ic_password_show else R.drawable.ic_password_hide
                                        ),
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        modifier = Modifier.size(22.dp),
                                        tint = Color(0xFF6B7280)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            isError = errorMessage != null,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF3F4F6),
                                unfocusedContainerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF3F4F6),
                                disabledContainerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF3F4F6),
                                errorContainerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF3F4F6),
                                focusedBorderColor = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFFE5E7EB),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = if (isDark) Color.White else Color(0xFF111827),
                                unfocusedTextColor = if (isDark) Color.White else Color(0xFF111827),
                                focusedPlaceholderColor = Color(0xFF9CA3AF),
                                unfocusedPlaceholderColor = Color(0xFF9CA3AF)
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Remember Me and Forgot Password
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { rememberMe = !rememberMe }
                                )
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (rememberMe) R.drawable.ic_check else R.drawable.ic_uncheck
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (rememberMe) {
                                        if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF004B30)
                                    } else Color(0xFF9CA3AF)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Remember me",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF4B5563)
                                )
                            }

                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF004B30),
                                modifier = Modifier.clickable {
                                    // Handle forgot password if needed
                                }
                            )
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        // Sign In Button
                        Button(
                            onClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please enter email and password"
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null

                                scope.launch {
                                    val result = repository?.login(email.trim(), password)
                                    isLoading = false
                                    result?.fold(
                                        onSuccess = { onLoginSuccess() },
                                        onFailure = { errorMessage = it.message ?: "Login failed" }
                                    )
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF004B30),
                                contentColor = Color.White,
                                disabledContainerColor = (if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF004B30)).copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(360.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "Sign In",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Footer Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have an account? ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563)
                    )
                    Text(
                        text = "Contact Admin",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF004B30),
                        modifier = Modifier.clickable {
                            // Contact Admin trigger
                        }
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun SecurePayLogo(modifier: Modifier = Modifier, isDark: Boolean = false) {
    val strokeColor = if (isDark) Color.White else Color(0xFF111827)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = w * 0.44f // Hexagon size bounding radius

            // Hexagon points at 30, 90, 150, 210, 270, 330 deg
            val points = (0..5).map { i ->
                val angleRad = Math.toRadians((i * 60 + 30).toDouble())
                androidx.compose.ui.geometry.Offset(
                    (cx + r * Math.cos(angleRad)).toFloat(),
                    (cy + r * Math.sin(angleRad)).toFloat()
                )
            }

            // Draw outer Hexagon
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1..5) {
                    lineTo(points[i].x, points[i].y)
                }
                close()
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Inner lines to divide the cube faces (Center to bottom, up-left, up-right)
            // i=1 (90 deg), i=3 (210 deg), i=5 (330 deg)
            drawLine(
                color = strokeColor,
                start = androidx.compose.ui.geometry.Offset(cx, cy),
                end = points[1],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = strokeColor,
                start = androidx.compose.ui.geometry.Offset(cx, cy),
                end = points[3],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = strokeColor,
                start = androidx.compose.ui.geometry.Offset(cx, cy),
                end = points[5],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Centered dollar sign representing financing
        Text(
            text = "$",
            color = strokeColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LoginScreenPreview() {
    SecurePayAgentTheme {
        LoginScreen(
            repository = null,
            onLoginSuccess = {}
        )
    }
}
