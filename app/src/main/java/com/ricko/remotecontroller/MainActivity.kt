package com.ricko.remotecontroller

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.databinding.DataBindingUtil
import com.ricko.remotecontroller.HelperClass.mapValues
import com.ricko.remotecontroller.HelperClass.retrieveAddressFromSharedPref
import com.ricko.remotecontroller.HelperClass.retrievePortFromSharedPref
import com.ricko.remotecontroller.HelperClass.saveAddressPortToSharedPref
import com.ricko.remotecontroller.HelperClass.sharedPref
import com.ricko.remotecontroller.MainViewModel.Companion.STATUS_OPENED
import com.ricko.remotecontroller.databinding.ActivityMainBinding
import com.ricko.remotecontroller.databinding.JoystickSettingsDialogLayoutBinding
import com.ricko.remotecontroller.databinding.TouchpadSettingsDialogLayoutBinding
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.createBalloon
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    companion object {
        lateinit var activity: Activity
    }

    override fun onBackPressed() {
        super.onBackPressed()
        viewModel.stopDataTransfer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.apply {
            mainActivityModel = viewModel
            lifecycleOwner = this@MainActivity
        }
        initInterface()
        activity = this

        sharedPref = getSharedPreferences("sharedPref", Context.MODE_PRIVATE)

        setObservers()
        viewModel.webSocketAddress.value = retrieveAddressFromSharedPref()
        viewModel.webSocketPort.value = retrievePortFromSharedPref()
        initTouchPad()
        initJoystick()
    }

    private fun initInterface() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            supportActionBar?.hide()
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun initJoystick() {
        viewJoystick?.setOnMoveListener { _, _ ->
            postDataValuesToViewModel()
        }
    }


    private fun initTouchPad() {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        var firstTouchX = 0f
        var firstTouchY = 0f
        var previousTouchPadStickX = 0f
        var previousTouchPadStickY = 0f
        viewTouchPad?.setOnTouchListener { view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                firstTouchX = motionEvent.x
                firstTouchY = motionEvent.y
                previousTouchPadStickX = viewTouchPadStick!!.x
                previousTouchPadStickY = viewTouchPadStick!!.y
            }
            if (motionEvent.action == MotionEvent.ACTION_MOVE) {
                moveTouchPadStick(
                    firstTouchX,
                    firstTouchY,
                    previousTouchPadStickX,
                    previousTouchPadStickY,
                    motionEvent.x,
                    motionEvent.y
                )
                moveTouchPadLines()
                postDataValuesToViewModel()
            }
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                if (viewModel.toggleRecenterTouchPad.value!!) {
                    recenterTouchPadStick()
                }
            }
            view.performClick()
            return@setOnTouchListener true
        }
    }

    @Suppress("unused")
    fun View.openTouchPadSettingsDialog() {
        val dialog = Dialog(context)
        val dialogBinding =
            TouchpadSettingsDialogLayoutBinding.inflate(LayoutInflater.from(context))
        dialogBinding.apply {
            mainViewModel = viewModel
            lifecycleOwner = this@MainActivity
        }
        dialog.setContentView(dialogBinding.root)
        dialog.show()
    }

    @Suppress("unused")
    fun View.openJoystickSettingsDialog() {
        val dialog = Dialog(context)
        val dialogBinding =
            JoystickSettingsDialogLayoutBinding.inflate(LayoutInflater.from(context))
        dialogBinding.apply {
            mainViewModel = viewModel
            lifecycleOwner = this@MainActivity
        }
        dialog.setContentView(dialogBinding.root)
        dialog.show()
    }

    private fun moveTouchPadStick(
        userTouchDownX: Float,
        userTouchDownY: Float,
        previousStickX: Float,
        previousStickY: Float,
        currentStickX: Float,
        currentStickY: Float,
    ) {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        viewTouchPadStick!!.x =
            previousStickX + (currentStickX - userTouchDownX) * viewModel.touchPadHorizontalSensitivity.value!! / 50f
        if (viewTouchPadStick!!.x < viewTouchPad!!.x) {
            viewTouchPadStick!!.x = viewTouchPad!!.x
        } else if (viewTouchPadStick!!.x + viewTouchPadStick!!.width > viewTouchPad!!.x + viewTouchPad!!.width) {
            viewTouchPadStick!!.x =
                viewTouchPad!!.x + viewTouchPad!!.width - viewTouchPadStick!!.width
        }

        viewTouchPadStick!!.y =
            previousStickY + (currentStickY - userTouchDownY) * viewModel.touchPadVerticalSensitivity.value!! / 50f
        if (viewTouchPadStick!!.y < viewTouchPad!!.y) {
            viewTouchPadStick!!.y = viewTouchPad!!.y
        } else if (viewTouchPadStick!!.y + viewTouchPadStick!!.height > viewTouchPad!!.y + viewTouchPad!!.height) {
            viewTouchPadStick!!.y =
                viewTouchPad!!.y + viewTouchPad!!.height - viewTouchPadStick!!.height
        }
    }

    private fun moveTouchPadLines() {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        touchPadVerticalLine!!.x =
            viewTouchPadStick!!.x + viewTouchPadStick!!.width / 2 - touchPadVerticalLine!!.width
        touchPadHorizontalLine!!.y =
            viewTouchPadStick!!.y + viewTouchPadStick!!.height / 2 - touchPadHorizontalLine!!.height
    }

    private fun recenterTouchPadStick() {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        viewModel.toggleRecenterTouchPad.value?.let {
            if (it) {
                viewTouchPadStick!!.x =
                    viewTouchPad!!.x + viewTouchPad!!.width / 2 - viewTouchPadStick!!.width / 2
                viewTouchPadStick!!.y =
                    viewTouchPad!!.y + viewTouchPad!!.height / 2 - viewTouchPadStick!!.height / 2
                postDataValuesToViewModel()
                moveTouchPadLines()
            }
        }
    }

    private fun setObservers() {

        viewModel.toggleRecenterJoystick.observe(this, {
            viewJoystick?.isAutoReCenterButton = it
        })

        viewModel.touchPadVerticalSensitivity.observe(this, {
            if (viewModel.toggleJointSensitivity.value!!) {
                viewModel.touchPadHorizontalSensitivity.postValue(it)
            }
        })
        viewModel.touchPadHorizontalSensitivity.observe(this, {
            if (viewModel.toggleJointSensitivity.value!!) {
                viewModel.touchPadVerticalSensitivity.postValue(it)
            }
        })

        viewModel.toggleJointSensitivity.observe(this, {
            if (it) {
                viewModel.touchPadHorizontalSensitivity.value =
                    viewModel.touchPadVerticalSensitivity.value
            }
        })

        viewModel.toggleRecenterTouchPad.observe(this, {
            if (it) {
                recenterTouchPadStick()
            }
        })

        viewModel.status.observe(this, {
            if (it == STATUS_OPENED) saveAddressPortToSharedPref(
                viewModel.webSocketAddress.value,
                viewModel.webSocketPort.value
            )
        })

        viewModel.connectSocketError.observe(this, {
            if (!it.isNullOrEmpty()) {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.connectSocketError.postValue(null)
            }
        })
    }

    fun View.openCloseWebSocket() {
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                when (viewModel.status.value) {
                    STATUS_OPENED -> {
                        viewModel.stopDataTransfer()
                    }
                    else -> viewModel.initWebSocket()
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                when (viewModel.status.value) {
                    STATUS_OPENED -> {
                        viewModel.stopDataTransfer()
                    }
                    else -> {
                        viewModel.webSocketAddress.value = retrieveAddressFromSharedPref()
                        viewModel.webSocketPort.value = retrievePortFromSharedPref()
                        viewModel.initWebSocket()
                    }
                }
                postDataValuesToViewModel()
            }
        }

    }

    private fun postDataValuesToViewModel() {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        val touchPadStickX = (viewTouchPadStick!!.x + viewTouchPadStick!!.width / 2).toInt()
        val touchPadStickY = (viewTouchPadStick!!.y + viewTouchPadStick!!.height / 2).toInt()
        val joystickX = viewJoystick!!.normalizedX
        val joystickY = viewJoystick!!.normalizedY

        val mappedTouchPadStickX = mapValues(0,
            viewTouchPad!!.width,
            viewModel.mapTouchPadXFrom.value!!.toInt(),
            viewModel.mapTouchPadXTo.value!!.toInt(),
            touchPadStickX)
        val mappedTouchPadStickY = mapValues(0,
            viewTouchPad!!.height,
            viewModel.mapTouchPadYFrom.value!!.toInt(),
            viewModel.mapTouchPadYTo.value!!.toInt(),
            touchPadStickY)

        val mappedJoystickX = mapValues(0,
            100,
            viewModel.mapJoystickXFrom.value!!.toInt(),
            viewModel.mapJoystickXTo.value!!.toInt(),
            joystickX)
        val mappedJoystickY = mapValues(0,
            100,
            viewModel.mapJoystickYFrom.value!!.toInt(),
            viewModel.mapJoystickYTo.value!!.toInt(),
            joystickY)

        val currentData = DataModel(
            touchPadStickX = mappedTouchPadStickX,
            touchPadStickY = mappedTouchPadStickY,
            joystickX = mappedJoystickX,
            joystickY = mappedJoystickY
        )
        viewModel.currentData.postValue(currentData)
    }

    fun View.showMappingInfoBalloon(){
        val balloon = createBalloon(context) {
            setWidthRatio(.2f)
            setHeight(80)
            setCornerRadius(4f)
            setAlpha(0.9f)
            arrowVisible = false
            setPadding(8)
            setText("Usually from 0 to 180 for servo motors or ESCs.")
            setTextColorResource(R.color.design_default_color_on_primary)
            setTextIsHtml(true)
            setBackgroundColorResource(R.color.gray)
            setBalloonAnimation(BalloonAnimation.FADE)
            setLifecycleOwner(lifecycleOwner)
        }

        balloon.show(this)
    }


    fun View.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }
}