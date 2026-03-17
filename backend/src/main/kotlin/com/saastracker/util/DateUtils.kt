package com.saastracker.util

import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun daysUntil(date: LocalDate, clock: Clock = Clock.systemUTC()): Int {
    val now = LocalDate.now(clock)
    return ChronoUnit.DAYS.between(now, date).toInt()
}

