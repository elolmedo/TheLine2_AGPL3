package es.romsolutions.theline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import es.romsolutions.theline.game.GameViewModel
import es.romsolutions.theline.ui.screens.GameScreen
import es.romsolutions.theline.ui.screens.MenuScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Menu) }
            val viewModel: GameViewModel = viewModel()

            when (val screen = currentScreen) {
                is Screen.Menu -> {
                    MenuScreen(onStartGame = { level ->
                        currentScreen = Screen.Game(level)
                    })
                }
                is Screen.Game -> {
                    GameScreen(
                        viewModel = viewModel,
                        level = screen.level,
                        onExit = { currentScreen = Screen.Menu }
                    )
                }
            }
        }
    }
}

sealed class Screen {
    object Menu : Screen()
    data class Game(val level: Int) : Screen()
}
