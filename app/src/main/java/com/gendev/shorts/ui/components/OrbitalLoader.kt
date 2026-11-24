package com.gendev.shorts.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gendev.shorts.R
import com.gendev.shorts.ui.theme.ElectricViolet
import com.gendev.shorts.ui.theme.ElectricVioletBright
import com.gendev.shorts.ui.theme.PureWhite
import com.gendev.shorts.ui.theme.SlateMuted
import com.gendev.shorts.ui.theme.SpaceGroteskFontFamily

@Composable
fun OrbitalLoadingIndicator(
    label: String? = null,
    size: Dp = 54.dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "orbital_loader")
    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outer_angle",
    )
    val innerAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "inner_angle",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeOuter = 3.dp.toPx()
            val strokeInner = 2.dp.toPx()

            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                style = Stroke(width = strokeOuter),
            )

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, ElectricViolet, ElectricVioletBright),
                ),
                startAngle = outerAngle,
                sweepAngle = 220f,
                useCenter = false,
                style = Stroke(width = strokeOuter, cap = StrokeCap.Round),
            )

            val inset = size.toPx() * 0.22f
            drawArc(
                color = PureWhite.copy(alpha = 0.85f),
                startAngle = innerAngle,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    width = size.toPx() - inset * 2f,
                    height = size.toPx() - inset * 2f,
                ),
                style = Stroke(width = strokeInner, cap = StrokeCap.Round),
            )
        }
        if (!label.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted,
            )
        }
    }
}

@Composable
fun GendevWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(ElectricVioletBright, ElectricViolet),
                    ),
                ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.brand_gendev),
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 0.8.sp,
            color = PureWhite,
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = stringResource(R.string.brand_shorts),
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp,
            color = ElectricVioletBright,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ElectricViolet.copy(alpha = 0.18f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
