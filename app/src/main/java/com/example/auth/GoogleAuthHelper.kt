package com.example.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun isFirebaseReady(context: Context): Boolean {
  val appContext = context.applicationContext ?: context
  return try {
    FirebaseApp.getApps(appContext).isNotEmpty() || FirebaseApp.initializeApp(appContext) != null
  } catch (e: Exception) {
    Log.w("Auth", "FirebaseApp not initialized: ${e.message}")
    false
  }
}

fun getFirebaseAuthOrNull(context: Context): FirebaseAuth? {
  return if (isFirebaseReady(context)) {
    try {
      Firebase.auth
    } catch (e: Exception) {
      Log.w("Auth", "Unable to acquire FirebaseAuth: ${e.message}")
      null
    }
  } else {
    null
  }
}

@SuppressLint("DiscouragedApi")
internal fun resolveDefaultWebClientId(context: Context): String {
  val appContext = context.applicationContext ?: context

  // 1) Try generated R.string.default_web_client_id via reflection so compilation stays safe
  // even when google-services.json is absent or omits a Web OAuth client.
  val reflectedId = runCatching {
    Class.forName("com.example.R\$string")
      .getField("default_web_client_id")
      .getInt(null)
  }.getOrDefault(0)
  if (reflectedId != 0) {
    val value = runCatching { context.getString(reflectedId) }.getOrNull()?.trim().orEmpty()
    if (value.isNotEmpty()) return value
  }

  // 2) Fallback to Resources.getIdentifier across candidate package names.
  val candidatePackages = listOf(context.packageName, appContext.packageName, "com.example").distinct()
  for (pkg in candidatePackages) {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", pkg)
    if (resId != 0) {
      val value = runCatching { context.getString(resId) }.getOrNull()?.trim().orEmpty()
      if (value.isNotEmpty()) return value
    }
  }

  return ""
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext?.findActivity()
  else -> null
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager?,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope
) {
  val auth = getFirebaseAuthOrNull(context)
  if (auth == null || credentialManager == null) {
    onUnauthenticated()
    return
  }
  if (auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId = resolveDefaultWebClientId(context)
  if (clientId.isBlank()) {
    onUnauthenticated()
    return
  }
  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(clientId)
    .setAutoSelectEnabled(true)
    .build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()
  scope.launch {
    try {
      val cred = credentialManager.getCredential(context, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (_: Exception) {
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager?,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {}
) {
  val auth = getFirebaseAuthOrNull(context)
  if (auth == null) {
    onAuthError("Firebase غير مهيأ في هذه النسخة: أضف google-services.json لتفعيل تسجيل الدخول")
    return
  }
  if (credentialManager == null) {
    onAuthError("خدمة تسجيل الدخول عبر Google غير متاحة على هذا الجهاز")
    return
  }
  val clientId = resolveDefaultWebClientId(context)
  if (clientId.isBlank()) {
    onAuthError(
      "تعذر العثور على Web Client ID (default_web_client_id) داخل google-services.json. " +
        "فعّل تسجيل الدخول عبر Google في Firebase Authentication ثم أعد تحميل google-services.json وتحديث GOOGLE_SERVICES_JSON."
    )
    return
  }
  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()
  scope.launch {
    try {
      val activityContext = context.findActivity() ?: context
      val cred = credentialManager.getCredential(activityContext, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onAuthError("نوع بيانات الاعتماد غير متوقع")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "تعذر تسجيل الدخول عبر Google")
    }
  }
}

fun signOut(
  credentialManager: CredentialManager?,
  scope: CoroutineScope,
  onSignOutComplete: () -> Unit = {}
) {
  scope.launch {
    withContext(NonCancellable) {
      try {
        credentialManager?.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w("Auth", "Failed to clear credential state: ${e.message}")
      } finally {
        try {
          Firebase.auth.signOut()
        } catch (_: Exception) {
        }
        onSignOutComplete()
      }
    }
  }
}
