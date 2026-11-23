package com.tianl.key.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianl.key.R

@Composable
fun VehicleStage(
    vehicleType: String,
    isConnected: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp),
        contentAlignment = Alignment.Center
    ) {
        // Watermark "RIDE"
        Text(
            text = "RIDE",
            fontSize = 90.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFE9ECF1),
            letterSpacing = 8.sp,
            modifier = Modifier.offset(y = (-40).dp)
        )

        // Vehicle Floor Shadow & Glow
        Box(
            modifier = Modifier
                .size(width = 240.dp, height = 30.dp)
                .offset(y = 80.dp)
                .background(Color(0x1A000000), shape = CircleShape)
                .blur(8.dp)
        )

        // Vehicle Image
        val imageRes = if (vehicleType == "compact") {
            R.drawable.scooter_compact
        } else {
            R.drawable.scooter
        }

        Image(
            painter = painterResource(id = imageRes),
            contentDescription = "车辆外观",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(if (vehicleType == "compact") 0.68f else 0.82f)
                .height(240.dp)
                .offset(y = (-5).dp)
                .alpha(if (isConnected) 1.0f else 0.82f)
        )
    }
}
