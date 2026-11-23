package com.example

import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.auth.formatSignInExceptionMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthHelperTest {

  @Test
  fun `formatSignInExceptionMessage maps Credential Manager exceptions to clear Arabic messages`() {
    val noCred = formatSignInExceptionMessage(NoCredentialException("No credentials available"))
    assertTrue(noCred.contains("حساب Google"))

    val configErr = formatSignInExceptionMessage(GetCredentialProviderConfigurationException("missing provider"))
    assertTrue(configErr.contains("Google Play"))

    val interrupted = formatSignInExceptionMessage(GetCredentialInterruptedException("interrupted"))
    assertTrue(interrupted.contains("المحاولة"))

    val generic = formatSignInExceptionMessage(IllegalStateException("network timeout"))
    assertTrue(generic.contains("network timeout"))
  }
}
