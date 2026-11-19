package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.data.ChatRepository
import com.example.data.model.RoomAccessState
import com.example.ui.ChatViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.FirstTimeInvitePinScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.theme.ShillaChatTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

  private var pendingInviteLinkState by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    pendingInviteLinkState = extractInviteFromIntent(intent)

    setContent {
      ShillaChatTheme {
        AppNavigation(
          pendingInviteLink = pendingInviteLinkState,
          onInviteConsumed = { pendingInviteLinkState = null }
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractInviteFromIntent(intent)?.let { incomingLink ->
      pendingInviteLinkState = incomingLink
    }
  }

  private fun extractInviteFromIntent(intent: Intent?): String? {
    val data = intent?.data ?: return null
    val roomParam = data.getQueryParameter("room")
    return if (!roomParam.isNullOrBlank()) data.toString() else null
  }
}

@Composable
fun AppNavigation(
  pendingInviteLink: String?,
  onInviteConsumed: () -> Unit,
  auth: FirebaseAuth = Firebase.auth
) {
  val context = LocalContext.current
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  LaunchedEffect(Unit) {
    if (auth.currentUser == null) {
      val credentialManager = CredentialManager.create(context)
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = this
      )
    }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(
      pendingInviteRoomId = pendingInviteLink,
      onAuthSuccess = {}
    )
  } else {
    val uid = user.uid
    val displayName = user.displayName?.takeIf { it.isNotBlank() }
      ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
      ?: "صديق شلتنا"
    val email = user.email ?: ""
    val isEmailVerified = user.isEmailVerified

    key(uid) {
      val chatViewModel: ChatViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY]) {
              "APPLICATION_KEY missing from CreationExtras"
            }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            ChatViewModel(
              repository = ChatRepository(db, auth),
              currentUserId = uid,
              currentUserName = displayName,
              currentUserEmail = email,
              isEmailVerified = isEmailVerified
            )
          }
        }
      )

      LaunchedEffect(pendingInviteLink) {
        if (!pendingInviteLink.isNullOrBlank()) {
          chatViewModel.handleInviteInput(pendingInviteLink)
          onInviteConsumed()
        }
      }

      val lifecycleOwner = LocalLifecycleOwner.current
      DisposableEffect(lifecycleOwner, chatViewModel) {
        val observer = LifecycleEventObserver { _, event ->
          when (event) {
            Lifecycle.Event.ON_RESUME -> chatViewModel.setPresence(online = true)
            Lifecycle.Event.ON_STOP -> chatViewModel.setPresence(online = false)
            else -> Unit
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
          lifecycleOwner.lifecycle.removeObserver(observer)
        }
      }

      val accessState by chatViewModel.roomAccessState.collectAsStateWithLifecycle()
      val isBusy by chatViewModel.isBusy.collectAsStateWithLifecycle()

      when (val state = accessState) {
        is RoomAccessState.None -> {
          LobbyScreen(viewModel = chatViewModel)
        }
        is RoomAccessState.Loading -> {
          Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(
              modifier = Modifier.fillMaxSize().padding(padding),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
          }
        }
        is RoomAccessState.FirstTimeInvitePin -> {
          FirstTimeInvitePinScreen(
            room = state.room,
            isBusy = isBusy,
            onSavePinAndEnter = { chatViewModel.confirmFirstTimePinSaved(state.room) },
            onBack = { chatViewModel.exitRoom() }
          )
        }
        is RoomAccessState.RequirePinUnlock -> {
          PinLockScreen(
            room = state.room,
            errorMessage = state.errorMessage,
            onVerifyPin = { pin -> chatViewModel.verifyPinAndEnter(state.room, pin) },
            onBack = { chatViewModel.exitRoom() }
          )
        }
        is RoomAccessState.UnlockedChat -> {
          ChatScreen(
            viewModel = chatViewModel,
            initialRoom = state.room,
            onBackToLobby = { chatViewModel.exitRoom() }
          )
        }
        is RoomAccessState.Error -> {
          Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(
              modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = state.message,
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { chatViewModel.exitRoom() }) {
                  Text("العودة للرئيسية")
                }
              }
            }
          }
        }
      }
    }
  }
}
