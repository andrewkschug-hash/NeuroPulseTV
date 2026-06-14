package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

@Composable
fun VodHubScreen(
    initialTab: Int = 0,
    initialSeriesId: Long? = null,
    onPlayMovie: (String, String) -> Unit,
    onPlayUrl: (String, String) -> Unit,
    onBack: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp)
        ) {
            Text("← Back", fontFamily = DmSansFamily)
        }
        Text(
            text = "VOD",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Movies", "Series").forEachIndexed { index, label ->
                Button(onClick = { tab = index }) {
                    Text(
                        text = label,
                        fontFamily = DmSansFamily,
                        fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (tab) {
                0 -> MoviesBrowserScreen(
                    onPlayMovie = onPlayMovie,
                    embedded = true
                )
                else -> SeriesBrowserScreen(
                    initialSeriesId = initialSeriesId,
                    onPlayUrl = onPlayUrl,
                    embedded = true
                )
            }
        }
    }
}
