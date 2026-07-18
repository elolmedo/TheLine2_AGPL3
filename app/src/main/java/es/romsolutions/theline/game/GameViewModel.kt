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
    private var lastItemSpawnTime = 0L
    private val itemSpawnInterval = 6000L // Reduced for testing
    private var lastAsteroidSpawnTime = 0L
    private val asteroidSpawnInterval = 1000L

    init {
        viewModelScope.launch {
            while (true) {
                if (isRunning) {
                    update()
                }
                delay(16)
            }
        }
    }

    fun startLevel(level: Int, width: Float, height: Float, keepState: Boolean = false) {
        val currentState = _gameState.value
        val centerX = width / 2f
        
        val initialShip = if (keepState && currentState != null) {
            currentState.ship.copy(
                position = Offset(centerX, height * 0.8f),
                velocity = Offset.Zero,
                invulnerabilityFrames = 120
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
            killCount = 0
        )
        isRunning = true
        lastUpdateTime = System.currentTimeMillis()
    }

    fun continueGame() {
        val state = _gameState.value ?: return
        _gameState.value = state.copy(
            lives = 3,
            isGameOver = false,
            ship = state.ship.copy(
                invulnerabilityFrames = 180,
                activePowerUps = emptySet(),
                shieldHealth = 0
            )
        )
        isRunning = true
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

        updatedBullets = updatedBullets + newProjectiles

        // 5. Update Enemies & Asteroids
        val updatedEnemies = state.enemies.map { 
            it.copy(position = it.position + it.velocity * deltaTime)
        }.filter { it.position.y < state.screenHeight + 100 && it.position.x > -100 && it.position.x < state.screenWidth + 100 }

        val updatedAsteroids = state.asteroids.map {
            it.copy(position = it.position + it.velocity * deltaTime)
        }.filter { it.position.y < state.screenHeight + 100 && it.position.x > -100 && it.position.x < state.screenWidth + 100 }

        val spawnedEnemies = mutableListOf<Enemy>()
        if (currentTime - lastEnemySpawnTime > enemySpawnInterval) {
            lastEnemySpawnTime = currentTime
            val spawnPos = Offset(Random.nextFloat() * state.screenWidth, -50f)
            val velocity = Offset(Random.nextFloat() * 200 - 100, Random.nextFloat() * 200 + 100)
            spawnedEnemies.add(Enemy(position = spawnPos, velocity = velocity, type = EnemyType.entries.random()))
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

        // 6. Update Items
        val updatedItems = state.items.map {
            it.copy(position = it.position.copy(y = it.position.y + scrollSpeed * deltaTime))
        }.filter { it.position.y < state.screenHeight + 100 }

        val spawnedItems = mutableListOf<Item>()
        if (currentTime - lastItemSpawnTime > itemSpawnInterval) {
            lastItemSpawnTime = currentTime
            val roadPointForSpawn = state.roadPoints.filter { it.y < -200 }.randomOrNull()
            if (roadPointForSpawn != null) {
                spawnedItems.add(Item(position = roadPointForSpawn, type = PowerUpType.entries.random()))
            }
        }

        // 7. Collision Detection
        var currentScore = state.score
        var currentKillCount = state.killCount
        var currentLives = state.lives
        var currentShieldHealth = state.ship.shieldHealth
        val currentPowerUps = state.ship.activePowerUps.toMutableSet()
        
        val finalEnemies = (updatedEnemies + spawnedEnemies).toMutableList()
        val finalAsteroids = (updatedAsteroids + spawnedAsteroids).toMutableList()
        val finalBullets = updatedBullets.toMutableList()
        val finalItems = (updatedItems + spawnedItems).toMutableList()
        
        val bulletsToRemove = mutableSetOf<Long>()
        val enemiesToRemove = mutableSetOf<Long>()
        val asteroidsToRemove = mutableSetOf<Long>()
        val itemsToRemove = mutableSetOf<Long>()
        
        for (bullet in finalBullets) {
            for (enemy in finalEnemies) {
                if ((bullet.position - enemy.position).getDistance() < bullet.radius + enemy.radius) {
                    bulletsToRemove.add(bullet.id); enemiesToRemove.add(enemy.id)
                    currentScore += 100; currentKillCount++
                    viewModelScope.launch { _events.emit(GameEvent.EnemyDestroyed) }
                }
            }
            for (asteroid in finalAsteroids) {
                if ((bullet.position - asteroid.position).getDistance() < bullet.radius + asteroid.radius) {
                    bulletsToRemove.add(bullet.id)
                    val newHealth = asteroid.health - 1
                    if (newHealth <= 0) {
                        asteroidsToRemove.add(asteroid.id); currentScore += 50; currentKillCount++
                    } else {
                        val idx = finalAsteroids.indexOf(asteroid)
                        if (idx != -1) finalAsteroids[idx] = asteroid.copy(health = newHealth)
                    }
                }
            }
            for (item in finalItems) {
                if ((bullet.position - item.position).getDistance() < bullet.radius + item.radius) {
                    bulletsToRemove.add(bullet.id)
                    itemsToRemove.add(item.id)
                }
            }
        }
        finalBullets.removeAll { bulletsToRemove.contains(it.id) }
        finalEnemies.removeAll { enemiesToRemove.contains(it.id) }
        finalAsteroids.removeAll { asteroidsToRemove.contains(it.id) }
        finalItems.removeAll { itemsToRemove.contains(it.id) }
        
        if (newInvul <= 0) {
            for (enemy in finalEnemies) {
                if ((newShipPos - enemy.position).getDistance() < state.ship.radius + enemy.radius) {
                    if (currentShieldHealth > 0) { currentShieldHealth-- } else { 
                        currentLives--
                        currentPowerUps.clear() 
                    }
                    enemiesToRemove.add(enemy.id); viewModelScope.launch { _events.emit(GameEvent.ShipHit) }
                }
            }
            for (asteroid in finalAsteroids) {
                if ((newShipPos - asteroid.position).getDistance() < state.ship.radius + asteroid.radius) {
                    if (currentShieldHealth > 0) { currentShieldHealth-- } else { 
                        currentLives--
                        currentPowerUps.clear()
                    }
                    asteroidsToRemove.add(asteroid.id); viewModelScope.launch { _events.emit(GameEvent.ShipHit) }
                }
            }
        }
        finalEnemies.removeAll { enemiesToRemove.contains(it.id) }
        finalAsteroids.removeAll { asteroidsToRemove.contains(it.id) }
        
        for (item in finalItems) {
            if ((newShipPos - item.position).getDistance() < state.ship.radius + item.radius) {
                itemsToRemove.add(item.id)
                viewModelScope.launch { _events.emit(GameEvent.PowerUpCollected) }
                when(item.type) {
                    PowerUpType.Shield -> currentShieldHealth = (currentShieldHealth + 1).coerceAtMost(3)
                    PowerUpType.Nuke -> {
                        currentScore += finalAsteroids.size * 50
                        currentKillCount += finalAsteroids.size
                        finalAsteroids.clear()
                    }
                    else -> currentPowerUps.add(item.type)
                }
            }
        }
        finalItems.removeAll { itemsToRemove.contains(it.id) }

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
            items = finalItems,
            score = currentScore,
            killCount = currentKillCount,
            lives = currentLives,
            levelOpacity = newLevelOpacity,
            isGameOver = currentLives <= 0
        )
    }
}
