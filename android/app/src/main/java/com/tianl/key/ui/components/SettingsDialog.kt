package com.tianl.key.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianl.key.ble.BleConfig
import com.tianl.key.ui.theme.CardBackground
import com.tianl.key.ui.theme.CardBorder
import com.tianl.key.ui.theme.DangerRed
import com.tianl.key.ui.theme.GreenDark
import com.tianl.key.ui.theme.GreenLight
import com.tianl.key.ui.theme.GreenPrimary
import com.tianl.key.ui.theme.TextMuted
import com.tianl.key.ui.theme.TextPrimary
import com.tianl.key.ui.theme.TextSecondary
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentName: String,
    currentSecret: String,
    vehicleType: String,
    isConnected: Boolean,
    isRenaming: Boolean,
    onRename: (String) -> Unit,
    onSaveSecret: (String) -> Unit,
    onSelectVehicleType: (String) -> Unit,
    onForgetVehicle: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var renameSuffix by remember(currentName) {
        mutableStateOf(BleConfig.extractSuffix(currentName))
    }
    var secretInput by remember(currentSecret) {
        mutableStateOf(currentSecret)
    }
    var showSecretPlain by remember { mutableStateOf(false) }

    val suffixBytes = remember(renameSuffix) {
        renameSuffix.toByteArray(StandardCharsets.UTF_8).size
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SETTINGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "钥匙设置",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F3F6))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 14.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Rename Section
            Text(
                text = "车辆重命名 (修改前缀 TianlKey- 后的名称)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = renameSuffix,
                    onValueChange = { if (it.toByteArray(StandardCharsets.UTF_8).size <= 12) renameSuffix = it },
                    singleLine = true,
                    enabled = isConnected && !isRenaming,
                    placeholder = { Text("例如：我的车") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = CardBorder
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = { onRename(renameSuffix) },
                    enabled = isConnected && !isRenaming && renameSuffix.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    if (isRenaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("保存")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (!isConnected) "需连接蓝牙后才可修改" else "将同步保存到 ESP32 NVS",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Text(
                    text = "$suffixBytes/12 字节",
                    fontSize = 11.sp,
                    color = if (suffixBytes > 12) DangerRed else TextMuted
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Secret Key Section
            Text(
                text = "配对密钥 (与 ESP32 固件中一致)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it.trim() },
                    singleLine = true,
                    placeholder = { Text("至少 6 位密钥") },
                    visualTransformation = if (showSecretPlain) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = CardBorder
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = { onSaveSecret(secretInput) },
                    enabled = secretInput.length >= 6,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Text("保存")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Vehicle Appearance Section
            Text(
                text = "车型外观主视觉",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large
                val isLargeSelected = vehicleType == "large"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isLargeSelected) GreenLight else CardBackground)
                        .border(
                            1.dp,
                            if (isLargeSelected) GreenPrimary else CardBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectVehicleType("large") }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "大车外观",
                        fontSize = 14.sp,
                        fontWeight = if (isLargeSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLargeSelected) GreenDark else TextPrimary
                    )
                }

                // Compact
                val isCompactSelected = vehicleType == "compact"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isCompactSelected) GreenLight else CardBackground)
                        .border(
                            1.dp,
                            if (isCompactSelected) GreenPrimary else CardBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectVehicleType("compact") }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "小车外观",
                        fontSize = 14.sp,
                        fontWeight = if (isCompactSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCompactSelected) GreenDark else TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Forget Vehicle Button
            Button(
                onClick = onForgetVehicle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFEBEE),
                    contentColor = DangerRed
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("移除这辆车", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
