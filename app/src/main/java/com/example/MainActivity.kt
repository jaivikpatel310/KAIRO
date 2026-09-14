package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.KairoService
import com.example.ui.theme.KairoBackground
import com.example.ui.theme.KairoMuted
import com.example.ui.theme.KairoPrimary
import com.example.ui.theme.KairoSurface
import com.example.ui.theme.KairoSurfaceVariant
import com.example.ui.theme.KairoText
import com.example.ui.theme.KairoTheme

class MainActivity : ComponentActivity() {

    private var checkPermissionsTrigger by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KairoTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KairoBackground),
                    containerColor = KairoBackground
                ) { innerPadding ->
                    KairoSetupScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        triggerCheck = checkPermissionsTrigger,
                        onRequestRefresh = { checkPermissionsTrigger++ }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate permissions when returning from system settings
        checkPermissionsTrigger++
    }
}

enum class PermissionStep(val stepNumber: Int) {
    AUDIO(1),
    NOTIFICATIONS(2),
    OVERLAY(3),
    BATTERY(4),
    COMPLETED(5)
}

@Composable
fun KairoSetupScreen(
    modifier: Modifier = Modifier,
    triggerCheck: Int,
    onRequestRefresh: () -> Unit
) {
    val context = LocalContext.current
    val isServiceRunning by KairoService.isRunning.collectAsStateWithLifecycle()
    val serviceStatus by KairoService.currentStatus.collectAsStateWithLifecycle()

    var currentStep by remember { mutableStateOf(PermissionStep.AUDIO) }

    // Evaluator helper
    fun evaluateNextStep() {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasOverlay = Settings.canDrawOverlays(context)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val hasBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true

        currentStep = when {
            !hasAudio -> PermissionStep.AUDIO
            !hasNotifications -> PermissionStep.NOTIFICATIONS
            !hasOverlay -> PermissionStep.OVERLAY
            !hasBattery -> PermissionStep.BATTERY
            else -> PermissionStep.COMPLETED
        }

        if (currentStep == PermissionStep.COMPLETED) {
            // Auto-start Kairo foreground service once all permissions are granted
            KairoService.start(context)
        }
    }

    LaunchedEffect(triggerCheck) {
        evaluateNextStep()
    }

    // Launchers
    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        evaluateNextStep()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        evaluateNextStep()
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        evaluateNextStep()
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        evaluateNextStep()
    }

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Minimal glowing orb badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(KairoSurfaceVariant)
                    .border(2.dp, KairoPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(KairoPrimary)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "KAIRO",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = KairoText,
                letterSpacing = 4.sp
            )

            Text(
                text = "ALWAYS-ON VOICE COMPANION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = KairoMuted,
                letterSpacing = 2.sp
            )
        }

        // Center Content: Either current single permission card or active engine status
        if (currentStep != PermissionStep.COMPLETED) {
            SinglePermissionCard(
                step = currentStep,
                onAction = {
                    when (currentStep) {
                        PermissionStep.AUDIO -> {
                            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        PermissionStep.NOTIFICATIONS -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                evaluateNextStep()
                            }
                        }
                        PermissionStep.OVERLAY -> {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayLauncher.launch(intent)
                        }
                        PermissionStep.BATTERY -> {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            batteryLauncher.launch(intent)
                        }
                        PermissionStep.COMPLETED -> {}
                    }
                }
            )
        } else {
            ActiveEngineCard(
                isServiceRunning = isServiceRunning,
                serviceStatus = serviceStatus,
                onRestart = { KairoService.start(context) },
                onPause = { KairoService.stop(context) }
            )
        }

        // Bottom progress indicator or operational note
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (currentStep != PermissionStep.COMPLETED) {
                Text(
                    text = "Step ${currentStep.stepNumber} of 4",
                    color = KairoMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "Voice-only. Say \"Wake up Kairo\" anywhere.",
                    color = KairoMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SinglePermissionCard(
    step: PermissionStep,
    onAction: () -> Unit
) {
    val (icon: ImageVector, title: String, rationale: String, buttonLabel: String, tag: String) = when (step) {
        PermissionStep.AUDIO -> {
            RationaleData(
                icon = Icons.Default.Mic,
                title = "Microphone Access",
                rationale = "Kairo listens continuously for 'Wake up Kairo' on-device so it's ready the instant you speak.",
                buttonLabel = "Grant Microphone Access",
                tag = "grant_audio_button"
            )
        }
        PermissionStep.NOTIFICATIONS -> {
            RationaleData(
                icon = Icons.Default.Notifications,
                title = "Notification Permission",
                rationale = "Android requires an active foreground notification to keep the on-device listening engine running.",
                buttonLabel = "Allow Notifications",
                tag = "grant_notifications_button"
            )
        }
        PermissionStep.OVERLAY -> {
            RationaleData(
                icon = Icons.Default.Layers,
                title = "System Overlay Access",
                rationale = "Kairo projects the concentric pulsing orb over whatever app is open when your wake phrase triggers.",
                buttonLabel = "Enable Overlay in Settings",
                tag = "grant_overlay_button"
            )
        }
        PermissionStep.BATTERY -> {
            RationaleData(
                icon = Icons.Default.BatteryChargingFull,
                title = "Battery Optimization Exemption",
                rationale = "Prevents Android from putting the background listener to sleep when your phone rests in standby.",
                buttonLabel = "Allow Unrestricted Battery",
                tag = "grant_battery_button"
            )
        }
        PermissionStep.COMPLETED -> {
            RationaleData(
                icon = Icons.Default.CheckCircle,
                title = "Setup Complete",
                rationale = "All permissions granted.",
                buttonLabel = "Continue",
                tag = "continue_button"
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = KairoSurface),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KairoSurfaceVariant))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(KairoSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = KairoPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = KairoText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = rationale,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = KairoText.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag(tag),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KairoPrimary,
                    contentColor = KairoBackground
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = buttonLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun ActiveEngineCard(
    isServiceRunning: Boolean,
    serviceStatus: String,
    onRestart: () -> Unit,
    onPause: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = KairoSurface),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KairoPrimary.copy(alpha = 0.35f)))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isServiceRunning) KairoPrimary else Color.Red)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServiceRunning) "VOICE ENGINE ACTIVE" else "ENGINE PAUSED",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isServiceRunning) KairoPrimary else Color.Red,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Standing by for \"Wake up Kairo\"",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = KairoText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Say the phrase to summon the orb. Ask what I can do, check if I'm here, or tell me to stop listening.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = KairoMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isServiceRunning) {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pause_service_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Pause Voice Listener", color = KairoText)
                }
            } else {
                Button(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_service_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KairoPrimary,
                        contentColor = KairoBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Resume Voice Listener", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class RationaleData(
    val icon: ImageVector,
    val title: String,
    val rationale: String,
    val buttonLabel: String,
    val tag: String
)

/**
 * Backward compatibility greeting composable for GreetingScreenshotTest
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier, color = KairoText)
}
