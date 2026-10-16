package com.ev3controller.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ev3controller.bluetooth.ChassisConfig
import com.ev3controller.bluetooth.ConnectionState
import com.ev3controller.bluetooth.Ev3Port
import com.ev3controller.ui.theme.*
import com.ev3controller.viewmodel.ControlMode
import com.ev3controller.viewmodel.Ev3ViewModel
import com.ev3controller.viewmodel.SpeedPreset
import kotlin.math.hypot
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(viewModel: Ev3ViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val bondedDevices by viewModel.bondedDevices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val controlMode by viewModel.controlMode.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val invertPorts by viewModel.invertPorts.collectAsState()
    val chassisConfig by viewModel.chassisConfig.collectAsState()
    val brakeMode by viewModel.brakeMode.collectAsState()
    val leftTelemetry by viewModel.leftMotorTelemetry.collectAsState()
    val rightTelemetry by viewModel.rightMotorTelemetry.collectAsState()
    val labMotorPowers by viewModel.labMotorPowers.collectAsState()
    val labLatchMode by viewModel.labLatchMode.collectAsState()

    val isConnected = connectionState is ConnectionState.Connected
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showSettingsSheet by remember { mutableStateOf(false) }
    var expandSettingsInPortrait by remember { mutableStateOf(true) }

    val (auxPort1, auxPort2) = remember(chassisConfig) {
        viewModel.getAuxiliaryPorts()
    }

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LandscapeTopBar(
                connectionState = connectionState,
                bondedDevices = bondedDevices,
                selectedDevice = selectedDevice,
                controlMode = controlMode,
                onSelectDevice = viewModel::selectDevice,
                onRefreshDevices = viewModel::refreshBondedDevices,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onSelectMode = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setControlMode(it)
                },
                onOpenSettings = { showSettingsSheet = true },
                onEmergencyStop = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.emergencyStop()
                }
            )

            when (controlMode) {
                ControlMode.JOYSTICK_360 -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                TelemetryHeader(
                                    chassisConfig = chassisConfig,
                                    leftPower = leftTelemetry,
                                    rightPower = rightTelemetry
                                )
                                VirtualJoystick360(
                                    enabled = isConnected,
                                    size = 190.dp,
                                    onMove = viewModel::onJoystickMove,
                                    onRelease = viewModel::stopChassis
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuxiliaryMotorsCard(
                                port1 = auxPort1,
                                port2 = auxPort2,
                                enabled = isConnected,
                                onDrivePort = viewModel::drivePortHold,
                                onStopPort = viewModel::stopPort
                            )
                            QuickSpeedAndSoundCard(
                                speed = speed,
                                isConnected = isConnected,
                                onSelectPreset = viewModel::selectSpeedPreset,
                                onSpeedChange = viewModel::setSpeed,
                                onBeep = viewModel::beep,
                                onRaceStart = viewModel::playRaceStartSignal
                            )
                        }
                    }
                }

                ControlMode.DPAD -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                TelemetryHeader(
                                    chassisConfig = chassisConfig,
                                    leftPower = leftTelemetry,
                                    rightPower = rightTelemetry
                                )
                                DPadControl(
                                    enabled = isConnected,
                                    buttonSize = 62.dp,
                                    onForwardDown = viewModel::moveForward,
                                    onBackwardDown = viewModel::moveBackward,
                                    onLeftDown = viewModel::turnLeft,
                                    onRightDown = viewModel::turnRight,
                                    onRelease = viewModel::stopChassis
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuxiliaryMotorsCard(
                                port1 = auxPort1,
                                port2 = auxPort2,
                                enabled = isConnected,
                                onDrivePort = viewModel::drivePortHold,
                                onStopPort = viewModel::stopPort
                            )
                            QuickSpeedAndSoundCard(
                                speed = speed,
                                isConnected = isConnected,
                                onSelectPreset = viewModel::selectSpeedPreset,
                                onSpeedChange = viewModel::setSpeed,
                                onBeep = viewModel::beep,
                                onRaceStart = viewModel::playRaceStartSignal
                            )
                        }
                    }
                }

                ControlMode.LAB_4_MOTORS -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        LabFourMotorsSection(
                            enabled = isConnected,
                            motorPowers = labMotorPowers,
                            invertPorts = invertPorts,
                            latchMode = labLatchMode,
                            onLatchModeChange = viewModel::setLabLatchMode,
                            onPowerChange = viewModel::setLabMotorPower,
                            onSliderRelease = viewModel::onLabSliderRelease,
                            onStopPort = viewModel::stopPort
                        )
                    }
                }
            }
        }
    } else {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🤖 EV3 Контроллер Pro",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Пульт управления LEGO Mindstorms EV3",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = { expandSettingsInPortrait = !expandSettingsInPortrait },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Настройки",
                        tint = if (expandSettingsInPortrait) AccentCyan else TextSecondary
                    )
                }
            }

            ConnectionCard(
                connectionState = connectionState,
                bondedDevices = bondedDevices,
                selectedDevice = selectedDevice,
                onSelectDevice = viewModel::selectDevice,
                onRefresh = viewModel::refreshBondedDevices,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect
            )

            ModeSelectorTabs(
                selectedMode = controlMode,
                onSelectMode = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setControlMode(it)
                }
            )

            when (controlMode) {
                ControlMode.JOYSTICK_360 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TelemetryHeader(
                                chassisConfig = chassisConfig,
                                leftPower = leftTelemetry,
                                rightPower = rightTelemetry
                            )

                            VirtualJoystick360(
                                enabled = isConnected,
                                size = 240.dp,
                                onMove = viewModel::onJoystickMove,
                                onRelease = viewModel::stopChassis
                            )

                            Text(
                                text = "Отпустите стик для мгновенной остановки шасси",
                                fontSize = 12.sp,
                                color = TextDim,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    AuxiliaryMotorsCard(
                        port1 = auxPort1,
                        port2 = auxPort2,
                        enabled = isConnected,
                        onDrivePort = viewModel::drivePortHold,
                        onStopPort = viewModel::stopPort
                    )
                }

                ControlMode.DPAD -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            TelemetryHeader(
                                chassisConfig = chassisConfig,
                                leftPower = leftTelemetry,
                                rightPower = rightTelemetry
                            )

                            DPadControl(
                                enabled = isConnected,
                                buttonSize = 76.dp,
                                onForwardDown = viewModel::moveForward,
                                onBackwardDown = viewModel::moveBackward,
                                onLeftDown = viewModel::turnLeft,
                                onRightDown = viewModel::turnRight,
                                onRelease = viewModel::stopChassis
                            )
                        }
                    }

                    AuxiliaryMotorsCard(
                        port1 = auxPort1,
                        port2 = auxPort2,
                        enabled = isConnected,
                        onDrivePort = viewModel::drivePortHold,
                        onStopPort = viewModel::stopPort
                    )
                }

                ControlMode.LAB_4_MOTORS -> {
                    LabFourMotorsSection(
                        enabled = isConnected,
                        motorPowers = labMotorPowers,
                        invertPorts = invertPorts,
                        latchMode = labLatchMode,
                        onLatchModeChange = viewModel::setLabLatchMode,
                        onPowerChange = viewModel::setLabMotorPower,
                        onSliderRelease = viewModel::onLabSliderRelease,
                        onStopPort = viewModel::stopPort
                    )
                }
            }

            EmergencyStopButton(
                enabled = isConnected,
                onEmergencyStop = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.emergencyStop()
                }
            )

            AnimatedVisibility(visible = expandSettingsInPortrait) {
                RoboticsSettingsPanel(
                    speed = speed,
                    invertPorts = invertPorts,
                    chassisConfig = chassisConfig,
                    brakeMode = brakeMode,
                    isConnected = isConnected,
                    onSelectSpeedPreset = viewModel::selectSpeedPreset,
                    onSpeedChange = viewModel::setSpeed,
                    onToggleInvertPort = viewModel::togglePortInversion,
                    onSelectChassisConfig = viewModel::setChassisConfig,
                    onBrakeModeChange = viewModel::setBrakeMode,
                    onBeep = viewModel::beep,
                    onRaceStart = viewModel::playRaceStartSignal
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = DarkBackground,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                RoboticsSettingsPanel(
                    speed = speed,
                    invertPorts = invertPorts,
                    chassisConfig = chassisConfig,
                    brakeMode = brakeMode,
                    isConnected = isConnected,
                    onSelectSpeedPreset = viewModel::selectSpeedPreset,
                    onSpeedChange = viewModel::setSpeed,
                    onToggleInvertPort = viewModel::togglePortInversion,
                    onSelectChassisConfig = viewModel::setChassisConfig,
                    onBrakeModeChange = viewModel::setBrakeMode,
                    onBeep = viewModel::beep,
                    onRaceStart = viewModel::playRaceStartSignal
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ModeSelectorTabs(
    selectedMode: ControlMode,
    onSelectMode: (ControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ControlMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            val bgColor by animateColorAsState(
                targetValue = if (selected) AccentCyan else Color.Transparent,
                animationSpec = tween(200),
                label = "tabBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) DarkBackground else TextSecondary,
                animationSpec = tween(200),
                label = "tabText"
            )
            val icon = when (mode) {
                ControlMode.JOYSTICK_360 -> Icons.Default.Gamepad
                ControlMode.DPAD -> Icons.Default.ControlCamera
                ControlMode.LAB_4_MOTORS -> Icons.Default.Science
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = mode.title,
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = mode.shortTitle,
                        color = textColor,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualJoystick360(
    enabled: Boolean,
    size: Dp,
    onMove: (Float, Float) -> Unit,
    onRelease: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var rawX by remember { mutableFloatStateOf(0f) }
    var rawY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isAtEdge by remember { mutableStateOf(false) }

    val animatedX by animateFloatAsState(
        targetValue = rawX,
        animationSpec = if (isDragging) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "joyX"
    )
    val animatedY by animateFloatAsState(
        targetValue = rawY,
        animationSpec = if (isDragging) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "joyY"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val centerX = this.size.width / 2f
                            val centerY = this.size.height / 2f
                            val maxRadius = (minOf(this.size.width, this.size.height) / 2f) * 0.78f
                            val dx = offset.x - centerX
                            val dy = offset.y - centerY
                            val dist = hypot(dx, dy)
                            val clampedDist = dist.coerceAtMost(maxRadius)
                            val angleRad = kotlin.math.atan2(dy, dx)
                            val finalDx = clampedDist * kotlin.math.cos(angleRad)
                            val finalDy = clampedDist * kotlin.math.sin(angleRad)

                            rawX = (finalDx / maxRadius).coerceIn(-1f, 1f)
                            rawY = (finalDy / maxRadius).coerceIn(-1f, 1f)
                            onMove(rawX, -rawY)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val centerX = this.size.width / 2f
                            val centerY = this.size.height / 2f
                            val maxRadius = (minOf(this.size.width, this.size.height) / 2f) * 0.78f
                            val dx = change.position.x - centerX
                            val dy = change.position.y - centerY
                            val dist = hypot(dx, dy)
                            val clampedDist = dist.coerceAtMost(maxRadius)
                            val angleRad = kotlin.math.atan2(dy, dx)
                            val finalDx = clampedDist * kotlin.math.cos(angleRad)
                            val finalDy = clampedDist * kotlin.math.sin(angleRad)

                            val normRatio = if (maxRadius > 0f) clampedDist / maxRadius else 0f
                            if (normRatio >= 0.96f && !isAtEdge) {
                                isAtEdge = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (normRatio < 0.90f) {
                                isAtEdge = false
                            }

                            rawX = (finalDx / maxRadius).coerceIn(-1f, 1f)
                            rawY = (finalDy / maxRadius).coerceIn(-1f, 1f)
                            onMove(rawX, -rawY)
                        },
                        onDragEnd = {
                            isDragging = false
                            isAtEdge = false
                            rawX = 0f
                            rawY = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRelease()
                        },
                        onDragCancel = {
                            isDragging = false
                            isAtEdge = false
                            rawX = 0f
                            rawY = 0f
                            onRelease()
                        }
                    )
                }
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = (minOf(this.size.width, this.size.height) / 2f) * 0.92f
            val maxStickRadius = (minOf(this.size.width, this.size.height) / 2f) * 0.75f
            val thumbRadius = outerRadius * 0.24f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(DarkSurfaceVariant.copy(alpha = 0.6f), DarkBackground),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            drawCircle(
                color = if (isDragging) AccentCyan else DarkBorder,
                radius = outerRadius,
                center = center,
                style = Stroke(width = if (isDragging) 3.dp.toPx() else 2.dp.toPx())
            )

            drawCircle(
                color = DarkBorder.copy(alpha = 0.4f),
                radius = maxStickRadius * 0.5f,
                center = center,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            )
            drawCircle(
                color = DarkBorder.copy(alpha = 0.5f),
                radius = maxStickRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            drawLine(
                color = DarkBorder.copy(alpha = 0.45f),
                start = Offset(center.x - outerRadius * 0.88f, center.y),
                end = Offset(center.x + outerRadius * 0.88f, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = DarkBorder.copy(alpha = 0.45f),
                start = Offset(center.x, center.y - outerRadius * 0.88f),
                end = Offset(center.x, center.y + outerRadius * 0.88f),
                strokeWidth = 1.dp.toPx()
            )

            val stickPos = Offset(
                x = center.x + animatedX * maxStickRadius,
                y = center.y + animatedY * maxStickRadius
            )

            if (hypot(animatedX, animatedY) > 0.03f) {
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(AccentCyan.copy(alpha = 0.4f), AccentOrange),
                        start = center,
                        end = stickPos
                    ),
                    start = center,
                    end = stickPos,
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val activeAccent = if (isAtEdge) AccentOrange else AccentCyan
            drawCircle(
                color = if (enabled) activeAccent.copy(alpha = if (isDragging) 0.35f else 0.18f)
                else TextDim.copy(alpha = 0.1f),
                radius = thumbRadius * 1.45f,
                center = stickPos
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (enabled) {
                        listOf(
                            if (isDragging) AccentOrange else AccentCyan,
                            if (isDragging) Color(0xFFC2410C) else AccentBlueDim
                        )
                    } else {
                        listOf(DarkSurfaceVariant, DarkBackground)
                    },
                    center = stickPos,
                    radius = thumbRadius
                ),
                radius = thumbRadius,
                center = stickPos
            )

            drawCircle(
                color = if (enabled) Color.White.copy(alpha = 0.7f) else TextDim,
                radius = thumbRadius,
                center = stickPos,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun TelemetryHeader(
    chassisConfig: ChassisConfig,
    leftPower: Int,
    rightPower: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBackground.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelemetryBadge(
            label = "Лев (${chassisConfig.leftPort.label})",
            power = leftPower
        )
        Text(
            text = "ШАССИ ${chassisConfig.shortLabel}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AccentCyan
        )
        TelemetryBadge(
            label = "Прав (${chassisConfig.rightPort.label})",
            power = rightPower
        )
    }
}

@Composable
private fun TelemetryBadge(label: String, power: Int) {
    val color = when {
        power > 0 -> AccentEmerald
        power < 0 -> AccentOrange
        else -> TextSecondary
    }
    val sign = if (power > 0) "+" else ""
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Text(
            text = "$sign$power%",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Composable
private fun DPadControl(
    enabled: Boolean,
    buttonSize: Dp,
    onForwardDown: () -> Unit,
    onBackwardDown: () -> Unit,
    onLeftDown: () -> Unit,
    onRightDown: () -> Unit,
    onRelease: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        DPadButton(
            icon = Icons.Default.KeyboardArrowUp,
            label = "Вперёд",
            enabled = enabled,
            accentColor = AccentEmerald,
            onDown = onForwardDown,
            onUp = onRelease,
            modifier = Modifier.size(width = buttonSize * 1.35f, height = buttonSize)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DPadButton(
                icon = Icons.Default.RotateLeft,
                label = "Влево",
                enabled = enabled,
                accentColor = AccentCyan,
                onDown = onLeftDown,
                onUp = onRelease,
                modifier = Modifier.size(width = buttonSize * 1.25f, height = buttonSize)
            )

            Box(
                modifier = Modifier
                    .size(buttonSize * 0.72f)
                    .clip(CircleShape)
                    .background(DarkBackground)
                    .border(1.dp, DarkBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Games,
                    contentDescription = null,
                    tint = AccentCyan.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            DPadButton(
                icon = Icons.Default.RotateRight,
                label = "Вправо",
                enabled = enabled,
                accentColor = AccentCyan,
                onDown = onRightDown,
                onUp = onRelease,
                modifier = Modifier.size(width = buttonSize * 1.25f, height = buttonSize)
            )
        }

        DPadButton(
            icon = Icons.Default.KeyboardArrowDown,
            label = "Назад",
            enabled = enabled,
            accentColor = AccentOrange,
            onDown = onBackwardDown,
            onUp = onRelease,
            modifier = Modifier.size(width = buttonSize * 1.35f, height = buttonSize)
        )
    }
}

@Composable
private fun DPadButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    accentColor: Color,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = tween(90),
        label = "dpadScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isPressed && enabled) accentColor else DarkSurfaceVariant,
        animationSpec = tween(90),
        label = "dpadBg"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) bgColor else DarkSurfaceVariant.copy(alpha = 0.4f))
            .border(
                width = 1.5.dp,
                color = if (isPressed && enabled) Color.White else accentColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp)
            )
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDown()
                                tryAwaitRelease()
                                isPressed = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUp()
                            }
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) {
                    if (isPressed) DarkBackground else TextPrimary
                } else TextDim,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    if (isPressed) DarkBackground else TextSecondary
                } else TextDim
            )
        }
    }
}

@Composable
private fun AuxiliaryMotorsCard(
    port1: Ev3Port,
    port2: Ev3Port,
    enabled: Boolean,
    onDrivePort: (Ev3Port, Boolean) -> Unit,
    onStopPort: (Ev3Port) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🦾 Механизмы и манипуляторы",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Удерживайте кнопку",
                    fontSize = 11.sp,
                    color = TextDim
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AuxPortControlColumn(
                    port = port1,
                    subtitle = "Манипулятор / Клешня",
                    enabled = enabled,
                    onDriveForward = { onDrivePort(port1, true) },
                    onDriveBackward = { onDrivePort(port1, false) },
                    onStop = { onStopPort(port1) },
                    modifier = Modifier.weight(1f)
                )

                AuxPortControlColumn(
                    port = port2,
                    subtitle = "Доп. привод / Орудие",
                    enabled = enabled,
                    onDriveForward = { onDrivePort(port2, true) },
                    onDriveBackward = { onDrivePort(port2, false) },
                    onStop = { onStopPort(port2) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AuxPortControlColumn(
    port: Ev3Port,
    subtitle: String,
    enabled: Boolean,
    onDriveForward: () -> Unit,
    onDriveBackward: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkBackground.copy(alpha = 0.65f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Порт ${port.label}",
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AccentCyan
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HoldActionButton(
                text = "▲ Вперёд",
                color = AccentEmerald,
                enabled = enabled,
                onDown = onDriveForward,
                onUp = onStop,
                modifier = Modifier.weight(1f)
            )
            HoldActionButton(
                text = "▼ Назад",
                color = AccentOrange,
                enabled = enabled,
                onDown = onDriveBackward,
                onUp = onStop,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HoldActionButton(
    text: String,
    color: Color,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(90),
        label = "holdScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isPressed && enabled) color else color.copy(alpha = 0.18f),
        animationSpec = tween(90),
        label = "holdBg"
    )

    Box(
        modifier = modifier
            .height(54.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) bgColor else DarkSurfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 1.5.dp,
                color = if (isPressed && enabled) Color.White else color.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDown()
                                tryAwaitRelease()
                                isPressed = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUp()
                            }
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) {
                if (isPressed) DarkBackground else color
            } else TextDim,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LabFourMotorsSection(
    enabled: Boolean,
    motorPowers: Map<Ev3Port, Int>,
    invertPorts: Map<Ev3Port, Boolean>,
    latchMode: Boolean,
    onLatchModeChange: (Boolean) -> Unit,
    onPowerChange: (Ev3Port, Int) -> Unit,
    onSliderRelease: (Ev3Port) -> Unit,
    onStopPort: (Ev3Port) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎛️ Лаборатория (4 независимых мотора)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Независимое тестирование портов A, B, C, D от -100% до +100%",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBackground.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (latchMode) "🔒 Режим рычагов: Фиксация скорости"
                    else "🧲 Режим рычагов: Возврат в 0% при отпускании",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (latchMode) AccentAmber else AccentCyan,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = latchMode,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLatchModeChange(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = AccentAmber,
                        uncheckedThumbColor = TextPrimary,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }

            Ev3Port.entries.forEach { port ->
                val power = motorPowers[port] ?: 0
                val isInverted = invertPorts[port] == true
                val accentColor = when {
                    power > 0 -> AccentEmerald
                    power < 0 -> AccentOrange
                    else -> AccentCyan
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkBackground.copy(alpha = 0.6f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Порт ${port.label}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            if (isInverted) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ИНВЕРСИЯ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentOrange,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AccentOrange.copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val sign = if (power > 0) "+" else ""
                            Text(
                                text = "$sign$power%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStopPort(port)
                                },
                                enabled = enabled,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Стоп 0%", fontSize = 11.sp, color = AccentRed)
                            }
                        }
                    }

                    Slider(
                        value = power.toFloat(),
                        onValueChange = { newVal ->
                            val rounded = (newVal / 5f).roundToInt() * 5
                            onPowerChange(port, rounded)
                        },
                        onValueChangeFinished = {
                            onSliderRelease(port)
                        },
                        enabled = enabled,
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun RoboticsSettingsPanel(
    speed: Int,
    invertPorts: Map<Ev3Port, Boolean>,
    chassisConfig: ChassisConfig,
    brakeMode: Boolean,
    isConnected: Boolean,
    onSelectSpeedPreset: (SpeedPreset) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onToggleInvertPort: (Ev3Port) -> Unit,
    onSelectChassisConfig: (ChassisConfig) -> Unit,
    onBrakeModeChange: (Boolean) -> Unit,
    onBeep: () -> Unit,
    onRaceStart: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ Ограничитель скорости",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$speed%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCyan
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedPreset.entries.forEach { preset ->
                        val isSelected = speed == preset.speedPercent
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectSpeedPreset(preset)
                            },
                            label = {
                                Text(
                                    text = preset.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCyan.copy(alpha = 0.25f),
                                selectedLabelColor = AccentCyan,
                                containerColor = DarkBackground,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Slider(
                    value = speed.toFloat(),
                    onValueChange = { onSpeedChange(it.roundToInt()) },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "⚙️ Конфигурация шасси и моторов",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Быстрая инверсия портов (если шестерёнки вращаются в обратную сторону):",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Ev3Port.entries.forEach { port ->
                            val inverted = invertPorts[port] == true
                            FilterChip(
                                selected = inverted,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleInvertPort(port)
                                },
                                label = {
                                    Text(
                                        text = "Инв. ${port.label}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentOrange.copy(alpha = 0.25f),
                                    selectedLabelColor = AccentOrange,
                                    containerColor = DarkBackground,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Порты колёс шасси (Левый + Правый):",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChassisConfig.entries.forEach { config ->
                            val selected = chassisConfig == config
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectChassisConfig(config)
                                },
                                label = {
                                    Text(
                                        text = config.shortLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentEmerald.copy(alpha = 0.25f),
                                    selectedLabelColor = AccentEmerald,
                                    containerColor = DarkBackground,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkBackground)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (brakeMode) "🛑 Режим остановки: Жёсткий тормоз (Brake)"
                            else "🛞 Режим остановки: Плавный накат (Coast)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (brakeMode) AccentEmerald else AccentAmber
                        )
                        Text(
                            text = if (brakeMode) "Моторы мгновенно фиксируют вал при отпускании"
                            else "Моторы свободно катятся по инерции при отпускании",
                            fontSize = 11.sp,
                            color = TextDim
                        )
                    }
                    Switch(
                        checked = brakeMode,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBrakeModeChange(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = AccentEmerald,
                            uncheckedThumbColor = TextPrimary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "🔊 Звуковая панель блока EV3",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBeep()
                        },
                        enabled = isConnected,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPurple,
                            disabledContainerColor = DarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "🔊 Тест связи (Писк)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRaceStart()
                        },
                        enabled = isConnected,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmber,
                            contentColor = DarkBackground,
                            disabledContainerColor = DarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "🏁 Сигнал старта гонки",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isConnected) DarkBackground else TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSpeedAndSoundCard(
    speed: Int,
    isConnected: Boolean,
    onSelectPreset: (SpeedPreset) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onBeep: () -> Unit,
    onRaceStart: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡ Скорость: $speed%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpeedPreset.entries.forEach { preset ->
                        val active = speed == preset.speedPercent
                        Text(
                            text = "${preset.speedPercent}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) DarkBackground else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) AccentCyan else DarkBackground)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectPreset(preset)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Slider(
                value = speed.toFloat(),
                onValueChange = { onSpeedChange(it.roundToInt()) },
                valueRange = 10f..100f,
                modifier = Modifier.height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AccentCyan,
                    activeTrackColor = AccentCyan,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBeep,
                    enabled = isConnected,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("🔊 Писк", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onRaceStart,
                    enabled = isConnected,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "🏁 Старт гонки",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isConnected) DarkBackground else TextDim
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ConnectionCard(
    connectionState: ConnectionState,
    bondedDevices: List<BluetoothDevice>,
    selectedDevice: BluetoothDevice?,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var showDeviceMenu by remember { mutableStateOf(false) }

    val (statusColor, statusText) = when (connectionState) {
        is ConnectionState.Disconnected -> AccentRed to "Отключено от блока EV3"
        is ConnectionState.Connecting -> AccentAmber to "Установка связи по Bluetooth SPP..."
        is ConnectionState.Connected -> AccentEmerald to "Подключено: ${connectionState.deviceName}"
        is ConnectionState.Error -> AccentRed to connectionState.message
    }

    val animatedStatusColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(350),
        label = "btStatusColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = animatedStatusColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(animatedStatusColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = statusText,
                    color = animatedStatusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            onRefresh()
                            showDeviceMenu = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(listOf(AccentCyan, AccentBlueDim))
                        )
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedDevice?.name ?: "Выбрать блок EV3...",
                            color = if (selectedDevice != null) TextPrimary else TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp
                        )
                    }

                    DropdownMenu(
                        expanded = showDeviceMenu,
                        onDismissRequest = { showDeviceMenu = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        if (bondedDevices.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Нет сопряжённых устройств (выполните поиск в настройках Bluetooth)",
                                        color = TextDim,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = { showDeviceMenu = false }
                            )
                        } else {
                            bondedDevices.forEach { device ->
                                val name = device.name ?: "Неизвестное устройство"
                                val isEv3 = name.contains("EV3", ignoreCase = true)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (isEv3) "🤖 " else "📱 ", fontSize = 15.sp)
                                            Column {
                                                Text(
                                                    text = name,
                                                    color = if (isEv3) AccentEmerald else TextPrimary,
                                                    fontWeight = if (isEv3) FontWeight.ExtraBold else FontWeight.Normal,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = device.address,
                                                    color = TextDim,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectDevice(device)
                                        showDeviceMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                val isConnected = connectionState is ConnectionState.Connected
                val isConnecting = connectionState is ConnectionState.Connecting

                Button(
                    onClick = { if (isConnected) onDisconnect() else onConnect() },
                    enabled = selectedDevice != null && !isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) AccentRed else AccentEmerald,
                        disabledContainerColor = DarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isConnected) "Отключить" else "Подключить",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isConnected) Color.White else DarkBackground,
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkBackground)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Обновить список",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun LandscapeTopBar(
    connectionState: ConnectionState,
    bondedDevices: List<BluetoothDevice>,
    selectedDevice: BluetoothDevice?,
    controlMode: ControlMode,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onRefreshDevices: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectMode: (ControlMode) -> Unit,
    onOpenSettings: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    val statusDotColor = when (connectionState) {
        is ConnectionState.Connected -> AccentEmerald
        is ConnectionState.Connecting -> AccentAmber
        is ConnectionState.Error -> AccentRed
        is ConnectionState.Disconnected -> AccentRed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkBackground)
                    .clickable {
                        onRefreshDevices()
                        showMenu = true
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedDevice?.name ?: "EV3 Bluetooth",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                bondedDevices.forEach { dev ->
                    DropdownMenuItem(
                        text = { Text(dev.name ?: dev.address, color = TextPrimary) },
                        onClick = {
                            onSelectDevice(dev)
                            showMenu = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = { if (isConnected) onDisconnect() else onConnect() },
            enabled = selectedDevice != null && !isConnecting,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) DarkSurfaceVariant else AccentEmerald
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (isConnected) "Откл." else "Подкл.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) TextPrimary else DarkBackground
            )
        }

        ModeSelectorTabs(
            selectedMode = controlMode,
            onSelectMode = onSelectMode,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkBackground)
        ) {
            Icon(Icons.Default.Tune, contentDescription = "Настройки", tint = AccentCyan)
        }

        Button(
            onClick = onEmergencyStop,
            enabled = isConnected,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentRed,
                disabledContainerColor = Color(0xFF4A1D1D)
            ),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(38.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "🛑 СТОП ВСЁ",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun EmergencyStopButton(
    enabled: Boolean,
    onEmergencyStop: () -> Unit
) {
    Button(
        onClick = onEmergencyStop,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .border(
                width = 2.dp,
                color = if (enabled) AccentRedBright else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentRed,
            disabledContainerColor = Color(0xFF3B1A22)
        ),
        shape = RoundedCornerShape(18.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
    ) {
        Text(
            text = "🛑 СТОП ВСЁ (ЭКСТРЕННЫЙ ТОРМОЗ)",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (enabled) Color.White else TextDim
        )
    }
}
