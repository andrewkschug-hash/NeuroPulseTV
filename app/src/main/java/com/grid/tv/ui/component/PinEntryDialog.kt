package com.grid.tv.ui.component

import com.grid.tv.ui.component.GlowFocusButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import kotlinx.coroutines.launch

@Composable
fun PinEntryDialog(
    profileName: String,
    title: String = "Enter PIN",
    subtitle: String? = null,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
    verifyPin: suspend (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(Color(0xFF1A1A28))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle ?: profileName,
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
            Row(
                modifier = Modifier.offset(x = shakeOffset.value.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (i < pin.length) EpgColors.Accent else Color(0xFF2A2A3A))
                    )
                }
            }
            PinNumberPad(
                onDigit = { d ->
                    if (pin.length < 4) {
                        pin += d
                        if (pin.length == 4) {
                            scope.launch {
                                if (verifyPin(pin)) {
                                    onVerified()
                                } else {
                                    repeat(3) {
                                        shakeOffset.animateTo(12f, tween(50))
                                        shakeOffset.animateTo(-12f, tween(50))
                                    }
                                    shakeOffset.animateTo(0f, tween(50))
                                    pin = ""
                                }
                            }
                        }
                    }
                },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
            )
            GlowFocusButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = DmSansFamily)
            }
        }
    }
}

@Composable
private fun PinNumberPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.size(64.dp))
                    } else {
                        GlowFocusButton(
                            onClick = {
                                if (key == "⌫") onBackspace() else onDigit(key)
                            },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Text(key, fontFamily = DmSansFamily, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
