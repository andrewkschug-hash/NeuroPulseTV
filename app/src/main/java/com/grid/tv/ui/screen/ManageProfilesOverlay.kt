package com.grid.tv.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.ui.component.DestructiveConfirmDialog
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.component.GlowFocusButtonText
import com.grid.tv.ui.component.GridFocusSurface
import com.grid.tv.ui.component.ProfileAvatarBadge
import com.grid.tv.ui.component.TvScrollContainer
import com.grid.tv.ui.component.TvTextInputDialog
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.tvFocusBorder
import com.grid.tv.ui.component.tvFocusScrollIntoView
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.MAX_HOUSEHOLD_PROFILES
import com.grid.tv.util.profileInitials

private val ModalTextPrimary = Color(0xFFFFFFFF)
private val ModalTextSecondary = Color(0xFFE0E0E0)
private val RowShape = RoundedCornerShape(10.dp)
private val RowRestBg = Color(0xFF1A1A28)
private val RowFocusBg = Color(0x30FFFFFF)
private val RowFocusBorder = EpgColors.FocusBorder

private data class ManageProfileRowFocus(
    val row: FocusRequester = FocusRequester(),
    val rename: FocusRequester = FocusRequester(),
    val delete: FocusRequester = FocusRequester(),
)

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
    var renameRestoreFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var deleteTarget by remember { mutableStateOf<UserProfile?>(null) }
    var newProfileName by remember { mutableStateOf("") }
    var renameValue by remember { mutableStateOf("") }
    val atProfileLimit = profiles.size >= MAX_HOUSEHOLD_PROFILES
    val canDelete = profiles.size > 1

    val rowFocus = remember(profiles.size, canDelete) {
        List(profiles.size) { ManageProfileRowFocus() }
    }
    val addProfileFocusRequester = remember { FocusRequester() }
    val doneFocusRequester = remember { FocusRequester() }
    val modalTrapFocusRequester = remember { FocusRequester() }

    val initialFocusRequester = remember(profiles.size, atProfileLimit) {
        when {
            profiles.isNotEmpty() -> rowFocus.first().row
            !atProfileLimit -> addProfileFocusRequester
            else -> doneFocusRequester
        }
    }

    BackHandler {
        when {
            showAddDialog -> showAddDialog = false
            renameTarget != null -> {
                renameTarget = null
                renameRestoreFocusRequester = null
            }
            deleteTarget != null -> deleteTarget = null
            else -> onDismiss()
        }
    }

    LaunchedEffect(profiles.size, atProfileLimit) {
        initialFocusRequester.requestFocusSafelyAfterLayout(50)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(680.dp)
                .background(Color(0xFF14141E), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .focusGroup()
                .focusRequester(modalTrapFocusRequester),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Manage profiles",
                color = ModalTextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Add, rename, or remove household profiles without leaving Settings.",
                color = ModalTextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )

            TvScrollContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                profiles.forEachIndexed { index, profile ->
                    val isActive = profile.id == activeProfileId
                    val focus = rowFocus[index]
                    val previousRow = rowFocus.getOrNull(index - 1)
                    val nextRow = rowFocus.getOrNull(index + 1)
                    val upTarget = previousRow?.row ?: FocusRequester.Cancel
                    val downTarget = nextRow?.row ?: when {
                        !atProfileLimit -> addProfileFocusRequester
                        else -> doneFocusRequester
                    }

                    ManageProfileRow(
                        profile = profile,
                        isActive = isActive,
                        canDelete = canDelete,
                        rowFocusRequester = focus.row,
                        renameFocusRequester = focus.rename,
                        deleteFocusRequester = focus.delete,
                        upTarget = upTarget,
                        downTarget = downTarget,
                        onRename = {
                            renameValue = profile.name
                            renameTarget = profile
                            renameRestoreFocusRequester = focus.rename
                        },
                        onDelete = { deleteTarget = profile }
                    )
                }
            }

            if (atProfileLimit) {
                Text(
                    text = "Up to $MAX_HOUSEHOLD_PROFILES profiles per household",
                    color = ModalTextSecondary,
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
                    GlowFocusButton(
                        onClick = {
                            newProfileName = ""
                            showAddDialog = true
                        },
                        modifier = Modifier
                            .focusRequester(addProfileFocusRequester)
                            .focusProperties {
                                up = rowFocus.lastOrNull()?.row ?: FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                right = doneFocusRequester
                            }
                            .tvFocusScrollIntoView()
                    ) {
                        GlowFocusButtonText(text = "Add profile", color = ModalTextPrimary)
                    }
                }
                GlowFocusButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .focusRequester(doneFocusRequester)
                        .focusProperties {
                            up = rowFocus.lastOrNull()?.row ?: addProfileFocusRequester
                            down = FocusRequester.Cancel
                            left = if (!atProfileLimit) addProfileFocusRequester else FocusRequester.Cancel
                        }
                        .tvFocusScrollIntoView()
                ) {
                    GlowFocusButtonText(text = "Done", color = ModalTextPrimary)
                }
            }
        }
    }

    if (showAddDialog) {
        TvTextInputDialog(
            label = "Create profile",
            value = newProfileName,
            placeholder = "Enter a name",
            confirmLabel = "Create",
            restoreFocusRequester = addProfileFocusRequester,
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
            restoreFocusRequester = renameRestoreFocusRequester,
            onConfirm = { entered ->
                val name = entered.trim().ifBlank { profile.name }
                onRenameProfile(profile.id, name)
                renameTarget = null
                renameRestoreFocusRequester = null
            },
            onDismiss = {
                renameTarget = null
                renameRestoreFocusRequester = null
            }
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ManageProfileRow(
    profile: UserProfile,
    isActive: Boolean,
    canDelete: Boolean,
    rowFocusRequester: FocusRequester,
    renameFocusRequester: FocusRequester,
    deleteFocusRequester: FocusRequester,
    upTarget: FocusRequester,
    downTarget: FocusRequester,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var rowFocused by remember { mutableStateOf(false) }
    var renameFocused by remember { mutableStateOf(false) }
    var deleteFocused by remember { mutableStateOf(false) }
    val anyFocused = rowFocused || renameFocused || deleteFocused
    val scale by animateFloatAsState(
        targetValue = if (anyFocused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "manageProfileRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = if (anyFocused) RowFocusBg else RowRestBg,
                shape = RowShape
            )
            .border(
                width = if (anyFocused) 2.dp else 1.dp,
                color = if (anyFocused) RowFocusBorder else Color.White.copy(alpha = 0.08f),
                shape = RowShape
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .focusGroup()
            .tvFocusScrollIntoView(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ManageProfileRowIdentity(
            modifier = Modifier.weight(1f),
            profile = profile,
            isActive = isActive,
            focused = rowFocused,
            focusRequester = rowFocusRequester,
            upTarget = upTarget,
            downTarget = downTarget,
            renameTarget = renameFocusRequester,
            onFocusChanged = { rowFocused = it }
        )

        GlowFocusButton(
            onClick = onRename,
            modifier = Modifier
                .focusRequester(renameFocusRequester)
                .focusProperties {
                    left = rowFocusRequester
                    right = if (canDelete) deleteFocusRequester else FocusRequester.Cancel
                    up = upTarget
                    down = downTarget
                }
                .onFocusChanged { renameFocused = it.isFocused }
        ) {
            GlowFocusButtonText(text = "Rename", color = ModalTextPrimary)
        }

        if (canDelete) {
            GlowFocusButton(
                onClick = onDelete,
                modifier = Modifier
                    .focusRequester(deleteFocusRequester)
                    .focusProperties {
                        left = renameFocusRequester
                        right = FocusRequester.Cancel
                        up = upTarget
                        down = downTarget
                    }
                    .onFocusChanged { deleteFocused = it.isFocused }
            ) {
                GlowFocusButtonText(text = "Delete", color = ModalTextPrimary)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ManageProfileRowIdentity(
    modifier: Modifier = Modifier,
    profile: UserProfile,
    isActive: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester,
    upTarget: FocusRequester,
    downTarget: FocusRequester,
    renameTarget: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
) {
    val subtitle = when {
        isActive && profile.isParental -> "Current · Parental"
        isActive -> "Current profile"
        profile.isParental -> "Parental"
        else -> "Household member"
    }

    GridFocusSurface(
        onClick = {
            renameTarget.requestFocus()
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties {
                right = renameTarget
                up = upTarget
                down = downTarget
            }
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .tvFocusBorder(
                focused = focused,
                shape = RoundedCornerShape(8.dp),
                unfocusedColor = Color.Transparent,
                focusedColor = RowFocusBorder
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.06f),
            pressedContainerColor = Color.White.copy(alpha = 0.10f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileAvatarBadge(
                initials = profileInitials(profile.name),
                colorHex = profile.avatarColor
            )
            Column {
                Text(
                    text = profile.name,
                    color = ModalTextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = ModalTextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp
                )
            }
        }
    }
}
