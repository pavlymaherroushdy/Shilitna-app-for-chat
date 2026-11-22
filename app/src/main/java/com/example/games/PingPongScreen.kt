package com.example.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingPongScreen(
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  val targetScore = 7
  val paddleWidthNorm = 0.26f
  val paddleHeightNorm = 0.025f
  val ballRadiusNorm = 0.022f

  var playerScore by remember { mutableIntStateOf(0) }
  var botScore by remember { mutableIntStateOf(0) }
  var rallyCount by remember { mutableIntStateOf(0) }
  var isRunning by remember { mutableStateOf(true) }
  var winnerText by remember { mutableStateOf<String?>(null) }

  var playerPaddleX by remember { mutableFloatStateOf(0.5f) }
  var botPaddleX by remember { mutableFloatStateOf(0.5f) }
  var ballX by remember { mutableFloatStateOf(0.5f) }
  var ballY by remember { mutableFloatStateOf(0.5f) }
  var velX by remember { mutableFloatStateOf(0.42f) }
  var velY by remember { mutableFloatStateOf(0.56f) }
  var canvasWidthPx by remember { mutableFloatStateOf(1f) }

  fun resetBall(serveTowardsPlayer: Boolean) {
    ballX = 0.5f
    ballY = 0.5f
    rallyCount = 0
    val dirX = if ((playerScore + botScore) % 2 == 0) 1f else -1f
    velX = 0.42f * dirX
    velY = if (serveTowardsPlayer) 0.56f else -0.56f
  }

  fun restartGame() {
    playerScore = 0
    botScore = 0
    playerPaddleX = 0.5f
    botPaddleX = 0.5f
    winnerText = null
    isRunning = true
    resetBall(serveTowardsPlayer = true)
  }

  LaunchedEffect(isRunning, winnerText) {
    if (!isRunning || winnerText != null) return@LaunchedEffect
    var lastFrameNanos = 0L
    while (isRunning && winnerText == null) {
      withFrameNanos { frameNanos ->
        if (lastFrameNanos == 0L) {
          lastFrameNanos = frameNanos
          return@withFrameNanos
        }
        val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.035f)
        lastFrameNanos = frameNanos

        // Move ball
        ballX += velX * dt
        ballY += velY * dt

        // Bounce side walls
        if (ballX <= ballRadiusNorm) {
          ballX = ballRadiusNorm
          velX = abs(velX)
        } else if (ballX >= 1f - ballRadiusNorm) {
          ballX = 1f - ballRadiusNorm
          velX = -abs(velX)
        }

        // Move AI paddle smoothly toward ball
        val botMaxSpeed = 0.68f
        val diff = ballX - botPaddleX
        val step = (diff.sign * minOf(abs(diff), botMaxSpeed * dt))
        botPaddleX = (botPaddleX + step).coerceIn(paddleWidthNorm / 2f, 1f - paddleWidthNorm / 2f)

        // Check Bot top paddle collision
        val topPaddleBottomY = 0.07f + paddleHeightNorm
        if (velY < 0f && ballY - ballRadiusNorm <= topPaddleBottomY && ballY >= 0.04f) {
          if (abs(ballX - botPaddleX) <= paddleWidthNorm / 2f + ballRadiusNorm) {
            ballY = topPaddleBottomY + ballRadiusNorm
            val hitOffset = (ballX - botPaddleX) / (paddleWidthNorm / 2f)
            velY = (abs(velY) * 1.04f).coerceAtMost(1.15f)
            velX = (velX + hitOffset * 0.35f).coerceIn(-0.95f, 0.95f)
            rallyCount += 1
          }
        }

        // Check Player bottom paddle collision
        val bottomPaddleTopY = 0.93f - paddleHeightNorm
        if (velY > 0f && ballY + ballRadiusNorm >= bottomPaddleTopY && ballY <= 0.96f) {
          if (abs(ballX - playerPaddleX) <= paddleWidthNorm / 2f + ballRadiusNorm) {
            ballY = bottomPaddleTopY - ballRadiusNorm
            val hitOffset = (ballX - playerPaddleX) / (paddleWidthNorm / 2f)
            velY = -(abs(velY) * 1.04f).coerceAtMost(1.15f)
            velX = (velX + hitOffset * 0.35f).coerceIn(-0.95f, 0.95f)
            rallyCount += 1
          }
        }

        // Scoring
        if (ballY < 0f) {
          playerScore += 1
          if (playerScore >= targetScore) {
            winnerText = "مبروك! فزت على الروبوت $playerScore - $botScore 🏓"
            isRunning = false
          } else {
            resetBall(serveTowardsPlayer = false)
          }
        } else if (ballY > 1f) {
          botScore += 1
          if (botScore >= targetScore) {
            winnerText = "انتهت المباراة! فاز الروبوت $botScore - $playerScore"
            isRunning = false
          } else {
            resetBall(serveTowardsPlayer = true)
          }
        }
      }
    }
  }

  val emeraldColor = MaterialTheme.colorScheme.primary
  val cyanColor = MaterialTheme.colorScheme.secondary
  val amberColor = MaterialTheme.colorScheme.tertiary
  val outlineColor = MaterialTheme.colorScheme.outline
  val surfaceColor = MaterialTheme.colorScheme.surfaceContainer

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("ping_pong_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع للألعاب")
          }
        },
        title = {
          Column {
            Text("بينج بونج السريع", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Text("اسحب أفقيًا لتحريك مضربك • الفوز من 7 نقاط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        actions = {
          IconButton(
            onClick = { if (winnerText == null) isRunning = !isRunning },
            modifier = Modifier.testTag("ping_pong_pause_button")
          ) {
            Icon(
              imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = if (isRunning) "إيقاف مؤقت" else "استئناف"
            )
          }
          IconButton(onClick = ::restartGame, modifier = Modifier.testTag("ping_pong_restart_button")) {
            Icon(Icons.Rounded.Refresh, contentDescription = "إعادة اللعبة")
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
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Live Score Header
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
              Text("الروبوت", style = MaterialTheme.typography.labelSmall, color = cyanColor)
              Text("$botScore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(10.dp)) {
              Text(
                text = "تبادل: $rallyCount",
                style = MaterialTheme.typography.labelMedium,
                color = amberColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text("أنت", style = MaterialTheme.typography.labelSmall, color = emeraldColor)
              Text("$playerScore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Responsive Canvas Table
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(surfaceColor)
            .border(1.5.dp, outlineColor, RoundedCornerShape(24.dp))
            .onSizeChanged { canvasWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
              detectHorizontalDragGestures { change, dragAmount ->
                change.consume()
                val deltaNorm = dragAmount / canvasWidthPx
                playerPaddleX = (playerPaddleX + deltaNorm).coerceIn(paddleWidthNorm / 2f, 1f - paddleWidthNorm / 2f)
              }
            }
            .testTag("ping_pong_canvas")
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Center dashed net
            drawLine(
              color = outlineColor,
              start = Offset(0f, h * 0.5f),
              end = Offset(w, h * 0.5f),
              strokeWidth = 4f,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
            )

            // Bot Top Paddle
            val paddleW = w * paddleWidthNorm
            val paddleH = h * paddleHeightNorm
            drawRoundRect(
              color = cyanColor,
              topLeft = Offset(w * botPaddleX - paddleW / 2f, h * 0.07f),
              size = Size(paddleW, paddleH),
              cornerRadius = CornerRadius(paddleH / 2f, paddleH / 2f)
            )

            // Player Bottom Paddle
            drawRoundRect(
              color = emeraldColor,
              topLeft = Offset(w * playerPaddleX - paddleW / 2f, h * 0.93f - paddleH),
              size = Size(paddleW, paddleH),
              cornerRadius = CornerRadius(paddleH / 2f, paddleH / 2f)
            )

            // Ball
            val radius = minOf(w, h) * ballRadiusNorm
            drawCircle(
              color = amberColor,
              radius = radius,
              center = Offset(w * ballX, h * ballY)
            )
          }

          if (winnerText != null) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f)),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
              ) {
                Text(
                  text = winnerText.orEmpty(),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Button(onClick = ::restartGame, modifier = Modifier.height(48.dp)) {
                  Icon(Icons.Rounded.Refresh, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("العب مجدداً")
                }
              }
            }
          }
        }

        // Quick Touch Buttons for accessibility alongside drag
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedButton(
            onClick = {
              playerPaddleX = (playerPaddleX - 0.14f).coerceIn(paddleWidthNorm / 2f, 1f - paddleWidthNorm / 2f)
            },
            modifier = Modifier.weight(1f).height(48.dp)
          ) {
            Text("◀ يسار")
          }
          OutlinedButton(
            onClick = {
              playerPaddleX = (playerPaddleX + 0.14f).coerceIn(paddleWidthNorm / 2f, 1f - paddleWidthNorm / 2f)
            },
            modifier = Modifier.weight(1f).height(48.dp)
          ) {
            Text("يمين ▶")
          }
        }
      }
    }
  }
}
