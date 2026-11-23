package com.tianl.key.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianl.key.R
import com.tianl.key.model.ConnectionStatus
import com.tianl.key.ui.theme.CardBackground
import com.tianl.key.ui.theme.CardBorder
import com.tianl.key.ui.theme.GreenLight
import com.tianl.key.ui.theme.GreenPrimary
import com.tianl.key.ui.theme.TextMuted
import com.tianl.key.ui.theme.TextPrimary
import com.tianl.key.ui.theme.TextSecondary

@Composable
fun BatteryAndWirelessRow(
    connectionStatus: ConnectionStatus,
    battery: Int,
    rssi: Int,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBluetoothClick: () -> Unit
) {
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED
    val isConnecting = connectionStatus == ConnectionStatus.CONNECTING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Battery Card
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRefreshClick
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = if (isConnected) "$battery" else "--",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFE9EDF2))
                ) {
                    val progressFraction = if (isConnected) (battery.coerceIn(0, 100) / 100f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isConnected) GreenPrimary else Color.Transparent)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isConnected) "车辆剩余电量" else "等待连接车辆",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Wireless Overview Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(CardBackground)
                    .border(1.dp, CardBorder, CircleShape)
                    .clickable(onClick = onRefreshClick),
                contentAlignment = Alignment.Center
            ) {
                SignalBars(isConnected = isConnected, rssi = rssi)
            }

            // Settings button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(CardBackground)
                    .border(1.dp, CardBorder, CircleShape)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = "重命名设置",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Bluetooth button
            val infiniteTransition = rememberInfiniteTransition(label = "bluetoothRotation")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotate"
            )

            val bgModifier = when {
                isConnected -> Modifier.background(GreenLight).border(1.dp, GreenPrimary, CircleShape)
                isConnecting -> Modifier.background(Color(0xFFE3F2FD)).border(1.dp, Color(0xFF2196F3), CircleShape)
                else -> Modifier.background(CardBackground).border(1.dp, CardBorder, CircleShape)
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .then(bgModifier)
                    .clickable(onClick = onBluetoothClick),
                contentAlignment = Alignment.Center
            ) {
                val iconRes = when {
                    isConnected -> R.drawable.bluetooth_connected
                    isConnecting -> R.drawable.bluetooth_connecting
                    else -> R.drawable.bluetooth
                }
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = "蓝牙状态",
                    modifier = Modifier
                        .size(20.dp)
                        .then(if (isConnecting) Modifier.rotate(angle) else Modifier)
                )
            }
        }
    }
}

@Composable
fun SignalBars(isConnected: Boolean, rssi: Int) {
    val activeColor = if (isConnected) GreenPrimary else Color(0xFFC4CBD4)
    val inactiveColor = Color(0xFFE2E7ED)

    val activeBars = when {
        !isConnected -> 0
        rssi >= -65 -> 3
        rssi >= -80 -> 2
        else -> 1
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (activeBars >= 1) activeColor else inactiveColor)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (activeBars >= 2) activeColor else inactiveColor)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (activeBars >= 3) activeColor else inactiveColor)
        )
    }
}
