package com.tianl.key.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianl.key.model.ConnectionStatus
import com.tianl.key.ui.components.BatteryAndWirelessRow
import com.tianl.key.ui.components.ControlSection
import com.tianl.key.ui.components.DeviceScanDialog
import com.tianl.key.ui.components.SettingsDialog
import com.tianl.key.ui.components.VehicleStage
import com.tianl.key.ui.theme.AmbientBlue
import com.tianl.key.ui.theme.AmbientPurple
import com.tianl.key.ui.theme.BackgroundEnd
import com.tianl.key.ui.theme.BackgroundMid
import com.tianl.key.ui.theme.BackgroundStart
import com.tianl.key.ui.theme.GreenPrimary
import com.tianl.key.ui.theme.TextMuted
import com.tianl.key.ui.theme.TextPrimary
import com.tianl.key.ui.theme.TextSecondary
import com.tianl.key.viewmodel.UiAlert
import com.tianl.key.viewmodel.VehicleViewModel

@Composable
fun MainScreen(viewModel: VehicleViewModel) {
    val context = LocalContext.current

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()

    val vehicleState by viewModel.vehicleState.collectAsState()
    val currentName by viewModel.currentName.collectAsState()
    val secret by viewModel.secret.collectAsState()
    val vehicleType by viewModel.vehicleType.collectAsState()
    val busyCommand by viewModel.busyCommand.collectAsState()

    val showScanDialog by viewModel.showScanDialog.collectAsState()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsState()
    val showPowerConfirmDialog by viewModel.showPowerConfirmDialog.collectAsState()

    var activeAlert by remember { mutableStateOf<UiAlert?>(null) }

    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

    // Handle Toast messages
    LaunchedEffect(Unit) {
        viewModel.uiToast.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Handle Alert dialogs
    LaunchedEffect(Unit) {
        viewModel.uiAlert.collect { alert ->
            activeAlert = alert
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BackgroundStart, BackgroundMid, BackgroundEnd)
                )
            )
    ) {
        // Ambient soft light blobs
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = (-60).dp, y = 80.dp)
                .background(AmbientPurple, shape = CircleShape)
                .blur(40.dp)
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = 30.dp)
                .background(AmbientBlue, shape = CircleShape)
                .blur(40.dp)
        )

        // Main Scroll Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 1. Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.openScanDialog() }
                        )
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "▼",
                        fontSize = 9.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Battery & Wireless Row
            BatteryAndWirelessRow(
                connectionStatus = connectionStatus,
                battery = vehicleState.battery,
                rssi = vehicleState.rssi,
                onRefreshClick = { viewModel.refreshStatus() },
                onSettingsClick = { viewModel.openRenameSettings() },
                onBluetoothClick = { viewModel.handleBluetoothConnectionClick() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Central Vehicle Stage
            VehicleStage(
                vehicleType = vehicleType,
                isConnected = isConnected
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Remote Control Cards
            ControlSection(
                isConnected = isConnected,
                isPowered = vehicleState.powered,
                lastAction = vehicleState.message,
                busyCommand = busyCommand,
                onPowerToggle = { viewModel.togglePower() },
                onFindVehicle = { viewModel.findVehicle() }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 5. Safety Reminder Note
            Text(
                text = "请在确认车辆周围安全后启动动力",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Dialogs
        if (showScanDialog) {
            DeviceScanDialog(
                isScanning = isScanning,
                devices = discoveredDevices,
                onDeviceSelected = { device -> viewModel.selectDevice(device) },
                onDismiss = { viewModel.closeScanDialog() }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                currentName = currentName,
                currentSecret = secret,
                vehicleType = vehicleType,
                isConnected = isConnected,
                isRenaming = busyCommand == "rename",
                onRename = { suffix -> viewModel.renameVehicle(suffix) },
                onSaveSecret = { newSecret -> viewModel.saveSecret(newSecret) },
                onSelectVehicleType = { type -> viewModel.setVehicleType(type) },
                onForgetVehicle = { viewModel.forgetVehicle() },
                onDismiss = { viewModel.closeSettingsDialog() }
            )
        }

        if (showPowerConfirmDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPowerConfirm() },
                title = {
                    Text("启动车辆", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text("请确认车辆已停稳且周围安全。启动后车辆将进入可骑行状态。")
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmPowerOn() },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Text("确认启动")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissPowerConfirm() }) {
                        Text("取消", color = TextSecondary)
                    }
                }
            )
        }

        activeAlert?.let { alert ->
            AlertDialog(
                onDismissRequest = { activeAlert = null },
                title = { Text(alert.title, fontWeight = FontWeight.Bold) },
                text = { Text(alert.message) },
                confirmButton = {
                    Button(
                        onClick = { activeAlert = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Text("知道了")
                    }
                }
            )
        }
    }
}
