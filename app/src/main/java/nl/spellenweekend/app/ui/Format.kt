package nl.spellenweekend.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NL: Locale = Locale.forLanguageTag("nl-NL")
private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", NL)
private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", NL)
private val shortDayFmt = DateTimeFormatter.ofPattern("EEE d", NL)

fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

fun fmtTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(timeFmt)

fun fmtRange(start: Long, end: Long): String = "${fmtTime(start)}–${fmtTime(end)}"

fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now(zone)
    return when (d) {
        today -> "Vandaag"
        today.plusDays(1) -> "Morgen"
        today.minusDays(1) -> "Gisteren"
        else -> d.format(shortDayFmt)
    }
}

fun dayHeader(d: LocalDate): String {
    val rel = when (d) {
        LocalDate.now(zone) -> "Vandaag · "
        LocalDate.now(zone).plusDays(1) -> "Morgen · "
        else -> ""
    }
    return rel + d.format(dayFmt).replaceFirstChar { it.uppercase() }
}

/** Huidige tijd, afgerond naar het volgende kwartier. */
fun roundedNow(): Long {
    val q = 15 * 60_000L
    val now = System.currentTimeMillis()
    return ((now + q - 1) / q) * q
}

fun epochOf(date: LocalDate, hour: Int, minute: Int): Long =
    date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

fun hourOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).hour
fun minuteOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).minute
