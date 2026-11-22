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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuctionGameScreen(
  initialPlayerNames: List<String>,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  var selectedMode by remember { mutableStateOf(AuctionBudgetMode.NORMAL) }
  val seededNames = remember(initialPlayerNames) {
    val distinct = initialPlayerNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(6)
    when {
      distinct.size >= 2 -> distinct
      distinct.size == 1 -> listOf(distinct.first(), "صديق 2")
      else -> listOf("اللاعب 1", "اللاعب 2", "اللاعب 3")
    }
  }
  val editableNames = remember { mutableStateListOf<String>().apply { addAll(seededNames) } }
  var auctionState by remember { mutableStateOf(AuctionState(phase = AuctionPhase.SETUP)) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("auction_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع للألعاب")
          }
        },
        title = {
          Column {
            Text(
              text = "مزاد اللاعبين والمدربين",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.tertiary
            )
            Text(
              text = "بنك المزاد • 100M أو 200M برو ماكس",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          if (auctionState.phase != AuctionPhase.SETUP) {
            TextButton(
              onClick = { auctionState = AuctionState(phase = AuctionPhase.SETUP) },
              modifier = Modifier.testTag("auction_reset_button")
            ) {
              Text("إعدادات")
            }
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
      when (auctionState.phase) {
        AuctionPhase.SETUP -> AuctionSetupView(
          selectedMode = selectedMode,
          onModeSelected = { selectedMode = it },
          playerNames = editableNames,
          onNameChange = { index, value ->
            if (index in editableNames.indices && value.length <= 24) {
              editableNames[index] = value
            }
          },
          onAddPlayer = {
            if (editableNames.size < 6) {
              editableNames.add("اللاعب ${editableNames.size + 1}")
            }
          },
          onRemovePlayer = { index ->
            if (editableNames.size > 2 && index in editableNames.indices) {
              editableNames.removeAt(index)
            }
          },
          onStartAuction = {
            auctionState = AuctionEngine.startAuction(
              mode = selectedMode,
              playerNames = editableNames.toList()
            )
          }
        )

        AuctionPhase.BIDDING -> AuctionBiddingView(
          state = auctionState,
          onPlaceBid = { increment -> auctionState = AuctionEngine.placeBid(auctionState, increment) },
          onPass = { auctionState = AuctionEngine.passTurn(auctionState) },
          onHammerNow = { auctionState = AuctionEngine.hammerCurrentLot(auctionState) },
          onFinishEarly = { auctionState = AuctionEngine.finishEarly(auctionState) }
        )

        AuctionPhase.LOT_RESULT -> AuctionLotResultView(
          state = auctionState,
          onNextLot = { auctionState = AuctionEngine.nextLot(auctionState) },
          onFinishEarly = { auctionState = AuctionEngine.finishEarly(auctionState) }
        )

        AuctionPhase.FINISHED -> AuctionSummaryView(
          state = auctionState,
          onPlayAgain = {
            auctionState = AuctionEngine.startAuction(
              mode = auctionState.budgetMode,
              playerNames = auctionState.participants.map { it.name }
            )
          },
          onChangeSettings = {
            auctionState = AuctionState(phase = AuctionPhase.SETUP)
          }
        )
      }
    }
  }
}

@Composable
private fun AuctionSetupView(
  selectedMode: AuctionBudgetMode,
  onModeSelected: (AuctionBudgetMode) -> Unit,
  playerNames: List<String>,
  onNameChange: (Int, String) -> Unit,
  onAddPlayer: () -> Unit,
  onRemovePlayer: (Int) -> Unit,
  onStartAuction: () -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .widthIn(max = 600.dp)
      .fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Text(
        text = "اختر نظام الميزانية",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        AuctionBudgetMode.entries.forEach { mode ->
          val isSelected = mode == selectedMode
          val tag = if (mode == AuctionBudgetMode.NORMAL) "auction_mode_normal" else "auction_mode_pro_max"
          Card(
            colors = CardDefaults.cardColors(
              containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
              .weight(1f)
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(20.dp)
              )
              .clip(RoundedCornerShape(20.dp))
              .clickable { onModeSelected(mode) }
              .testTag(tag)
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = "${mode.startingBudgetM}M",
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "مختار",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }
              Text(
                text = mode.titleAr,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = mode.subtitleAr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "المتنافسون (${playerNames.size}/6)",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground
        )
        if (playerNames.size < 6) {
          OutlinedButton(
            onClick = onAddPlayer,
            modifier = Modifier.height(48.dp).testTag("auction_add_player_button")
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("إضافة لاعب")
          }
        }
      }
    }

    itemsIndexed(playerNames) { index, name ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { onNameChange(index, it) },
          label = { Text("اسم المتنافس ${index + 1}") },
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("auction_player_name_$index")
        )
        if (playerNames.size > 2) {
          IconButton(
            onClick = { onRemovePlayer(index) },
            modifier = Modifier.size(48.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = "حذف اللاعب",
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(6.dp))
      Button(
        onClick = onStartAuction,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("start_auction_button")
      ) {
        Icon(Icons.Rounded.Gavel, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "بدء المزاد (${selectedMode.startingBudgetM}M لكل لاعب)",
          style = MaterialTheme.typography.titleMedium
        )
      }
    }
  }
}

@Composable
private fun AuctionBiddingView(
  state: AuctionState,
  onPlaceBid: (Int) -> Unit,
  onPass: () -> Unit,
  onHammerNow: () -> Unit,
  onFinishEarly: () -> Unit
) {
  val lot = state.currentLot ?: return
  val turnPlayer = state.currentTurnParticipant ?: return
  val minBid = AuctionEngine.minimumNextBid(state)
  val canAffordMin = AuctionEngine.canCurrentPlayerBid(state, minBid)

  LazyColumn(
    modifier = Modifier
      .widthIn(max = 600.dp)
      .fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Progress + Finish Early
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(10.dp)
        ) {
          Text(
            text = "صفقة ${state.currentLotIndex + 1} من ${state.lots.size} • ${state.budgetMode.titleAr}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
        TextButton(
          onClick = onFinishEarly,
          modifier = Modifier.testTag("finish_auction_early_button")
        ) {
          Text("إنهاء المزاد", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    // Current Player / Coach Showcase Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
          .testTag("auction_lot_card")
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              color = if (lot.isCoach) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(10.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = if (lot.isCoach) Icons.Rounded.Sports else Icons.Rounded.Person,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = if (lot.isCoach) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Text(
                  text = if (lot.isCoach) "مدرب" else "لاعب",
                  style = MaterialTheme.typography.labelMedium,
                  color = if (lot.isCoach) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
              }
            }

            Surface(
              color = MaterialTheme.colorScheme.tertiaryContainer,
              shape = RoundedCornerShape(10.dp)
            ) {
              Text(
                text = "OVR ${lot.rating}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }

          Text(
            text = lot.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "${lot.roleOrPos} • ${lot.clubOrNation}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          HorizontalDivider(color = MaterialTheme.colorScheme.outline)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = if (state.highestBidderIndex == null) "السعر الابتدائي" else "أعلى عرض حالي",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "${state.currentBidM}M",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("auction_current_bid_text")
              )
            }

            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "صاحب أعلى عرض",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = state.highestBidderParticipant?.name ?: "لا يوجد عرض بعد",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (state.highestBidderParticipant != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("auction_highest_bidder_text")
              )
            }
          }
        }
      }
    }

    // Active Turn Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "الدور الآن على:",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
              text = turnPlayer.name,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.testTag("auction_turn_player_text")
            )
          }
          Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(12.dp)
          ) {
            Text(
              text = "الميزانية: ${turnPlayer.remainingBudgetM}M",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
          }
        }
      }
    }

    // Participants strip
    item {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        itemsIndexed(state.participants) { idx, p ->
          val isTurn = idx == state.currentTurnIndex
          val isHighest = idx == state.highestBidderIndex
          val hasPassed = idx in state.passedIndices
          Card(
            colors = CardDefaults.cardColors(
              containerColor = when {
                isTurn -> MaterialTheme.colorScheme.surfaceContainerHighest
                hasPassed -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
              }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .border(
                width = if (isTurn) 1.5.dp else 1.dp,
                color = when {
                  isHighest -> MaterialTheme.colorScheme.tertiary
                  isTurn -> MaterialTheme.colorScheme.primary
                  else -> MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(16.dp)
              )
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = p.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (hasPassed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "${p.remainingBudgetM}M • ${p.wonLots.size} صفقات",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
              )
              when {
                isHighest -> Text("أعلى مزايد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                hasPassed -> Text("منسحب", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                isTurn -> Text("دوره الآن", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      }
    }

    // Bid & Pass Controls
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!canAffordMin) {
          Text(
            text = "ميزانية ${turnPlayer.name} (${turnPlayer.remainingBudgetM}M) أقل من الحد الأدنى (${minBid}M) — اضغط تخطي للمتابعة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }

        if (state.highestBidderIndex == null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = { onPlaceBid(0) },
              enabled = AuctionEngine.canCurrentPlayerBid(state, lot.basePriceM),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("auction_bid_base_button")
            ) {
              Text("مزايدة (${lot.basePriceM}M)")
            }
            Button(
              onClick = { onPlaceBid(5) },
              enabled = AuctionEngine.canCurrentPlayerBid(state, lot.basePriceM + 5),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
              ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("auction_bid_plus_5_button")
            ) {
              Text("+5M (${lot.basePriceM + 5}M)")
            }
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf(1, 5, 10).forEach { inc ->
              val proposed = state.currentBidM + inc
              Button(
                onClick = { onPlaceBid(inc) },
                enabled = AuctionEngine.canCurrentPlayerBid(state, proposed),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(50.dp)
                  .testTag("auction_bid_plus_${inc}_button")
              ) {
                Text("+${inc}M")
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onPass,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .testTag("auction_pass_button")
          ) {
            Text("تخطي / انسحاب (Pass)", color = MaterialTheme.colorScheme.error)
          }

          if (state.highestBidderIndex != null) {
            OutlinedButton(
              onClick = onHammerNow,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("auction_hammer_button")
            ) {
              Text("حسم للأعلى الآن", color = MaterialTheme.colorScheme.tertiary)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AuctionLotResultView(
  state: AuctionState,
  onNextLot: () -> Unit,
  onFinishEarly: () -> Unit
) {
  val hasWinner = state.lastWinnerIndex != null
  val isLastLot = state.currentLotIndex + 1 >= state.lots.size

  Column(
    modifier = Modifier
      .widthIn(max = 600.dp)
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Card(
      colors = CardDefaults.cardColors(
        containerColor = if (hasWinner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
      ),
      shape = RoundedCornerShape(24.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(
          1.5.dp,
          if (hasWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
          RoundedCornerShape(24.dp)
        )
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = if (hasWinner) Icons.Rounded.EmojiEvents else Icons.Rounded.Gavel,
          contentDescription = null,
          tint = if (hasWinner) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(44.dp)
        )
        Text(
          text = state.lastResultMessage ?: "انتهت الجولة",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag("auction_lot_result_text")
        )
      }
    }

    Text(
      text = "الميزانيات والصفقات حتى الآن",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onBackground
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(state.participants, key = { it.id }) { p ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(p.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
              Text(
                text = "المتبقي: ${p.remainingBudgetM}M",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
              )
            }
            if (p.wonLots.isEmpty()) {
              Text("لم يفز بأي صفقة بعد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
              Text(
                text = p.wonLots.joinToString(" • ") { "${it.lot.name} (${it.pricePaidM}M)" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
              )
            }
          }
        }
      }
    }

    Button(
      onClick = onNextLot,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("auction_next_lot_button")
    ) {
      Text(if (isLastLot) "عرض الملخص النهائي" else "الصفقة التالية")
    }

    if (!isLastLot) {
      OutlinedButton(
        onClick = onFinishEarly,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
      ) {
        Text("إنهاء المزاد الآن")
      }
    }
  }
}

@Composable
private fun AuctionSummaryView(
  state: AuctionState,
  onPlayAgain: () -> Unit,
  onChangeSettings: () -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .widthIn(max = 600.dp)
      .fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(18.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(36.dp)
          )
          Column {
            Text(
              text = "الملخص النهائي للمزاد",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "${state.budgetMode.titleAr} (${state.budgetMode.startingBudgetM}M)",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.tertiary
            )
          }
        }
      }
    }

    items(state.participants, key = { it.id }) { p ->
      val spent = state.budgetMode.startingBudgetM - p.remainingBudgetM
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
              Text(
                text = "المتبقي ${p.remainingBudgetM}M (صُرف ${spent}M)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }
          if (p.wonLots.isEmpty()) {
            Text("لا توجد صفقات في هذا الفريق", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            p.wonLots.forEach { won ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "${won.lot.name} (${if (won.lot.isCoach) "مدرب" else won.lot.roleOrPos})",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "${won.pricePaidM}M",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.tertiary
                )
              }
            }
          }
        }
      }
    }

    if (state.unsoldLots.isNotEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("أسماء لم تُبع:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = state.unsoldLots.joinToString(" • ") { it.name },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = onPlayAgain,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.weight(1f).height(50.dp).testTag("auction_restart_button")
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("إعادة بنفس الفرق")
        }
        OutlinedButton(
          onClick = onChangeSettings,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.weight(1f).height(50.dp)
        ) {
          Text("تغيير المود والأسماء")
        }
      }
    }
  }
}
