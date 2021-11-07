package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
@Keep
data class Bike(
    val name: String? = null,
    val sensors: String? = null,
    val dataOfPurchase: Long? = null,
    val weight: Float? = null,
    val wheelCircumference: Float? = null,
    @PrimaryKey(autoGenerate = true) val id: Long? = null
)
