package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.*
import models.Exercise
import java.util.UUID

// List of common muscle groups for the dropdown selection when adding a new exercise.
val muscleGroups = listOf("Front Delts", "Mid Delts", "Rear Delts", "Traps", "Lats", "Chest", "Lower Back",
    "Biceps", "Triceps", "Forearms", "Quads", "Hamstrings", "Glutes", "Calves", "Abs / Core", "Cardio"
)

/**
 * Screen for adding a new exercise to the app. It includes form validation and uses reusable components.
 */
@Composable
fun AddExerciseScreen(
    onExerciseSaved: (Exercise) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var muscleGroup by remember { mutableStateOf("") }

    val formIsValid = name.isNotBlank() && muscleGroup.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("New Exercise", style = MaterialTheme.typography.headlineMedium)

        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "Exercise Name",
            placeholder = "e.g. Bench Press",
            isError = name.isBlank(),
            errorMessage = "Name is required"
        )

        AppDropdown(
            value = muscleGroup,
            onValueChange = { muscleGroup = it },
            label = "Muscle Group",
            options = muscleGroups
        )

        Spacer(modifier = Modifier.weight(1f))

        AppButton(
            text = "Save Exercise",
            onClick = {
                onExerciseSaved(
                    Exercise(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        muscleGroup = muscleGroup
                    )
                )
            },
            enabled = formIsValid,
            isFullWidth = true
        )
    }
}