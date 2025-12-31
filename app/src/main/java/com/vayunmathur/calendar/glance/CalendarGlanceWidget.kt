package com.vayunmathur.calendar.glance

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.action.toParametersKey
import androidx.glance.appwidget.GlanceAppWidget
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults.defaultTextStyle
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vayunmathur.calendar.Calendar
import com.vayunmathur.calendar.Event
import com.vayunmathur.calendar.Instance
import com.vayunmathur.calendar.MainActivity
import com.vayunmathur.calendar.R
import com.vayunmathur.calendar.ui.atEndOfDayIn
import com.vayunmathur.calendar.ui.computePositionedEventsForDay
import com.vayunmathur.calendar.ui.dateFormat
import com.vayunmathur.calendar.ui.dateRangeString
import com.vayunmathur.calendar.ui.theme.CalendarThemeGlance
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class CalendarGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val events = Event.getAllEvents(context).associateBy { it.id!! }
        val calendars = Calendar.getAllCalendars(context).associateBy { it.id }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date

        val nextMonth = today + DatePeriod(months = 1)
        val days = today..<nextMonth

        val instances = Instance.getInstances(context, today.atStartOfDayIn(TimeZone.currentSystemDefault()), nextMonth.atEndOfDayIn(
            TimeZone.currentSystemDefault()))

        val positionedEvents = days.associateWith { day ->
            computePositionedEventsForDay(
                instances.filter { day in it.spanDays },
                events,
                calendars,
                day
            ).map{posEvt -> instances.find{it.id == posEvt.instanceID}!!} + instances.filter { day in it.spanDays && events[it.eventID]!!.allDay }
        }

        provideContent {
            CalendarThemeGlance(context) {
                Content(positionedEvents, events, calendars)
            }
        }
    }
}
@SuppressLint("RestrictedApi")
@Composable
fun Content(positionedEvents: Map<LocalDate, List<Instance>>, events: Map<Long, Event>, calendars: Map<Long, Calendar>) {
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
        TitleBar(ImageProvider(R.drawable.calendar_today_24px), today.format(dateFormatS), modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()))
    }) {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            for(day in days) {
                if(positionedEvents[day]!!.isNotEmpty()) {
                    item {
                        Text(day.format(dateFormat), GlanceModifier.padding(vertical = 6.dp), style = defaultTextStyle.copy(color = GlanceTheme.colors.onSurface))
                    }
                }
                items(positionedEvents[day]!!) { instance ->
                    val orEvent = events[instance.eventID]!!
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(6.dp).clickable (actionStartActivity<MainActivity>(actionParametersOf(
                            stringPreferencesKey("instance").toParametersKey() to Json.encodeToString(instance))))) {
                            Box(GlanceModifier.background(ColorProvider(Color(orEvent.color ?: calendars[orEvent.calendarID]!!.color))).width(8.dp).fillMaxHeight()) {}
                            Column(GlanceModifier.padding(4.dp).fillMaxWidth()) {
                                Text(
                                    orEvent.title,
                                    style = TextStyle(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    dateRangeString(
                                        instance.startDateTime.date,
                                        instance.endDateTime.date,
                                        instance.startDateTime.time,
                                        instance.endDateTime.time,
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


