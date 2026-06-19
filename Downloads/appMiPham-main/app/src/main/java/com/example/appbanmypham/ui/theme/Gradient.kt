package com.example.appbanmypham.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
object AppGradients {
    val mintHorizontal = Brush.horizontalGradient(
        colors = listOf(MintGreen, MintLight)
    )
    val heroVertical = Brush.verticalGradient(
        colors = listOf(PinkLight, BackgroundHero)
    )
    val buttonPink = Brush.horizontalGradient(
        colors = listOf(PinkPrimary, PinkDark)
    )
}