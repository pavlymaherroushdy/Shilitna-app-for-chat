package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.auth.getFirebaseAuthOrNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupSafetyTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun mainActivityLaunchesSafelyAndShowsSignInButton() {
    composeTestRule.onNodeWithTag("google_signin_button").assertIsDisplayed()
    assertNotNull(getFirebaseAuthOrNull(composeTestRule.activity))
  }
}
