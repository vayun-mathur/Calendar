package com.vayunmathur.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavBackStack
import com.vayunmathur.calendar.Calendar
import com.vayunmathur.calendar.ContactViewModel
import com.vayunmathur.calendar.Event
import com.vayunmathur.calendar.Route
import com.vayunmathur.calendar.vutil.ResultEffect
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock


@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CalendarScreen(viewModel: ContactViewModel, backStack: NavBackStack<Route>) {
    val events by viewModel.events.collectAsState()
    val calendarsList by viewModel.calendars.collectAsState()
    val calendars = calendarsList.associateBy { it.id }
    val calendarVisibility by viewModel.calendarVisibility.collectAsState()

    // compute today's date
    var dateViewing by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date) }

    // state for which week to show; 0 = current week, +1 = next week, -1 = previous week
    var weekOffset by remember { mutableStateOf(0) }

    // shared vertical scroll so hour labels and grid scroll together
    val verticalState = rememberScrollState()

    ResultEffect<LocalDate>("GotoDate") { result ->
        dateViewing = result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // show month/year of the currently visible week's Sunday
                    val daysToSubtract = (dateViewing.dayOfWeek.ordinal - DayOfWeek.SUNDAY.ordinal + 7) % 7
                    val baseSunday = if (daysToSubtract == 0) dateViewing else dateViewing.minus(DatePeriod(days = daysToSubtract))
                    val visibleSunday = baseSunday.plus(DatePeriod(days = weekOffset * 7))
                    val mon = MonthNames.ENGLISH_ABBREVIATED.names[visibleSunday.month.number - 1]
                    Row(Modifier.clickable { backStack.add(Route.Calendar.GotoDialog(dateViewing.toEpochDays())) }, verticalAlignment = Alignment.CenterVertically) {
                        Text("$mon ${visibleSunday.year}", fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                },
                actions = {
                    IconButton(onClick = { backStack.add(Route.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(),
        floatingActionButton = {
            FloatingActionButton(onClick = { backStack.add(Route.EditEvent(null)) }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
    ) { innerPadding ->
        Row(Modifier.padding(innerPadding).fillMaxSize()) {

            var yOffset by remember { mutableStateOf(0.dp) }

            // Hour labels column - fixed on the left, shares vertical scroll state with grid
            Column() {
                Spacer(Modifier.height(yOffset))
                Column(Modifier.verticalScroll(verticalState)) {
                    for (hour in 0..23) {
                        Box(modifier = Modifier.height(56.dp).width(56.dp)) {
                            Text(
                                text = if (hour == 0) "12 AM" else if (hour < 12) "$hour AM" else if (hour == 12) "12 PM" else "${hour - 12} PM",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
        }
            // remember drag total so it survives recomposition
            val dragTotal = remember { mutableStateOf(0f) }

            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, delta ->
                            dragTotal.value += delta
                            change.consume()
                        },
                        onDragEnd = {
                            val threshold = 100f // pixels
                            if (dragTotal.value <= -threshold) {
                                weekOffset += 1
                            } else if (dragTotal.value >= threshold) {
                                weekOffset -= 1
                            }
                            dragTotal.value = 0f
                        },
                        onDragCancel = {
                            dragTotal.value = 0f
                        }
                    )
                }
            ) {

                // animate between weekOffset values with a horizontal slide
                AnimatedContent(targetState = weekOffset, transitionSpec = {
                    val duration = 300
                    if (targetState > initialState) {
                        slideInHorizontally(animationSpec = tween(durationMillis = duration)) { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = tween(durationMillis = duration)) { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally(animationSpec = tween(durationMillis = duration)) { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = tween(durationMillis = duration)) { width -> width } + fadeOut()
                    }
                }) { offset ->
                    // compute week days for this offset
                    val daysToSubtract = (dateViewing.dayOfWeek.ordinal - DayOfWeek.SUNDAY.ordinal + 7) % 7
                    val baseSunday = if (daysToSubtract == 0) dateViewing else dateViewing.minus(DatePeriod(days = daysToSubtract))
                    val sunday = baseSunday.plus(DatePeriod(days = offset * 7))
                    val weekDays = (0..6).map { sunday.plus(DatePeriod(days = it)) }

                    // partition events into the week (only include events that intersect the week)
                    val weekEvents = events.filter { ev ->
                        // respect calendar visibility: if not present in map, default to true
                        val visible = calendarVisibility[ev.calendarID] ?: true
                        visible && ev.spanDays.last() >= weekDays.first() && ev.spanDays.first() <= weekDays.last()
                    }

                    val allDayByDate: Map<LocalDate, List<Event>> = weekDays.associateWith { d ->
                        weekEvents.filter { ev -> ev.allDay && d in ev.spanDays }
                    }

                    val timedByDateHour: Map<LocalDate, Map<Int, List<Event>>> = weekDays.associateWith { d ->
                        val timed = weekEvents.filter { ev -> !ev.allDay && d in ev.spanDays }
                        timed.groupBy { ev ->
                            ev.startDateTime.hour
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        WeekHeader(weekDays)
                        AllDayRow(allDayByDate, calendars, weekDays) { id -> backStack.add(Route.Event(id)) }
                        Spacer(Modifier.onGloballyPositioned{
                            yOffset = with(density) { it.positionInParent().y.toDp() }
                        })
                        HourlyGrid(timedByDateHour, calendars, weekDays, verticalState, onEventClick = { id -> backStack.add(Route.Event(id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(weekDays: List<LocalDate>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        weekDays.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(text = d.dayOfWeek.name.take(3), fontSize = 12.sp, color = Color.Gray)
                Text(text = d.day.toString(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AllDayRow(allDayByDate: Map<LocalDate, List<Event>>, calendars: Map<Long, Calendar>, weekDays: List<LocalDate>, onEventClick: (Long) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        weekDays.forEach { d ->
            val events = allDayByDate[d].orEmpty()
            Column(modifier = Modifier.weight(1f)) {
                if (events.isEmpty()) {
                    Box(modifier = Modifier.height(32.dp).border(1.dp, Color.DarkGray)) {}
                } else {
                    Column {
                        events.forEach { ev ->
                            Box(modifier = Modifier
                                .padding(bottom = 4.dp)
                                .background(Color(ev.color ?: calendars[ev.calendarID]!!.color))
                                .height(28.dp)
                                .clickable {
                                    onEventClick(ev.id!!)
                                }
                                .fillMaxWidth()) {
                                Text(text = ev.title.ifEmpty { "(No title)" }, color = Color.White, modifier = Modifier.padding(4.dp), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyGrid(
    timedByDateHour: Map<LocalDate, Map<Int, List<Event>>>,
    calendars: Map<Long, Calendar>,
    weekDays: List<LocalDate>,
    verticalState: ScrollState,
    onEventClick: (Long) -> Unit
) {
    // Each hour row height
    val hourRowHeight = 56.dp
    val minEventHeight = 18.dp
    val minEventWidth = 56.dp

    Column(modifier = Modifier.fillMaxSize().verticalScroll(verticalState)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // create 7 equal columns with weight so all 7 fit on screen
            for (d in weekDays) {
                // collect unique timed events for this day (timedByDateHour groups by hour)
                val eventsForDay = timedByDateHour[d]?.values?.flatten().orEmpty().distinctBy { it.id }

                Box(modifier = Modifier.weight(1f)) {
                    // background hourly grid — fixed 24 rows
                    Column {
                        for (hour in 0..23) {
                            Box(
                                modifier = Modifier
                                    .height(hourRowHeight)
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF222222))
                                    .background(Color(0xFF0F0F0F))
                            ) {
                                // hour cell background only; we overlay events below
                            }
                        }
                    }

                    // compute positioned events using the helper that assigns columns for overlaps
                    val positioned = computePositionedEventsForDay(eventsForDay, calendars, d)

                    // overlay event segments positioned by their time within the day and column
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columnWidth = this.maxWidth
                        val hourHeight = hourRowHeight

                        positioned.forEach { ev ->
                            // compute vertical position and height
                            val startHours = ev.startMinutes.toFloat() / 60f
                            val lengthHours = (ev.endMinutes - ev.startMinutes).toFloat() / 60f

                            val yOffset = hourHeight * startHours
                            var heightDp = hourHeight * lengthHours
                            if (heightDp < minEventHeight) heightDp = minEventHeight

                            // compute horizontal position and size
                            val widthFraction = 1f / ev.totalColumns.toFloat()
                            val xFraction = ev.columnIndex * widthFraction
                            val xOffsetDp = columnWidth * xFraction
                            val widthDp = (columnWidth * widthFraction).coerceAtLeast(minEventWidth)

                            Box(
                                modifier = Modifier
                                    .offset(x = xOffsetDp, y = yOffset)
                                    .width(widthDp)
                                    .height(heightDp)
                                    .padding(2.dp)
                                    .zIndex(1f + ev.columnIndex * 0.01f)
                                    .background(Color(ev.color))
                                    .clickable(enabled = true) { onEventClick(ev.id) }
                            ) {
                                Text(
                                    text = ev.title.ifEmpty { "(No title)" },
                                    color = Color.White,
                                    modifier = Modifier.padding(6.dp),
                                    maxLines = 2,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
