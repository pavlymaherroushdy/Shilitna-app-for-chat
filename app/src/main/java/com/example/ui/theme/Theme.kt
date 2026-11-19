package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val ShillaDarkColorScheme = darkColorScheme(
  primary = EmeraldMint,
  onPrimary = DeepObsidian,
  primaryContainer = EmeraldDarkContainer,
  onPrimaryContainer = EmeraldGlow,
  secondary = ElectricCyan,
  onSecondary = DeepObsidian,
  secondaryContainer = CyanDarkContainer,
  onSecondaryContainer = ElectricCyan,
  tertiary = WarmAmber,
  onTertiary = DeepObsidian,
  tertiaryContainer = AmberDarkContainer,
  onTertiaryContainer = WarmAmber,
  background = DeepObsidian,
  onBackground = CrispText,
  surface = MidnightSurface,
  onSurface = CrispText,
  surfaceVariant = ElevatedCard,
  onSurfaceVariant = MutedText,
  surfaceContainer = MidnightSurface,
  surfaceContainerHigh = ElevatedCard,
  surfaceContainerHighest = ElevatedCardHighest,
  outline = SubtleBorder,
  error = SoftCoral,
  onError = DeepObsidian,
  errorContainer = CoralDarkContainer,
  onErrorContainer = SoftCoral
)

@Composable
fun ShillaChatTheme(
  content: @Composable () -> Unit
) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    MaterialTheme(
      colorScheme = ShillaDarkColorScheme,
      typography = ShillaTypography,
      content = content
    )
  }
}
