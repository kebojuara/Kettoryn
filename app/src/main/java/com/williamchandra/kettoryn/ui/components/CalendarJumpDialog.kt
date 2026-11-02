package com.williamchandra.kettoryn.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal fun calendarMonth(current: YearMonth, year: Int? = null, month: Int? = null): YearMonth =
    YearMonth.of(year ?: current.year, month ?: current.monthValue)

/** Browsing a year/month does not change either endpoint until a day is chosen. */
@Composable
fun CalendarJumpDialog(initialStart: LocalDate, initialEnd: LocalDate, onDismiss: () -> Unit, onApply: (LocalDate, LocalDate) -> Unit) {
    var startDay by rememberSaveable { mutableLongStateOf(initialStart.toEpochDay()) }
    var endDay by rememberSaveable { mutableLongStateOf(initialEnd.toEpochDay()) }
    var editingEnd by rememberSaveable { mutableStateOf(false) }
    var viewYear by rememberSaveable { mutableIntStateOf(initialStart.year) }
    var viewMonth by rememberSaveable { mutableIntStateOf(initialStart.monthValue) }
    val displayed = YearMonth.of(viewYear, viewMonth)
    val start = LocalDate.ofEpochDay(startDay)
    val end = LocalDate.ofEpochDay(endDay)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom date range") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { editingEnd = false; viewYear = start.year; viewMonth = start.monthValue }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("From: ${start.format(formatter)}${if (!editingEnd) " •" else ""}")
                }
                OutlinedButton(onClick = { editingEnd = true; viewYear = end.year; viewMonth = end.monthValue }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("To: ${end.format(formatter)}${if (editingEnd) " •" else ""}")
                }
                Text(if (editingEnd) "Choose end date" else "Choose start date", style = MaterialTheme.typography.titleMedium)
                DatePartMenu("Year", viewYear.toString(), ((maxOf(LocalDate.now().year, viewYear) + 1) downTo 1900).toList(), { it.toString() }) {
                    viewYear = calendarMonth(displayed, year = it).year
                }
                DatePartMenu("Month", displayed.month.getDisplayName(TextStyle.FULL, Locale.US), (1..12).toList(), { Month.of(it).getDisplayName(TextStyle.FULL, Locale.US) }) {
                    viewMonth = calendarMonth(displayed, month = it).monthValue
                }
                DatePartMenu("Day", "Select day", (1..displayed.lengthOfMonth()).toList(), { it.toString() }) { day ->
                    val chosen = displayed.atDay(day).toEpochDay()
                    if (editingEnd) endDay = chosen else startDay = chosen
                }
                if (start > end) Text("Start date must be on or before end date.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(start, end) }, enabled = start <= end) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DatePartMenu(label: String, value: String, choices: List<Int>, title: (Int) -> String, onChoose: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("$label: $value ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(title(choice)) }, onClick = { expanded = false; onChoose(choice) })
            }
        }
    }
}
