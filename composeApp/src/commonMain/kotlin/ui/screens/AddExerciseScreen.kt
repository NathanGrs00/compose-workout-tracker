package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import models.Exercise
import models.muscleGroups
import ui.Format
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
    var repMin by remember { mutableStateOf((existing?.repMin ?: 8).toString()) }
    var repMax by remember { mutableStateOf((existing?.repMax ?: 12).toString()) }
    var increment by remember { mutableStateOf(Format.number(existing?.weightIncrement ?: 2.5)) }

    val min = repMin.toIntOrNull()
    val max = repMax.toIntOrNull()
    val step = Format.parseDecimal(increment)
    val rangeValid = min != null && max != null && min >= 1 && max >= min
    val stepValid = step != null && step > 0
    val formIsValid = name.isNotBlank() && muscleGroup.isNotBlank() && rangeValid && stepValid

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

        Text("Progression", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = repMin,
                onValueChange = { repMin = it.filter(Char::isDigit).take(3) },
                label = "Min reps",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                isError = !rangeValid,
                errorMessage = "Min ≤ max",
            )
            AppTextField(
                value = repMax,
                onValueChange = { repMax = it.filter(Char::isDigit).take(3) },
                label = "Max reps",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = increment,
                onValueChange = { increment = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) },
                label = "Step (kg)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
                isError = !stepValid,
                errorMessage = "> 0",
            )
        }
        Text(
            "Suggestions add one step of weight once you reach the max reps on every set, " +
                "then start again from the min reps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        AppButton(
            text = "Save Exercise",
            onClick = {
                if (existing == null) {
                    viewModel.addExercise(name, muscleGroup, min!!, max!!, step!!)
                } else {
                    viewModel.updateExercise(
                        existing.copy(name = name.trim(), muscleGroup = muscleGroup, repMin = min!!, repMax = max!!, weightIncrement = step!!)
                    )
                }
                onExerciseSaved()
            },
            enabled = formIsValid,
            isFullWidth = true
        )
    }
}
