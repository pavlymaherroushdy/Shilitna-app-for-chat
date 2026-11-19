package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOut
import com.example.data.model.SquadRoom
import com.example.data.model.UiState
import com.example.ui.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
  viewModel: ChatViewModel
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val roomsState by viewModel.roomsState.collectAsStateWithLifecycle()
  val newlyCreatedRoom by viewModel.newlyCreatedRoomForInvite.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  var showCreateDialog by remember { mutableStateOf(false) }
  var showJoinLinkDialog by remember { mutableStateOf(false) }

  LaunchedEffect(statusMessage) {
    val msg = statusMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
              )
              if (viewModel.isPlatformAdmin) {
                Surface(
                  color = MaterialTheme.colorScheme.tertiaryContainer,
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text(
                    text = "مشرف",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
              }
            }
            Text(
              text = "أهلاً، ${viewModel.currentUserName}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        },
        actions = {
          IconButton(
            onClick = { signOut(credentialManager, scope) },
            modifier = Modifier.testTag("sign_out_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Logout,
              contentDescription = "تسجيل الخروج",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxSize()
          .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Primary Actions Card
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
          ),
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Button(
              onClick = { showCreateDialog = true },
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("create_group_button")
            ) {
              Icon(imageVector = Icons.Rounded.GroupAdd, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(id = R.string.create_group_invite),
                style = MaterialTheme.typography.labelLarge
              )
            }

            OutlinedButton(
              onClick = { showJoinLinkDialog = true },
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("join_invite_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(id = R.string.join_by_link),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
              )
            }
          }
        }

        Text(
          text = "غرف الشلة الخاصة بك",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground
        )

        when (val state = roomsState) {
          is UiState.Loading -> {
            Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
          }
          is UiState.Error -> {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp)
              )
            }
          }
          is UiState.Success -> {
            if (state.data.isEmpty()) {
              EmptyRoomsCard(onCreateClick = { showCreateDialog = true })
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                items(state.data, key = { it.id }) { room ->
                  RoomItemCard(
                    room = room,
                    isAdmin = viewModel.isUserAdminFor(room),
                    onClick = { viewModel.openRoomFromLobby(room) }
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateGroupDialog(
      defaultPin = remember { viewModel.generateRandomPin() },
      isBusy = isBusy,
      onDismiss = { showCreateDialog = false },
      onCreate = { name, pin ->
        showCreateDialog = false
        viewModel.createSquadRoom(name, pin)
      }
    )
  }

  if (showJoinLinkDialog) {
    JoinByLinkDialog(
      onDismiss = { showJoinLinkDialog = false },
      onJoin = { rawLink ->
        showJoinLinkDialog = false
        viewModel.handleInviteInput(rawLink)
      }
    )
  }

  val inviteRoom = newlyCreatedRoom
  if (inviteRoom != null) {
    val inviteUrl = viewModel.buildInviteLink(inviteRoom.id)
    val whatsAppText = viewModel.buildWhatsAppInviteText(inviteRoom)
    AlertDialog(
      onDismissRequest = { viewModel.dismissNewlyCreatedInvite() },
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      title = {
        Text(
          text = "تم إنشاء غرفة «${inviteRoom.name}» 🎉",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "شارك رابط الدعوة مع شلتك عبر واتساب. عند فتحهم للرابط أول مرة سيظهر لهم الرمز السري لحفظه، وفي الزيارات التالية سيُطلب منهم إدخاله.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            shape = RoundedCornerShape(14.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "الرمز السري للغرفة: ${inviteRoom.pin}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary
              )
              Text(
                text = inviteUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
              )
            }
          }
          Button(
            onClick = { shareViaWhatsApp(context, whatsAppText) },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("share_whatsapp_button")
          ) {
            Icon(imageVector = Icons.Rounded.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.share_whatsapp), style = MaterialTheme.typography.labelLarge)
          }
          OutlinedButton(
            onClick = {
              clipboard.setText(AnnotatedString(inviteUrl))
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("copy_invite_link_button")
          ) {
            Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("نسخ رابط الدعوة", style = MaterialTheme.typography.labelLarge)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.dismissNewlyCreatedInvite()
            viewModel.openRoomFromLobby(inviteRoom)
          },
          modifier = Modifier.testTag("enter_created_room_button")
        ) {
          Text("دخول الغرفة الآن")
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissNewlyCreatedInvite() }) {
          Text("إغلاق")
        }
      }
    )
  }
}

@Composable
private fun RoomItemCard(
  room: SquadRoom,
  isAdmin: Boolean,
  onClick: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
      .clip(RoundedCornerShape(20.dp))
      .clickable(onClick = onClick)
      .testTag("room_card_${room.id}")
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(
            if (isAdmin) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.primaryContainer
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Lock,
          contentDescription = null,
          tint = if (isAdmin) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = room.name,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = if (isAdmin) "أنت المشرف • دخول مباشر بدون رمز" else "مشرف الغرفة: ${room.adminName} • محمية بالرمز",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(10.dp)
      ) {
        Text(
          text = "${room.memberUids.size} أعضاء",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
      }
    }
  }
}

@Composable
private fun EmptyRoomsCard(onCreateClick: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
  ) {
    Column(
      modifier = Modifier.padding(24.dp).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(
        imageVector = Icons.Rounded.Forum,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(44.dp)
      )
      Text(
        text = "لا توجد غرف دردشة بعد",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = "أنشئ غرفة جديدة لشلتك الآن وشارك رابط الدعوة عبر واتساب، أو افتح رابط دعوة وصلك من صديق.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Button(
        onClick = onCreateClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.height(48.dp)
      ) {
        Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("إنشاء أول غرفة")
      }
    }
  }
}

@Composable
private fun CreateGroupDialog(
  defaultPin: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onCreate: (String, String) -> Unit
) {
  var groupName by remember { mutableStateOf("") }
  var pin by remember { mutableStateOf(defaultPin) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    title = { Text("إنشاء غرفة شلة جديدة", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = groupName,
          onValueChange = { if (it.length <= 60) groupName = it },
          label = { Text("اسم المجموعة (مثال: شلة السهرة)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_room_name_input")
        )
        OutlinedTextField(
          value = pin,
          onValueChange = { if (it.length <= 12) pin = it },
          label = { Text("الرمز السري (4 - 12 خانة)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_room_pin_input")
        )
        Text(
          text = "بصفتك منشئ الغرفة ستدخل دائماً مباشرة دون مطالبتك بالرمز السري.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreate(groupName, pin) },
        enabled = !isBusy && groupName.isNotBlank() && pin.trim().length in 4..12,
        modifier = Modifier.testTag("confirm_create_room_button")
      ) {
        Text("إنشاء وتوليد الرابط")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("إلغاء") }
    }
  )
}

@Composable
private fun JoinByLinkDialog(
  onDismiss: () -> Unit,
  onJoin: (String) -> Unit
) {
  var linkInput by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    title = { Text("فتح رابط دعوة", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "الصق رابط الدعوة الذي وصلك عبر واتساب أو رمز الغرفة:",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
          value = linkInput,
          onValueChange = { linkInput = it },
          placeholder = { Text("https://shillachat.app/join?room=...") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("join_link_input")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onJoin(linkInput) },
        enabled = linkInput.isNotBlank(),
        modifier = Modifier.testTag("confirm_join_link_button")
      ) {
        Text("فتح الدعوة")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("إلغاء") }
    }
  )
}

fun shareViaWhatsApp(context: Context, message: String) {
  val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, message)
    setPackage("com.whatsapp")
  }
  try {
    context.startActivity(whatsappIntent)
  } catch (_: ActivityNotFoundException) {
    val chooserIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(chooserIntent, "مشاركة رابط الدعوة"))
  }
}
