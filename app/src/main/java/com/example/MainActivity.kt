package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.ui.theme.GlowNeon
import com.example.ui.theme.GlowBlue
import com.example.ui.theme.AccentRed
import kotlinx.coroutines.delay

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable

class MainActivity : ComponentActivity() {
    private val viewModel: ZoyaViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val recordAudioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
                
                LaunchedEffect(Unit) {
                    if (!recordAudioPermission.status.isGranted) {
                        recordAudioPermission.launchPermissionRequest()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZoyaScreen(
                        appState = viewModel.appState.collectAsState().value,
                        onPressMic = { viewModel.startListening() },
                        onReleaseMic = { viewModel.stopListeningAndSend() }
                    )
                }
            }
        }
    }
}

@Composable
fun ZoyaScreen(
    appState: AppState,
    onPressMic: () -> Unit,
    onReleaseMic: () -> Unit
) {
    val context = LocalContext.current
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isCameraOn by remember { mutableStateOf(false) }
    var isScreenSharing by remember { mutableStateOf(false) }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            onDismiss = { showApiKeyDialog = false },
            context = context
        )
    }

    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Zoya Branding
            Text(
                text = "ZOYA",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 8.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            StatusText(appState)

            Spacer(modifier = Modifier.height(64.dp))

            // Center Mic Button / Orb
            Orb(
                appState = appState,
                onPressMic = onPressMic,
                onReleaseMic = onReleaseMic
            )

            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "HOLD TO SPEAK",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (isMuted) AccentRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (isMuted) AccentRed else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { isScreenSharing = !isScreenSharing },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (isScreenSharing) GlowNeon.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isScreenSharing) Icons.Default.ScreenShare else Icons.Default.StopScreenShare,
                        contentDescription = "Screen Share",
                        tint = if (isScreenSharing) GlowNeon else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { isCameraOn = !isCameraOn },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (isCameraOn) GlowBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Live Camera",
                        tint = if (isCameraOn) GlowBlue else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = { showApiKeyDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ApiKeyDialog(onDismiss: () -> Unit, context: Context) {
    val prefs = remember { context.getSharedPreferences("zoya_prefs", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Configuration") },
        text = {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    prefs.edit().putString("api_key", apiKey.trim()).apply()
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StatusText(state: AppState) {
    val text = when (state) {
        AppState.IDLE -> "Idling in the mainframe."
        AppState.LISTENING -> "Tell me everything..."
        AppState.THINKING -> "Thinking..."
        AppState.SPEAKING -> "Speaking"
        AppState.ERROR -> "Oops, system glitch."
    }

    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}

@Composable
fun Orb(
    appState: AppState,
    onPressMic: () -> Unit,
    onReleaseMic: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")

    // Determine scale based on state
    val baseScale by animateFloatAsState(
        targetValue = when (appState) {
            AppState.LISTENING -> 1.5f
            AppState.SPEAKING -> 1.2f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "baseScale"
    )

    // Pulse animation
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (appState == AppState.IDLE) 1.05f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Rotating gradient colors based on state
    val color1 = when (appState) {
        AppState.LISTENING -> AccentRed
        AppState.THINKING -> GlowBlue
        AppState.SPEAKING -> GlowNeon
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val color2 = when(appState) {
        AppState.LISTENING -> GlowNeon
        AppState.THINKING -> Color.White
        AppState.SPEAKING -> GlowBlue
        else -> MaterialTheme.colorScheme.surface
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onPressMic()
        } else {
            // Need to make sure we don't call release immediately on mount.
            // Only if it was pressed. Actually, the viewmodel ignores stop if not listening.
            onReleaseMic()
        }
    }

    // Touch Handling (Hold to speak)
    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(baseScale * pulse)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(color1, color2)
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default ripple
                onClick = {} // Handle through state
            )
    )
}
