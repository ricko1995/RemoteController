package com.ricko.remotecontroller

import android.content.SharedPreferences

object HelperClass {
    private const val WEBSOCKET_ADDRESS_KEY = "WEBSOCKET_ADDRESS_KEY"
    private const val WEBSOCKET_PORT_KEY = "WEBSOCKET_PORT_KEY"

    lateinit var sharedPref: SharedPreferences

    fun saveAddressPortToSharedPref(address: String?, port: String?) {
        sharedPref.edit()
            .putString(WEBSOCKET_ADDRESS_KEY, address)
            .putString(WEBSOCKET_PORT_KEY, port)
            .apply()
    }

    fun retrieveAddressFromSharedPref() = sharedPref.getString(WEBSOCKET_ADDRESS_KEY, "")
    fun retrievePortFromSharedPref() = sharedPref.getString(WEBSOCKET_PORT_KEY, "")

    @Suppress("UNCHECKED_CAST")
    fun <T> mapValues(xMinA: T, xMaxA: T, xMinB: T, xMaxB: T, xA: T): T {
        return (((xMaxB as Number) - (xMinB as Number)) * ((xA as Number) - (xMinA as Number)) / ((xMaxA as Number) - (xMinA as Number)) + (xMinB as Number)) as T
    }

}

private operator fun <T> T.plus(other: Number): Number {
    return when (this) {
        is Long -> this.toLong() + other.toLong()
        is Int -> this.toInt() + other.toInt()
        is Short -> this.toShort() + other.toShort()
        is Byte -> this.toByte() + other.toByte()
        is Double -> this.toDouble() + other.toDouble()
        is Float -> this.toFloat() + other.toFloat()
        else -> throw RuntimeException("Unknown numeric type")
    }
}

private operator fun <T> T.div(other: Number): Number {
    return when (this) {
        is Long -> this.toLong() / other.toLong()
        is Int -> this.toInt() / other.toInt()
        is Short -> this.toShort() / other.toShort()
        is Byte -> this.toByte() / other.toByte()
        is Double -> this.toDouble() / other.toDouble()
        is Float -> this.toFloat() / other.toFloat()
        else -> throw RuntimeException("Unknown numeric type")
    }
}

private operator fun <T> T.times(other: Number): Number {
    return when (this) {
        is Long -> this.toLong() * other.toLong()
        is Int -> this.toInt() * other.toInt()
        is Short -> this.toShort() * other.toShort()
        is Byte -> this.toByte() * other.toByte()
        is Double -> this.toDouble() * other.toDouble()
        is Float -> this.toFloat() * other.toFloat()
        else -> throw RuntimeException("Unknown numeric type")
    }
}

private operator fun <T> T.minus(other: Number): Number {
    return when (this) {
        is Long -> this.toLong() - other.toLong()
        is Int -> this.toInt() - other.toInt()
        is Short -> this.toShort() - other.toShort()
        is Byte -> this.toByte() - other.toByte()
        is Double -> this.toDouble() - other.toDouble()
        is Float -> this.toFloat() - other.toFloat()
        else -> throw RuntimeException("Unknown numeric type")
    }
}

