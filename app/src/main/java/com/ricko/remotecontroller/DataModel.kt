package com.ricko.remotecontroller

import java.util.*

data class DataModel(
    val touchPadStickX: Float = 0f,
    val touchPadStickY: Float = 0f,
    var time: Long = 0L,
    val id: String = UUID.randomUUID().toString()
)