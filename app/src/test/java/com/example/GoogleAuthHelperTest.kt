package com.example

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.auth.formatSignInExceptionMessage
import com.example.auth.shouldFallbackToGoogleIdOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthHelperTest {

  @Test
  fun `formatSignInExceptionMessage includes exact exception details and clear Arabic guidance`() {
    val noCred = formatSignInExceptionMessage(
      error = NoCredentialException("No credentials available"),
      packageName = "com.aistudio.shillachat.qmkxrp",
      clientId = "615748545225-h0e77cagld1lo6k9gsfkh7tm3gk21jrk.apps.googleusercontent.com"
    )
    assertTrue(noCred.contains("حساب Google"))
    assertTrue(noCred.contains("NoCredentialException"))
    assertTrue(noCred.contains("com.aistudio.shillachat.qmkxrp"))

    val reauth16 = formatSignInExceptionMessage(
      error = GetCredentialCancellationException("[16] Account reauth failed."),
      packageName = "com.aistudio.shillachat.qmkxrp",
      clientId = "615748545225-h0e77cagld1lo6k9gsfkh7tm3gk21jrk.apps.googleusercontent.com"
    )
    assertTrue(reauth16.contains("[16] Account reauth failed."))
    assertTrue(reauth16.contains("OAuth Consent Screen"))
    assertTrue(reauth16.contains("GetCredentialCancellationException"))

    val configErr = formatSignInExceptionMessage(GetCredentialProviderConfigurationException("missing provider"))
    assertTrue(configErr.contains("Google Play"))

    val interrupted = formatSignInExceptionMessage(GetCredentialInterruptedException("interrupted"))
    assertTrue(interrupted.contains("المحاولة"))
  }

  @Test
  fun `shouldFallbackToGoogleIdOption triggers for reauth 16 and NoCredentialException only`() {
    assertTrue(shouldFallbackToGoogleIdOption(GetCredentialCancellationException("[16] Account reauth failed.")))
    assertTrue(shouldFallbackToGoogleIdOption(NoCredentialException("No credentials")))
    assertFalse(shouldFallbackToGoogleIdOption(GetCredentialCancellationException("Activity is cancelled by the user.")))
  }
}
