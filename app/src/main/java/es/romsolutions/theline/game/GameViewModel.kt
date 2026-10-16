package es.romsolutions.theline.game

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.romsolutions.theline.data.GameDataStore
import es.romsolutions.theline.game.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = GameDataStore(application)
    private val roadGenerator = RoadGenerator()

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>()
    val events = _events.asSharedFlow()

    private var isRunning = false
    private var isPaused = false
    private var lastUpdateTime = 0L

    // Input states
    var rotateLeftPressed = false
    var rotateRightPressed = false
    var thrustPressed = false
    var firePressed = false

    private var lastFireTime = 0L
    private val fireInterval = 200L
    private var lastEnemySpawnTime = 0L
    private val enemySpawnInterval = 1500L
    private var lastAsteroidSpawnTime = 0L
    private val asteroidSpawnInterval = 1000L
    private var lastCubeShootTime = 0L
    private val cubeShootInterval = 1800L

    init {
        viewModelScope.launch {
            while (true) {
                if (isRunning && !isPaused) {
                    update()
                }
                delay(16)
            }
        }
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (!paused) {
            lastUpdateTime = System.currentTimeMillis() // Reset time delta after unpausing
        }
    }

    fun startLevel(level: Int, width: Float, height: Float, keepState: Boolean = false) {
        val currentState = _gameState.value
        val centerX = width / 2f
        
        val initialShip = if (keepState && currentState != null) {
            currentState.ship.copy(
                position = Offset(centerX, height * 0.8f),
                velocity = Offset.Zero,
                invulnerabilityFrames = 120,
                activePowerUps = currentState.ship.activePowerUps,
                shieldHealth = currentState.ship.shieldHealth
            )
        } else {
            Ship(position = Offset(centerX, height * 0.8f))
        }

        val initialStars = currentState?.stars ?: List(100) {
            Star(
                position = Offset(Random.nextFloat() * width, Random.nextFloat() * height),
                alpha = Random.nextFloat() * 0.9f + 0.1f,
                size = Random.nextInt(1, 4).toFloat(),
                layer = Random.nextInt(0, 2)
            )
        }
        
        val roadPoints = roadGenerator.generateRoad(height, -height * 10, level, width)

        _gameState.value = GameState(
            ship = initialShip,
            asteroids = emptyList(),
            enemies = emptyList(),
            bullets = emptyList(),
            items = emptyList(),
            stars = initialStars,
            screenWidth = width,
            screenHeight = height,
            currentLevel = level,
            roadPoints = roadPoints,
            levelOpacity = 1.0f,
            score = currentState?.score ?: 0,
            lives = currentState?.lives ?: 3,
            killCount = 0,
            itemSpawnedThisLevel = false
        )
        isRunning = true
        isPaused = false
        lastUpdateTime = System.currentTimeMillis()
    }

    var sessionPlayCount = 0

    fun resetGame() {
        isRunning = false
        _gameState.value = null
    }

    fun continueGame() {
        val state = _gameState.value ?: return
        val centerX = if (state.screenWidth > 0) state.screenWidth / 2f else state.ship.position.x
        val centerY = if (state.screenHeight > 0) state.screenHeight * 0.8f else state.ship.position.y
        _gameState.value = state.copy(
            lives = 3,
            isGameOver = false,
            ship = state.ship.copy(
                position = Offset(centerX, centerY),
                velocity = Offset.Zero,
                invulnerabilityFrames = 180,
                activePowerUps = emptySet(),
                shieldHealth = 0
            )
        )
        isRunning = true
        isPaused = false
        lastUpdateTime = System.currentTimeMillis()
    }

    private fun update() {
        val state = _gameState.value ?: return
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastUpdateTime) / 1000f
        lastUpdateTime = currentTime

        if (state.isGameOver || state.isVictory) return

        // 1. Level Progress
        val scrollSpeed = 300f
        val newLevelOpacity = (state.levelOpacity - deltaTime * 0.5f).coerceAtLeast(0f)

        val targetKills = 10 + (state.currentLevel - 1) * 5
        if (state.killCount >= targetKills) {
            val nextLevel = state.currentLevel + 1
            viewModelScope.launch {
                dataStore.saveProgress(nextLevel, isUnlock = true)
            }
            startLevel(nextLevel, state.screenWidth, state.screenHeight, keepState = true)
            return
        }

        // 2. Update Ship
        var newRotation = state.ship.rotation
        if (rotateLeftPressed) newRotation -= 180f * deltaTime
        if (rotateRightPressed) newRotation += 180f * deltaTime

        val rad = Math.toRadians(newRotation.toDouble() - 90.0)
        val dirX = cos(rad).toFloat()
        val dirY = sin(rad).toFloat()

        var shipVelocity = state.ship.velocity
        if (thrustPressed) {
            shipVelocity += Offset(dirX * 800f * deltaTime, dirY * 800f * deltaTime)
        } else {
            shipVelocity *= 0.95f
        }

        val speed = sqrt(shipVelocity.x * shipVelocity.x + shipVelocity.y * shipVelocity.y)
        if (speed > 600f) shipVelocity = shipVelocity * (600f / speed)

        var newShipPos = state.ship.position + shipVelocity * deltaTime
        
        val roadPoint = state.roadPoints.minByOrNull { abs(it.y - newShipPos.y) }
        val roadX = roadPoint?.x ?: state.screenWidth / 2f
        newShipPos = Offset(roadX, newShipPos.y)

        val bottomLimit = state.screenHeight * 0.9f
        if (newShipPos.y > bottomLimit) {
            newShipPos = newShipPos.copy(y = bottomLimit)
            if (shipVelocity.y > 0) shipVelocity = shipVelocity.copy(y = 0f)
        }
        
        newShipPos = newShipPos.copy(
            y = newShipPos.y.coerceIn(state.ship.radius, state.screenHeight - state.ship.radius)
        )

        val newInvul = (state.ship.invulnerabilityFrames - 1).coerceAtLeast(0)

        // 3. Update Road & Stars
        val updatedRoadPoints = state.roadPoints.map { it.copy(y = it.y + scrollSpeed * deltaTime) }
            .filter { it.y < state.screenHeight + 500 }
        
        val updatedStars = state.stars.map { 
            val starSpeed = if (it.layer == 0) scrollSpeed * 0.5f else scrollSpeed
            var newY = it.position.y + starSpeed * deltaTime
            if (newY > state.screenHeight) newY = 0f
            it.copy(position = it.position.copy(y = newY))
        }

        // 4. Update Bullets & Homing Missiles
        var updatedBullets = state.bullets.map {
            if (it.isHoming) {
                val target = (state.asteroids.map { a -> a.position } + state.enemies.map { e -> e.position })
                    .minByOrNull { p -> (p - it.position).getDistance() }
                
                if (target != null) {
                    val angleToTarget = atan2(target.y - it.position.y, target.x - it.position.x)
                    val currentAngle = atan2(it.velocity.y, it.velocity.x)
                    val newAngle = currentAngle + (angleToTarget - currentAngle).coerceIn(-0.1f, 0.1f)
                    val speedHoming = 800f
                    it.copy(
                        position = it.position + Offset(cos(newAngle) * speedHoming * deltaTime, sin(newAngle) * speedHoming * deltaTime),
                        velocity = Offset(cos(newAngle) * speedHoming, sin(newAngle) * speedHoming)
                    )
                } else {
                    it.copy(position = it.position + it.velocity * deltaTime)
                }
            } else {
                it.copy(position = it.position + it.velocity * deltaTime)
            }
        }.filter { it.position.y > -100 && it.position.y < state.screenHeight + 100 && it.position.x > -100 && it.position.x < state.screenWidth + 100 }

        val newProjectiles = mutableListOf<Bullet>()
        
        // Manual Firing
        if (firePressed && currentTime - lastFireTime > fireInterval) {
            lastFireTime = currentTime
            viewModelScope.launch { _events.emit(GameEvent.ShipFired) }
            
            if (state.ship.activePowerUps.contains(PowerUpType.TripleShoot)) {
                for (angle in listOf(-15f, 0f, 15f)) {
                    val bRad = Math.toRadians(newRotation.toDouble() - 90.0 + angle)
                    newProjectiles.add(Bullet(position = newShipPos, velocity = Offset(cos(bRad).toFloat() * 1000f, sin(bRad).toFloat() * 1000f)))
                }
            } else if (state.ship.activePowerUps.contains(PowerUpType.DoubleShoot)) {
                newProjectiles.add(Bullet(position = newShipPos, velocity = Offset(dirX * 1000f, dirY * 1000f)))
                newProjectiles.add(Bullet(position = newShipPos, velocity = Offset(-dirX * 1000f, -dirY * 1000f)))
            } else {
                newProjectiles.add(Bullet(position = newShipPos, velocity = Offset(dirX * 1000f, dirY * 1000f)))
            }
        }
        
        // Auto Missiles every 7 seconds
        var newLastMissileTime = state.ship.lastMissileTime
        if (state.ship.activePowerUps.contains(PowerUpType.HomingMissile) && currentTime - state.ship.lastMissileTime > 7000L) {
            newLastMissileTime = currentTime
            val bRad1 = Math.toRadians(newRotation.toDouble() - 90.0 - 45.0)
            val bRad2 = Math.toRadians(newRotation.toDouble() - 90.0 + 45.0)
            newProjectiles.add(Bullet(position = newShipPos, velocity = Offset(cos(bRad1).toFloat() * 500f, sin(bRad1).toFloat() * 500f), isHoming = true, radius = 8f))
            newProjectiles.add(Bullet(position = newShipPos, velocity = Offset(cos(bRad2).toFloat() * 500f, sin(bRad2).toFloat() * 500f), isHoming = true, radius = 8f))
        }

        // Cube Enemies Shooting (red bullet at 10% offset of player's last position)
        if (currentTime - lastCubeShootTime > cubeShootInterval) {
            lastCubeShootTime = currentTime
            state.enemies.filter { it.type == EnemyType.Cube && it.position.y > 0 && it.position.y < state.screenHeight }.forEach { cube ->
                val targetX = newShipPos.x + (Random.nextFloat() - 0.5f) * (state.screenWidth * 0.10f)
                val targetY = newShipPos.y + (Random.nextFloat() - 0.5f) * (state.screenHeight * 0.10f)
                val targetPos = Offset(targetX, targetY)

                val dir = targetPos - cube.position
                val dist = dir.getDistance()
                if (dist > 0) {
                    val normDir = dir / dist
                    val bulletSpeed = 450f
                    newProjectiles.add(
                        Bullet(
                            position = cube.position,
                            velocity = normDir * bulletSpeed,
                            radius = 6f,
                            isEnemyBullet = true
                        )
                    )
                }
            }
        }

        updatedBullets = updatedBullets + newProjectiles

        // 5. Update Enemies & Asteroids
        val updatedEnemies = state.enemies.map { 
            it.copy(position = it.position + it.velocity * deltaTime)
        }.filter { it.position.y < state.screenHeight + 100 && it.position.x > -100 && it.position.x < state.screenWidth + 100 }

        val updatedAsteroids = state.asteroids.map {
            it.copy(position = it.position + it.velocity * deltaTime)
        }.filter { it.position.y < state.screenHeight + 100 && it.position.x > -100 && it.position.x < state.screenWidth + 100 }

        val spawnedEnemies = mutableListOf<Enemy>()
        var newItemSpawnedState = state.itemSpawnedThisLevel
        if (currentTime - lastEnemySpawnTime > enemySpawnInterval) {
            lastEnemySpawnTime = currentTime
            val spawnPos = Offset(Random.nextFloat() * state.screenWidth, -50f)
            val velocity = Offset(Random.nextFloat() * 200 - 100, Random.nextFloat() * 200 + 100)
            
            // Only 1 enemy carries a power-up per level!
            val powerUp = if (!state.itemSpawnedThisLevel && Random.nextFloat() < 0.4f) {
                newItemSpawnedState = true
                PowerUpType.entries.random()
            } else null

            spawnedEnemies.add(Enemy(position = spawnPos, velocity = velocity, type = EnemyType.entries.random(), carryingPowerUp = powerUp))
        }

        val spawnedAsteroids = mutableListOf<Asteroid>()
        if (currentTime - lastAsteroidSpawnTime > asteroidSpawnInterval) {
            lastAsteroidSpawnTime = currentTime
            val sizeType = Random.nextInt(0, 3)
            val radius = when(sizeType) { 0 -> 25f; 1 -> 45f; else -> 70f }
            val baseSpeed = 150f * 1.05f
            val finalSpeed = when(sizeType) { 0 -> baseSpeed * 1.02f; 1 -> baseSpeed; else -> baseSpeed * 0.97f }
            val spawnPos = Offset(Random.nextFloat() * state.screenWidth, -100f)
            spawnedAsteroids.add(Asteroid(position = spawnPos, velocity = Offset(Random.nextFloat() * 100 - 50, finalSpeed), radius = radius, health = sizeType + 1, maxHealth = sizeType + 1))
        }

        // Update existing explosions
        val updatedExplosions = state.explosions
            .map { it.copy(elapsedTime = it.elapsedTime + deltaTime) }
            .filter { it.elapsedTime < it.duration }
            .toMutableList()

        // 6. Collision Detection
        var currentScore = state.score
        var currentKillCount = state.killCount
        var currentLives = state.lives
        var currentShieldHealth = state.ship.shieldHealth
        val currentPowerUps = state.ship.activePowerUps.toMutableSet()
        
        val finalEnemies = (updatedEnemies + spawnedEnemies).toMutableList()
        val finalAsteroids = (updatedAsteroids + spawnedAsteroids).toMutableList()
        val finalBullets = updatedBullets.toMutableList()
        
        val bulletsToRemove = mutableSetOf<Long>()
        val enemiesToRemove = mutableSetOf<Long>()
        val asteroidsToRemove = mutableSetOf<Long>()
        
        for (bullet in finalBullets) {
            if (!bullet.isEnemyBullet) {
                for (enemy in finalEnemies) {
                    if ((bullet.position - enemy.position).getDistance() < bullet.radius + enemy.radius) {
                        bulletsToRemove.add(bullet.id)
                        enemiesToRemove.add(enemy.id)
                        updatedExplosions.add(Explosion(position = enemy.position, radius = enemy.radius * 1.5f, spriteType = getEnemyDestructionType(enemy)))
                        currentScore += 100
                        currentKillCount++
                        
                        // Directly grant power-up if carrying one!
                        enemy.carryingPowerUp?.let { p ->
                            when (p) {
                                PowerUpType.Shield -> currentShieldHealth = (currentShieldHealth + 1).coerceAtMost(3)
                                PowerUpType.Nuke -> {
                                    currentScore += finalAsteroids.size * 50
                                    currentKillCount += finalAsteroids.size
                                    updatedExplosions.addAll(finalAsteroids.map { Explosion(it.position, radius = it.radius * 1.5f, spriteType = getAsteroidDestructionType(it)) })
                                    finalAsteroids.clear()
                                }
                                else -> currentPowerUps.add(p)
                            }
                            viewModelScope.launch { _events.emit(GameEvent.PowerUpCollected) }
                        }
                        
                        viewModelScope.launch { _events.emit(GameEvent.EnemyDestroyed) }
                    }
                }
                for (asteroid in finalAsteroids) {
                    if ((bullet.position - asteroid.position).getDistance() < bullet.radius + asteroid.radius) {
                        bulletsToRemove.add(bullet.id)
                        val newHealth = asteroid.health - 1
                        if (newHealth <= 0) {
                            asteroidsToRemove.add(asteroid.id)
                            updatedExplosions.add(Explosion(position = asteroid.position, radius = asteroid.radius * 1.5f, spriteType = getAsteroidDestructionType(asteroid)))
                            currentScore += 50
                            currentKillCount++
                        } else {
                            val idx = finalAsteroids.indexOf(asteroid)
                            if (idx != -1) finalAsteroids[idx] = asteroid.copy(health = newHealth)
                        }
                    }
                }
            } else {
                // Enemy red bullets vs player ship
                if (newInvul <= 0 && (bullet.position - newShipPos).getDistance() < bullet.radius + state.ship.radius) {
                    bulletsToRemove.add(bullet.id)
                    if (currentShieldHealth > 0) {
                        currentShieldHealth--
                    } else {
                        currentLives--
                        currentPowerUps.clear()
                    }
                    updatedExplosions.add(Explosion(position = newShipPos, radius = state.ship.radius * 1.8f, spriteType = "trian1"))
                    viewModelScope.launch { _events.emit(GameEvent.ShipHit) }
                }
            }
        }
        finalBullets.removeAll { bulletsToRemove.contains(it.id) }
        finalEnemies.removeAll { enemiesToRemove.contains(it.id) }
        finalAsteroids.removeAll { asteroidsToRemove.contains(it.id) }
        
        if (newInvul <= 0) {
            for (enemy in finalEnemies) {
                if ((newShipPos - enemy.position).getDistance() < state.ship.radius + enemy.radius) {
                    if (currentShieldHealth > 0) { currentShieldHealth-- } else { 
                        currentLives--
                        currentPowerUps.clear() 
                    }
                    enemiesToRemove.add(enemy.id)
                    updatedExplosions.add(Explosion(position = enemy.position, radius = enemy.radius * 1.5f, spriteType = getEnemyDestructionType(enemy)))
                    updatedExplosions.add(Explosion(position = newShipPos, radius = state.ship.radius * 1.8f, spriteType = "trian1"))
                    viewModelScope.launch { _events.emit(GameEvent.ShipHit) }
                }
            }
            for (asteroid in finalAsteroids) {
                if ((newShipPos - asteroid.position).getDistance() < state.ship.radius + asteroid.radius) {
                    if (currentShieldHealth > 0) { currentShieldHealth-- } else { 
                        currentLives--
                        currentPowerUps.clear()
                    }
                    asteroidsToRemove.add(asteroid.id)
                    updatedExplosions.add(Explosion(position = asteroid.position, radius = asteroid.radius * 1.5f, spriteType = getAsteroidDestructionType(asteroid)))
                    updatedExplosions.add(Explosion(position = newShipPos, radius = state.ship.radius * 1.8f, spriteType = "trian1"))
                    viewModelScope.launch { _events.emit(GameEvent.ShipHit) }
                }
            }
        }
        finalEnemies.removeAll { enemiesToRemove.contains(it.id) }
        finalAsteroids.removeAll { asteroidsToRemove.contains(it.id) }

        // Sparks update & generation
        val updatedSparks = state.sparks
            .map { it.copy(position = it.position + it.velocity * deltaTime, elapsedTime = it.elapsedTime + deltaTime) }
            .filter { it.elapsedTime < it.duration }
            .toMutableList()

        if (thrustPressed || Random.nextFloat() < 0.6f) {
            val numSparks = if (thrustPressed) Random.nextInt(2, 5) else 1
            repeat(numSparks) {
                val vx = (Random.nextFloat() - 0.5f) * (if (thrustPressed) 350f else 150f)
                val vy = (Random.nextFloat() - 0.5f) * (if (thrustPressed) 350f else 150f)
                updatedSparks.add(
                    Spark(
                        position = newShipPos + Offset((Random.nextFloat() - 0.5f) * 24f, (Random.nextFloat() - 0.5f) * 24f),
                        velocity = Offset(vx, vy),
                        isWhite = Random.nextBoolean()
                    )
                )
            }
        }

        // Shockwaves update & generation
        val updatedShockwaves = state.shockwaves
            .map { it.copy(distanceProgress = it.distanceProgress + it.speed * deltaTime) }
            .filter { it.distanceProgress < it.maxDistance }
            .toMutableList()

        if (thrustPressed && (state.shockwaves.isEmpty() || state.shockwaves.last().distanceProgress > 150f)) {
            updatedShockwaves.add(Shockwave(originY = newShipPos.y))
        }

        val newFlowOffset = (state.flowOffset + deltaTime * 200f) % 120f

        _gameState.value = state.copy(
            ship = state.ship.copy(
                position = newShipPos, velocity = shipVelocity, rotation = newRotation, 
                isThrusting = thrustPressed, activePowerUps = currentPowerUps, 
                shieldHealth = currentShieldHealth, invulnerabilityFrames = newInvul,
                lastMissileTime = newLastMissileTime
            ),
            roadPoints = updatedRoadPoints,
            stars = updatedStars,
            enemies = finalEnemies,
            asteroids = finalAsteroids,
            bullets = finalBullets,
            explosions = updatedExplosions,
            sparks = updatedSparks,
            shockwaves = updatedShockwaves,
            flowOffset = newFlowOffset,
            items = emptyList(),
            score = currentScore,
            killCount = currentKillCount,
            lives = currentLives,
            levelOpacity = newLevelOpacity,
            itemSpawnedThisLevel = newItemSpawnedState,
            isGameOver = currentLives <= 0
        )
    }

    private fun getEnemyDestructionType(enemy: Enemy): String {
        return when (enemy.type) {
            EnemyType.Pyramid -> {
                val idx = abs((enemy.id % 3).toInt())
                if (idx == 2) "trian3" else "trian1"
            }
            EnemyType.Cube -> "trian1"
            else -> {
                val planetList = listOf("planet4", "planet6", "planet7")
                planetList[abs((enemy.id % planetList.size).toInt())]
            }
        }
    }

    private fun getAsteroidDestructionType(asteroid: Asteroid): String {
        val planetList = listOf("planet4", "planet6", "planet7")
        return planetList[abs((asteroid.id % planetList.size).toInt())]
    }
}
