package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
@Keep
data class Bike(
    val name: String? = null,
    val dateOfPurchase: Long? = null,
    val weight: Float? = null,
    val wheelCircumference: Float? = null,
    val isDefault: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Long? = null
)
