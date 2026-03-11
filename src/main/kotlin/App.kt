import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import ui.screens.AddExerciseScreen
import ui.screens.ExerciseLibraryScreen
import ui.screens.HomeScreen
import viewmodel.ExerciseViewModel

/**
 * Main application composable that manages navigation between screens.
 */
sealed class Screen {
    data object Home : Screen()
    data object ExerciseLibrary : Screen()
    data object AddExercise : Screen()
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val exerciseViewModel = remember { ExerciseViewModel() }

    MaterialTheme {
        when (currentScreen) {
            is Screen.Home -> HomeScreen(
                onNavigateToExercises = { currentScreen = Screen.ExerciseLibrary }
            )
            is Screen.ExerciseLibrary -> ExerciseLibraryScreen(
                viewModel = exerciseViewModel,
                onNavigateToAddExercise = { currentScreen = Screen.AddExercise },
                onNavigateBack = { currentScreen = Screen.Home }
            )
            is Screen.AddExercise -> AddExerciseScreen(
                viewModel = exerciseViewModel,
                onExerciseSaved = { currentScreen = Screen.ExerciseLibrary },
                onNavigateBack = { currentScreen = Screen.ExerciseLibrary }
            )
        }
    }
}