package com.vayunmathur.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus


private fun DayOfWeek.toIcal(): String = this.name.take(2)

// Helper to format LocalDate to YYYYMMDD
private fun LocalDate.toIcalString(): String {
    val monthStr = month.number.toString().padStart(2, '0')
    val dayStr = day.toString().padStart(2, '0')
    return "$year$monthStr$dayStr"
}

private fun RRule.EndCondition.toRRuleSuffix(): String = when (this) {
    is RRule.EndCondition.Never -> ""
    is RRule.EndCondition.Count -> ";COUNT=$count"
    is RRule.EndCondition.Until -> ";UNTIL=${date.toIcalString()}"
}

private val LocalDate.FAR_FUTURE: LocalDate
    get() = LocalDate(9999, 12, 31)

private fun LocalDate.withNthDayOfWeek(n: Int, dayOfWeek: DayOfWeek): LocalDate {
    val firstOfMonth = LocalDate(this.year, this.month, 1)
    val firstOccurrence = if (firstOfMonth.dayOfWeek == dayOfWeek) {
        firstOfMonth
    } else {
        val daysToAdd = (dayOfWeek.isoDayNumber - firstOfMonth.dayOfWeek.isoDayNumber + 7) % 7
        firstOfMonth.plus(daysToAdd, DateTimeUnit.DAY)
    }
    return firstOccurrence.plus((n - 1) * 7, DateTimeUnit.DAY)
}

data class EveryXYears(val years: Int, override val endCondition: RRule.EndCondition) : RRule {
    override fun asString(firstDay: LocalDate): String = "FREQ=YEARLY;INTERVAL=$years${endCondition.toRRuleSuffix()}"

    override fun lastDay(firstDay: LocalDate): LocalDate = when (val end = endCondition) {
        is RRule.EndCondition.Never -> firstDay.FAR_FUTURE
        is RRule.EndCondition.Until -> end.date
        is RRule.EndCondition.Count -> firstDay.plus((end.count - 1) * years, DateTimeUnit.YEAR)
    }
}

data class EveryXMonths(val months: Int, val type: Int, override val endCondition: RRule.EndCondition) : RRule {
    override fun asString(firstDay: LocalDate): String {
        val base = "FREQ=MONTHLY;INTERVAL=$months"
        val suffix = endCondition.toRRuleSuffix()
        return if (type == 1) {
            val dayOfWeek = firstDay.dayOfWeek.toIcal()
            val weekIndex = (firstDay.day - 1) / 7 + 1
            "$base;BYDAY=$weekIndex$dayOfWeek$suffix"
        } else "$base$suffix"
    }

    override fun lastDay(firstDay: LocalDate): LocalDate = when (val end = endCondition) {
        is RRule.EndCondition.Never -> firstDay.FAR_FUTURE
        is RRule.EndCondition.Until -> end.date
        is RRule.EndCondition.Count -> {
            val n = (firstDay.day - 1) / 7 + 1
            val dayOfWeek = firstDay.dayOfWeek
            var current = firstDay
            repeat(end.count.toInt() - 1) {
                current = current.plus(months, DateTimeUnit.MONTH)
            }
            if (type == 1) current.withNthDayOfWeek(n, dayOfWeek) else current
        }
    }
}

data class EveryXWeeks(val weeks: Int, val daysOfWeek: List<DayOfWeek>, override val endCondition: RRule.EndCondition) : RRule {
    override fun asString(firstDay: LocalDate): String {
        val days = daysOfWeek.sorted().joinToString(",") { it.toIcal() }
        return "FREQ=WEEKLY;INTERVAL=$weeks;BYDAY=$days${endCondition.toRRuleSuffix()}"
    }

    override fun lastDay(firstDay: LocalDate): LocalDate = when (val end = endCondition) {
        is RRule.EndCondition.Never -> firstDay.FAR_FUTURE
        is RRule.EndCondition.Until -> end.date
        is RRule.EndCondition.Count -> {
            var count = 0L
            var current = firstDay
            var lastFound = firstDay

            // Loop through weeks and then check days within the week
            while (count < end.count) {
                daysOfWeek.sorted().forEach { day ->
                    val daysUntil = (day.isoDayNumber - current.dayOfWeek.isoDayNumber + 7) % 7
                    val candidate = current.plus(daysUntil, DateTimeUnit.DAY)

                    // Only count if it's today or in the future of the current week start
                    if (candidate >= current) {
                        count++
                        lastFound = candidate
                        if (count == end.count) return@forEach
                    }
                }
                if(count == end.count) break
                current = current.plus(weeks, DateTimeUnit.WEEK)
                // Adjust current to the start of the next interval week (Monday)
                current = current.plus(-(current.dayOfWeek.isoDayNumber - 1), DateTimeUnit.DAY)
            }
            lastFound
        }
    }
}

data class EveryXDays(val days: Int, override val endCondition: RRule.EndCondition) : RRule {
    override fun asString(firstDay: LocalDate): String = "FREQ=DAILY;INTERVAL=$days${endCondition.toRRuleSuffix()}"

    override fun lastDay(firstDay: LocalDate): LocalDate = when (val end = endCondition) {
        is RRule.EndCondition.Never -> firstDay.FAR_FUTURE
        is RRule.EndCondition.Until -> end.date
        is RRule.EndCondition.Count -> firstDay.plus((end.count - 1) * days, DateTimeUnit.DAY)
    }
}

interface RRule {
    val endCondition: EndCondition
    fun asString(firstDay: LocalDate): String
    fun lastDay(firstDay: LocalDate): LocalDate

    companion object {
        fun parse(content: String): RRule? {
            if (content.isBlank()) return null

            // 1. Clean the string and split into parts
            // Handles both "RRULE:FREQ=..." and just "FREQ=..."
            val cleanContent = content.removePrefix("RRULE:").trim()
            val parts = cleanContent.split(";").associate {
                val split = it.split("=")
                if (split.size != 2) return null // Malformed part
                split[0].uppercase() to split[1].uppercase()
            }

            // 2. Extract common fields
            val freq = parts["FREQ"] ?: return null
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1

            val endCondition = when {
                parts.containsKey("COUNT") ->
                    EndCondition.Count(parts["COUNT"]?.toLongOrNull() ?: 1L)
                parts.containsKey("UNTIL") -> {
                    val untilStr = parts["UNTIL"]!!
                    // Standard iCal UNTIL is YYYYMMDD or YYYYMMDDTHHMMSSZ
                    val datePart = untilStr.take(8)
                    try {
                        val year = datePart.substring(0, 4).toInt()
                        val month = datePart.substring(4, 6).toInt()
                        val day = datePart.substring(6, 8).toInt()
                        EndCondition.Until(LocalDate(year, month, day))
                    } catch (e: Exception) {
                        return null
                    }
                }
                else -> EndCondition.Never
            }

            // 3. Dispatch to specific classes based on FREQ
            return when (freq) {
                "DAILY" -> EveryXDays(interval, endCondition)

                "WEEKLY" -> {
                    val byDay = parts["BYDAY"]
                    val days = byDay?.split(",")?.mapNotNull { dayStr ->
                        // Expecting values like MO, TU, etc.
                        DayOfWeek.entries.find { it.name.startsWith(dayStr.take(2)) }
                    } ?: emptyList()
                    EveryXWeeks(interval, days, endCondition)
                }

                "MONTHLY" -> {
                    val byDay = parts["BYDAY"]
                    // type 1 if BYDAY contains a numeric prefix (e.g., 2TU or 1MO)
                    val type = if (byDay != null && byDay.any { it.isDigit() }) 1 else 0
                    EveryXMonths(interval, type, endCondition)
                }

                "YEARLY" -> EveryXYears(interval, endCondition)

                else -> null // Unsupported frequency (e.g., HOURLY)
            }
        }
    }

    sealed interface EndCondition {
        object Never : EndCondition
        data class Count(val count: Long) : EndCondition
        data class Until(val date: LocalDate) : EndCondition
    }
}