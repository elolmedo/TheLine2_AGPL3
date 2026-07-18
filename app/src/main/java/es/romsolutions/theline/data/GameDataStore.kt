package es.romsolutions.theline.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "the_line_prefs_v1")

data class PersistedProgress(
    val unlockedLevels: Set<Int>,
    val currentLevel: Int,
    val lives: Int,
    val activePowerUps: Set<String>
)

class GameDataStore(private val context: Context) {
    private val UNLOCKED_LEVELS = stringSetPreferencesKey("unlocked_levels")
    private val CURRENT_LEVEL = intPreferencesKey("current_level")
    private val LIVES = intPreferencesKey("lives")
    private val ACTIVE_POWERUPS = stringSetPreferencesKey("active_powerups")

    val progressFlow: Flow<PersistedProgress> = context.dataStore.data
        .map { preferences ->
            val set = preferences[UNLOCKED_LEVELS] ?: setOf("1")
            val unlockedSet = set.mapNotNull { it.toIntOrNull() }.toSet()
            PersistedProgress(
                unlockedLevels = unlockedSet,
                currentLevel = preferences[CURRENT_LEVEL] ?: 1,
                lives = preferences[LIVES] ?: 3,
                activePowerUps = preferences[ACTIVE_POWERUPS] ?: emptySet()
            )
        }

    suspend fun saveProgress(
        level: Int, 
        isUnlock: Boolean = false, 
        lives: Int? = null,
        activePowerUps: Set<String>? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_LEVEL] = level
            if (isUnlock) {
                val current = preferences[UNLOCKED_LEVELS] ?: setOf("1")
                preferences[UNLOCKED_LEVELS] = current + "$level"
            }
            lives?.let { preferences[LIVES] = it }
            activePowerUps?.let { preferences[ACTIVE_POWERUPS] = it }
        }
    }

    suspend fun clearProgress() {
        context.dataStore.edit { preferences ->
            preferences[UNLOCKED_LEVELS] = setOf("1")
            preferences[CURRENT_LEVEL] = 1
            preferences[LIVES] = 3
            preferences[ACTIVE_POWERUPS] = emptySet()
        }
    }
}
