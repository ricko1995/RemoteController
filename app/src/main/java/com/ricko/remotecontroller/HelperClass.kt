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
}