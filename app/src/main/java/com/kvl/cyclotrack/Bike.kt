package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
@Keep
data class Bike(
    val name: String,
    val sensors: String? = null,
    val dataOfPurchase: Long,
    val weight: Float,
    val wheelCircumference: Float,
    @PrimaryKey(autoGenerate = true) val id: Long? = null
)
