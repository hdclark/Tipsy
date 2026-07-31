import re

content = open("app/src/main/java/com/hdclark/tipsy/TipsyGameView.kt", "r").read()

# 1. Replace the button vars
button_paints = '''    private var lastErrorToastText = ""

    private var lastFrameNanos = System.nanoTime()'''

new_button_paints = '''    private var lastErrorToastText = ""

    private var lastFrameNanos = System.nanoTime()

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 150, 80)
        style = Paint.Style.FILL
    }
    private val buttonBounds = android.graphics.RectF()'''
content = content.replace(button_paints, new_button_paints)

# 2. Draw button
old_hud_call = 'drawHud(canvas, state)\n            postInvalidateOnAnimation()'
new_hud_call = '''drawHud(canvas, state)

            // Draw reset button in center
            val cx = width / 2f
            val cy = height / 2f
            buttonBounds.set(cx - 80f, cy - 40f, cx + 80f, cy + 40f)
            canvas.drawRoundRect(buttonBounds, 16f, 16f, buttonPaint)
            textPaint.textSize = 28f
            val tw = textPaint.measureText("RESET")
            canvas.drawText("RESET", cx - tw / 2f, cy + 10f, textPaint)

            postInvalidateOnAnimation()'''
content = content.replace(old_hud_call, new_hud_call)

# 3. Touch event handling and reset function
old_touch = '''override fun onTouchEvent(event: MotionEvent): Boolean {
        val game = world ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                game.applyProdImpulse(event.x / pxPerMeter, event.y / pxPerMeter)
                return true
            }
        }
        return super.onTouchEvent(event)
    }'''

new_touch = '''override fun onTouchEvent(event: MotionEvent): Boolean {
        val game = world ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (buttonBounds.contains(event.x, event.y)) {
                    resetWorld(System.currentTimeMillis().toInt())
                    return true
                }
                game.applyProdImpulse(event.x / pxPerMeter, event.y / pxPerMeter)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!buttonBounds.contains(event.x, event.y)) {
                    game.applyProdImpulse(event.x / pxPerMeter, event.y / pxPerMeter)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resetWorld(seed: Int) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        pxPerMeter = min(w, h) / 12f
        runCatching {
            GameWorld(
                widthMeters = w / pxPerMeter,
                heightMeters = h / pxPerMeter,
                seed = seed
            ) { entry ->
                runCatching {
                    leaderboardStore.append(entry)
                    history = leaderboardStore.loadHistory()
                }.onFailure { reportError("Leaderboard save failed", it) }
            }
        }.onSuccess {
            world = it
            fatalErrorMessage = null
            invalidate()
        }.onFailure {
            world = null
            fatalErrorMessage = "Game failed to initialize. Please restart and report this issue."
            reportError("Game initialization failed", it)
            invalidate()
        }
    }'''
content = content.replace(old_touch, new_touch)

# 4. Modify onSizeChanged to use resetWorld
old_size = '''override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        pxPerMeter = min(w, h) / 12f
        runCatching {
            GameWorld(
                widthMeters = w / pxPerMeter,
                heightMeters = h / pxPerMeter,
                seed = 20260731
            ) { entry ->
                runCatching {
                    leaderboardStore.append(entry)
                    history = leaderboardStore.loadHistory()
                }.onFailure { reportError("Leaderboard save failed", it) }
            }
        }.onSuccess {
            world = it
            fatalErrorMessage = null
        }.onFailure {
            world = null
            fatalErrorMessage = "Game failed to initialize. Please restart and report this issue."
            reportError("Game initialization failed", it)
        }
    }'''

new_size = '''override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetWorld(System.currentTimeMillis().toInt())
    }'''
content = content.replace(old_size, new_size)

open("app/src/main/java/com/hdclark/tipsy/TipsyGameView.kt", "w").write(content)
