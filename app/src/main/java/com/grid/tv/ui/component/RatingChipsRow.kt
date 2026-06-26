package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

data class RatingChip(
    val source: String,
    val value: String
)

@Composable
fun RatingChipsRow(
    chips: List<RatingChip>,
    modifier: Modifier = Modifier
) {
    if (chips.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { chip ->
            Text(
                text = "${chip.source} ${chip.value}",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(EpgColors.DetailPanelBg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

fun buildRatingChips(
    providerRating: String?,
    tmdbRating: Double?,
    imdbRating: String? = null,
    rottenTomatoes: String? = null,
    metacritic: String? = null
): List<RatingChip> = buildList {
    imdbRating?.takeIf { it.isNotBlank() }?.let { add(RatingChip("IMDb", it)) }
    rottenTomatoes?.takeIf { it.isNotBlank() }?.let { add(RatingChip("RT", it)) }
    metacritic?.takeIf { it.isNotBlank() }?.let { add(RatingChip("MC", it)) }
    tmdbRating?.takeIf { it > 0 }?.let { add(RatingChip("TMDB", String.format("%.1f", it))) }
    providerRating?.takeIf { it.isNotBlank() }?.let { add(RatingChip("★", it)) }
}
