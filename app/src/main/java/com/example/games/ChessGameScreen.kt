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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessGameScreen(
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  var gameState by remember { mutableStateOf(ChessGameState()) }
  var vsBot by remember { mutableStateOf(true) }

  // Bot plays Black automatically
  LaunchedEffect(gameState.turn, vsBot, gameState.winner, gameState.isDraw) {
    if (vsBot && gameState.turn == ChessColor.BLACK && gameState.winner == null && !gameState.isDraw) {
      delay(350)
      val botMove = ChessEngine.pickBotMove(gameState)
      if (botMove != null) {
        gameState = ChessEngine.applyMove(gameState, botMove.first, botMove.second)
      }
    }
  }

  val statusText = when {
    gameState.winner == ChessColor.WHITE -> "كش مات! فاز الأبيض ♔"
    gameState.winner == ChessColor.BLACK -> "كش مات! فاز الأسود ♚"
    gameState.isDraw -> "تعادل (Stalemate)!"
    gameState.inCheck == ChessColor.WHITE -> "كش ملك على الأبيض!"
    gameState.inCheck == ChessColor.BLACK -> "كش ملك على الأسود!"
    gameState.turn == ChessColor.WHITE -> "دور الأبيض (أنت)"
    vsBot -> "دور البوت الأسود..."
    else -> "دور الأسود"
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("chess_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع للألعاب")
          }
        },
        title = {
          Column {
            Text("شطرنج الشلة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            Text("لعب كامل ضد البوت أو صديقك", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        actions = {
          IconButton(
            onClick = { gameState = ChessGameState() },
            modifier = Modifier.testTag("chess_reset_button")
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "لعبة جديدة")
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
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Mode Switcher
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          FilterChip(
            selected = vsBot,
            onClick = {
              vsBot = true
              gameState = ChessGameState()
            },
            label = { Text("ضد البوت 🤖") },
            modifier = Modifier.testTag("chess_mode_bot")
          )
          FilterChip(
            selected = !vsBot,
            onClick = {
              vsBot = false
              gameState = ChessGameState()
            },
            label = { Text("لاعب ضد لاعب 👥") },
            modifier = Modifier.testTag("chess_mode_pvp")
          )
        }

        // Status Card
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (gameState.inCheck != null || gameState.winner != null) {
              MaterialTheme.colorScheme.tertiaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceContainerHigh
            }
          ),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = statusText,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("chess_status_text")
            )
            Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp)) {
              Text(
                text = "حركات: ${gameState.moveCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }

        // Captured by White (black pieces)
        if (gameState.capturedByWhite.isNotEmpty()) {
          Text(
            text = "مأسور للأبيض: ${gameState.capturedByWhite.joinToString(" ") { it.symbol() }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
          )
        }

        // 8x8 Chess Board (LTR for standard file/rank layout)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(1f)
              .clip(RoundedCornerShape(14.dp))
              .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
              .testTag("chess_board")
          ) {
            for (r in 0..7) {
              Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0..7) {
                  val sq = Square(r, c)
                  val piece = gameState.board[r][c]
                  val isLightSquare = (r + c) % 2 == 0
                  val isSelected = gameState.selected == sq
                  val isLegalTarget = sq in gameState.legalTargets
                  val isLastMove = sq == gameState.lastMoveFrom || sq == gameState.lastMoveTo
                  val isCheckKing = piece?.type == PieceType.KING && piece.color == gameState.inCheck

                  val bgColor = when {
                    isCheckKing -> MaterialTheme.colorScheme.errorContainer
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    isLastMove -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                    isLightSquare -> Color(0xFF31405A)
                    else -> Color(0xFF1A2438)
                  }

                  val canTap = !vsBot || gameState.turn == ChessColor.WHITE

                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .fillMaxSize()
                      .background(bgColor)
                      .clickable(enabled = canTap) {
                        gameState = ChessEngine.onSquareTapped(gameState, sq)
                      }
                      .testTag("chess_sq_${r}_${c}"),
                    contentAlignment = Alignment.Center
                  ) {
                    if (isLegalTarget && piece == null) {
                      Box(
                        modifier = Modifier
                          .size(12.dp)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                      )
                    } else if (isLegalTarget && piece != null) {
                      Box(
                        modifier = Modifier
                          .fillMaxSize()
                          .padding(3.dp)
                          .border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape)
                      )
                    }

                    if (piece != null) {
                      Text(
                        text = piece.symbol(),
                        fontSize = 26.sp,
                        color = if (piece.color == ChessColor.WHITE) Color(0xFFF8FAFC) else Color(0xFF22D3EE)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Captured by Black (white pieces)
        if (gameState.capturedByBlack.isNotEmpty()) {
          Text(
            text = "مأسور للأسود: ${gameState.capturedByBlack.joinToString(" ") { it.symbol() }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
          )
        }

        // Bottom Controls
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              if (vsBot && gameState.turn == ChessColor.WHITE && gameState.history.size >= 2) {
                gameState = ChessEngine.undoMove(ChessEngine.undoMove(gameState))
              } else {
                gameState = ChessEngine.undoMove(gameState)
              }
            },
            enabled = gameState.history.isNotEmpty(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("chess_undo_button")
          ) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("تراجع")
          }
          OutlinedButton(
            onClick = { gameState = ChessGameState() },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(48.dp)
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("بدء جديد")
          }
        }
      }
    }
  }
}
