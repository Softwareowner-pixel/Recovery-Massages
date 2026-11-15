package com.example.messagevault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AVATAR_COLORS = listOf(
    Color(0xFF128C7E), Color(0xFF5E35B1), Color(0xFFD84315), Color(0xFF1565C0),
    Color(0xFF2E7D32), Color(0xFFAD1457), Color(0xFF6D4C41), Color(0xFF00838F)
)

/** Round coloured circle with the first letter of the name (like a contact photo placeholder). */
@Composable
fun Avatar(name: String, size: Dp = 48.dp) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val color = AVATAR_COLORS[(name.hashCode() and 0x7fffffff) % AVATAR_COLORS.size]
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
