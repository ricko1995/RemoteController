package com.ricko.remotecontroller

import android.graphics.Color
import android.widget.Toast
import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.ricko.remotecontroller.MainActivity.Companion.activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.*
import kotlin.Exception
import kotlin.collections.ArrayList

class MainViewModel : ViewModel(), Observable {

    companion object {
        const val STATUS_CLOSED = "STATUS_CLOSED"
        const val STATUS_OPENED = "STATUS_OPENED"
        const val STATUS_FAILURE = "STATUS_FAILURE"
        const val STATUS_CLOSING = "STATUS_CLOSING"

        const val DELAY_BETWEEN_MESSAGES = 30L

        const val TIME_DIFFERENCE = 1599387900000
    }

    private var webSocket: WebSocket? = null
    private var dataTransferJob: Job? = null

    private val sentMessages: ArrayList<DataModel> = ArrayList()

    @Bindable
    val webSocketAddress: MutableLiveData<String> = MutableLiveData()

    @Bindable
    val webSocketPort: MutableLiveData<String> = MutableLiveData()

    private val _status: MutableLiveData<String> = MutableLiveData(null)
    val status: LiveData<String> = _status

    val connectSocketError: MutableLiveData<String> = MutableLiveData()

    val currentData: MutableLiveData<DataModel> = MutableLiveData(DataModel())

    private val _ping: MutableLiveData<Long> = MutableLiveData(0)
    val ping: LiveData<Long> = _ping

    val statusColor: MutableLiveData<Int> = MutableLiveData(Color.GRAY)

    @Bindable
    val toggleRecenterTouchPad: MutableLiveData<Boolean> = MutableLiveData(false)

    @Bindable
    val toggleRecenterJoystick: MutableLiveData<Boolean> = MutableLiveData(true)

    @Bindable
    val touchPadHorizontalSensitivity: MutableLiveData<Int> = MutableLiveData(50)

    @Bindable
    val touchPadVerticalSensitivity: MutableLiveData<Int> = MutableLiveData(50)

    @Bindable
    val toggleJointSensitivity: MutableLiveData<Boolean> = MutableLiveData(true)

    @Bindable
    val mapTouchPadXFrom: MutableLiveData<String> = MutableLiveData("0")

    @Bindable
    val mapTouchPadXTo: MutableLiveData<String> = MutableLiveData("180")

    @Bindable
    val mapTouchPadYFrom: MutableLiveData<String> = MutableLiveData("0")

    @Bindable
    val mapTouchPadYTo: MutableLiveData<String> = MutableLiveData("180")

    @Bindable
    val mapJoystickXFrom: MutableLiveData<String> = MutableLiveData("0")

    @Bindable
    val mapJoystickXTo: MutableLiveData<String> = MutableLiveData("180")

    @Bindable
    val mapJoystickYFrom: MutableLiveData<String> = MutableLiveData("0")

    @Bindable
    val mapJoystickYTo: MutableLiveData<String> = MutableLiveData("180")


    private var myToast: Toast? = null

    private fun showToast(msg: String) {
        myToast?.cancel()
        myToast = Toast.makeText(activity, msg, Toast.LENGTH_SHORT)
        myToast?.show()
    }

    fun initWebSocket() {
        _status.observeForever {
            when (it) {
                STATUS_OPENED -> {
                    initiateDataTransfer()
                    showToast("Successful connection")
                    statusColor.value = Color.GREEN
                }
                STATUS_CLOSED -> {
                    showToast("Connection closed")
                    statusColor.value = Color.GRAY
                    _ping.postValue(0)
                    sentMessages.clear()
//                    stopDataTransfer()
//                    closeSocketConnection("User closed connection")
                }
                STATUS_FAILURE -> {
//                    showToast("Failed to connect")
                    statusColor.value = Color.RED
                    sentMessages.clear()
//                    stopDataTransfer()
//                    closeSocketConnection("Something went wrong")
                }
                STATUS_CLOSING -> {
                    statusColor.value = Color.YELLOW
                    sentMessages.clear()
                    showToast("Closing connection")
                }
            }
        }

        try {
            val url = "${webSocketAddress.value}:${webSocketPort.value}"
            val request = Request.Builder().url(url).build()
            webSocket = OkHttpClient().newWebSocket(request, object : WebSocketListener() {

                override fun onMessage(webSocket: WebSocket, text: String) {
                    super.onMessage(webSocket, text)
                    resendData(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    super.onClosed(webSocket, code, reason)
                    _status.postValue(STATUS_CLOSED)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    super.onFailure(webSocket, t, response)
                    _status.postValue(STATUS_FAILURE)
                    connectSocketError.postValue("${response?.message} failure error")
                }

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    super.onOpen(webSocket, response)
                    _status.postValue(STATUS_OPENED)
                }
            })
        } catch (e: Exception) {
            connectSocketError.postValue("${e.message} callback error")
        }
    }

    fun stopDataTransfer() {
        closeSocketConnection("User closed connection")
        dataTransferJob?.cancel()
        dataTransferJob = null
        currentData.postValue(DataModel())
        sentMessages.clear()
    }

    private fun initiateDataTransfer() {
        val dataToSend = Gson().toJson(currentData.value)
        webSocket?.send(dataToSend)
    }

    private fun resendData(msg: String) {
        if (_status.value != STATUS_OPENED) return
        extractDataFromMessage(msg)
        dataTransferJob = CoroutineScope(IO).launch {
            delay(DELAY_BETWEEN_MESSAGES)
            try {
                currentData.value?.apply {
                    time = System.currentTimeMillis() - TIME_DIFFERENCE
                    id = UUID.randomUUID().toString()
                    sentMessages.add(this)
                    webSocket?.send(Gson().toJson(this))
                }
            } catch (e: Exception) {
                connectSocketError.postValue("${e.message} sending error")
            }
        }
    }

    private fun extractDataFromMessage(msg: String) {
        var msgObject: DataModel? = null
        try {
            msgObject = Gson().fromJson(msg, DataModel::class.java)
        } catch (e: Exception) {
            connectSocketError.postValue("${e.message} Gson error")
        } finally {
            calculatePing(msgObject?.id, msgObject?.time)
            updateInterfaceWithNewData(msgObject)
        }
    }

    private fun calculatePing(id: String?, time: Long?) {
        id?.let { msgId ->
            time?.let { time ->
                val a = sentMessages.filter { it.id == msgId }
                if (a.isNotEmpty()) {
                    val pingLoc = (System.currentTimeMillis() - time) - TIME_DIFFERENCE
                    _ping.postValue(pingLoc)
                    sentMessages.removeAll(a)
                }
            }
        }

    }

    private fun updateInterfaceWithNewData(msgObject: DataModel?) {
        msgObject?.let {

        }
    }

    private fun closeSocketConnection(msg: String) {
        _status.postValue(STATUS_CLOSING)
        webSocket?.close(1000, msg)
    }

    override fun addOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {}

    override fun removeOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {}
}