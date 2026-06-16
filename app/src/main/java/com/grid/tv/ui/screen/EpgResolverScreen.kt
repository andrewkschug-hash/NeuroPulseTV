package com.grid.tv.ui.screen

import com.grid.tv.ui.component.GlowFocusButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.tv.material3.Text
import com.grid.tv.data.db.entity.EpgResolutionSuggestionEntity
import com.grid.tv.data.db.entity.EpgSourceChannelEntity
import com.grid.tv.ui.viewmodel.EpgResolverViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgResolverScreen(viewModel: EpgResolverViewModel = hiltViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val unresolved by viewModel.unresolved.collectAsStateWithLifecycle()
    val manualCandidates by viewModel.manualCandidates.collectAsStateWithLifecycle()

    var manualTargetChannelId by remember { mutableLongStateOf(-1L) }
    var manualQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("EPG Auto-Resolve Channels") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Total channels: ${summary.totalChannels}")
                Text("EPG matched: ${summary.matched}", color = Color(0xFF30C85A))
                Text("Awaiting confirmation: ${summary.awaitingConfirmation}", color = Color(0xFFF3BF2D))
                Text("Unresolved: ${summary.unresolved}", color = Color(0xFFE84C4C))
                val last = if (summary.lastResolvedAt > 0) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(summary.lastResolvedAt))
                } else {
                    "Never"
                }
                Text("Last resolved: $last")
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowFocusButton(onClick = { viewModel.runResolver() }, enabled = !running) { Text("Run Auto-Resolver") }
                GlowFocusButton(onClick = { viewModel.cancelResolver() }, enabled = running) { Text("Cancel") }
            }
        }

        if (progress != null) {
            item {
                val p = progress!!
                val fraction = if (p.total <= 0) 0f else p.completed.toFloat() / p.total.toFloat()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text("Resolving: ${p.currentChannel}")
                    Text("${p.autoMatched} auto-matched, ${p.suggested} need your input, ${p.failed} unresolved")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Needs Your Confirmation")
                GlowFocusButton(onClick = { viewModel.acceptAll() }, enabled = suggestions.isNotEmpty()) { Text("Accept All") }
            }
        }

        items(suggestions) { s ->
            SuggestionRow(
                item = s,
                onAccept = { viewModel.acceptSuggestion(s) },
                onDismiss = { viewModel.dismissSuggestion(s) }
            )
        }

        item { Text("Unresolved Channels") }
        items(unresolved) { channel ->
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2A2A2A)).padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(channel.name)
                GlowFocusButton(onClick = {
                    manualTargetChannelId = channel.id
                    manualQuery = channel.name
                    viewModel.searchManualCandidates(channel.name)
                }) { Text("Manual Assign") }
            }
        }
    }

    if (manualTargetChannelId > 0) {
        AlertDialog(
            onDismissRequest = { manualTargetChannelId = -1L },
            title = { Text("Manual EPG Assignment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualQuery,
                        onValueChange = {
                            manualQuery = it
                            viewModel.searchManualCandidates(it)
                        },
                        label = { Text("Search EPG channel") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(manualCandidates) { candidate ->
                            GlowFocusButton(onClick = {
                                viewModel.applyManual(manualTargetChannelId, candidate.epgId, candidate.source)
                                manualTargetChannelId = -1L
                            }) {
                                Text("${candidate.displayName} (${candidate.source})")
                            }
                        }
                    }
                }
            },
            confirmButton = { GlowFocusButton(onClick = { manualTargetChannelId = -1L }) { Text("Close") } }
        )
    }
}

@Composable
private fun SuggestionRow(
    item: EpgResolutionSuggestionEntity,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF2A2A2A)).padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Channel ${item.channelId} -> ${item.suggestedEpgName}")
            Text("${item.confidence}% match", color = Color(0xFFF3BF2D))
            Text(item.source)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowFocusButton(onClick = onAccept) { Text("Accept") }
            GlowFocusButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
