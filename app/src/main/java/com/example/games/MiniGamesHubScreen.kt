package com.example.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class MiniGameDestination {
  HUB,
  AUCTION,
  PENALTY,
  PING_PONG,
  CHESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniGamesHubScreen(
  memberNames: List<String> = emptyList(),
  onClose: () -> Unit
) {
  var currentDestination by remember { mutableStateOf(MiniGameDestination.HUB) }
  var showMysterySlotDialog by remember { mutableStateOf(false) }

  BackHandler {
    if (currentDestination != MiniGameDestination.HUB) {
      currentDestination = MiniGameDestination.HUB
    } else {
      onClose()
    }
  }

  when (currentDestination) {
    MiniGameDestination.AUCTION -> {
      AuctionGameScreen(
        initialPlayerNames = memberNames,
        onBack = { currentDestination = MiniGameDestination.HUB }
      )
      return
    }
    MiniGameDestination.PENALTY -> {
      PenaltyShootoutScreen(
        onBack = { currentDestination = MiniGameDestination.HUB }
      )
      return
    }
    MiniGameDestination.PING_PONG -> {
      PingPongScreen(
        onBack = { currentDestination = MiniGameDestination.HUB }
      )
      return
    }
    MiniGameDestination.CHESS -> {
      ChessGameScreen(
        onBack = { currentDestination = MiniGameDestination.HUB }
      )
      return
    }
    MiniGameDestination.HUB -> Unit
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
          IconButton(onClick = onClose, modifier = Modifier.testTag("games_hub_close_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
          }
        },
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text("مركز ألعاب شلتنا", style = MaterialTheme.typography.titleLarge)
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
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
          Text(
            text = "اختر لعبة لتبدأ التحدي الآن",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // 1. Auction Game (Imbabi Style)
        item {
          MiniGameHubCard(
            title = "مزاد إمبابي (بنك اللاعبين والمدربين)",
            subtitle = "اختر الميزانية: عادي 100M أو برو ماكس 200M، زايد أو انسحب (Pass)، واجمع أقوى تشكيلة!",
            badge = "100M / 200M",
            icon = Icons.Rounded.Gavel,
            accentColor = MaterialTheme.colorScheme.tertiary,
            containerAccent = MaterialTheme.colorScheme.tertiaryContainer,
            testTag = "hub_card_auction",
            onClick = { currentDestination = MiniGameDestination.AUCTION }
          )
        }

        // 2. Football Penalty & Keeper
        item {
          MiniGameHubCard(
            title = "ركلات الترجيح والحارس ⚽",
            subtitle = "سدد في زوايا المرمى الخمس ثم تصدَّ لكرات الخصم في 5 جولات وركلات الحسم.",
            badge = "كرة قدم",
            icon = Icons.Rounded.SportsSoccer,
            accentColor = MaterialTheme.colorScheme.primary,
            containerAccent = MaterialTheme.colorScheme.primaryContainer,
            testTag = "hub_card_penalty",
            onClick = { currentDestination = MiniGameDestination.PENALTY }
          )
        }

        // 3. Ping Pong
        item {
          MiniGameHubCard(
            title = "بينج بونج السريع 🏓",
            subtitle = "تحكم سريع باللمس في مواجهة الروبوت حتى 7 نقاط مع تسارع الكرة في كل تبادل.",
            badge = "سريع",
            icon = Icons.Rounded.SportsTennis,
            accentColor = MaterialTheme.colorScheme.secondary,
            containerAccent = MaterialTheme.colorScheme.secondaryContainer,
            testTag = "hub_card_ping_pong",
            onClick = { currentDestination = MiniGameDestination.PING_PONG }
          )
        }

        // 4. Chess
        item {
          MiniGameHubCard(
            title = "شطرنج الشلة ♔",
            subtitle = "لوحة شطرنج كاملة بجميع القوانين: العب ضد البوت الذكي أو لاعب ضد لاعب على نفس الجهاز.",
            badge = "تكتيك",
            icon = Icons.Rounded.Extension,
            accentColor = MaterialTheme.colorScheme.tertiary,
            containerAccent = MaterialTheme.colorScheme.tertiaryContainer,
            testTag = "hub_card_chess",
            onClick = { currentDestination = MiniGameDestination.CHESS }
          )
        }

        // 5. Dedicated Hidden / Bonus Surprise Slot
        item {
          Spacer(modifier = Modifier.height(4.dp))
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
              .clip(RoundedCornerShape(22.dp))
              .clickable { showMysterySlotDialog = true }
              .testTag("hub_card_mystery_slot")
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Rounded.Lock,
                  contentDescription = "لعبة سرية قادمة",
                  tint = MaterialTheme.colorScheme.secondary
                )
              }
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = "الخزنة السرية / مفاجأة الشلة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                  )
                  Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                  )
                }
                Text(
                  text = "مكان مخصص للعبة إضافية سرية ومفاجآت التحديث القادم!",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = "سري 🎁",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }
        }
      }
    }
  }

  if (showMysterySlotDialog) {
    AlertDialog(
      onDismissRequest = { showMysterySlotDialog = false },
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
          Text("مفاجأة الشلة القادمة 🎁", style = MaterialTheme.typography.titleLarge)
        }
      },
      text = {
        Text(
          text = "هذه الخانة محجوزة للعبة إضافية سرية في التحديث المقبل لشلتنا! استمتع الآن بالمزاد وركلات الترجيح وبينج بونج والشطرنج.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      },
      confirmButton = {
        TextButton(onClick = { showMysterySlotDialog = false }) {
          Text("متحمس!")
        }
      }
    )
  }
}

@Composable
private fun MiniGameHubCard(
  title: String,
  subtitle: String,
  badge: String,
  icon: ImageVector,
  accentColor: Color,
  containerAccent: Color,
  testTag: String,
  onClick: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    shape = RoundedCornerShape(22.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
      .clip(RoundedCornerShape(22.dp))
      .clickable(onClick = onClick)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(containerAccent),
        contentAlignment = Alignment.Center
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Surface(color = containerAccent, shape = RoundedCornerShape(8.dp)) {
        Text(
          text = badge,
          style = MaterialTheme.typography.labelSmall,
          color = accentColor,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
      }
    }
  }
}
