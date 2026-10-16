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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.abs

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

    val saturnBitmap = ImageBitmap.imageResource(id = R.drawable.saturno)
    val planetBitmaps = listOf(
        ImageBitmap.imageResource(id = R.drawable.planet2),
        ImageBitmap.imageResource(id = R.drawable.planet3),
        ImageBitmap.imageResource(id = R.drawable.planet4),
        ImageBitmap.imageResource(id = R.drawable.planet5),
        ImageBitmap.imageResource(id = R.drawable.planet6),
        ImageBitmap.imageResource(id = R.drawable.planet7),
        ImageBitmap.imageResource(id = R.drawable.planet8),
        ImageBitmap.imageResource(id = R.drawable.planet9)
    )
    val shipBitmap = ImageBitmap.imageResource(id = R.drawable.nave)
    val flamesBitmap = ImageBitmap.imageResource(id = R.drawable.llamas)
    val explosionBitmap = ImageBitmap.imageResource(id = R.drawable.explosion)
    val starBitmaps = listOf(
        ImageBitmap.imageResource(id = R.drawable.estrella1),
        ImageBitmap.imageResource(id = R.drawable.estrella2),
        ImageBitmap.imageResource(id = R.drawable.estrella3),
        ImageBitmap.imageResource(id = R.drawable.estrella4),
        ImageBitmap.imageResource(id = R.drawable.estrella5)
    )
    val cuadraBitmaps = listOf(
        ImageBitmap.imageResource(id = R.drawable.cuadra1),
        ImageBitmap.imageResource(id = R.drawable.cuadra2)
    )
    val trianBitmaps = listOf(
        ImageBitmap.imageResource(id = R.drawable.trian1),
        ImageBitmap.imageResource(id = R.drawable.trian2),
        ImageBitmap.imageResource(id = R.drawable.trian3)
    )
    val destructionBitmaps = mapOf(
        "planet4" to ImageBitmap.imageResource(id = R.drawable.destruction_planet4),
        "planet5" to ImageBitmap.imageResource(id = R.drawable.destruction_planet5),
        "planet6" to ImageBitmap.imageResource(id = R.drawable.destruction_planet6),
        "planet7" to ImageBitmap.imageResource(id = R.drawable.destruction_planet7),
        "trian1" to ImageBitmap.imageResource(id = R.drawable.destruction_trian1),
        "trian3" to ImageBitmap.imageResource(id = R.drawable.destruction_trian3)
    )

    val onLeftPress: (Boolean) -> Unit = { viewModel.rotateLeftPressed = it }
    val onRightPress: (Boolean) -> Unit = { viewModel.rotateRightPressed = it }
    val onThrustPress: (Boolean) -> Unit = { viewModel.thrustPressed = it }
    val onFirePress: (Boolean) -> Unit = { viewModel.firePressed = it }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is GameEvent.ShipFired -> soundManager.playLaser()
                is GameEvent.EnemyDestroyed -> soundManager.playExplosion()
                is GameEvent.ShipHit -> soundManager.playExplosion()
                is GameEvent.PowerUpCollected -> soundManager.playItem()
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
            viewModel.setPaused(true)
            adManager.showInterstitial(context as Activity) {
                viewModel.setPaused(false)
            }
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
                if (width > 0 && height > 0) {
                    if (viewModel.gameState.value == null) {
                        if (viewModel.sessionPlayCount >= 1) {
                            viewModel.setPaused(true)
                            adManager.showInterstitial(context as Activity) {
                                viewModel.startLevel(level, width, height)
                                viewModel.sessionPlayCount++
                                viewModel.setPaused(false)
                            }
                        } else {
                            viewModel.startLevel(level, width, height)
                            viewModel.sessionPlayCount++
                        }
                    }
                }
            }

            gameState?.let { state ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 1. Moving Stars (estrella1.png .. estrella5.png)
                    state.stars.forEachIndexed { index, star ->
                        val starBitmap = starBitmaps[index % starBitmaps.size]
                        val starDrawSize = (star.size * 6f).toInt().coerceAtLeast(10)
                        val topLeft = IntOffset((star.position.x - starDrawSize / 2f).toInt(), (star.position.y - starDrawSize / 2f).toInt())
                        drawImage(
                            image = starBitmap,
                            dstOffset = topLeft,
                            dstSize = IntSize(starDrawSize, starDrawSize),
                            alpha = star.alpha
                        )
                    }

                    // 2. Road (Multilayer Neon Guide: Outer translucent glow, intense neon body, inner white core)
                    if (state.roadPoints.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(state.roadPoints[0].x, state.roadPoints[0].y)
                            for (i in 1 until state.roadPoints.size) {
                                lineTo(state.roadPoints[i].x, state.roadPoints[i].y)
                            }
                        }
                        // Outer translucent cyan glow (18px)
                        drawPath(
                            path = path,
                            color = NeonCyan.copy(alpha = 0.25f),
                            style = Stroke(width = 18f)
                        )
                        // Intense neon body (6px)
                        drawPath(
                            path = path,
                            color = NeonCyan,
                            style = Stroke(width = 6f)
                        )
                        // Inner white core (2px)
                        drawPath(
                            path = path,
                            color = Color.White,
                            style = Stroke(width = 2f)
                        )

                        // 2b. Flow Pulses (moving light nodes along geometry)
                        val stepDistance = 120f
                        var accumulatedDist = state.flowOffset
                        for (i in 0 until state.roadPoints.size - 1) {
                            val p1 = state.roadPoints[i]
                            val p2 = state.roadPoints[i + 1]
                            val segDist = (p2 - p1).getDistance()
                            while (accumulatedDist <= segDist) {
                                val t = if (segDist > 0) accumulatedDist / segDist else 0f
                                val pulsePt = p1 + (p2 - p1) * t
                                drawCircle(color = Color.White, radius = 4f, center = pulsePt)
                                drawCircle(color = NeonCyan, radius = 9f, center = pulsePt, style = Stroke(width = 2f))
                                accumulatedDist += stepDistance
                            }
                            accumulatedDist -= segDist
                        }

                        // Shockwaves along the road
                        state.shockwaves.forEach { shockwave ->
                            val waveTopY = shockwave.originY - shockwave.distanceProgress
                            val waveBotY = shockwave.originY + shockwave.distanceProgress
                            val alpha = (1f - shockwave.distanceProgress / shockwave.maxDistance).coerceIn(0f, 1f)

                            state.roadPoints.forEach { pt ->
                                if (abs(pt.y - waveTopY) < 22f || abs(pt.y - waveBotY) < 22f) {
                                    drawCircle(color = Color.White.copy(alpha = alpha), radius = 10f, center = pt)
                                    drawCircle(color = NeonCyan.copy(alpha = alpha * 0.8f), radius = 22f, center = pt, style = Stroke(width = 3f))
                                }
                            }
                        }

                        // Reacción Neón de Proximidad (flash when asteroids or enemies cross/approach guide line)
                        val entityPositions = state.enemies.map { Pair(it.position, it.radius) } + state.asteroids.map { Pair(it.position, it.radius) }
                        entityPositions.forEach { (pos, radius) ->
                            val closestPt = state.roadPoints.minByOrNull { abs(it.y - pos.y) }
                            if (closestPt != null) {
                                val dist = (pos - closestPt).getDistance()
                                if (dist < radius + 20f) {
                                    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 10f, center = closestPt)
                                    drawCircle(color = NeonCyan.copy(alpha = 0.7f), radius = 22f, center = closestPt, style = Stroke(width = 3f))
                                }
                            }
                        }
                    }

                    // 2c. Chisporroteo y Fricción (4x4px cyan and white pixel sparks)
                    state.sparks.forEach { spark ->
                        val sparkAlpha = (1f - spark.elapsedTime / spark.duration).coerceIn(0f, 1f)
                        val sparkColor = if (spark.isWhite) Color.White else NeonCyan
                        drawRect(
                            color = sparkColor.copy(alpha = sparkAlpha),
                            topLeft = spark.position - Offset(2f, 2f),
                            size = Size(4f, 4f)
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
                        val spriteIndex = abs((asteroid.id % planetBitmaps.size).toInt())
                        val planetBitmap = planetBitmaps[spriteIndex]
                        val r = asteroid.radius
                        val drawSize = (r * 2.2f).toInt()
                        val topLeft = IntOffset((asteroid.position.x - drawSize / 2f).toInt(), (asteroid.position.y - drawSize / 2f).toInt())
                        
                        drawImage(
                            image = planetBitmap,
                            dstOffset = topLeft,
                            dstSize = IntSize(drawSize, drawSize)
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

                    // 5. Enemies (Saturn for items, cuadra*.png for cubes, trian*.png for pyramids, planets for round)
                    state.enemies.forEach { enemy ->
                        val r = enemy.radius
                        if (enemy.carryingPowerUp != null) {
                            val auraColor = when(enemy.carryingPowerUp) {
                                PowerUpType.Shield -> Color.White
                                PowerUpType.DoubleShoot -> Color.Cyan
                                PowerUpType.TripleShoot -> Color.Green
                                PowerUpType.HomingMissile -> Color.Magenta
                                PowerUpType.Nuke -> Color.Yellow
                            }
                            drawCircle(
                                color = auraColor.copy(alpha = 0.4f),
                                radius = r * 1.5f,
                                center = enemy.position
                            )
                            drawCircle(
                                color = auraColor,
                                radius = r * 1.2f,
                                center = enemy.position,
                                style = Stroke(width = 3f)
                            )

                            val saturnAspect = saturnBitmap.width.toFloat() / saturnBitmap.height.toFloat()
                            val drawHeight = (r * 2.4f).toInt()
                            val drawWidth = (drawHeight * saturnAspect).toInt()
                            val topLeft = IntOffset((enemy.position.x - drawWidth / 2f).toInt(), (enemy.position.y - drawHeight / 2f).toInt())
                            drawImage(
                                image = saturnBitmap,
                                dstOffset = topLeft,
                                dstSize = IntSize(drawWidth, drawHeight)
                            )
                        } else {
                            when (enemy.type) {
                                EnemyType.Cube -> {
                                    val cuadraBitmap = cuadraBitmaps[abs((enemy.id % cuadraBitmaps.size).toInt())]
                                    val drawSize = (r * 2.2f).toInt()
                                    val topLeft = IntOffset((enemy.position.x - drawSize / 2f).toInt(), (enemy.position.y - drawSize / 2f).toInt())
                                    drawImage(
                                        image = cuadraBitmap,
                                        dstOffset = topLeft,
                                        dstSize = IntSize(drawSize, drawSize)
                                    )
                                }
                                EnemyType.Pyramid -> {
                                    val trianBitmap = trianBitmaps[abs((enemy.id % trianBitmaps.size).toInt())]
                                    val trianAspect = trianBitmap.width.toFloat() / trianBitmap.height.toFloat()
                                    val drawHeight = (r * 2.2f).toInt()
                                    val drawWidth = (drawHeight * trianAspect).toInt()
                                    val topLeft = IntOffset((enemy.position.x - drawWidth / 2f).toInt(), (enemy.position.y - drawHeight / 2f).toInt())
                                    drawImage(
                                        image = trianBitmap,
                                        dstOffset = topLeft,
                                        dstSize = IntSize(drawWidth, drawHeight)
                                    )
                                }
                                else -> {
                                    val spriteIndex = abs((enemy.id % planetBitmaps.size).toInt())
                                    val planetBitmap = planetBitmaps[spriteIndex]
                                    val drawSize = (r * 2.2f).toInt()
                                    val topLeft = IntOffset((enemy.position.x - drawSize / 2f).toInt(), (enemy.position.y - drawSize / 2f).toInt())
                                    drawImage(
                                        image = planetBitmap,
                                        dstOffset = topLeft,
                                        dstSize = IntSize(drawSize, drawSize)
                                    )
                                }
                            }
                        }
                    }

                    // 6. Items ("saturno es para los que tiene item")
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
                            radius = item.radius * 1.3f,
                            center = item.position,
                            style = Stroke(width = 4f)
                        )
                        drawCircle(
                            color = color.copy(alpha = 0.3f),
                            radius = item.radius * 1.1f,
                            center = item.position
                        )

                        val r = item.radius
                        val saturnAspect = saturnBitmap.width.toFloat() / saturnBitmap.height.toFloat()
                        val drawHeight = (r * 2.2f).toInt()
                        val drawWidth = (drawHeight * saturnAspect).toInt()
                        val topLeft = IntOffset((item.position.x - drawWidth / 2f).toInt(), (item.position.y - drawHeight / 2f).toInt())
                        drawImage(
                            image = saturnBitmap,
                            dstOffset = topLeft,
                            dstSize = IntSize(drawWidth, drawHeight)
                        )
                    }

                    // 7. Explosions (Animated frame-by-frame destruction spritesheets or explosion.png)
                    state.explosions.forEach { explosion ->
                        val progress = (explosion.elapsedTime / explosion.duration).coerceIn(0f, 0.999f)
                        val sheetBitmap = explosion.spriteType?.let { destructionBitmaps[it] }

                        if (sheetBitmap != null) {
                            val totalFrames = 6
                            val frameIndex = (progress * totalFrames).toInt().coerceIn(0, totalFrames - 1)
                            val frameWidth = sheetBitmap.width / totalFrames
                            val frameHeight = sheetBitmap.height

                            val srcOffset = IntOffset(frameIndex * frameWidth, 0)
                            val srcSize = IntSize(frameWidth, frameHeight)

                            val drawSize = (explosion.radius * 2.2f).toInt()
                            val dstOffset = IntOffset((explosion.position.x - drawSize / 2f).toInt(), (explosion.position.y - drawSize / 2f).toInt())
                            val dstSize = IntSize(drawSize, drawSize)

                            drawImage(
                                image = sheetBitmap,
                                srcOffset = srcOffset,
                                srcSize = srcSize,
                                dstOffset = dstOffset,
                                dstSize = dstSize
                            )
                        } else {
                            val alpha = (1f - progress).coerceIn(0f, 1f)
                            val scale = 0.8f + 0.6f * progress
                            val drawSize = (explosion.radius * 2f * scale).toInt()
                            val topLeft = IntOffset((explosion.position.x - drawSize / 2f).toInt(), (explosion.position.y - drawSize / 2f).toInt())
                            drawImage(
                                image = explosionBitmap,
                                dstOffset = topLeft,
                                dstSize = IntSize(drawSize, drawSize),
                                alpha = alpha
                            )
                        }
                    }

                    // 8. Ship with Thrust Animation & Ship Sprite (nave.png & llamas.png)
                    val alpha = if (state.ship.invulnerabilityFrames > 0) 0.5f else 1f
                    rotate(state.ship.rotation, pivot = state.ship.position) {
                        val r = state.ship.radius

                        if (state.ship.isThrusting) {
                            val flameAspect = flamesBitmap.width.toFloat() / flamesBitmap.height.toFloat()
                            val flameWidth = r * 1.6f
                            val flameHeight = flameWidth / flameAspect
                            val flameTopLeft = IntOffset(
                                (state.ship.position.x - flameWidth / 2f).toInt(),
                                (state.ship.position.y + r * 0.7f).toInt()
                            )
                            drawImage(
                                image = flamesBitmap,
                                dstOffset = flameTopLeft,
                                dstSize = IntSize(flameWidth.toInt(), flameHeight.toInt()),
                                alpha = alpha
                            )
                        }

                        val shipAspect = shipBitmap.width.toFloat() / shipBitmap.height.toFloat()
                        val shipWidth = r * 2.2f
                        val shipHeight = shipWidth / shipAspect
                        val shipTopLeft = IntOffset(
                            (state.ship.position.x - shipWidth / 2f).toInt(),
                            (state.ship.position.y - shipHeight / 2f).toInt()
                        )
                        drawImage(
                            image = shipBitmap,
                            dstOffset = shipTopLeft,
                            dstSize = IntSize(shipWidth.toInt(), shipHeight.toInt()),
                            alpha = alpha
                        )

                        if (state.ship.shieldHealth > 0) {
                            val shieldColor = when(state.ship.shieldHealth) {
                                1 -> Color.White.copy(alpha = 0.4f)
                                2 -> Color.Cyan.copy(alpha = 0.4f)
                                else -> Color.Magenta.copy(alpha = 0.4f)
                            }
                            drawCircle(color = shieldColor, radius = r * 1.5f, center = state.ship.position, style = Stroke(width = 3.dp.toPx()))
                        }
                    }

                    // 9. Synthwave Double CRT Frame (Neon Orange Outer, Neon Cyan Inner with Rounded Corners)
                    val frameOuterColor = Color(0xFFFF6600)
                    val outerMargin = 6.dp.toPx()
                    val innerMargin = 14.dp.toPx()
                    val cornerRadiusOuter = 20.dp.toPx()
                    val cornerRadiusInner = 14.dp.toPx()

                    drawRoundRect(
                        color = frameOuterColor,
                        topLeft = Offset(outerMargin, outerMargin),
                        size = Size(size.width - outerMargin * 2, size.height - outerMargin * 2),
                        cornerRadius = CornerRadius(cornerRadiusOuter, cornerRadiusOuter),
                        style = Stroke(width = 6f)
                    )

                    drawRoundRect(
                        color = NeonCyan,
                        topLeft = Offset(innerMargin, innerMargin),
                        size = Size(size.width - innerMargin * 2, size.height - innerMargin * 2),
                        cornerRadius = CornerRadius(cornerRadiusInner, cornerRadiusInner),
                        style = Stroke(width = 3f)
                    )
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
                                    onClick = {
                                        viewModel.resetGame()
                                        onExit()
                                    },
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
