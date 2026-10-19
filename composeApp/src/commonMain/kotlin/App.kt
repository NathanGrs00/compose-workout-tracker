import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import models.Exercise
import ui.PlatformBackHandler
import ui.brand.SplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import ui.LocalBarWeight
import androidx.compose.runtime.CompositionLocalProvider
import ui.components.SyncIndicator
import ui.screens.AddExerciseScreen
import ui.screens.HistoryScreen
import ui.screens.LibraryScreen
import ui.screens.ProgressScreen
import ui.screens.RoutineEditorScreen
import ui.screens.SessionDetailScreen
import ui.screens.SettingsScreen
import ui.screens.WorkoutScreen
import ui.theme.WorkoutTheme
import viewmodel.ExerciseViewModel

/** Top-level destinations, shown in the bottom bar (phone) or navigation rail (PC). */
enum class Tab(val label: String, val icon: ImageVector) {
    Workout("Workout", Icons.Default.PlayArrow),
    History("History", Icons.Default.DateRange),
    Progress("Progress", Icons.Default.Star),
    Library("Library", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Default.Settings),
}

/** Screens opened on top of a tab. */
sealed class Screen {
    data class SessionDetail(val sessionId: String) : Screen()
    data class RoutineEditor(val routineId: String?) : Screen()
    data class ExerciseEditor(val exercise: Exercise?) : Screen()
}

/**
 * Main application composable: adaptive navigation between tabs and sub-screens.
 * Narrow windows (phones) get a bottom bar; wide windows (PC) get a side rail and open on Progress.
 * A short branded splash screen shows on launch.
 */
@Composable
fun App(container: AppContainer) {
    val repository = container.repository
    val exerciseViewModel = remember { ExerciseViewModel(repository) }
    DisposableEffect(Unit) { onDispose { exerciseViewModel.clear() } }

    val syncState by container.syncManager.state.collectAsState()
    val pendingChanges by repository.pendingChanges.collectAsState(0L)
    var selectedTab by remember { mutableStateOf<Tab?>(null) }
    var libraryTab by remember { mutableIntStateOf(0) }
    val backStack = remember { mutableStateListOf<Screen>() }
    var barWeight by remember { mutableStateOf(container.settings.barWeight) }
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(900)
        showSplash = false
    }

    fun push(screen: Screen) = backStack.add(screen)
    fun pop() {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
    }

    PlatformBackHandler(enabled = backStack.isNotEmpty()) { pop() }

    val syncIndicator: @Composable () -> Unit = {
        SyncIndicator(syncState, pendingChanges) { container.syncManager.requestSync() }
    }

    CompositionLocalProvider(LocalBarWeight provides barWeight) {
        WorkoutTheme {
            Box(Modifier.fillMaxSize()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        val wide = maxWidth >= 720.dp
                        val tab = selectedTab ?: if (wide) Tab.Progress else Tab.Workout
                        val selectTab: (Tab) -> Unit = {
                            selectedTab = it
                            backStack.clear()
                        }

                        val content: @Composable (Modifier) -> Unit = { modifier ->
                            Box(modifier, contentAlignment = Alignment.TopCenter) {
                                when (val screen = backStack.lastOrNull()) {
                                    is Screen.SessionDetail -> SessionDetailScreen(screen.sessionId, repository, onNavigateBack = ::pop)
                                    is Screen.RoutineEditor -> RoutineEditorScreen(screen.routineId, repository, onDone = ::pop)
                                    is Screen.ExerciseEditor -> AddExerciseScreen(
                                        viewModel = exerciseViewModel,
                                        onExerciseSaved = ::pop,
                                        onNavigateBack = ::pop,
                                        existing = screen.exercise,
                                    )
                                    null -> when (tab) {
                                        Tab.Workout -> WorkoutScreen(
                                            repository, syncIndicator,
                                            onEditRoutine = { push(Screen.RoutineEditor(it)) },
                                            restNotifier = container.restNotifier,
                                        )
                                        Tab.History -> HistoryScreen(repository, syncIndicator, onOpenSession = { push(Screen.SessionDetail(it)) })
                                        Tab.Progress -> ProgressScreen(repository, syncIndicator)
                                        Tab.Library -> LibraryScreen(
                                            repository = repository,
                                            exerciseViewModel = exerciseViewModel,
                                            syncIndicator = syncIndicator,
                                            selectedTab = libraryTab,
                                            onSelectTab = { libraryTab = it },
                                            onAddExercise = { push(Screen.ExerciseEditor(null)) },
                                            onEditExercise = { push(Screen.ExerciseEditor(it)) },
                                            onEditRoutine = { push(Screen.RoutineEditor(it)) },
                                        )
                                        Tab.Settings -> SettingsScreen(
                                            container.settings, container.syncManager, syncState, pendingChanges,
                                            barWeight = barWeight,
                                            onBarWeightChange = {
                                                container.settings.barWeight = it
                                                barWeight = it
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (wide) {
                            Row(Modifier.fillMaxSize()) {
                                NavigationRail(Modifier.fillMaxHeight().padding(top = 8.dp)) {
                                    Tab.entries.forEach { t ->
                                        NavigationRailItem(
                                            selected = t == tab,
                                            onClick = { selectTab(t) },
                                            icon = { Icon(t.icon, null) },
                                            label = { Text(t.label) },
                                        )
                                    }
                                }
                                content(Modifier.weight(1f).fillMaxHeight())
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                content(Modifier.weight(1f))
                                NavigationBar(windowInsets = WindowInsets(0)) {
                                    Tab.entries.forEach { t ->
                                        NavigationBarItem(
                                            selected = t == tab,
                                            onClick = { selectTab(t) },
                                            icon = { Icon(t.icon, null) },
                                            label = { Text(t.label, maxLines = 1) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Branded launch screen, fades out once the app is ready.
                AnimatedVisibility(visible = showSplash, enter = EnterTransition.None, exit = fadeOut(tween(400))) {
                    SplashScreen()
                }
            }
        }
    }
}
