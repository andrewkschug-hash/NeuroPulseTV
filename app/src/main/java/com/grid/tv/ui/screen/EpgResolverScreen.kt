package com.grid.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.data.db.entity.EpgResolutionSuggestionEntity
import com.grid.tv.domain.epg.EpgFixProposal
import com.grid.tv.domain.epg.EpgMatchReason
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.EpgResolverViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PanelShape = RoundedCornerShape(12.dp)
private val RowShape = RoundedCornerShape(10.dp)
private val PanelBg = Color(0xFF1C1C28)
private val RowBg = Color(0xFF252836)
private val FixRowBg = Color(0xFF1E2A24)
private val SuccessGreen = Color(0xFF4ADE80)
private val WarningAmber = Color(0xFFFBBF24)
private val ErrorRed = Color(0xFFF87171)
private val DialogBg = Color(0xFF14141E)

@Composable
fun EpgResolverScreen(viewModel: EpgResolverViewModel = hiltViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val unresolved by viewModel.unresolved.collectAsStateWithLifecycle()
    val manualCandidates by viewModel.manualCandidates.collectAsStateWithLifecycle()
    val fixProposals by viewModel.fixProposals.collectAsStateWithLifecycle()
    val fixScanning by viewModel.fixScanning.collectAsStateWithLifecycle()
    val channelNames by viewModel.channelNames.collectAsStateWithLifecycle()

    var manualTargetChannelId by remember { mutableLongStateOf(-1L) }
    var manualQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "EPG Matching & Repair",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Match channels to guide data and fix missing EPG entries.",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item {
            ResolverPanel(title = "Channel overview") {
                StatLine("Total channels", "${summary.totalChannels}")
                StatLine(
                    label = "EPG matched",
                    value = "${summary.matched} (${"%.1f".format(summary.matchRatePercent)}%)",
                    valueColor = SuccessGreen
                )
                StatLine(
                    label = "Awaiting confirmation",
                    value = "${summary.awaitingConfirmation}",
                    valueColor = WarningAmber
                )
                StatLine(
                    label = "Unresolved",
                    value = "${summary.unresolved}",
                    valueColor = ErrorRed
                )
                val last = if (summary.lastResolvedAt > 0) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(summary.lastResolvedAt))
                } else {
                    "Never"
                }
                StatLine("Last resolved", last)
            }
        }

        item {
            ResolverPanel(title = "Fix missing guide data") {
                Text(
                    text = "Scan channels that are missing guide data and apply suggested fixes.",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    ResolverButton(
                        text = "Scan for Fixes",
                        onClick = { viewModel.scanForGuideFixes() },
                        enabled = !running && !fixScanning
                    )
                    ResolverButton(
                        text = "Apply All (${fixProposals.size})",
                        onClick = { viewModel.applyAllGuideFixes() },
                        enabled = fixProposals.isNotEmpty() && !running
                    )
                    ResolverButton(
                        text = "Clear",
                        onClick = { viewModel.clearGuideFixes() },
                        enabled = fixProposals.isNotEmpty()
                    )
                }
                if (fixScanning) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 14.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = EpgColors.Accent,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Scanning channels for missing guide data…",
                            color = EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        items(fixProposals) { proposal ->
            FixProposalRow(proposal)
        }

        item {
            ResolverPanel(title = "Auto-match") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResolverButton(
                        text = "Run Full Auto-Match",
                        onClick = { viewModel.runResolver() },
                        enabled = !running
                    )
                    ResolverButton(
                        text = "Cancel",
                        onClick = { viewModel.cancelResolver() },
                        enabled = running
                    )
                }
            }
        }

        if (progress != null) {
            item {
                val p = progress!!
                val fraction = if (p.total <= 0) 0f else p.completed.toFloat() / p.total.toFloat()
                ResolverPanel(title = "Matching in progress") {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = EpgColors.Accent,
                        trackColor = Color(0xFF3A3A4A)
                    )
                    Text(
                        text = "Resolving: ${p.currentChannel}",
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = "${p.autoMatched} auto-matched · ${p.suggested} need input · ${p.failed} unresolved",
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item {
            ResolverPanel(title = "Match analytics") {
                AnalyticsLine("Auto match rate", "${"%.1f".format(analytics.matchRatePercent)}%")
                AnalyticsLine("Unmatched rate", "${"%.1f".format(analytics.unmatchedRatePercent)}%")
                AnalyticsLine("Manual correction rate", "${"%.1f".format(analytics.manualCorrectionRatePercent)}%")
                AnalyticsLine(
                    "Match sources",
                    "TVG-ID: ${analytics.tvgIdMatches} · Learned: ${analytics.learnedMatches} · Canonical: ${analytics.canonicalMatches}"
                )
                AnalyticsLine(
                    "Name matching",
                    "Exact: ${analytics.exactNameMatches} · Fuzzy: ${analytics.fuzzyMatches}"
                )
                if (analytics.topAliases.isNotEmpty()) {
                    AnalyticsLine(
                        "Common aliases",
                        analytics.topAliases.take(5).joinToString { it.first }
                    )
                }
            }
        }

        item {
            ResolverPanel(title = "Needs your confirmation") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${suggestions.size} suggestion(s) waiting",
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    ResolverButton(
                        text = "Accept All",
                        onClick = { viewModel.acceptAll() },
                        enabled = suggestions.isNotEmpty()
                    )
                }
            }
        }

        items(suggestions) { s ->
            SuggestionRow(
                item = s,
                channelName = channelNames[s.channelId.toLongOrNull() ?: -1L],
                onAccept = { viewModel.acceptSuggestion(s) },
                onDismiss = { viewModel.dismissSuggestion(s) }
            )
        }

        if (unresolved.isNotEmpty()) {
            item {
                Text(
                    text = "Unresolved channels",
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        items(unresolved) { channel ->
            UnresolvedRow(
                channel = channel,
                onManualAssign = {
                    manualTargetChannelId = channel.id
                    manualQuery = channel.name
                    viewModel.searchManualCandidates(channel.name)
                }
            )
        }
    }

    if (manualTargetChannelId > 0) {
        AlertDialog(
            onDismissRequest = { manualTargetChannelId = -1L },
            containerColor = DialogBg,
            titleContentColor = EpgColors.TextPrimary,
            textContentColor = EpgColors.TextSecondary,
            title = {
                Text(
                    text = "Manual EPG assignment",
                    fontFamily = DmSansFamily,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = manualQuery,
                        onValueChange = {
                            manualQuery = it
                            viewModel.searchManualCandidates(it)
                        },
                        label = {
                            Text(
                                "Search EPG channel",
                                color = EpgColors.TextSecondary,
                                fontFamily = DmSansFamily
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = EpgColors.TextPrimary,
                            fontFamily = DmSansFamily,
                            fontSize = 15.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = EpgColors.TextPrimary,
                            unfocusedTextColor = EpgColors.TextPrimary,
                            focusedBorderColor = EpgColors.FocusBorder,
                            unfocusedBorderColor = Color(0xFF4A4A5A),
                            cursorColor = EpgColors.Accent,
                            focusedLabelColor = EpgColors.TextSecondary,
                            unfocusedLabelColor = EpgColors.TextDimmed
                        )
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(manualCandidates) { candidate ->
                            ResolverButton(
                                text = "${candidate.displayName} (${candidate.source})",
                                onClick = {
                                    viewModel.applyManual(
                                        manualTargetChannelId,
                                        candidate.epgId,
                                        candidate.displayName,
                                        candidate.source
                                    )
                                    manualTargetChannelId = -1L
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                ResolverButton(text = "Close", onClick = { manualTargetChannelId = -1L })
            }
        )
    }
}

@Composable
private fun ResolverPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelBg, PanelShape)
            .border(1.dp, EpgColors.BorderSubtle.copy(alpha = 0.35f), PanelShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = {
            Text(
                text = title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            content()
        }
    )
}

@Composable
private fun StatLine(
    label: String,
    value: String,
    valueColor: Color = EpgColors.TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AnalyticsLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ResolverButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    GlowFocusButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        containerColor = if (enabled) Color(0xFF2E2E3E) else Color(0xFF1E1E28)
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FixProposalRow(proposal: EpgFixProposal) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FixRowBg, RowShape)
            .border(1.dp, SuccessGreen.copy(alpha = 0.25f), RowShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = proposal.channelName,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "→ ${proposal.proposedEpgName}",
            color = SuccessGreen,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        Text(
            text = "${proposal.confidence}% · ${reasonLabel(proposal.reason)}",
            color = WarningAmber,
            fontFamily = DmSansFamily,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SuggestionRow(
    item: EpgResolutionSuggestionEntity,
    channelName: String?,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RowBg, RowShape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = channelName ?: "Channel ${item.channelId}",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "→ ${item.suggestedEpgName}",
                color = SuccessGreen,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
            Text(
                text = "${item.confidence}% · ${safeReasonLabel(item.matchReason)}",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )
            Text(
                text = item.source,
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 12.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResolverButton(text = "Accept", onClick = onAccept)
            ResolverButton(text = "Dismiss", onClick = onDismiss)
        }
    }
}

@Composable
private fun UnresolvedRow(
    channel: ChannelEntity,
    onManualAssign: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RowBg, RowShape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = channel.name,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        ResolverButton(text = "Manual Assign", onClick = onManualAssign)
    }
}

private fun reasonLabel(reason: EpgMatchReason): String = reason.label

private fun safeReasonLabel(raw: String): String =
    runCatching { reasonLabel(EpgMatchReason.valueOf(raw)) }.getOrDefault(raw)
