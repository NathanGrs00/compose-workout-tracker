// Main.kt
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ui.screens.AddExerciseScreen
import viewmodel.ExerciseViewModel

sealed class Screen {
    data object Home : Screen()
    data object AddExercise : Screen()
}

@Composable
@Preview
fun app() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val exerciseViewModel = remember { ExerciseViewModel() }

    MaterialTheme {
        when (currentScreen) {
            is Screen.Home -> homeScreen(
                onNavigateToAddExercise = { currentScreen = Screen.AddExercise }
            )
            is Screen.AddExercise -> AddExerciseScreen(
                viewModel = exerciseViewModel,
                onExerciseSaved = { currentScreen = Screen.Home }
            )
        }
    }
}

@Composable
fun homeScreen(
    onNavigateToAddExercise: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Workout Tracker", style = MaterialTheme.typography.headlineLarge)

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNavigateToAddExercise,
            modifier = Modifier.fillMaxWidth(0.5f)
        ) {
            Text("Exercises")
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Workout Tracker"
    ) {
        app()
    }
}