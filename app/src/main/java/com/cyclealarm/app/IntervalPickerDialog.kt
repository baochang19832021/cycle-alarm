package com.cyclealarm.app

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class IntervalPickerDialog(
    context: Context,
    private val initialValue: Int,
    private val onConfirm: (Int) -> Unit
) : Dialog(context, R.style.IntervalPickerDialog) {

    private var selectedValue = initialValue.coerceIn(0, 365)
    private var isRendering = false
    private lateinit var hundredsWheel: WheelView
    private lateinit var tensWheel: WheelView
    private lateinit var onesWheel: WheelView
    private lateinit var selectionHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_interval_picker)

        findViewById<TextView>(R.id.btnCancel).setOnClickListener { dismiss() }
        findViewById<TextView>(R.id.btnConfirm).setOnClickListener {
            onConfirm(selectedValue)
            dismiss()
        }

        selectionHint = findViewById(R.id.tvSelectionHint)
        createReels(findViewById(R.id.reelContainer))
        renderValue(selectedValue)
    }

    private fun createReels(container: FrameLayout) {
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }

        hundredsWheel = createWheel().also { wheel ->
            wheel.onIndexChanged = { updateFromWheels() }
            row.addView(createReelFrame(wheel))
        }
        tensWheel = createWheel().also { wheel ->
            wheel.onIndexChanged = { updateFromWheels() }
            row.addView(createReelFrame(wheel))
        }
        onesWheel = createWheel().also { wheel ->
            wheel.onIndexChanged = { updateFromWheels() }
            row.addView(createReelFrame(wheel))
        }

        row.addView(TextView(context).apply {
            text = "天"
            textSize = 17f
            setTextColor(0xFF777777.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
        })

        container.addView(
            row,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    private fun createWheel(): WheelView = WheelView(context, 64, 3).apply {
        isCyclic = true
    }

    private fun createReelFrame(wheel: WheelView): FrameLayout = FrameLayout(context).apply {
        background = context.getDrawable(R.drawable.bg_interval_reel)
        layoutParams = LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }
        addView(
            wheel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    private fun updateFromWheels() {
        if (isRendering) return
        val hundreds = hundredsWheel.currentIndex
        val tens = tensWheel.items[tensWheel.currentIndex].toInt()
        val ones = onesWheel.items[onesWheel.currentIndex].toInt()
        renderValue((hundreds * 100 + tens * 10 + ones).coerceIn(0, 365))
    }

    private fun renderValue(value: Int) {
        selectedValue = value.coerceIn(0, 365)
        val hundreds = selectedValue / 100
        val tens = (selectedValue % 100) / 10
        val ones = selectedValue % 10
        val maxTens = if (hundreds == 3) 6 else 9
        val maxOnes = if (hundreds == 3 && tens == 6) 5 else 9

        isRendering = true
        setWheelDigits(hundredsWheel, 0..3, hundreds)
        setWheelDigits(tensWheel, 0..maxTens, tens)
        setWheelDigits(onesWheel, 0..maxOnes, ones)
        isRendering = false

        selectionHint.text = if (selectedValue == 0) {
            "000 天：当天一次提醒，响铃关闭后自动结束"
        } else {
            String.format("%03d 天：每 %d 天提醒一次", selectedValue, selectedValue)
        }
    }

    private fun setWheelDigits(wheel: WheelView, digits: IntRange, selectedDigit: Int) {
        val values = digits.map { it.toString() }
        wheel.items = values
        wheel.currentIndex = values.indexOf(selectedDigit.toString()).coerceAtLeast(0)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
