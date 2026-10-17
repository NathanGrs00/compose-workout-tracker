package ui.screens

import analytics.Analytics
import analytics.ProgressMetric
import analytics.SessionPoint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import models.Exercise
import models.LoggedSet
import models.WorkoutSession
import repository.WorkoutRepository
import repository.nowMillis
import ui.Format
import ui.charts.BarChart
import ui.charts.BarDatum
import ui.charts.ChartPoint
import ui.charts.HorizontalBars
import ui.charts.LineChart
import ui.components.AppDropdown
import ui.components.SectionCard
import ui.components.StatTile
import ui.components.TabHeader
import kotlin.math.ceil

private const val DAY = 24 * 60 * 60 * 1000L

enum class TimeRange(val label: String, val days: Int?) {
    MONTH("1M", 30), QUARTER("3M", 91), HALF("6M", 182), YEAR("1Y", 365), ALL("All", null)
}

/**
 * Progress & analysis: training overview, weekly volume, muscle-group balance and per-exercise progress.
 * Lays out in two columns on wide (desktop) windows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(repository: WorkoutRepository, syncIndicator: @Composable () -> Unit) {
    val sessions by repository.sessions.collectAsState(emptyList())
    val sets by repository.allSets.collectAsState(emptyList())
    val exercises by repository.exercises.collectAsState(emptyList())
    var range by remember { mutableStateOf(TimeRange.QUARTER) }

    val now = remember(sessions, sets) { nowMillis() }
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val sessionsById = remember(sessions) { sessions.associateBy { it.id } }
    val validSets = remember(sets, sessionsById) { sets.filter { it.sessionId in sessionsById } }
    val firstSessionAt = sessions.minOfOrNull { it.startedAt } ?: now
    val cutoff = range.days?.let { now - it * DAY } ?: firstSessionAt
    val rangeSessions = sessions.filter { it.startedAt >= cutoff }
    val rangeSessionIds = rangeSessions.map { it.id }.toSet()
    val rangeSets = validSets.filter { it.sessionId in rangeSessionIds }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TabHeader("Progress") { syncIndicator() }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeRange.entries.forEach { r ->
                FilterChip(selected = r == range, onClick = { range = r }, label = { Text(r.label) })
            }
        }
        Spacer(Modifier.height(8.dp))

        if (validSets.isEmpty()) {
            SectionCard("No data yet") {
                Text("Log a workout on your phone (or here) and your progress charts will appear on every device.")
            }
            return@Column
        }

        val weeks = ceil((now - cutoff).toDouble() / (7 * DAY)).toInt().coerceIn(1, 104)
        val workoutsInRange = rangeSets.map { it.sessionId }.distinct().size
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Workouts", "$workoutsInRange", caption = "in ${range.label.lowercase()}".takeIf { range != TimeRange.ALL } ?: "all time")
            StatTile("Per week", Format.number(workoutsInRange.toDouble() / weeks, 1), caption = "average")
            StatTile("Volume", Format.compact(rangeSets.sumOf { it.volume }) + " kg", caption = "${rangeSets.size} sets")
            val trainedSessionIds = remember(validSets) { validSets.map { it.sessionId }.toSet() }
            val streak = Analytics.weekStreak(sessions.filter { it.id in trainedSessionIds }, now, timeZone)
            StatTile("Streak", "$streak wk", caption = "weeks in a row")
        }
        Spacer(Modifier.height(16.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 900.dp
            val overview = @Composable { mod: Modifier ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WeeklyVolumeCard(validSets, sessions, weeks, now, timeZone)
                    MuscleGroupCard(rangeSets, exercises)
                }
            }
            val exerciseSection = @Composable { mod: Modifier ->
                ExerciseProgressCard(mod, validSets, sessionsById, exercises, cutoff)
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    exerciseSection(Modifier.weight(1f))
                    overview(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    exerciseSection(Modifier)
                    overview(Modifier)
                }
            }
        }
    }
}

@Composable
private fun WeeklyVolumeCard(
    sets: List<LoggedSet>,
    sessions: List<WorkoutSession>,
    weeks: Int,
    now: Long,
    timeZone: TimeZone,
) {
    val buckets = remember(sets, sessions, weeks, now) { Analytics.weeklySummary(sets, sessions, weeks.coerceAtLeast(4), now, timeZone) }
    SectionCard("Weekly volume", subtitle = "Total kg lifted per week (weight × reps)") {
        BarChart(
            bars = buckets.map {
                BarDatum(
                    label = Format.shortDate(it.weekStart),
                    value = it.volume,
                    tooltip = "Week of ${Format.shortDate(it.weekStart)}: ${Format.thousands(it.volume)} kg · ${it.workouts} workouts · ${it.sets} sets",
                )
            },
            yLabel = { Format.compact(it) },
        )
    }
}

@Composable
private fun MuscleGroupCard(sets: List<LoggedSet>, exercises: List<Exercise>) {
    val groups = remember(sets, exercises) { Analytics.setsPerMuscleGroup(sets, exercises) }
    SectionCard("Sets per muscle group", subtitle = "In the selected period. Spot what you're under-training.") {
        if (groups.isEmpty()) Text("No sets in this period.")
        HorizontalBars(groups.map { it.first to it.second.toDouble() }, valueLabel = { it.toInt().toString() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseProgressCard(
    modifier: Modifier,
    sets: List<LoggedSet>,
    sessionsById: Map<String, WorkoutSession>,
    exercises: List<Exercise>,
    cutoff: Long,
) {
    // Exercises with data, most trained first.
    val trained = remember(sets, exercises) {
        val counts = sets.groupingBy { it.exerciseId }.eachCount()
        exercises.filter { it.id in counts }.sortedByDescending { counts[it.id] }
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var metric by remember { mutableStateOf(ProgressMetric.E1RM) }
    val selected = trained.firstOrNull { it.id == selectedId } ?: trained.firstOrNull()

    SectionCard("Exercise progress", modifier = modifier) {
        if (selected == null) {
            Text("No exercises with logged sets yet.")
            return@SectionCard
        }
        AppDropdown(
            value = selected.name,
            onValueChange = { name -> selectedId = trained.firstOrNull { it.name == name }?.id },
            label = "Exercise",
            options = trained.map { it.name },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressMetric.entries.forEach { m ->
                FilterChip(selected = m == metric, onClick = { metric = m }, label = { Text(m.label) })
            }
        }

        val history = remember(sets, sessionsById, selected.id) { Analytics.exerciseHistory(selected.id, sets, sessionsById) }
        val visible = history.filter { it.date >= cutoff }
        val unit = if (metric == ProgressMetric.MAX_REPS) "reps" else "kg"
        val values = visible.map { it.date to metric.valueOf(it) }.filter { it.second > 0 }

        if (values.isEmpty()) {
            Text("No ${metric.label.lowercase()} data for ${selected.name} in this period.")
        } else {
            LineChart(
                points = values.map { (date, value) ->
                    ChartPoint(date, value, "${Format.date(date)}: ${Format.number(value, 1)} $unit")
                },
                yLabel = { Format.compact(it) },
                xLabel = { Format.shortDate(Format.localDate(it)) },
            )
            TrendText(values, unit)
        }

        val records = remember(history) { Analytics.personalRecords(history) }
        Text("Personal records", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            records.heaviest?.takeIf { it.weight > 0 }?.let {
                StatTile("Heaviest", Format.weight(it.weight), caption = "× ${it.reps} · ${Format.date(sessionsById[it.sessionId]?.startedAt ?: it.performedAt)}")
            }
            records.bestE1rmSet?.takeIf { records.bestE1rm > 0 }?.let {
                StatTile("Best est. 1RM", "${Format.number(records.bestE1rm, 1)} kg", caption = Format.set(it.weight, it.reps))
            }
            records.mostReps?.let {
                StatTile("Most reps", "${it.reps}", caption = if (it.weight > 0) "at ${Format.weight(it.weight)}" else "bodyweight")
            }
            records.bestSessionVolume?.let {
                StatTile("Best session", Format.thousands(it.volume) + " kg", caption = Format.date(it.date))
            }
        }

        Text("Recent sessions", style = MaterialTheme.typography.titleSmall)
        RecentSessionsTable(history.takeLast(8).reversed())
    }
}

@Composable
private fun TrendText(values: List<Pair<Long, Double>>, unit: String) {
    val trend = Analytics.trendPerWeek(values)
    val first = values.first().second
    val last = values.last().second
    val change = if (first > 0) (last - first) / first * 100 else null
    val text = buildString {
        if (trend != null) {
            append(if (trend >= 0) "Trend: +" else "Trend: ")
            append(Format.number(trend, 2))
            append(" $unit/week")
        }
        if (change != null && values.size > 1) {
            if (isNotEmpty()) append(" · ")
            append(if (change >= 0) "+" else "")
            append(Format.number(change, 1))
            append("% over this period")
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if ((trend ?: change ?: 0.0) >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun RecentSessionsTable(points: List<SessionPoint>) {
    Column(Modifier.widthIn(max = 640.dp)) {
        TableRow("Date", "Best set", "Est. 1RM", "Volume", header = true)
        HorizontalDivider()
        points.forEach { p ->
            TableRow(
                Format.date(p.date),
                Format.set(p.bestSet.weight, p.bestSet.reps),
                if (p.bestE1rm > 0) Format.number(p.bestE1rm, 1) else "–",
                Format.thousands(p.volume),
            )
        }
    }
}

@Composable
private fun TableRow(a: String, b: String, c: String, d: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(a, style = style, color = color, modifier = Modifier.weight(1.3f))
        Text(b, style = style, color = color, modifier = Modifier.weight(1f))
        Text(c, style = style, color = color, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
        Text(d, style = style, color = color, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
    }
}
