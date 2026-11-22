package com.example.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private enum class GoalZone(val labelAr: String) {
  TOP_LEFT("أعلى يسار"),
  TOP_RIGHT("أعلى يمين"),
  CENTER("المنتصف"),
  BOTTOM_LEFT("أسفل يسار"),
  BOTTOM_RIGHT("أسفل يمين")
}

private enum class ShootoutTurn {
  SHOOTING,
  KEEPING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenaltyShootoutScreen(
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  var playerGoals by remember { mutableIntStateOf(0) }
  var botGoals by remember { mutableIntStateOf(0) }
  var playerKicks by remember { mutableStateOf(emptyList<Boolean>()) }
  var botKicks by remember { mutableStateOf(emptyList<Boolean>()) }
  var turn by remember { mutableStateOf(ShootoutTurn.SHOOTING) }
  var lastBallZone by remember { mutableStateOf<GoalZone?>(null) }
  var lastKeeperZone by remember { mutableStateOf<GoalZone?>(null) }
  var statusHeadline by remember { mutableStateOf("اختر زاوية التسديد في المرمى!") }
  var winnerText by remember { mutableStateOf<String?>(null) }

  fun resetMatch() {
    playerGoals = 0
    botGoals = 0
    playerKicks = emptyList()
    botKicks = emptyList()
    turn = ShootoutTurn.SHOOTING
    lastBallZone = null
    lastKeeperZone = null
    statusHeadline = "اختر زاوية التسديد في المرمى!"
    winnerText = null
  }

  fun evaluateWinner(pKicks: List<Boolean>, bKicks: List<Boolean>, pScore: Int, bScore: Int): String? {
    val pCount = pKicks.size
    val bCount = bKicks.size
    if (pCount <= 5 && bCount <= 5) {
      val pRemaining = 5 - pCount
      val bRemaining = 5 - bCount
      if (pScore > bScore + bRemaining) return "فوز مستحق لك بالضربات الترجيحية! 🏆"
      if (bScore > pScore + pRemaining) return "فاز الحاسوب في ركلات الترجيح!"
      if (pCount == 5 && bCount == 5 && pScore != bScore) {
        return if (pScore > bScore) "أنت البطل! فزت $pScore - $bScore 🏆" else "فاز الخصم $bScore - $pScore"
      }
      return null
    }
    // Sudden death: check only after both kicked equal times
    if (pCount == bCount && pScore != bScore) {
      return if (pScore > bScore) "فوز دراماتيكي في الركلات الحاسمة! 🏆" else "حسمها الخصم في الركلات الحاسمة!"
    }
    return null
  }

  fun onZoneSelected(zone: GoalZone) {
    if (winnerText != null) return
    val botChoice = GoalZone.entries[Random.nextInt(GoalZone.entries.size)]

    if (turn == ShootoutTurn.SHOOTING) {
      lastBallZone = zone
      lastKeeperZone = botChoice
      val scored = zone != botChoice
      val newPKicks = playerKicks + scored
      val newPScore = playerGoals + if (scored) 1 else 0
      playerKicks = newPKicks
      playerGoals = newPScore

      statusHeadline = if (scored) {
        "جوووول! ⚽ سددت في ${zone.labelAr} والحارس قفز إلى ${botChoice.labelAr}"
      } else {
        "تصدٍ! 🧤 توقع الحارس زاوية ${zone.labelAr}"
      }

      val maybeWinner = evaluateWinner(newPKicks, botKicks, newPScore, botGoals)
      if (maybeWinner != null) {
        winnerText = maybeWinner
      } else {
        turn = ShootoutTurn.KEEPING
      }
    } else {
      lastKeeperZone = zone
      lastBallZone = botChoice
      val saved = zone == botChoice
      val newBKicks = botKicks + !saved
      val newBScore = botGoals + if (saved) 0 else 1
      botKicks = newBKicks
      botGoals = newBScore

      statusHeadline = if (saved) {
        "تصدٍ بطولي منك! 🧤 أمسكت الكرة في ${zone.labelAr}"
      } else {
        "هدف للخصم ⚽ سدد في ${botChoice.labelAr}"
      }

      val maybeWinner = evaluateWinner(playerKicks, newBKicks, playerGoals, newBScore)
      if (maybeWinner != null) {
        winnerText = maybeWinner
      } else {
        turn = ShootoutTurn.SHOOTING
      }
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("penalty_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع للألعاب")
          }
        },
        title = {
          Column {
            Text("ركلات الترجيح والحارس", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("تحدي 5 ركلات + الموت المفاجئ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        actions = {
          IconButton(onClick = ::resetMatch, modifier = Modifier.testTag("penalty_restart_icon")) {
            Icon(Icons.Rounded.Refresh, contentDescription = "إعادة المباراة")
          }
        }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Scoreboard Card
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("أنت", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                  text = "$playerGoals",
                  style = MaterialTheme.typography.displaySmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
              Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp)) {
                Text(
                  text = if (playerKicks.size >= 5 && botKicks.size >= 5) "ركلة حاسمة" else "الجولة ${(minOf(playerKicks.size, 4) + 1)} / 5",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
              }
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("الخصم", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text(
                  text = "$botGoals",
                  style = MaterialTheme.typography.displaySmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }

            // Kick Dots
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              KickDotsRow(label = "تسديداتك", kicks = playerKicks)
              KickDotsRow(label = "الخصم", kicks = botKicks)
            }
          }
        }

        // Turn & Result Banner
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (winnerText != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
          ),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            if (winnerText != null) {
              Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
              Text(
                text = winnerText.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
              )
            } else {
              Text(
                text = if (turn == ShootoutTurn.SHOOTING) "دورك: سدد الكرة ⚽" else "دورك: تصدَّ للكرة كحارس 🧤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Text(
              text = statusHeadline,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }

        // Interactive Goal Frame (LTR inside for accurate left/right placement)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(MaterialTheme.colorScheme.surfaceContainer)
              .border(3.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              GoalZoneButton(
                zone = GoalZone.TOP_LEFT,
                lastBallZone = lastBallZone,
                lastKeeperZone = lastKeeperZone,
                enabled = winnerText == null,
                modifier = Modifier.weight(1f),
                onClick = { onZoneSelected(GoalZone.TOP_LEFT) }
              )
              GoalZoneButton(
                zone = GoalZone.TOP_RIGHT,
                lastBallZone = lastBallZone,
                lastKeeperZone = lastKeeperZone,
                enabled = winnerText == null,
                modifier = Modifier.weight(1f),
                onClick = { onZoneSelected(GoalZone.TOP_RIGHT) }
              )
            }

            GoalZoneButton(
              zone = GoalZone.CENTER,
              lastBallZone = lastBallZone,
              lastKeeperZone = lastKeeperZone,
              enabled = winnerText == null,
              modifier = Modifier.fillMaxWidth(),
              onClick = { onZoneSelected(GoalZone.CENTER) }
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              GoalZoneButton(
                zone = GoalZone.BOTTOM_LEFT,
                lastBallZone = lastBallZone,
                lastKeeperZone = lastKeeperZone,
                enabled = winnerText == null,
                modifier = Modifier.weight(1f),
                onClick = { onZoneSelected(GoalZone.BOTTOM_LEFT) }
              )
              GoalZoneButton(
                zone = GoalZone.BOTTOM_RIGHT,
                lastBallZone = lastBallZone,
                lastKeeperZone = lastKeeperZone,
                enabled = winnerText == null,
                modifier = Modifier.weight(1f),
                onClick = { onZoneSelected(GoalZone.BOTTOM_RIGHT) }
              )
            }
          }
        }

        if (winnerText != null) {
          Button(
            onClick = ::resetMatch,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("penalty_restart_button")
          ) {
            Icon(Icons.Rounded.SportsSoccer, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("العب مباراة جديدة")
          }
        }
      }
    }
  }
}

@Composable
private fun GoalZoneButton(
  zone: GoalZone,
  lastBallZone: GoalZone?,
  lastKeeperZone: GoalZone?,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val hasBall = zone == lastBallZone
  val hasKeeper = zone == lastKeeperZone

  Surface(
    color = when {
      hasBall && hasKeeper -> MaterialTheme.colorScheme.errorContainer
      hasBall -> MaterialTheme.colorScheme.primaryContainer
      hasKeeper -> MaterialTheme.colorScheme.secondaryContainer
      else -> MaterialTheme.colorScheme.surfaceContainerHigh
    },
    shape = RoundedCornerShape(14.dp),
    modifier = modifier
      .height(74.dp)
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
      .clip(RoundedCornerShape(14.dp))
      .clickable(enabled = enabled, onClick = onClick)
      .testTag("penalty_zone_${zone.name.lowercase()}")
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      val badge = buildString {
        if (hasBall) append("⚽ ")
        if (hasKeeper) append("🧤")
      }.trim()
      if (badge.isNotEmpty()) {
        Text(text = badge, fontSize = 20.sp)
      }
      Text(
        text = zone.labelAr,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun KickDotsRow(label: String, kicks: List<Boolean>) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      val totalSlots = maxOf(5, kicks.size)
      for (i in 0 until totalSlots) {
        val color = when {
          i >= kicks.size -> MaterialTheme.colorScheme.outline
          kicks[i] -> MaterialTheme.colorScheme.primary
          else -> MaterialTheme.colorScheme.error
        }
        Box(
          modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
        )
      }
    }
  }
}
