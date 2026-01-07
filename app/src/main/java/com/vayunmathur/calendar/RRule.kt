package com.vayunmathur.calendar

import com.vayunmathur.calendar.ui.dateFormat
import com.vayunmathur.calendar.ui.dialog.capitalcase
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.number
import kotlinx.serialization.Serializable


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

private fun RRule.EndCondition.toStringSuffix(): String = when (this) {
    is RRule.EndCondition.Never -> ""
    is RRule.EndCondition.Count -> ", $count times"
    is RRule.EndCondition.Until -> ", Until ${date.format(dateFormat)}"
}

@Serializable
abstract class RRule {
    abstract val endCondition: EndCondition
    abstract fun asString(firstDay: LocalDate): String
    fun toString(firstDay: LocalDate): String {
        return toStringImpl(firstDay) + endCondition.toStringSuffix()
    }
    protected abstract fun toStringImpl(firstDay: LocalDate): String

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
                    } catch (_: Exception) {
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

    @Serializable
    sealed interface EndCondition {
        @Serializable
        object Never : EndCondition
        @Serializable
        data class Count(val count: Long) : EndCondition
        @Serializable
        data class Until(val date: LocalDate) : EndCondition
    }

    data class EveryXYears(val years: Int, override val endCondition: EndCondition) : RRule() {
        override fun asString(firstDay: LocalDate): String = "FREQ=YEARLY;INTERVAL=$years${endCondition.toRRuleSuffix()}"
        override fun toStringImpl(firstDay: LocalDate): String = "Every $years years"
    }

    data class EveryXMonths(val months: Int, val type: Int, override val endCondition: EndCondition) : RRule() {
        override fun asString(firstDay: LocalDate): String {
            val base = "FREQ=MONTHLY;INTERVAL=$months"
            val suffix = endCondition.toRRuleSuffix()
            return if (type == 1) {
                val dayOfWeek = firstDay.dayOfWeek.toIcal()
                val weekIndex = (firstDay.day - 1) / 7 + 1
                "$base;BYDAY=$weekIndex$dayOfWeek$suffix"
            } else "$base$suffix"
        }
        override fun toStringImpl(firstDay: LocalDate): String = "Every $months months"
    }

    data class EveryXWeeks(val weeks: Int, val daysOfWeek: List<DayOfWeek>, override val endCondition: EndCondition) : RRule() {
        override fun asString(firstDay: LocalDate): String {
            val days = daysOfWeek.sorted().joinToString(",") { it.toIcal() }
            return "FREQ=WEEKLY;INTERVAL=$weeks;BYDAY=$days${endCondition.toRRuleSuffix()}"
        }
        override fun toStringImpl(firstDay: LocalDate): String = "Every $weeks weeks on ${
            daysOfWeek.joinToString(", ") { it.name.take(3).capitalcase() }
        }"
    }

    data class EveryXDays(val days: Int, override val endCondition: EndCondition) : RRule() {
        override fun asString(firstDay: LocalDate): String = "FREQ=DAILY;INTERVAL=$days${endCondition.toRRuleSuffix()}"
        override fun toStringImpl(firstDay: LocalDate): String = "Every $days days"
    }
}