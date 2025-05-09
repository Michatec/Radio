/*
 * Collection.kt
 * Implements the Collection class
 * A Collection object holds a list of radio stations
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.michatec.radio.core

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.michatec.radio.Keys
import kotlinx.parcelize.Parcelize
import java.util.*


/*
 * Collection class
 */
@Keep
@Parcelize
data class Collection(
    @Expose val version: Int = Keys.CURRENT_COLLECTION_CLASS_VERSION_NUMBER,
    @Expose var stations: MutableList<Station> = mutableListOf(),
    @Expose var modificationDate: Date = Date()
) : Parcelable {

    /* overrides toString method */
    override fun toString(): String {
        val stringBuilder: StringBuilder = StringBuilder()
        stringBuilder.append("Format version: $version\n")
        stringBuilder.append("Number of stations in collection: ${stations.size}\n\n")
        stations.forEach {
            stringBuilder.append("$it\n")
        }
        return stringBuilder.toString()
    }


    /* Creates a deep copy of a Collection */
    fun deepCopy(): Collection {
        val stationsCopy: MutableList<Station> = mutableListOf()
        stations.forEach { stationsCopy.add(it.deepCopy()) }
        return Collection(
            version = version,
            stations = stationsCopy,
            modificationDate = modificationDate
        )
    }

}
