package com.example.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private var activeAutoSignInJob: Job? = null

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

  val reflectedId = runCatching {
    Class.forName("com.example.R\$string")
      .getField("default_web_client_id")
      .getInt(null)
  }.getOrDefault(0)
  if (reflectedId != 0) {
    val value = runCatching { context.getString(reflectedId) }.getOrNull()?.trim().orEmpty()
    if (value.isNotEmpty()) return value
  }

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

internal fun extractGoogleIdToken(credential: Credential): String? {
  return when (credential) {
    is GoogleIdTokenCredential -> credential.idToken.takeIf { it.isNotBlank() }
    is CustomCredential -> {
      if (credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        runCatching { GoogleIdTokenCredential.createFrom(credential.data).idToken }
          .getOrNull()
          ?.takeIf { it.isNotBlank() }
      } else {
        null
      }
    }
    else -> null
  }
}

internal fun shouldFallbackToGoogleIdOption(error: Throwable): Boolean {
  if (error is NoCredentialException) return true
  if (error is GetCredentialException) {
    val msg = error.errorMessage?.toString().orEmpty()
    if (msg.contains("reauth", ignoreCase = true) || msg.contains("[16]")) {
      return true
    }
  }
  return false
}

internal fun formatSignInExceptionMessage(
  error: Throwable,
  packageName: String = "",
  clientId: String = ""
): String {
  val exceptionName = error::class.java.simpleName.ifBlank { "Exception" }
  val causeDetail = error.cause?.let { c ->
    "${c::class.java.simpleName}: ${c.message ?: c.localizedMessage ?: "unknown"}"
  }

  val maskedClient = if (clientId.length > 20) {
    "${clientId.take(16)}...${clientId.takeLast(12)}"
  } else {
    clientId.ifBlank { "missing" }
  }

  val headline = when (error) {
    is NoCredentialException -> {
      "لم يتم العثور على حساب Google جاهز لتسجيل الدخول على هذا الجهاز. تأكد من تسجيل الدخول بحساب Google في إعدادات الهاتف."
    }
    is GetCredentialProviderConfigurationException -> {
      "خدمات Google Play أو Credential Manager غير مهيأة على هذا الجهاز."
    }
    is GetCredentialInterruptedException -> {
      "توقفت عملية تسجيل الدخول مؤقتاً. يرجى المحاولة مرة أخرى."
    }
    is GetCredentialCancellationException -> {
      val msg = error.errorMessage?.toString().orEmpty()
      if (msg.contains("reauth", ignoreCase = true) || msg.contains("[16]")) {
        "فشلت إعادة مصادقة حساب Google من خدمات Google Play ([16] Account reauth failed). " +
          "تأكد من: 1) أن شاشة OAuth Consent Screen في Google Cloud مهيأة (ومضاف إليها بريدك كـ Test User إذا كانت Testing)، " +
          "2) أن الحساب على الهاتف لا يطلب إعادة تسجيل الدخول في إعدادات Google، " +
          "3) تطابق Web Client ID وبصمة SHA-1 في نفس مشروع Firebase."
      } else {
        "تم إلغاء نافذة تسجيل الدخول عبر Google أو تعذر إتمامها."
      }
    }
    is GetCredentialCustomException -> {
      "تعذر إكمال المصادقة من Google (${error.type})."
    }
    is GetCredentialException -> {
      "تعذر تسجيل الدخول عبر Google (${error.type})."
    }
    is FirebaseNetworkException -> {
      "تعذر الاتصال بالإنترنت أثناء تسجيل الدخول إلى Firebase."
    }
    is FirebaseAuthException -> {
      when (error.errorCode) {
        "ERROR_OPERATION_NOT_ALLOWED" ->
          "مزوّد تسجيل الدخول عبر Google غير مفعّل في Firebase Console (Authentication -> Sign-in method)."
        "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_CUSTOM_TOKEN" ->
          "رمز Google غير صالح أو لا يطابق إعدادات Firebase (تأكد من default_web_client_id وبصمة SHA-1)."
        "ERROR_USER_DISABLED" ->
          "تم تعطيل هذا الحساب في Firebase."
        else ->
          "فشل مصادقة Firebase (${error.errorCode})."
      }
    }
    else -> "تعذر تسجيل الدخول عبر Google."
  }

  val rawMsg = when (error) {
    is GetCredentialException -> error.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: error.localizedMessage
    is FirebaseAuthException -> "${error.errorCode}: ${error.localizedMessage ?: error.message}"
    else -> error.localizedMessage ?: error.message
  }?.trim().orEmpty().ifBlank { "no error message" }

  val credType = (error as? GetCredentialException)?.type
  val techParts = buildList {
    add("Exception=$exceptionName")
    if (!credType.isNullOrBlank()) add("credType=$credType")
    add("detail=$rawMsg")
    if (!causeDetail.isNullOrBlank()) add("cause=$causeDetail")
    if (packageName.isNotBlank()) add("pkg=$packageName")
    if (clientId.isNotBlank()) add("webClient=$maskedClient")
  }.joinToString(" | ")

  return "$headline\n[$techParts]"
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

  activeAutoSignInJob?.cancel()
  activeAutoSignInJob = scope.launch {
    try {
      val activityContext = context.findActivity() ?: context
      val cred = credentialManager.getCredential(activityContext, request).credential
      val token = extractGoogleIdToken(cred)
      if (token != null) {
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        if ((result.user ?: auth.currentUser) != null) {
          withContext(Dispatchers.Main.immediate) { onAuthSuccess() }
        } else {
          withContext(Dispatchers.Main.immediate) { onUnauthenticated() }
        }
      } else {
        withContext(Dispatchers.Main.immediate) { onUnauthenticated() }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      withContext(Dispatchers.Main.immediate) { onUnauthenticated() }
    }
  }
}

private suspend fun signInWithCredentialOption(
  activityContext: Context,
  credentialManager: CredentialManager,
  request: GetCredentialRequest,
  auth: FirebaseAuth
): FirebaseUser {
  val cred = credentialManager.getCredential(activityContext, request).credential
  val token = extractGoogleIdToken(cred)
    ?: throw IllegalStateException("Unexpected credential type: ${cred.type} (${cred::class.java.simpleName})")
  val authResult = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
  return authResult.user ?: auth.currentUser
    ?: throw IllegalStateException("Firebase signInWithCredential succeeded but currentUser is null")
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager?,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: (String) -> Unit = {}
) {
  activeAutoSignInJob?.cancel()
  activeAutoSignInJob = null

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
  val primaryRequest = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()

  scope.launch {
    val activityContext = context.findActivity() ?: context
    try {
      signInWithCredentialOption(activityContext, credentialManager, primaryRequest, auth)
      withContext(Dispatchers.Main.immediate) { onAuthSuccess() }
    } catch (e: CancellationException) {
      throw e
    } catch (primaryError: Exception) {
      Log.w(
        "Auth",
        "Primary GetSignInWithGoogleOption failed (${primaryError::class.java.simpleName}): ${primaryError.message}",
        primaryError
      )

      if (shouldFallbackToGoogleIdOption(primaryError)) {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        val fallbackOption = GetGoogleIdOption.Builder()
          .setFilterByAuthorizedAccounts(false)
          .setServerClientId(clientId)
          .setAutoSelectEnabled(false)
          .build()
        val fallbackRequest = GetCredentialRequest.Builder()
          .addCredentialOption(fallbackOption)
          .build()
        try {
          signInWithCredentialOption(activityContext, credentialManager, fallbackRequest, auth)
          withContext(Dispatchers.Main.immediate) { onAuthSuccess() }
          return@launch
        } catch (e: CancellationException) {
          throw e
        } catch (fallbackError: Exception) {
          Log.e(
            "Auth",
            "Fallback GetGoogleIdOption also failed (${fallbackError::class.java.simpleName}): ${fallbackError.message}",
            fallbackError
          )
          val primaryDetail = formatSignInExceptionMessage(primaryError, context.packageName, clientId)
          val fallbackSummary = "${fallbackError::class.java.simpleName}: ${(fallbackError as? GetCredentialException)?.errorMessage ?: fallbackError.message ?: "unknown"}"
          val combined = "$primaryDetail\n[Fallback: $fallbackSummary]"
          withContext(Dispatchers.Main.immediate) {
            onAuthError(combined)
          }
          return@launch
        }
      }

      val formatted = formatSignInExceptionMessage(primaryError, context.packageName, clientId)
      withContext(Dispatchers.Main.immediate) {
        if (primaryError is GetCredentialCancellationException) {
          onAuthCancelled(formatted)
        } else {
          onAuthError(formatted)
        }
      }
    }
  }
}

fun signOut(
  credentialManager: CredentialManager?,
  scope: CoroutineScope,
  onSignOutComplete: () -> Unit = {}
) {
  activeAutoSignInJob?.cancel()
  activeAutoSignInJob = null
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
        withContext(Dispatchers.Main.immediate) {
          onSignOutComplete()
        }
      }
    }
  }
}
