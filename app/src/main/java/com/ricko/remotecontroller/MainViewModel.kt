package com.ricko.remotecontroller

import android.graphics.Color
import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.*
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


    private val _isLoading: MutableLiveData<Boolean> = MutableLiveData(false)
    val isLoading: MutableLiveData<Boolean> = _isLoading

    private val initStatusObserver by lazy {
        _status.observeForever {
            when (it) {
                STATUS_OPENED -> {
                    initiateDataTransfer()
                    statusColor.value = Color.GREEN
                    _isLoading.postValue(false)
                }
                STATUS_CLOSED -> {
                    statusColor.value = Color.GRAY
                    _ping.postValue(0)
                    sentMessages.clear()
                    _isLoading.postValue(false)
                }
                STATUS_FAILURE -> {
                    statusColor.value = Color.RED
                    sentMessages.clear()
                    _isLoading.postValue(false)
                }
                STATUS_CLOSING -> {
                    statusColor.value = Color.YELLOW
                    sentMessages.clear()
                    _isLoading.postValue(false)
                }
            }
        }
    }

    fun initWebSocket() {
        if (_isLoading.value!!) return
        if (_status.value != STATUS_CLOSED || _status.value != null) _status.value = STATUS_CLOSED
        initStatusObserver
        _isLoading.postValue(true)

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
                    connectSocketError.postValue(t.message)
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
        isLoading.value = true
        _status.postValue(STATUS_CLOSING)
        webSocket?.close(1000, "User closed connection")
        dataTransferJob?.cancel()
        dataTransferJob = null
        currentData.postValue(DataModel())
        sentMessages.clear()
        isLoading.value = false
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
        val msgObject: DataModel?
        try {
            msgObject = Gson().fromJson(msg, DataModel::class.java)
            calculatePing(msgObject?.id, msgObject?.time)
            updateInterfaceWithNewData(msgObject)
        } catch (e: Exception) {
            connectSocketError.postValue("${e.message}\nGson error")
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


    override fun addOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {}

    override fun removeOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {}
}