package com.cyclealarm.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.media.AudioManager
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.OverScroller
import kotlin.math.abs

class WheelView(
    context: Context,
    private val itemHeightDpVal: Int = 52,
    private val visibleCountVal: Int = 3
) : View(context) {

    var items: List<String> = emptyList()
        set(value) {
            field = value
            currentIndex = normalizeIndex(currentIndex)
            scrollOffset = 0f
            invalidate()
        }

    var currentIndex: Int = 0
        set(value) {
            field = normalizeIndex(value)
            invalidate()
        }

    var onIndexChanged: ((Int) -> Unit)? = null

    var isCyclic: Boolean = false
        set(value) {
            field = value
            currentIndex = currentIndex
        }

    var isTickSoundEnabled: Boolean = true

    private val density = resources.displayMetrics.density
    private val itemHeightPx = itemHeightDpVal * density
    private val totalVisibleHeight = itemHeightPx * visibleCountVal
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF202124.toInt()
        textSize = 36f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8D949C.toInt()
        textSize = 25f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE7E9EC.toInt()
        strokeWidth = density
    }

    private val scroller = OverScroller(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var settleAnimator: ValueAnimator? = null
    private var velocityTracker: VelocityTracker? = null
    private var scrollOffset = 0f
    private var lastTouchY = 0f
    private var lastScrollerY = 0
    private var moved = false
    private var lastTickSoundAt = 0L

    private val centerY: Float
        get() = totalVisibleHeight / 2f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = suggestedMinimumWidth
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        setMeasuredDimension(width, totalVisibleHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val width = width.toFloat()
        val centerTop = centerY - itemHeightPx / 2f
        val centerBottom = centerY + itemHeightPx / 2f
        canvas.drawLine(0f, centerTop, width, centerTop, dividerPaint)
        canvas.drawLine(0f, centerBottom, width, centerBottom, dividerPaint)

        for (relativeIndex in -3..3) {
            val rawItemIndex = currentIndex + relativeIndex
            if (!isCyclic && rawItemIndex !in items.indices) continue
            val itemIndex = normalizeIndex(rawItemIndex)

            val y = centerY + relativeIndex * itemHeightPx + scrollOffset
            if (y < -itemHeightPx || y > height + itemHeightPx) continue

            val distance = abs(y - centerY) / itemHeightPx
            val selected = distance < 0.45f
            val paint = if (selected) selectedPaint else normalPaint
            paint.alpha = if (selected) 255 else (180 - distance * 55).toInt().coerceIn(45, 180)

            val scale = if (selected) 1f else (1f - distance * 0.08f).coerceIn(0.82f, 0.96f)
            canvas.save()
            canvas.scale(scale, scale, width / 2f, y)
            canvas.drawText(items[itemIndex], width / 2f, y - (paint.ascent() + paint.descent()) / 2f, paint)
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopMotion()
                lastTouchY = event.y
                moved = false
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val delta = event.y - lastTouchY
                if (abs(delta) > 1f) moved = true
                lastTouchY = event.y
                moveBy(delta)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityY = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null
                parent?.requestDisallowInterceptTouchEvent(false)

                if (!moved && event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                    when {
                        event.y < centerY - itemHeightPx / 2f -> moveBy(itemHeightPx)
                        event.y > centerY + itemHeightPx / 2f -> moveBy(-itemHeightPx)
                    }
                }

                if (abs(velocityY) > 350f && moved) {
                    startFling(velocityY)
                } else {
                    settleToCenter()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun computeScroll() {
        if (!scroller.computeScrollOffset()) return

        val delta = (scroller.currY - lastScrollerY).toFloat()
        lastScrollerY = scroller.currY
        moveBy(delta)
        if (scroller.isFinished) {
            settleToCenter()
        } else {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopMotion()
        super.onDetachedFromWindow()
    }

    private fun startFling(velocityY: Float) {
        lastScrollerY = 0
        val maxDistance = Int.MAX_VALUE / 4
        scroller.fling(0, 0, 0, velocityY.toInt(), 0, 0, -maxDistance, maxDistance)
        postInvalidateOnAnimation()
    }

    private fun moveBy(delta: Float) {
        if (items.size < 2) return
        scrollOffset += delta

        while (scrollOffset >= itemHeightPx && (isCyclic || currentIndex > 0)) {
            scrollOffset -= itemHeightPx
            updateIndex(currentIndex - 1)
        }
        while (scrollOffset <= -itemHeightPx && (isCyclic || currentIndex < items.lastIndex)) {
            scrollOffset += itemHeightPx
            updateIndex(currentIndex + 1)
        }

        if (!isCyclic && currentIndex == 0 && scrollOffset > 0f) scrollOffset = 0f
        if (!isCyclic && currentIndex == items.lastIndex && scrollOffset < 0f) scrollOffset = 0f
        invalidate()
    }

    private fun settleToCenter() {
        if (items.isEmpty()) return
        if (scrollOffset > itemHeightPx / 2f && (isCyclic || currentIndex > 0)) {
            scrollOffset -= itemHeightPx
            updateIndex(currentIndex - 1)
        } else if (scrollOffset < -itemHeightPx / 2f && (isCyclic || currentIndex < items.lastIndex)) {
            scrollOffset += itemHeightPx
            updateIndex(currentIndex + 1)
        }

        settleAnimator?.cancel()
        settleAnimator = ValueAnimator.ofFloat(scrollOffset, 0f).apply {
            duration = 150L
            addUpdateListener {
                scrollOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun updateIndex(index: Int) {
        val normalizedIndex = normalizeIndex(index)
        if (normalizedIndex == currentIndex) return
        currentIndex = normalizedIndex
        playTickSound()
        onIndexChanged?.invoke(normalizedIndex)
    }

    private fun normalizeIndex(index: Int): Int {
        if (items.isEmpty()) return 0
        if (!isCyclic) return index.coerceIn(0, items.lastIndex)
        return ((index % items.size) + items.size) % items.size
    }

    private fun playTickSound() {
        val now = System.currentTimeMillis()
        if (!isTickSoundEnabled || now - lastTickSoundAt < 45L) return
        lastTickSoundAt = now
        audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.16f)
    }

    private fun stopMotion() {
        if (!scroller.isFinished) scroller.forceFinished(true)
        settleAnimator?.cancel()
        settleAnimator = null
    }
}
