package com.vayunmathur.calendar.glance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults.defaultTextStyle
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vayunmathur.calendar.Calendar
import com.vayunmathur.calendar.Event
import com.vayunmathur.calendar.MainActivity
import com.vayunmathur.calendar.R
import com.vayunmathur.calendar.ui.PositionedEvent
import com.vayunmathur.calendar.ui.computePositionedEventsForDay
import com.vayunmathur.calendar.ui.dateFormat
import com.vayunmathur.calendar.ui.dateRangeString
import com.vayunmathur.calendar.ui.theme.CalendarTheme
import com.vayunmathur.calendar.ui.theme.CalendarThemeGlance
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.collections.listOf
import kotlin.time.Clock

class CalendarGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val events = Event.getAllEvents(context)
        val calendars = Calendar.getAllCalendars(context).associateBy { it.id }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date

        val nextMonth = today + DatePeriod(months = 1)
        val days = today..<nextMonth

        val positionedEvents = days.associateWith { day ->
            computePositionedEventsForDay(
                events.filter { day in it.spanDays },
                calendars,
                day
            ).map{posEvt -> events.find{it.id == posEvt.id}!!} + events.filter { day in it.spanDays && it.allDay }
        }

        provideContent {
            CalendarThemeGlance(context) {
                Content(context, positionedEvents, events.filter { it.spanDays.last() > today && it.spanDays.first() < nextMonth }, calendars)
            }
        }
    }
}
@SuppressLint("RestrictedApi")
@Composable
fun Content(context: Context, positionedEvents: Map<LocalDate, List<Event>>, events: List<Event>, calendars: Map<Long, Calendar>) {
    val dateFormatS = LocalDate.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
        chars(", ")
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        chars(" ")
        day(Padding.NONE)
    }

    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val today = now.date

    val nextMonth = today + DatePeriod(months = 1)
    val days = today..<nextMonth

    Scaffold(titleBar = {
        TitleBar(ImageProvider(R.drawable.calendar_today_24px), today.format(dateFormatS), modifier = GlanceModifier.clickable{
            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        })
    }) {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            for(day in days) {
                if(positionedEvents[day]!!.isNotEmpty()) {
                    item {
                        Text(day.format(dateFormat), GlanceModifier.padding(vertical = 6.dp), style = defaultTextStyle.copy(color = GlanceTheme.colors.onSurface))
                    }
                }
                items(positionedEvents[day]!!) { orEvent ->
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(6.dp).clickable {
                            val intent = Intent(context, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putExtra("id", orEvent.id!!)
                            context.startActivity(intent)
                        }) {
                            Box(GlanceModifier.background(ColorProvider(Color(orEvent.color ?: calendars[orEvent.calendarID]!!.color))).width(8.dp).fillMaxHeight()) {}
                            Column(GlanceModifier.padding(4.dp).fillMaxWidth()) {
                                Text(
                                    orEvent.title,
                                    style = TextStyle(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    dateRangeString(
                                        orEvent.startDateTime.date,
                                        orEvent.endDateTime.date,
                                        orEvent.startDateTime.time,
                                        orEvent.endDateTime.time,
                                        orEvent.allDay
                                    ),
                                    style = defaultTextStyle.copy(color = GlanceTheme.colors.onPrimaryContainer)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

