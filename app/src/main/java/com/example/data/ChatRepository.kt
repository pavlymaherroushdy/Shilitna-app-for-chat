package com.example.data

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.RoomMember
import com.example.data.model.SquadRoom
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("يجب تسجيل الدخول أولاً قبل استخدام المحادثات.")
  }

  fun observeUserRooms(uid: String): Flow<List<SquadRoom>> = flow {
    if (uid.isBlank()) {
      emit(emptyList())
      return@flow
    }
    val path = "rooms"
    db.collection(path)
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(SquadRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.updatedAt?.seconds ?: 0L }
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
      .collect { emit(it) }
  }

  fun observeRoom(roomId: String): Flow<SquadRoom?> = flow {
    val uid = requireUserId()
    val path = "rooms/$roomId"
    db.collection("rooms").document(roomId)
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(SquadRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else null
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
          handleFirestoreError(exceptionToLog, OperationType.GET, path)
        }
        throw error
      }
      .collect { emit(it) }
  }

  fun observeMembers(roomId: String, uid: String): Flow<List<RoomMember>> = flow {
    val path = "rooms/$roomId/members"
    db.collection("rooms").document(roomId).collection("members")
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(RoomMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedWith(compareByDescending<RoomMember> { it.online }.thenBy { it.displayName })
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
      .collect { emit(it) }
  }

  fun observeMessages(roomId: String, uid: String): Flow<List<ChatMessage>> = flow {
    val path = "rooms/$roomId/messages"
    db.collection("rooms").document(roomId).collection("messages")
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
      .collect { emit(it) }
  }

  suspend fun getRoom(roomId: String): Result<SquadRoom?> {
    val path = "rooms/$roomId"
    return try {
      requireUserId()
      require(roomId.matches(Regex("^[a-zA-Z0-9_\\-]+$")) && roomId.length <= 128) {
        "رمز الغرفة غير صالح"
      }
      val snap = db.collection("rooms").document(roomId).get().await()
      val room = if (snap.exists()) {
        snap.toObject(SquadRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else null
      Result.success(room)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  suspend fun getMember(roomId: String, uid: String): Result<RoomMember?> {
    val path = "rooms/$roomId/members/$uid"
    return try {
      requireUserId()
      val snap = db.collection("rooms").document(roomId).collection("members").document(uid).get().await()
      val member = if (snap.exists()) {
        snap.toObject(RoomMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else null
      Result.success(member)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  suspend fun createRoom(name: String, pin: String, displayName: String): Result<SquadRoom> {
    val cleanName = name.trim().take(60)
    val cleanPin = pin.trim().take(12)
    val cleanAdminName = displayName.trim().ifBlank { "مشرف المجموعة" }.take(60)
    val roomId = "sq_${UUID.randomUUID().toString().replace("-", "").take(10)}"
    val path = "rooms/$roomId"
    return try {
      val uid = requireUserId()
      require(cleanName.isNotEmpty()) { "يرجى إدخال اسم المجموعة" }
      require(cleanPin.length in 4..12) { "يجب أن يكون الرمز السري بين 4 و12 خانة" }

      val roomRef = db.collection("rooms").document(roomId)
      val memberRef = roomRef.collection("members").document(uid)

      val roomPayload = mapOf(
        "name" to cleanName,
        "adminUid" to uid,
        "adminName" to cleanAdminName,
        "pin" to cleanPin,
        "memberUids" to listOf(uid),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      val memberPayload = mapOf(
        "uid" to uid,
        "displayName" to cleanAdminName,
        "role" to "admin",
        "online" to true,
        "adminUid" to uid,
        "memberUids" to listOf(uid),
        "lastSeen" to FieldValue.serverTimestamp(),
        "joinedAt" to FieldValue.serverTimestamp()
      )

      db.batch().apply {
        set(roomRef, roomPayload)
        set(memberRef, memberPayload)
      }.commit().await()

      Result.success(
        SquadRoom(
          id = roomId,
          name = cleanName,
          adminUid = uid,
          adminName = cleanAdminName,
          pin = cleanPin,
          memberUids = listOf(uid)
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun joinRoomFirstTime(
    room: SquadRoom,
    displayName: String,
    isAdminUser: Boolean
  ): Result<Unit> {
    val path = "rooms/${room.id}/members"
    return try {
      val uid = requireUserId()
      val cleanName = displayName.trim().ifBlank { "صديق" }.take(60)
      val updatedMemberUids = (room.memberUids + uid).distinct()
      require(updatedMemberUids.size <= 50) { "اكتمل الحد الأقصى لأعضاء هذه الغرفة (50)" }

      val roomRef = db.collection("rooms").document(room.id)
      val existingMembersSnap = roomRef.collection("members")
        .whereArrayContains("memberUids", room.adminUid)
        .get()
        .await()
      val existingMessagesSnap = roomRef.collection("messages")
        .whereArrayContains("memberUids", room.adminUid)
        .get()
        .await()

      val role = if (isAdminUser) "admin" else "member"
      val newMemberPayload = mapOf(
        "uid" to uid,
        "displayName" to cleanName,
        "role" to role,
        "online" to true,
        "adminUid" to room.adminUid,
        "memberUids" to updatedMemberUids,
        "lastSeen" to FieldValue.serverTimestamp(),
        "joinedAt" to FieldValue.serverTimestamp()
      )

      val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()
      ops.add { b ->
        b.update(
          roomRef,
          mapOf(
            "memberUids" to FieldValue.arrayUnion(uid),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      }
      ops.add { b ->
        b.set(roomRef.collection("members").document(uid), newMemberPayload)
      }
      existingMembersSnap.documents.forEach { doc ->
        if (doc.id != uid) {
          ops.add { b ->
            b.update(doc.reference, mapOf("memberUids" to FieldValue.arrayUnion(uid)))
          }
        }
      }
      existingMessagesSnap.documents.forEach { doc ->
        ops.add { b ->
          b.update(doc.reference, mapOf("memberUids" to FieldValue.arrayUnion(uid)))
        }
      }

      // Commit in Firestore batch chunks of up to 450 operations
      ops.chunked(450).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { applyOp -> applyOp(batch) }
        batch.commit().await()
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun updateRoomPin(roomId: String, newPin: String): Result<Unit> {
    val path = "rooms/$roomId"
    return try {
      requireUserId()
      val cleanPin = newPin.trim()
      require(cleanPin.length in 4..12) { "يجب أن يكون الرمز السري بين 4 و12 خانة" }
      db.collection("rooms").document(roomId).update(
        mapOf(
          "pin" to cleanPin,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updatePresence(
    roomId: String,
    online: Boolean,
    displayName: String
  ): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.success(Unit)
    val path = "rooms/$roomId/members/$uid"
    return try {
      val cleanName = displayName.trim().ifBlank { "عضو" }.take(60)
      db.collection("rooms").document(roomId)
        .collection("members").document(uid)
        .update(
          mapOf(
            "online" to online,
            "displayName" to cleanName,
            "lastSeen" to FieldValue.serverTimestamp()
          )
        ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun sendMessage(
    room: SquadRoom,
    senderName: String,
    text: String,
    mediaType: String = "text",
    mediaBase64: String? = null,
    mediaName: String? = null,
    mediaDurationMs: Long? = null,
    localMediaUri: String? = null
  ): Result<Unit> {
    val path = "rooms/${room.id}/messages"
    return try {
      val uid = requireUserId()
      val cleanText = text.trim().take(2000)
      require(cleanText.isNotEmpty() || mediaType != "text") { "لا يمكن إرسال رسالة فارغة" }
      val cleanSenderName = senderName.trim().ifBlank { "عضو" }.take(60)
      val members = (room.memberUids + uid).distinct().take(50)

      val payload = mapOf(
        "senderId" to uid,
        "senderName" to cleanSenderName,
        "text" to cleanText,
        "mediaType" to mediaType,
        "mediaBase64" to mediaBase64?.take(350_000),
        "mediaName" to mediaName?.take(120),
        "mediaDurationMs" to mediaDurationMs,
        "localMediaUri" to localMediaUri?.take(500),
        "adminUid" to room.adminUid,
        "memberUids" to members,
        "createdAt" to FieldValue.serverTimestamp()
      ).filterValues { it != null } as Map<String, Any>

      db.collection("rooms").document(room.id)
        .collection("messages")
        .document()
        .set(payload)
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }
}
