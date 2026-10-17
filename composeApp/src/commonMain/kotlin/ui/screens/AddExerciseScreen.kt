package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var targetSets by remember { mutableStateOf((existing?.targetSets ?: 3).toString()) }
    var restSeconds by remember { mutableStateOf((existing?.restSeconds ?: 90).toString()) }
    var isBarbell by remember { mutableStateOf(existing?.isBarbell ?: false) }
    var setupNote by remember { mutableStateOf(existing?.setupNote ?: "") }

    val min = repMin.toIntOrNull()
    val max = repMax.toIntOrNull()
    val step = Format.parseDecimal(increment)
    val rangeValid = min != null && max != null && min >= 1 && max >= min
    val stepValid = step != null && step > 0
    val sets = targetSets.toIntOrNull()
    val rest = restSeconds.toIntOrNull()
    val setsValid = sets != null && sets in 1..20
    val restValid = rest != null && rest in 0..1800
    val formIsValid = name.isNotBlank() && muscleGroup.isNotBlank() && rangeValid && stepValid && setsValid && restValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
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

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Barbell exercise", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Shows plates per side and warm-up sets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Switch(checked = isBarbell, onCheckedChange = { isBarbell = it })
        }

        AppTextField(
            value = setupNote,
            onValueChange = { setupNote = it.take(300) },
            label = "Setup note",
            placeholder = "e.g. seat height 4, wide grip",
            singleLine = false,
        )

        Text("Plan", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberStepper(
                value = targetSets,
                onValueChange = { targetSets = it },
                label = "Sets",
                step = 1.0,
                decimal = false,
                modifier = Modifier.weight(1f),
            )
            NumberStepper(
                value = restSeconds,
                onValueChange = { restSeconds = it },
                label = "Rest (sec)",
                step = 15.0,
                decimal = false,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            rest?.let { "Rest ${Format.duration(it * 1000L)} between sets. " }.orEmpty() +
                "After the planned sets the workout moves on to the next exercise; an extra set is always possible.",
            style = MaterialTheme.typography.bodySmall,
            color = if (setsValid && restValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
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
            "Suggestions add one step of weight once you reach the max reps on every set " +
                "(or did an extra set), then start again from the min reps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        AppButton(
            text = "Save Exercise",
            onClick = {
                if (existing == null) {
                    viewModel.addExercise(Exercise("", name.trim(), muscleGroup, min!!, max!!, step!!, sets!!, rest!!, isBarbell, setupNote.trim()))
                } else {
                    viewModel.updateExercise(
                        existing.copy(
                            name = name.trim(), muscleGroup = muscleGroup, repMin = min!!, repMax = max!!,
                            weightIncrement = step!!, targetSets = sets!!, restSeconds = rest!!,
                            isBarbell = isBarbell, setupNote = setupNote.trim(),
                        )
                    )
                }
                onExerciseSaved()
            },
            enabled = formIsValid,
            isFullWidth = true
        )
    }
}
