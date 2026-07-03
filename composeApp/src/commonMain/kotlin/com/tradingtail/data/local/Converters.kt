package com.tradingtail.data.local

import androidx.room.TypeConverter
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.bigDecimal

/** Room converters. Enums are handled natively by Room; only BigDecimal and List<Long> need help. */
class Converters {
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal): String = value.toString()

    @TypeConverter
    fun stringToBigDecimal(value: String): BigDecimal = bigDecimal(value)

    @TypeConverter
    fun longListToString(value: List<Long>): String = value.joinToString(",")

    @TypeConverter
    fun stringToLongList(value: String): List<Long> =
        if (value.isEmpty()) emptyList() else value.split(",").map { it.toLong() }
}
