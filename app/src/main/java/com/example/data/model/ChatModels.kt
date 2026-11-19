package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val PLATFORM_ADMIN_EMAIL = "pavlymaherroushdy@gmail.com"
const val INVITE_WEB_BASE_URL = "https://shillachat.app/join"
const val INVITE_SCHEME_PREFIX = "shillachat://join"

@Keep
@IgnoreExtraProperties
data class SquadRoom(
  @DocumentId val id: String = "",
  val name: String = "",
  val adminUid: String = "",
  val adminName: String = "",
  val pin: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RoomMember(
  @DocumentId val id: String = "",
  val uid: String = "",
  val displayName: String = "",
  val role: String = "member",
  val online: Boolean = false,
  val adminUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val lastSeen: Timestamp? = null,
  @ServerTimestamp val joinedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
  @DocumentId val id: String = "",
  val senderId: String = "",
  val senderName: String = "",
  val text: String = "",
  val mediaType: String = "text",
  val mediaBase64: String? = null,
  val mediaName: String? = null,
  val mediaDurationMs: Long? = null,
  val localMediaUri: String? = null,
  val adminUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

sealed interface RoomAccessState {
  data object None : RoomAccessState
  data object Loading : RoomAccessState
  data class FirstTimeInvitePin(val room: SquadRoom) : RoomAccessState
  data class RequirePinUnlock(val room: SquadRoom, val errorMessage: String? = null) : RoomAccessState
  data class UnlockedChat(val room: SquadRoom) : RoomAccessState
  data class Error(val message: String) : RoomAccessState
}
