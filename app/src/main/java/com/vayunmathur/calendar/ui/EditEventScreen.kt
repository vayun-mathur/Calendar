package com.vayunmathur.calendar.ui

import android.content.ContentValues
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.vayunmathur.calendar.ContactViewModel
import com.vayunmathur.calendar.R
import com.vayunmathur.calendar.Route
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventScreen(viewModel: ContactViewModel, eventId: Long?, backStack: NavBackStack<Route>) {
    val events by viewModel.events.collectAsState()
    val calendars by viewModel.calendars.collectAsState()

    val event = events.find { it.id == eventId }

    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

    var title by remember { mutableStateOf(event?.title ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var location by remember { mutableStateOf(event?.location ?: "") }
    var selectedCalendar by remember { mutableStateOf(event?.calendarID ?: calendars.firstOrNull()?.id ?: -1L) }
    var allDay by remember { mutableStateOf(event?.allDay ?: false) }
    var startDate by remember { mutableStateOf(event?.startDateTime?.date ?: today) }
    var endDate by remember { mutableStateOf(event?.endDateTime?.date ?: today) }
    var startTime by remember { mutableStateOf(event?.startDateTime?.time ?: now) }
    var endTime by remember { mutableStateOf(event?.endDateTime?.time ?: now) }
    var timezone by remember { mutableStateOf(event?.timezone ?: TimeZone.currentSystemDefault().id) }

    if (selectedCalendar == -1L) {
        // simple empty state
    }

    Scaffold(topBar = {
        TopAppBar({}, actions = {
            IconButton({
                val values = ContentValues().apply {
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.EVENT_LOCATION, location)
                    put(CalendarContract.Events.CALENDAR_ID, selectedCalendar)
                    put(CalendarContract.Events.DTSTART, startDate.atTime(startTime).toInstant(TimeZone.of(timezone)).toEpochMilliseconds())
                    put(CalendarContract.Events.DTEND, endDate.atTime(endTime).toInstant(TimeZone.of(timezone)).toEpochMilliseconds())
                    put(CalendarContract.Events.ALL_DAY, if(allDay) 1 else 0)
                    put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
                }
                viewModel.upsertEvent(eventId, values)
                backStack.removeAt(backStack.lastIndex)
            }) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
        }, navigationIcon = {
            IconButton({
                backStack.removeAt(backStack.lastIndex)
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save")
            }
        })
    }, contentWindowInsets = WindowInsets()) { paddingValues ->
        Column(Modifier.padding(paddingValues).verticalScroll(rememberScrollState())) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(8.dp), label = { Text("Title") })
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth().padding(8.dp), label = { Text("Description") })
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Item(
                { Icon(Icons.Default.AccessTime, null) },
                {Text("All-day")},
                { Switch(allDay, { allDay = it }) }
            )
            Item(
                {},
                { Text(startDate.format(dateFormat)) },
                { if(!allDay) Text(startTime.format(timeFormat)) }
            )
            Item(
                {},
                { Text(endDate.format(dateFormat)) },
                { if(!allDay) Text(endTime.format(timeFormat)) }
            )
            if(!allDay) Item(
                {Icon(painterResource(R.drawable.globe_24px), null)},
                {Text(timezone)}
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth().padding(8.dp), label = { Text("Location") })
        }
    }
}

@Composable
fun Item(icon: @Composable () -> Unit = {}, left: @Composable () -> Unit, right: @Composable () -> Unit = {}) {
    Row(Modifier.padding(8.dp).padding(horizontal = 8.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
            Box(Modifier.size(24.dp)) {
                icon()
            }
            Spacer(Modifier.width(24.dp))
            Box(Modifier.weight(1f)) {
                left()
            }
            right()
        }
    }
}

val dateFormat = LocalDate.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    chars(", ")
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    day(Padding.NONE)
    chars(", ")
    year(Padding.NONE)
}

val timeFormat = LocalTime.Format {
    amPmHour(Padding.NONE)
    chars(":")
    minute()
    chars(" ")
    amPmMarker("AM", "PM")
}