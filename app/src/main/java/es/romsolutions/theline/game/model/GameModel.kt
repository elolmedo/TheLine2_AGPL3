package es.romsolutions.theline.game.model

import androidx.compose.ui.geometry.Offset
import java.util.UUID

enum class PowerUpType {
    Shield, DoubleShoot, TripleShoot, HomingMissile, Nuke
}

enum class EnemyType {
    Cube, Pyramid, Diamond, Cylinder
}

data class Ship(
    val position: Offset,
    val velocity: Offset = Offset.Zero,
    val rotation: Float = 0f,
    val radius: Float = 40f,
    val shieldHealth: Int = 0, // 0 to 3
    val isThrusting: Boolean = false,
    val activePowerUps: Set<PowerUpType> = emptySet(),
    val invulnerabilityFrames: Int = 0,
    val lastMissileTime: Long = 0L
)

data class Enemy(
    val position: Offset,
    val velocity: Offset,
    val rotation: Float = 0f,
    val type: EnemyType,
    val radius: Float = 30f,
    val health: Int = 1,
    val maxHealth: Int = 1,
    val id: Long = UUID.randomUUID().mostSignificantBits
)

data class Bullet(
    val position: Offset,
    val velocity: Offset,
    val radius: Float = 5f,
    val isEnemyBullet: Boolean = false,
    val isHoming: Boolean = false,
    val id: Long = UUID.randomUUID().mostSignificantBits
)

data class Star(
    val position: Offset,
    val alpha: Float,
    val size: Float,
    val layer: Int // 0 for far, 1 for near
)

data class Item(
    val position: Offset,
    val type: PowerUpType,
    val radius: Float = 25f,
    val id: Long = UUID.randomUUID().mostSignificantBits
)

data class Asteroid(
    val position: Offset,
    val velocity: Offset,
    val radius: Float,
    val health: Int = 1,
    val maxHealth: Int = 1,
    val isSpecial: Boolean = false,
    val id: Long = UUID.randomUUID().mostSignificantBits
)

data class GameState(
    val ship: Ship,
    val asteroids: List<Asteroid> = emptyList(),
    val enemies: List<Enemy> = emptyList(),
    val bullets: List<Bullet> = emptyList(),
    val items: List<Item> = emptyList(),
    val stars: List<Star> = emptyList(),
    val score: Int = 0,
    val killCount: Int = 0,
    val lives: Int = 3,
    val isGameOver: Boolean = false,
    val isVictory: Boolean = false,
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f,
    val currentLevel: Int = 1,
    val scrollOffset: Float = 0f, // Vertical scroll progress
    val roadPoints: List<Offset> = emptyList(), // The guide line points in view
    val levelOpacity: Float = 0f // For "Level N" title animation
)

sealed class GameEvent {
    object ShipFired : GameEvent()
    object ShipHit : GameEvent()
    object EnemyDestroyed : GameEvent()
    object PowerUpCollected : GameEvent()
}
