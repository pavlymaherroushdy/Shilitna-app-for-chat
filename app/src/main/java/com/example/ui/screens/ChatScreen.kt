package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.MediaProcessor
import com.example.data.model.ChatMessage
import com.example.data.model.RoomMember
import com.example.data.model.SquadRoom
import com.example.data.model.UiState
import com.example.games.MiniGamesHubScreen
import com.example.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val QuickEmojis = listOf("😂", "🔥", "❤️", "👍", "🥳", "🙌", "😎", "🤝", "🎉", "✨", "💬", "🎮", "📸", "🤣")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  viewModel: ChatViewModel,
  initialRoom: SquadRoom,
  onBackToLobby: () -> Unit
) {
  BackHandler(onBack = onBackToLobby)

  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  val liveRoom by viewModel.activeRoomLive.collectAsStateWithLifecycle()
  val room = liveRoom ?: initialRoom
  val isAdmin = viewModel.isUserAdminFor(room)

  val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
  val membersState by viewModel.membersState.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  val listState = rememberLazyListState()

  var messageText by remember { mutableStateOf("") }
  var showEmojiTray by remember { mutableStateOf(false) }
  var showMoreMenu by remember { mutableStateOf(false) }
  var showRoomManagementSheet by remember { mutableStateOf(false) }
  var showMembersSheet by remember { mutableStateOf(false) }
  var showMiniGamesSheet by remember { mutableStateOf(false) }
  var fullscreenImageBase64 by remember { mutableStateOf<String?>(null) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) viewModel.sendPickedImage(context, uri)
  }

  val videoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) viewModel.sendPickedVideo(context, uri)
  }

  LaunchedEffect(statusMessage) {
    val msg = statusMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  val onlineCount = when (val mState = membersState) {
    is UiState.Success -> mState.data.count { it.online }
    else -> 0
  }

  val messageCount = (messagesState as? UiState.Success)?.data?.size ?: 0
  LaunchedEffect(messageCount) {
    if (messageCount > 0) {
      listState.animateScrollToItem(messageCount - 1)
    }
  }

  if (showMiniGamesSheet) {
    val memberNames = (membersState as? UiState.Success)?.data?.map { it.displayName }.orEmpty()
    MiniGamesHubScreen(
      memberNames = memberNames,
      onClose = { showMiniGamesSheet = false }
    )
    return
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
          IconButton(
            onClick = onBackToLobby,
            modifier = Modifier.testTag("chat_back_button")
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع للغرف")
          }
        },
        title = {
          Column(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .clickable { showMembersSheet = true }
              .padding(vertical = 4.dp)
          ) {
            Text(
              text = room.name,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary)
              )
              Text(
                text = "$onlineCount متصل الآن • عرض الأعضاء",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        },
        actions = {
          if (isAdmin) {
            IconButton(
              onClick = { showRoomManagementSheet = true },
              modifier = Modifier.testTag("room_management_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.AdminPanelSettings,
                contentDescription = stringResource(id = R.string.room_management),
                tint = MaterialTheme.colorScheme.tertiary
              )
            }
          }

          Box {
            IconButton(
              onClick = { showMoreMenu = true },
              modifier = Modifier.testTag("chat_more_menu_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "المزيد"
              )
            }
            DropdownMenu(
              expanded = showMoreMenu,
              onDismissRequest = { showMoreMenu = false },
              modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
              DropdownMenuItem(
                text = { Text(stringResource(id = R.string.share_photo)) },
                leadingIcon = {
                  Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                onClick = {
                  showMoreMenu = false
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier.testTag("menu_share_photo")
              )
              DropdownMenuItem(
                text = { Text(stringResource(id = R.string.share_video)) },
                leadingIcon = {
                  Icon(Icons.Rounded.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                },
                onClick = {
                  showMoreMenu = false
                  videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                  )
                },
                modifier = Modifier.testTag("menu_share_video")
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline)
              DropdownMenuItem(
                text = { Text(stringResource(id = R.string.mini_games)) },
                leadingIcon = {
                  Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                },
                onClick = {
                  showMoreMenu = false
                  showMiniGamesSheet = true
                },
                modifier = Modifier.testTag("menu_mini_games")
              )
            }
          }
        }
      )
    },
    bottomBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .navigationBarsPadding()
          .imePadding()
      ) {
        if (isBusy) {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
          )
        }

        AnimatedVisibility(visible = showEmojiTray) {
          LazyRow(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(QuickEmojis) { emoji ->
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .clickable { messageText += emoji }
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(text = emoji, fontSize = 20.sp)
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          IconButton(
            onClick = { showEmojiTray = !showEmojiTray },
            modifier = Modifier.testTag("emoji_toggle_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.EmojiEmotions,
              contentDescription = "إيموجي",
              tint = if (showEmojiTray) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          OutlinedTextField(
            value = messageText,
            onValueChange = { if (it.length <= 2000) messageText = it },
            placeholder = { Text("اكتب رسالة للشلة...") },
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("chat_message_input")
          )

          Surface(
            color = if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .clickable(enabled = messageText.isNotBlank()) {
                val payload = messageText
                messageText = ""
                viewModel.sendTextMessage(payload)
              }
              .testTag("send_message_button")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "إرسال",
                tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      when (val state = messagesState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
        is UiState.Error -> {
          Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
              text = state.message,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.error
            )
          }
        }
        is UiState.Success -> {
          if (state.data.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
              Text(
                text = "لا توجد رسائل بعد. ابدأ المحادثة مع شلتك الآن! 👋",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          } else {
            LazyColumn(
              state = listState,
              modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              items(state.data, key = { it.id }) { msg ->
                ChatMessageBubble(
                  message = msg,
                  isMe = msg.senderId == viewModel.currentUserId,
                  isAdminSender = msg.senderId == room.adminUid,
                  onImageClick = { base64 -> fullscreenImageBase64 = base64 },
                  onVideoClick = { localUri ->
                    if (!localUri.isNullOrBlank()) {
                      runCatching {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                          setDataAndType(Uri.parse(localUri), "video/*")
                          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                      }.onFailure {
                        viewModel.sendTextMessage("") // no-op fallback avoided
                      }
                    }
                  }
                )
              }
            }
          }
        }
      }
    }
  }

  // Room Management Sheet (Admin Only)
  if (showRoomManagementSheet && isAdmin) {
    var updatedPin by remember(room.pin) { mutableStateOf(room.pin) }
    val inviteUrl = viewModel.buildInviteLink(room.id)
    val whatsAppText = viewModel.buildWhatsAppInviteText(room)

    ModalBottomSheet(
      onDismissRequest = { showRoomManagementSheet = false },
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text(
          text = "إدارة الغرفة (المشرف)",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.tertiary
        )

        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = "الرمز السري الحالي",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = room.pin,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.testTag("admin_current_pin_text")
              )
            }
            IconButton(
              onClick = { clipboard.setText(AnnotatedString(room.pin)) },
              modifier = Modifier.testTag("admin_copy_pin_button")
            ) {
              Icon(Icons.Rounded.ContentCopy, contentDescription = "نسخ الرمز السري", tint = MaterialTheme.colorScheme.secondary)
            }
          }
        }

        OutlinedTextField(
          value = updatedPin,
          onValueChange = { if (it.length <= 12) updatedPin = it },
          label = { Text("تعديل الرمز السري (4 - 12 خانة)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("admin_update_pin_input")
        )

        Button(
          onClick = {
            viewModel.updateRoomPin(room.id, updatedPin)
            showRoomManagementSheet = false
          },
          enabled = updatedPin.trim().length in 4..12 && updatedPin.trim() != room.pin,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_save_pin_button")
        ) {
          Text("تحديث الرمز السري")
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Button(
          onClick = { shareViaWhatsApp(context, whatsAppText) },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
          ),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
          Icon(Icons.Rounded.Share, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(id = R.string.share_whatsapp))
        }

        OutlinedButton(
          onClick = { clipboard.setText(AnnotatedString(inviteUrl)) },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("نسخ رابط الدعوة")
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }

  // Online Members Sheet
  if (showMembersSheet) {
    ModalBottomSheet(
      onDismissRequest = { showMembersSheet = false },
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Rounded.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("أعضاء الشلة وحالة الاتصال", style = MaterialTheme.typography.titleLarge)
        }
        when (val mState = membersState) {
          is UiState.Success -> {
            mState.data.forEach { member ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(MaterialTheme.colorScheme.background)
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Box(
                    modifier = Modifier
                      .size(10.dp)
                      .clip(CircleShape)
                      .background(if (member.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                  )
                  Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                  if (member.role == "admin") {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                      Text(
                        text = "مشرف",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                      )
                    }
                  }
                  Text(
                    text = if (member.online) "متصل الآن" else "غير متصل",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (member.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
          is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
          is UiState.Error -> Text(mState.message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(10.dp))
      }
    }
  }

  val previewBase64 = fullscreenImageBase64
  if (previewBase64 != null) {
    val decoded = remember(previewBase64) { MediaProcessor.decodeBase64Image(previewBase64) }
    Dialog(onDismissRequest = { fullscreenImageBase64 = null }) {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
      ) {
        if (decoded != null) {
          Image(
            bitmap = decoded,
            contentDescription = "عرض الصورة",
            modifier = Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit
          )
        }
      }
    }
  }
}

@Composable
private fun ChatMessageBubble(
  message: ChatMessage,
  isMe: Boolean,
  isAdminSender: Boolean,
  onImageClick: (String) -> Unit,
  onVideoClick: (String?) -> Unit
) {
  val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }
  val timeText = message.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: "الآن"

  val bubbleColor = if (isMe) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceContainerHigh
  }

  val bubbleShape = if (isMe) {
    RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
  } else {
    RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isMe) Arrangement.Start else Arrangement.End
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 310.dp)
        .clip(bubbleShape)
        .background(bubbleColor)
        .border(
          width = 1.dp,
          color = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline,
          shape = bubbleShape
        )
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = if (isMe) "أنت" else message.senderName,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        )
        if (isAdminSender) {
          Text(
            text = "★ مشرف",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
          )
        }
      }

      if (message.mediaType == "image" && !message.mediaBase64.isNullOrBlank()) {
        val bitmap = remember(message.mediaBase64) { MediaProcessor.decodeBase64Image(message.mediaBase64) }
        if (bitmap != null) {
          Image(
            bitmap = bitmap,
            contentDescription = message.mediaName ?: "صورة مرسلة",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .height(190.dp)
              .clip(RoundedCornerShape(14.dp))
              .clickable { onImageClick(message.mediaBase64) }
          )
        }
      } else if (message.mediaType == "video") {
        val thumb = remember(message.mediaBase64) { MediaProcessor.decodeBase64Image(message.mediaBase64) }
        val durationSec = ((message.mediaDurationMs ?: 0L) / 1000L).coerceAtLeast(0L)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onVideoClick(message.localMediaUri) },
          contentAlignment = Alignment.Center
        ) {
          if (thumb != null) {
            Image(
              bitmap = thumb,
              contentDescription = message.mediaName ?: "فيديو",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Rounded.PlayCircle,
              contentDescription = "تشغيل الفيديو",
              tint = Color.White,
              modifier = Modifier.size(48.dp)
            )
            Text(
              text = "${message.mediaName ?: "فيديو"} • ${durationSec}ث",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White
            )
          }
        }
      }

      if (message.text.isNotBlank()) {
        Text(
          text = message.text,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Text(
        text = timeText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.End)
      )
    }
  }
}
