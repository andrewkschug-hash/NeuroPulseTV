package com.grid.tv.feature.subtitles

import android.util.TypedValue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleStyleApplicator @Inject constructor() {
    fun apply(playerView: PlayerView?, settings: AppSettings) {
        val subtitleView = playerView?.subtitleView ?: return
        val textSizeSp = when (settings.subtitleFontSize) {
            SubtitleFontSize.SMALL -> 14f
            SubtitleFontSize.MEDIUM -> 18f
            SubtitleFontSize.LARGE -> 22f
        }
        subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setStyle(CaptionStyleCompat.DEFAULT)
        val bottomPadding = when (settings.subtitlePosition) {
            SubtitlePosition.BOTTOM -> 0.08f
            SubtitlePosition.MIDDLE -> 0.42f
            SubtitlePosition.TOP -> 0.82f
        }
        subtitleView.setBottomPaddingFraction(bottomPadding)
    }
}
