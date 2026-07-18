package es.romsolutions.theline.ui.screens

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.romsolutions.theline.R
import es.romsolutions.theline.ads.AdManager
import es.romsolutions.theline.game.GameViewModel
import es.romsolutions.theline.game.model.*
import es.romsolutions.theline.game.sound.SoundManager
import es.romsolutions.theline.ui.components.InputButton
import es.romsolutions.theline.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    level: Int,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val adManager = remember { AdManager(context) }
    val gameState by viewModel.gameState.collectAsState()

    val onLeftPress: (Boolean) -> Unit = { viewModel.rotateLeftPressed = it }
    val onRightPress: (Boolean) -> Unit = { viewModel.rotateRightPressed = it }
    val onThrustPress: (Boolean) -> Unit = { viewModel.thrustPressed = it }
    val onFirePress: (Boolean) -> Unit = { viewModel.firePressed = it }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is GameEvent.ShipFired -> soundManager.playLaser()
                else -> {}
            }
        }
    }

    LaunchedEffect(level) {
        soundManager.playBackgroundMusic(level)
    }
    
    // Show Interstitial every 2 levels (approx logic based on level change)
    LaunchedEffect(gameState?.currentLevel) {
        val currentLvl = gameState?.currentLevel ?: return@LaunchedEffect
        if (currentLvl > 1 && currentLvl % 2 == 1) {
            adManager.showInterstitial(context as Activity) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            soundManager.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Stats Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            gameState?.let { state ->
                Column {
                    Text(text = "LEVEL ${state.currentLevel}", color = NeonCyan.copy(alpha = 0.7f), fontSize = 14.sp)
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        repeat(state.lives) {
                            Icon(
                                painter = painterResource(id = R.drawable.image_2),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).padding(end = 4.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    val target = 10 + (state.currentLevel - 1) * 5
                    Text(text = "KILLS: ${state.killCount}/$target", color = NeonCyan, fontWeight = FontWeight.Bold)
                    Text(text = "SCORE: ${state.score.toString().padStart(6, '0')}", color = Color.White, fontSize = 18.sp)
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()

            LaunchedEffect(width, height) {
                if (width > 0 && height > 0 && viewModel.gameState.value == null) {
                    viewModel.startLevel(level, width, height)
                }
            }

            gameState?.let { state ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 1. Stars
                    state.stars.forEach { star ->
                        drawCircle(
                            color = Color.White.copy(alpha = star.alpha),
                            radius = star.size,
                            center = star.position
                        )
                    }

                    // 2. Road
                    if (state.roadPoints.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(state.roadPoints[0].x, state.roadPoints[0].y)
                            for (i in 1 until state.roadPoints.size) {
                                lineTo(state.roadPoints[i].x, state.roadPoints[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = NeonCyan.copy(alpha = 0.5f),
                            style = Stroke(width = 8f)
                        )
                    }

                    // 3. Bullets
                    state.bullets.forEach { bullet ->
                        drawCircle(
                            color = if (bullet.isEnemyBullet) CriticalRed else EnergyYellow,
                            radius = bullet.radius,
                            center = bullet.position
                        )
                    }

                    // 4. Asteroids
                    state.asteroids.forEach { asteroid ->
                        val color = if (asteroid.maxHealth > 1) AsteroidGray else Color.Gray
                        drawCircle(
                            color = color,
                            radius = asteroid.radius,
                            center = asteroid.position
                        )
                        if (asteroid.maxHealth > 1) {
                            val hpPerc = asteroid.health.toFloat() / asteroid.maxHealth
                            drawCircle(
                                color = Color.Red.copy(alpha = 0.5f),
                                radius = asteroid.radius * hpPerc,
                                center = asteroid.position,
                                style = Stroke(width = 4f)
                            )
                        }
                    }

                    // 5. Enemies
                    state.enemies.forEach { enemy ->
                        val color = Color.White.copy(alpha = 0.7f)
                        val r = enemy.radius
                        when (enemy.type) {
                            EnemyType.Cube -> {
                                drawRect(color = color, topLeft = enemy.position - androidx.compose.ui.geometry.Offset(r, r), size = androidx.compose.ui.geometry.Size(r*2, r*2), style = Stroke(width = 4f))
                            }
                            EnemyType.Pyramid -> {
                                val p = Path().apply {
                                    moveTo(enemy.position.x, enemy.position.y - r)
                                    lineTo(enemy.position.x - r, enemy.position.y + r)
                                    lineTo(enemy.position.x + r, enemy.position.y + r)
                                    close()
                                }
                                drawPath(p, color, style = Stroke(width = 4f))
                            }
                            else -> {
                                drawCircle(color, radius = r, center = enemy.position, style = Stroke(width = 4f))
                            }
                        }
                    }

                    // 6. Items
                    state.items.forEach { item ->
                        val color = when(item.type) {
                            PowerUpType.Shield -> Color.White
                            PowerUpType.DoubleShoot -> Color.Cyan
                            PowerUpType.TripleShoot -> Color.Green
                            PowerUpType.HomingMissile -> Color.Magenta
                            PowerUpType.Nuke -> Color.Yellow
                        }
                        drawCircle(
                            color = color,
                            radius = item.radius,
                            center = item.position,
                            style = Stroke(width = 4f)
                        )
                        drawCircle(
                            color = color.copy(alpha = 0.3f),
                            radius = item.radius * 0.6f,
                            center = item.position
                        )
                    }

                    // 7. Ship with Thrust Animation
                    val alpha = if (state.ship.invulnerabilityFrames > 0) 0.5f else 1f
                    rotate(state.ship.rotation, pivot = state.ship.position) {
                        val r = state.ship.radius
                        val shipPath = Path().apply {
                            moveTo(state.ship.position.x, state.ship.position.y - r)
                            lineTo(state.ship.position.x - r * 0.7f, state.ship.position.y + r)
                            lineTo(state.ship.position.x + r * 0.7f, state.ship.position.y + r)
                            close()
                        }
                        drawPath(path = shipPath, color = NeonCyan.copy(alpha = alpha))

                        if (state.ship.isThrusting) {
                            val baseOffset = androidx.compose.ui.geometry.Offset(state.ship.position.x, state.ship.position.y + r)
                            drawPath(
                                path = Path().apply {
                                    moveTo(baseOffset.x - r * 0.4f, baseOffset.y)
                                    lineTo(baseOffset.x + r * 0.4f, baseOffset.y)
                                    lineTo(baseOffset.x, baseOffset.y + 40f)
                                    close()
                                },
                                color = CriticalRed.copy(alpha = 0.8f * alpha)
                            )
                            drawPath(
                                path = Path().apply {
                                    moveTo(baseOffset.x - r * 0.2f, baseOffset.y)
                                    lineTo(baseOffset.x + r * 0.2f, baseOffset.y)
                                    lineTo(baseOffset.x, baseOffset.y + 25f)
                                    close()
                                },
                                color = EnergyYellow.copy(alpha = alpha)
                            )
                        }
                        
                        if (state.ship.shieldHealth > 0) {
                            val shieldColor = when(state.ship.shieldHealth) {
                                1 -> Color.White.copy(alpha = 0.4f)
                                2 -> Color.Cyan.copy(alpha = 0.4f)
                                else -> Color.Magenta.copy(alpha = 0.4f)
                            }
                            drawCircle(color = shieldColor, radius = r * 1.5f, center = state.ship.position, style = Stroke(width = 3.dp.toPx()))
                        }
                    }
                }

                // Level Title
                if (state.levelOpacity > 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Level ${state.currentLevel}",
                            color = Color.White,
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.alpha(state.levelOpacity)
                        )
                    }
                }

                // Game Over Overlay
                if (state.isGameOver) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MISSION FAILED", color = CriticalRed, fontSize = 40.sp, fontWeight = FontWeight.Black)
                            Text("PILOT TERMINATED", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("FINAL SCORE: ${state.score}", color = Color.White, fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(32.dp))
                            Row {
                                Button(
                                    onClick = {
                                        adManager.showRewarded(
                                            activity = context as Activity,
                                            onRewardEarned = { viewModel.continueGame() },
                                            onAdDismissed = {}
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                ) {
                                    Text("CONTINUE (ADS)", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Button(
                                    onClick = onExit,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("QUIT", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Controls Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.DarkGray.copy(alpha = 0.3f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InputButton(icon = Icons.AutoMirrored.Rounded.RotateLeft, onPressChanged = onLeftPress)
            InputButton(icon = Icons.Rounded.KeyboardArrowUp, onPressChanged = onThrustPress)
            InputButton(icon = Icons.AutoMirrored.Rounded.RotateRight, onPressChanged = onRightPress)
            InputButton(icon = Icons.Rounded.LocalFireDepartment, onPressChanged = onFirePress, backgroundColor = CriticalRed.copy(alpha = 0.6f))
        }
    }
}
