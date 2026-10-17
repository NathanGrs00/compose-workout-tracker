package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import models.Exercise
import models.muscleGroups
import ui.components.*
import viewmodel.ExerciseViewModel

/**
 * Screen for adding a new exercise (or editing one when [existing] is given).
 * It includes form validation and uses reusable components.
 */
@Composable
fun AddExerciseScreen(
    viewModel: ExerciseViewModel,
    onExerciseSaved: () -> Unit,
    onNavigateBack: () -> Unit,
    existing: Exercise? = null,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var muscleGroup by remember { mutableStateOf(existing?.muscleGroup ?: "") }

    val formIsValid = name.isNotBlank() && muscleGroup.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = if (existing == null) "New Exercise" else "Edit Exercise",
            onNavigateBack = onNavigateBack,
            backLabel = "Library"
        )

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
                if (existing == null) {
                    viewModel.addExercise(name, muscleGroup)
                } else {
                    viewModel.updateExercise(existing.copy(name = name.trim(), muscleGroup = muscleGroup))
                }
                onExerciseSaved()
            },
            enabled = formIsValid,
            isFullWidth = true
        )
    }
}
