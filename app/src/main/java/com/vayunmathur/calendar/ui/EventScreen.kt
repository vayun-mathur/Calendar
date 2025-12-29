package com.vayunmathur.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColor
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.vayunmathur.calendar.ContactViewModel
import com.vayunmathur.calendar.EditEventPage
import com.vayunmathur.calendar.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(viewModel: ContactViewModel, eventId: Long, backStack: NavBackStack<NavKey>) {
    val events by viewModel.events.collectAsState()
    val calendars by viewModel.calendars.collectAsState()

    val event = events.find { it.id == eventId }
    if (event == null) {
        // simple empty state
        Text("Event not found")
        return
    }

    val calendar = calendars.find { it.id == event.calendarID }!!

    val isEditable = calendar.canModify

    Scaffold(topBar = {
        TopAppBar({}, navigationIcon = {
            IconButton({
                backStack.removeAt(backStack.lastIndex)
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save")
            }
        }, actions = {
            if(isEditable) {
                IconButton({
                    backStack.add(EditEventPage(event.id))
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton({
                    viewModel.deleteEvent(event.id!!)
                    backStack.removeAt(backStack.lastIndex)
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Edit")
                }
            }
        })
    }, contentWindowInsets = WindowInsets()) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            ListItem({
                Text(event.title, style = MaterialTheme.typography.titleLarge)
            }, supportingContent = {
                Text(dateRangeString(event.startDateTime.date, event.endDateTime.date, event.startDateTime.time, event.endDateTime.time, event.allDay))
            }, leadingContent = {
                Box(Modifier.size(24.dp).background(Color(calendar.color), RoundedCornerShape(4.dp)))
            })
            if(event.description.isNotBlank()) ListItem({
                Text(event.description)
            }, leadingContent = {
                Icon(Icons.Default.Description, null)
            })
            if(event.location.isNotBlank()) ListItem({Text(event.location)}, leadingContent =
                {Icon(painterResource(R.drawable.globe_24px), null)},
            )
        }
    }
}

fun dateRangeString(startDate: LocalDate, endDate: LocalDate, startTime: LocalTime, endTime: LocalTime, allDay: Boolean): String {
    return if(allDay) {
        if(startDate.toEpochDays() + 1 == endDate.toEpochDays()) {
            startDate.format(dateFormat)
        } else {
            "${startDate.format(dateFormat)} - ${endDate.format(dateFormat)}"
        }
    } else {
        if(startDate == endDate) {
            "${startDate.format(dateFormat)} • ${startTime.format(timeFormat)} - ${endTime.format(timeFormat)}"
        } else {
            "${startDate.format(dateFormat)}, ${startTime.format(timeFormat)} - ${endDate.format(dateFormat)}, ${endTime.format(timeFormat)}"
        }
    }
}