package com.vayunmathur.calendar

import android.content.Context
import android.provider.CalendarContract
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant


@Serializable
data class Instance(
    val id: Long,
    val eventID: Long,
    val begin: Long,
    val end: Long
) {

    val startDateTime: LocalDateTime
        get() = Instant.fromEpochMilliseconds(begin).toLocalDateTime(TimeZone.currentSystemDefault())

    val endDateTime: LocalDateTime
        get() = Instant.fromEpochMilliseconds(end).toLocalDateTime(TimeZone.currentSystemDefault())

    val spanDays: List<LocalDate>
        get() = (startDateTime.date..endDateTime.date).toList()


    companion object {
        fun getInstances(context: Context, startTime: Instant, endTime: Instant): List<Instance> {
            val instances = mutableListOf<Instance>()

            val uri = CalendarContract.Instances.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Instances._ID,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )
            val cursor = CalendarContract.Instances.query(
                context.contentResolver,
                projection,
                startTime.toEpochMilliseconds(),
                endTime.toEpochMilliseconds()
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Instances._ID))
                    val eventID =
                        it.getLong(it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID))
                    val start =
                        it.getLong(it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
                    val end = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Instances.END))

                    if (end < start) continue
                    instances.add(Instance(id, eventID, start, end))
                }
            }

            return instances
        }
    }
}
