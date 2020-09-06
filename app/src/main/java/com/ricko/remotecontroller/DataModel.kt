package com.ricko.remotecontroller

import java.util.*

data class DataModel(
    var id: String = UUID.randomUUID().toString(),
    var time: Long = 0L,
    val touchPadStickX: Int = 0,
    val touchPadStickY: Int = 0,
    val joystickX: Int = 0,
    val joystickY: Int = 0
)