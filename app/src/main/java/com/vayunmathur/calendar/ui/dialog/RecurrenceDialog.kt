package com.vayunmathur.calendar.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import com.vayunmathur.calendar.RecurrenceParams
import com.vayunmathur.calendar.Route
import com.vayunmathur.calendar.RRule
import com.vayunmathur.calendar.vutil.LocalNavResultRegistry
import com.vayunmathur.calendar.vutil.ResultEffect
import com.vayunmathur.calendar.vutil.pop
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceDialog(backStack: NavBackStack<Route>, resultKey: String, initial: RecurrenceParams?) {
    val registry = LocalNavResultRegistry.current
    val scope = rememberCoroutineScope()

    var freq by remember { mutableStateOf(initial?.freq ?: "NONE") }
    var intervalStr by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var monthlyType by remember { mutableStateOf(initial?.monthlyType ?: 0) }
    var daysOfWeek by remember { mutableStateOf(initial?.daysOfWeek ?: emptyList<DayOfWeek>()) }
    var endCondition by remember { mutableStateOf(initial?.endCondition ?: RRule.EndCondition.Never) }

    // result key for the nested date picker used for UNTIL
    val KEY_UNTIL = "$resultKey.until"
    // listen for date picker result
    ResultEffect<LocalDate>(KEY_UNTIL) { selected ->
        endCondition = RRule.EndCondition.Until(selected)
    }

    AlertDialog(
        onDismissRequest = { backStack.pop() },
        confirmButton = {
            Button(onClick = {
                val params = if (freq == "NONE") null else RecurrenceParams(
                    freq = freq,
                    interval = intervalStr.toIntOrNull() ?: 1,
                    daysOfWeek = daysOfWeek,
                    monthlyType = monthlyType,
                    endCondition = endCondition
                )

                val rrule = params?.let { p ->
                    when (p.freq) {
                        "DAILY" -> RRule.EveryXDays(p.interval, p.endCondition)
                        "WEEKLY" -> RRule.EveryXWeeks(p.interval, p.daysOfWeek, p.endCondition)
                        "MONTHLY" -> RRule.EveryXMonths(p.interval, p.monthlyType, p.endCondition)
                        "YEARLY" -> RRule.EveryXYears(p.interval, p.endCondition)
                        else -> null
                    }
                } ?: ""

                scope.launch { registry.dispatchResult(resultKey, rrule) }
                backStack.pop()
            }) { Text("OK") }
        },
        dismissButton = {
            Button(onClick = { backStack.pop() }) { Text("Cancel") }
        },
        text = {
            Column(Modifier.padding(8.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Repeat", Modifier.padding(8.dp))
                    listOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { f ->
                        Row(Modifier.clickable { freq = f }.padding(8.dp)) {
                            RadioButton(selected = freq == f, onClick = { freq = f })
                            Text(f)
                        }
                    }
                }

                OutlinedTextField(value = intervalStr, onValueChange = { intervalStr = it }, label = { Text("Every n") })

                if (freq == "WEEKLY") {
                    Text("On days of week")
                    DayOfWeek.values().forEach { d ->
                        Row(Modifier.clickable {
                            daysOfWeek = if (daysOfWeek.contains(d)) daysOfWeek - d else daysOfWeek + d
                        }.padding(4.dp)) {
                            RadioButton(selected = daysOfWeek.contains(d), onClick = {
                                daysOfWeek = if (daysOfWeek.contains(d)) daysOfWeek - d else daysOfWeek + d
                            })
                            Text(d.name)
                        }
                    }
                }

                if (freq == "MONTHLY") {
                    Text("Monthly type")
                    Row(Modifier.clickable { monthlyType = 0 }.padding(4.dp)) {
                        RadioButton(selected = monthlyType == 0, onClick = { monthlyType = 0 })
                        Text("By month day")
                    }
                    Row(Modifier.clickable { monthlyType = 1 }.padding(4.dp)) {
                        RadioButton(selected = monthlyType == 1, onClick = { monthlyType = 1 })
                        Text("By weekday (e.g., 2nd Tue)")
                    }
                }

                Text("End")
                Row(Modifier.clickable { endCondition = RRule.EndCondition.Never }.padding(4.dp)) {
                    RadioButton(selected = endCondition is RRule.EndCondition.Never, onClick = { endCondition = RRule.EndCondition.Never })
                    Text("Never")
                }
                Row(Modifier.clickable { endCondition = RRule.EndCondition.Count(1) }.padding(4.dp)) {
                    RadioButton(selected = endCondition is RRule.EndCondition.Count, onClick = { endCondition = RRule.EndCondition.Count(1) })
                    Text("Count")
                }
                if (endCondition is RRule.EndCondition.Count) {
                    var countStr by remember { mutableStateOf((endCondition as RRule.EndCondition.Count).count.toString()) }
                    OutlinedTextField(countStr, { new ->
                        val v = new.toLongOrNull() ?: 1L
                        countStr = new
                        endCondition = RRule.EndCondition.Count(v)
                    }, label = { Text("Count") })
                }

                Row(Modifier.clickable {
                    // open the date picker dialog for UNTIL
                    val current = if (endCondition is RRule.EndCondition.Until) (endCondition as RRule.EndCondition.Until).date else LocalDate(java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue, java.time.LocalDate.now().dayOfMonth)
                    backStack.add(Route.EditEvent.DatePickerDialog(KEY_UNTIL, current))
                }.padding(4.dp)) {
                    RadioButton(selected = endCondition is RRule.EndCondition.Until, onClick = { if (!(endCondition is RRule.EndCondition.Until)) endCondition = RRule.EndCondition.Until(LocalDate(java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue, java.time.LocalDate.now().dayOfMonth)) })
                    Text("Until: ${if (endCondition is RRule.EndCondition.Until) (endCondition as RRule.EndCondition.Until).date.toString() else "Select date"}")
                }
            }
        }
    )
}
