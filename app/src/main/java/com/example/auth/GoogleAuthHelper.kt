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

internal fun formatSignInExceptionMessage(error: Throwable): String {
  return when (error) {
    is NoCredentialException -> {
      "لم يتم العثور على حساب Google جاهز لتسجيل الدخول على هذا الجهاز. تأكد من تسجيل الدخول بحساب Google في إعدادات الجهاز."
    }
    is GetCredentialProviderConfigurationException -> {
      "خدمات Google Play أو Credential Manager غير مهيأة على هذا الجهاز."
    }
    is GetCredentialInterruptedException -> {
      "توقفت عملية تسجيل الدخول مؤقتاً. يرجى المحاولة مرة أخرى."
    }
    is GetCredentialCustomException -> {
      val detail = error.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: error.type
      "تعذر إكمال المصادقة من Google: $detail"
    }
    is GetCredentialException -> {
      val detail = error.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: error.localizedMessage ?: error.type
      "تعذر تسجيل الدخول عبر Google: $detail"
    }
    is FirebaseNetworkException -> {
      "تعذر الاتصال بالإنترنت أثناء تسجيل الدخول إلى Firebase."
    }
    is FirebaseAuthException -> {
      when (error.errorCode) {
        "ERROR_OPERATION_NOT_ALLOWED" ->
          "مزوّد تسجيل الدخول عبر Google غير مفعّل في Firebase Console (Authentication -> Sign-in method)."
        "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_CUSTOM_TOKEN" ->
          "رمز Google غير صالح أو لا يطابق إعدادات Firebase (تأكد من default_web_client_id وبصمة SHA-1 في Firebase Console)."
        "ERROR_USER_DISABLED" ->
          "تم تعطيل هذا الحساب في Firebase."
        else ->
          "خطأ مصادقة Firebase (${error.errorCode}): ${error.localizedMessage ?: "تعذر تسجيل الدخول"}"
      }
    }
    else -> error.localizedMessage?.takeIf { it.isNotBlank() } ?: "تعذر تسجيل الدخول عبر Google"
  }
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

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager?,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: (String) -> Unit = {}
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
      val token = extractGoogleIdToken(cred)
      if (token == null) {
        Log.w("Auth", "Unexpected credential type returned: ${cred.type} (${cred::class.java.simpleName})")
        withContext(Dispatchers.Main.immediate) {
          onAuthError("نوع بيانات الاعتماد المستلمة من Google غير متوقع (${cred.type})")
        }
        return@launch
      }

      val authResult = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
      val signedInUser = authResult.user ?: auth.currentUser
      if (signedInUser != null) {
        withContext(Dispatchers.Main.immediate) {
          onAuthSuccess()
        }
      } else {
        Log.e("Auth", "signInWithCredential completed but currentUser is null")
        withContext(Dispatchers.Main.immediate) {
          onAuthError("تعذر تأكيد جلسة المستخدم بعد اختيار الحساب. حاول مرة أخرى.")
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: GetCredentialCancellationException) {
      val reason = e.errorMessage?.toString()?.trim().orEmpty()
      Log.w("Auth", "Google Sign-In flow cancelled or dismissed: $reason", e)
      val userNotice = if (reason.isNotEmpty() && !reason.equals("activity is cancelled", ignoreCase = true)) {
        "لم يكتمل تسجيل الدخول من Google ($reason). إذا اخترت حساباً وعادت الشاشة، تأكد من إضافة بصمة SHA-1 لتطبيقك في Firebase Console."
      } else {
        "تم إلغاء تسجيل الدخول أو رفضه بعد اختيار الحساب. إذا لم تقم بالإلغاء، فتأكد من تسجيل بصمة SHA-1 في Firebase Console وتفعيل Google Sign-In."
      }
      withContext(Dispatchers.Main.immediate) {
        onAuthCancelled(userNotice)
      }
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In failed", e)
      val message = formatSignInExceptionMessage(e)
      withContext(Dispatchers.Main.immediate) {
        onAuthError(message)
      }
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
        withContext(Dispatchers.Main.immediate) {
          onSignOutComplete()
        }
      }
    }
  }
}
