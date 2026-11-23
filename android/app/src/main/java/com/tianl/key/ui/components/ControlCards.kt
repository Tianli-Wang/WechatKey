package com.tianl.key.ui.components

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianl.key.ui.theme.CardBackground
import com.tianl.key.ui.theme.CardBorder
import com.tianl.key.ui.theme.GreenDark
import com.tianl.key.ui.theme.GreenLight
import com.tianl.key.ui.theme.GreenPrimary
import com.tianl.key.ui.theme.TextMuted
import com.tianl.key.ui.theme.TextPrimary
import com.tianl.key.ui.theme.TextSecondary

@Composable
fun ControlSection(
    isConnected: Boolean,
    isPowered: Boolean,
    lastAction: String,
    busyCommand: String?,
    onPowerToggle: () -> Unit,
    onFindVehicle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "REMOTE CONTROL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "车辆控制",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Text(
                text = lastAction,
                fontSize = 13.sp,
                color = GreenDark,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Grid of 2 Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Power Card
            val isPowerBusy = busyCommand == "power_on" || busyCommand == "power_off"
            val powerBg = if (isPowered) GreenLight else CardBackground
            val powerBorder = if (isPowered) GreenPrimary else CardBorder

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(powerBg)
                    .border(1.dp, powerBorder, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isPowerBusy,
                        onClick = onPowerToggle
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isPowered) GreenPrimary else Color(0xFFF1F3F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⏻",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPowered) Color.White else TextPrimary
                            )
                        }

                        if (isPowerBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = GreenPrimary
                            )
                        } else {
                            Text(
                                text = "↗",
                                fontSize = 16.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isPowered) "关闭车辆" else "启动车辆",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPowered) GreenDark else TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val descText = when {
                        !isConnected -> "点击连接车辆"
                        isPowered -> "轻触关闭动力"
                        else -> "安全确认后启动"
                    }
                    Text(
                        text = descText,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Find Vehicle Card
            val isFindBusy = busyCommand == "find"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBackground)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isFindBusy,
                        onClick = onFindVehicle
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F3F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⌖",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        if (isFindBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = GreenPrimary
                            )
                        } else {
                            Text(
                                text = "↗",
                                fontSize = 16.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "寻车",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "鸣笛并闪灯",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
