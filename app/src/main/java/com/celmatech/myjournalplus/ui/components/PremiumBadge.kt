package com.celmatech.myjournalplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celmatech.myjournalplus.ui.theme.PremiumGold

@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Text(
        text = "✦ Premium",
        modifier = modifier
            .background(PremiumGold.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = PremiumGold,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}
