/*
 * DateTimeHelper.kt
 * Implements the DateTimeHelper object
 * A DateTimeHelper provides helper methods for converting Date and Time objects
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.michatec.radio.helpers

import android.util.Log
import com.michatec.radio.Keys
import java.text.SimpleDateFormat
import java.util.*


/*
 * DateTimeHelper object
 */
object DateTimeHelper {


    /* Define log tag */
    private val TAG: String = DateTimeHelper::class.java.simpleName


    /* Main class variables */
    private const val pattern: String = "EEE, dd MMM yyyy HH:mm:ss Z"
    private val dateFormat: SimpleDateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)


    /* Converts RFC 2822 string representation of a date to DATE */
    fun convertFromRfc2822(dateString: String): Date {
        val date: Date = try {
            // parse date string using standard pattern
            dateFormat.parse((dateString)) ?: Keys.DEFAULT_DATE
        } catch (e: Exception) {
            Log.w(TAG, "Unable to parse. Trying an alternative Date format. $e")
            // try alternative parsing patterns
            tryAlternativeRfc2822Parsing(dateString)
        }
        return date
    }


    /* Converts a DATE to its RFC 2822 string representation */
    fun convertToRfc2822(date: Date): String {
        val dateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)
        return dateFormat.format(date)
    }


    /* Converts a milliseconds into a readable format (HH:mm:ss) */
    fun convertToHoursMinutesSeconds(milliseconds: Long, negativeValue: Boolean = false): String {
        // convert milliseconds to hours, minutes, and seconds
        val hours: Long = milliseconds / 1000 / 3600
        val minutes: Long = milliseconds / 1000 % 3600 / 60
        val seconds: Long = milliseconds / 1000 % 60
        val hourPart = if (hours > 0) {
            "${hours.toString().padStart(2, '0')}:"
        } else {
            ""
        }

        var timeString =
            "$hourPart${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        if (negativeValue) {
            // add a minus sign if a negative values was requested
            timeString = "-$timeString"
        }
        return timeString
    }


    /* Converts RFC 2822 string representation of a date to DATE - using alternative patterns */
    private fun tryAlternativeRfc2822Parsing(dateString: String): Date {
        var date: Date = Keys.DEFAULT_DATE
        try {
            // try to parse without seconds
            date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm Z", Locale.ENGLISH).parse((dateString))
                ?: Keys.DEFAULT_DATE
        } catch (e: Exception) {
            try {
                Log.w(TAG, "Unable to parse. Trying an alternative Date format. $e")
                // try to parse without time zone
                date = SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss",
                    Locale.ENGLISH
                ).parse((dateString)) ?: Keys.DEFAULT_DATE
            } catch (e: Exception) {
                Log.e(TAG, "Unable to parse. Returning a default date. $e")
            }
        }
        return date
    }

}
