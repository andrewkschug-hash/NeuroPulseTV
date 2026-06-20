package com.grid.tv.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.ui.component.DestructiveConfirmDialog
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.component.ProfileAvatarBadge
import com.grid.tv.ui.component.TvScrollContainer
import com.grid.tv.ui.component.TvTextInputDialog
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.MAX_HOUSEHOLD_PROFILES
import com.grid.tv.util.profileInitials

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ManageProfilesOverlay(
    profiles: List<UserProfile>,
    activeProfileId: Long?,
    onDismiss: () -> Unit,
    onCreateProfile: (name: String) -> Unit,
    onRenameProfile: (profileId: Long, name: String) -> Unit,
    onDeleteProfile: (profileId: Long) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<UserProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<UserProfile?>(null) }
    var newProfileName by remember { mutableStateOf("") }
    var renameValue by remember { mutableStateOf("") }
    val closeFocusRequester = remember { FocusRequester() }
    val atProfileLimit = profiles.size >= MAX_HOUSEHOLD_PROFILES

    BackHandler {
        when {
            showAddDialog -> showAddDialog = false
            renameTarget != null -> renameTarget = null
            deleteTarget != null -> deleteTarget = null
            else -> onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .focusable()
            .focusProperties { canFocus = true },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .background(EpgColors.GridBg, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Manage profiles",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Add, rename, or remove household profiles without leaving Settings.",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )

            TvScrollContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profiles.forEach { profile ->
                    val isActive = profile.id == activeProfileId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EpgColors.ChannelColumnBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileAvatarBadge(
                            initials = profileInitials(profile.name),
                            colorHex = profile.avatarColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                color = EpgColors.TextPrimary,
                                fontFamily = DmSansFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = when {
                                    isActive && profile.isParental -> "Current · Parental"
                                    isActive -> "Current profile"
                                    profile.isParental -> "Parental"
                                    else -> "Household member"
                                },
                                color = EpgColors.TextSecondary,
                                fontFamily = DmSansFamily,
                                fontSize = 12.sp
                            )
                        }
                        GlowFocusButton(onClick = {
                            renameValue = profile.name
                            renameTarget = profile
                        }) {
                            Text("Rename", fontFamily = DmSansFamily, fontSize = 14.sp)
                        }
                        if (profiles.size > 1) {
                            GlowFocusButton(onClick = { deleteTarget = profile }) {
                                Text("Delete", fontFamily = DmSansFamily, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            if (atProfileLimit) {
                Text(
                    text = "Up to $MAX_HOUSEHOLD_PROFILES profiles per household",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!atProfileLimit) {
                    GlowFocusButton(onClick = {
                        newProfileName = ""
                        showAddDialog = true
                    }) {
                        Text("Add profile", fontFamily = DmSansFamily)
                    }
                }
                GlowFocusButton(
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(closeFocusRequester)
                ) {
                    Text("Done", fontFamily = DmSansFamily)
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        closeFocusRequester.requestFocusSafelyAfterLayout()
    }

    if (showAddDialog) {
        TvTextInputDialog(
            label = "Create profile",
            value = newProfileName,
            placeholder = "Enter a name",
            confirmLabel = "Create",
            onConfirm = { entered ->
                val name = entered.ifBlank { "Profile ${profiles.size + 1}" }
                onCreateProfile(name)
                newProfileName = ""
                showAddDialog = false
            },
            onDismiss = {
                newProfileName = ""
                showAddDialog = false
            }
        )
    }

    renameTarget?.let { profile ->
        TvTextInputDialog(
            label = "Rename profile",
            value = renameValue,
            placeholder = profile.name,
            confirmLabel = "Save",
            onConfirm = { entered ->
                val name = entered.trim().ifBlank { profile.name }
                onRenameProfile(profile.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { profile ->
        DestructiveConfirmDialog(
            title = "Delete profile?",
            message = "Delete \"${profile.name}\" and its favorites, watch history, and settings? This cannot be undone.",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteProfile(profile.id)
                deleteTarget = null
            }
        )
    }
}
