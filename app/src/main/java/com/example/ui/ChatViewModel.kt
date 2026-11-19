package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.data.MediaProcessor
import com.example.data.model.ChatMessage
import com.example.data.model.INVITE_WEB_BASE_URL
import com.example.data.model.PLATFORM_ADMIN_EMAIL
import com.example.data.model.RoomAccessState
import com.example.data.model.RoomMember
import com.example.data.model.SquadRoom
import com.example.data.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
  private val repository: ChatRepository,
  val currentUserId: String,
  val currentUserName: String,
  val currentUserEmail: String,
  val isEmailVerified: Boolean
) : ViewModel() {

  private val unlockedRoomsInSession = mutableSetOf<String>()

  val isPlatformAdmin: Boolean
    get() = isEmailVerified && currentUserEmail.equals(PLATFORM_ADMIN_EMAIL, ignoreCase = true)

  private val _roomAccessState = MutableStateFlow<RoomAccessState>(RoomAccessState.None)
  val roomAccessState: StateFlow<RoomAccessState> = _roomAccessState.asStateFlow()

  private val _newlyCreatedRoomForInvite = MutableStateFlow<SquadRoom?>(null)
  val newlyCreatedRoomForInvite: StateFlow<SquadRoom?> = _newlyCreatedRoomForInvite.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  val roomsState: StateFlow<UiState<List<SquadRoom>>> = repository.observeUserRooms(currentUserId)
    .map<List<SquadRoom>, UiState<List<SquadRoom>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val root = firestoreError ?: error
      Log.w(TAG, "Error observing user rooms", root)
      emit(UiState.Error(root.message ?: "تعذر تحميل الغرف"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading
    )

  private val activeUnlockedRoomId: StateFlow<String?> = _roomAccessState
    .map { state -> (state as? RoomAccessState.UnlockedChat)?.room?.id }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val activeRoomLive: StateFlow<SquadRoom?> = activeUnlockedRoomId
    .flatMapLatest { roomId ->
      if (roomId == null) flowOf(null)
      else repository.observeRoom(roomId).catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing active room", firestoreError ?: error)
        emit(null)
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val membersState: StateFlow<UiState<List<RoomMember>>> = activeUnlockedRoomId
    .flatMapLatest { roomId ->
      if (roomId == null) {
        flowOf(UiState.Loading)
      } else {
        repository.observeMembers(roomId, currentUserId)
          .map<List<RoomMember>, UiState<List<RoomMember>>> { UiState.Success(it) }
          .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing members", root)
            emit(UiState.Error(root.message ?: "تعذر تحميل الأعضاء"))
          }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val messagesState: StateFlow<UiState<List<ChatMessage>>> = activeUnlockedRoomId
    .flatMapLatest { roomId ->
      if (roomId == null) {
        flowOf(UiState.Loading)
      } else {
        repository.observeMessages(roomId, currentUserId)
          .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
          .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing messages", root)
            emit(UiState.Error(root.message ?: "تعذر تحميل الرسائل"))
          }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  fun isUserAdminFor(room: SquadRoom): Boolean {
    return room.adminUid == currentUserId || isPlatformAdmin
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  fun dismissNewlyCreatedInvite() {
    _newlyCreatedRoomForInvite.value = null
  }

  fun generateRandomPin(): String = Random.nextInt(1000, 9999).toString()

  fun buildInviteLink(roomId: String): String = "$INVITE_WEB_BASE_URL?room=$roomId"

  fun buildWhatsAppInviteText(room: SquadRoom): String {
    val link = buildInviteLink(room.id)
    return "أهلاً بك في شلتنا! 🔥\nانضم الآن إلى غرفة الدردشة الخاصة «${room.name}» عبر الرابط:\n$link\n\nعند الفتح لأول مرة سيظهر لك الرمز السري — احفظه للدخول لاحقاً 🔐"
  }

  fun createSquadRoom(name: String, pin: String) {
    val cleanName = name.trim()
    val cleanPin = pin.trim()
    if (cleanName.isEmpty()) {
      _statusMessage.value = "يرجى كتابة اسم للمجموعة"
      return
    }
    if (cleanPin.length !in 4..12) {
      _statusMessage.value = "الرمز السري يجب أن يكون من 4 إلى 12 خانة"
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val result = repository.createRoom(cleanName, cleanPin, currentUserName)
      _isBusy.value = false
      result.onSuccess { createdRoom ->
        _newlyCreatedRoomForInvite.value = createdRoom
      }.onFailure { err ->
        _statusMessage.value = err.localizedMessage ?: "تعذر إنشاء المجموعة"
      }
    }
  }

  fun openRoomFromLobby(room: SquadRoom) {
    viewModelScope.launch {
      openRoomInternal(roomId = room.id, prefetchedRoom = room, fromInviteLink = false)
    }
  }

  fun handleInviteInput(rawInput: String) {
    val roomId = extractRoomId(rawInput)
    if (roomId == null) {
      _statusMessage.value = "رابط أو رمز الدعوة غير صالح"
      return
    }
    viewModelScope.launch {
      openRoomInternal(roomId = roomId, prefetchedRoom = null, fromInviteLink = true)
    }
  }

  private suspend fun openRoomInternal(
    roomId: String,
    prefetchedRoom: SquadRoom?,
    fromInviteLink: Boolean
  ) {
    _roomAccessState.value = RoomAccessState.Loading
    val roomResult = if (prefetchedRoom != null) Result.success(prefetchedRoom) else repository.getRoom(roomId)
    val room = roomResult.getOrElse { err ->
      _roomAccessState.value = RoomAccessState.Error(err.localizedMessage ?: "تعذر الاتصال بالغرفة")
      return
    }
    if (room == null) {
      _roomAccessState.value = RoomAccessState.Error("لم يتم العثور على هذه الغرفة أو انتهى رابط الدعوة.")
      return
    }

    // Admin enters directly without PIN prompt
    if (isUserAdminFor(room)) {
      if (currentUserId !in room.memberUids) {
        val joinRes = repository.joinRoomFirstTime(room, currentUserName, isAdminUser = true)
        if (joinRes.isFailure) {
          _roomAccessState.value = RoomAccessState.Error(
            joinRes.exceptionOrNull()?.localizedMessage ?: "تعذر الانضمام كمشرف"
          )
          return
        }
      }
      unlockedRoomsInSession.add(room.id)
      repository.updatePresence(room.id, online = true, displayName = currentUserName)
      _roomAccessState.value = RoomAccessState.UnlockedChat(room)
      return
    }

    // Same active session unlock check
    if (unlockedRoomsInSession.contains(room.id) && !fromInviteLink) {
      repository.updatePresence(room.id, online = true, displayName = currentUserName)
      _roomAccessState.value = RoomAccessState.UnlockedChat(room)
      return
    }

    // Check if friend has already joined before (first-time invite vs subsequent visit)
    val memberRes = repository.getMember(room.id, currentUserId)
    val existingMember = memberRes.getOrElse { err ->
      _roomAccessState.value = RoomAccessState.Error(err.localizedMessage ?: "تعذر التحقق من العضوية")
      return
    }

    if (existingMember == null) {
      // First time opening invite link: show Secret PIN clearly and ask them to save it
      _roomAccessState.value = RoomAccessState.FirstTimeInvitePin(room)
    } else {
      // Subsequent visit or reload: require entering Secret PIN
      _roomAccessState.value = RoomAccessState.RequirePinUnlock(room)
    }
  }

  fun confirmFirstTimePinSaved(room: SquadRoom) {
    viewModelScope.launch {
      _isBusy.value = true
      val joinResult = repository.joinRoomFirstTime(room, currentUserName, isAdminUser = isUserAdminFor(room))
      _isBusy.value = false
      joinResult.onSuccess {
        val updatedRoom = room.copy(memberUids = (room.memberUids + currentUserId).distinct())
        unlockedRoomsInSession.add(room.id)
        _roomAccessState.value = RoomAccessState.UnlockedChat(updatedRoom)
      }.onFailure { err ->
        _statusMessage.value = err.localizedMessage ?: "تعذر الانضمام إلى الغرفة"
      }
    }
  }

  fun verifyPinAndEnter(room: SquadRoom, enteredPin: String) {
    val cleanEntered = enteredPin.trim()
    if (cleanEntered.isEmpty()) {
      _roomAccessState.value = RoomAccessState.RequirePinUnlock(room, "يرجى إدخال الرمز السري")
      return
    }
    viewModelScope.launch {
      // Fetch latest room PIN in case admin recently changed it
      val latestRoom = repository.getRoom(room.id).getOrNull() ?: room
      if (cleanEntered == latestRoom.pin) {
        unlockedRoomsInSession.add(latestRoom.id)
        repository.updatePresence(latestRoom.id, online = true, displayName = currentUserName)
        _roomAccessState.value = RoomAccessState.UnlockedChat(latestRoom)
      } else {
        _roomAccessState.value = RoomAccessState.RequirePinUnlock(
          latestRoom,
          "الرمز السري غير صحيح، حاول مرة أخرى"
        )
      }
    }
  }

  fun updateRoomPin(roomId: String, newPin: String) {
    val clean = newPin.trim()
    if (clean.length !in 4..12) {
      _statusMessage.value = "يجب أن يكون الرمز السري بين 4 و12 خانة"
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.updateRoomPin(roomId, clean)
      _isBusy.value = false
      res.onSuccess {
        _statusMessage.value = "تم تحديث الرمز السري بنجاح"
      }.onFailure { err ->
        _statusMessage.value = err.localizedMessage ?: "تعذر تحديث الرمز السري"
      }
    }
  }

  fun sendTextMessage(text: String) {
    val currentRoom = activeRoomLive.value
      ?: (_roomAccessState.value as? RoomAccessState.UnlockedChat)?.room
      ?: return
    if (text.isBlank()) return
    viewModelScope.launch {
      val res = repository.sendMessage(
        room = currentRoom,
        senderName = currentUserName,
        text = text,
        mediaType = "text"
      )
      res.onFailure { err ->
        _statusMessage.value = err.localizedMessage ?: "تعذر إرسال الرسالة"
      }
    }
  }

  fun sendPickedImage(context: Context, uri: Uri) {
    val currentRoom = activeRoomLive.value
      ?: (_roomAccessState.value as? RoomAccessState.UnlockedChat)?.room
      ?: return
    viewModelScope.launch {
      _isBusy.value = true
      val processed = MediaProcessor.processPickedImage(context, uri)
      processed.onSuccess { img ->
        val res = repository.sendMessage(
          room = currentRoom,
          senderName = currentUserName,
          text = "",
          mediaType = "image",
          mediaBase64 = img.base64,
          mediaName = img.fileName
        )
        res.onFailure { err ->
          _statusMessage.value = err.localizedMessage ?: "تعذر إرسال الصورة"
        }
      }.onFailure { err ->
        _statusMessage.value = err.localizedMessage ?: "تعذر معالجة الصورة"
      }
      _isBusy.value = false
    }
  }

  fun sendPickedVideo(context: Context, uri: Uri) {
    val currentRoom = activeRoomLive.value
      ?: (_roomAccessState.value as? RoomAccessState.UnlockedChat)?.room
      ?: return
    viewModelScope.launch {
      _isBusy.value = true
      val processed = MediaProcessor.processPickedVideo(context, uri)
      processed.onSuccess { vid ->
        val res = repository.sendMessage(
          room = currentRoom,
          senderName = currentUserName,
          text = "",
          mediaType = "video",
          mediaBase64 = vid.thumbnailBase64,
          mediaName = vid.fileName,
          mediaDurationMs = vid.durationMs,
          localMediaUri = vid.uriString
        )
        res.onFailure { err ->
          _statusMessage.value = err.localizedMessage ?: "تعذر إرسال الفيديو"
        }
      }.onFailure { err ->
        _statusMessage.value = err.localizedMessage ?: "تعذر معالجة الفيديو"
      }
      _isBusy.value = false
    }
  }

  fun setPresence(online: Boolean) {
    val roomId = activeUnlockedRoomId.value ?: return
    viewModelScope.launch {
      repository.updatePresence(roomId, online = online, displayName = currentUserName)
    }
  }

  fun exitRoom() {
    val roomId = activeUnlockedRoomId.value
    if (roomId != null) {
      // Clear session unlock for non-admins so subsequent visits require PIN as requested
      unlockedRoomsInSession.remove(roomId)
      viewModelScope.launch {
        repository.updatePresence(roomId, online = false, displayName = currentUserName)
      }
    }
    _roomAccessState.value = RoomAccessState.None
  }

  private fun extractRoomId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val uriMatch = Regex("""[?&]room=([a-zA-Z0-9_\-]+)""").find(trimmed)
    val candidate = uriMatch?.groupValues?.getOrNull(1) ?: trimmed
    return if (candidate.matches(Regex("^[a-zA-Z0-9_\\-]{3,128}$"))) candidate else null
  }

  companion object {
    private const val TAG = "ChatViewModel"
  }
}
