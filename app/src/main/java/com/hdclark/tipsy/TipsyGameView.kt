package com.hdclark.tipsy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import androidx.core.content.getSystemService
import kotlin.math.min

class TipsyGameView(context: Context) : View(context), SensorEventListener {
    private var world: GameWorld? = null
    private var pxPerMeter = 1f

    private val sensorManager: SensorManager? = context.getSystemService()
    private var gravityX = 0f
    private var gravityY = 0f

    private val leaderboardStore = LeaderboardStore(context)
    private var history: List<RaceHistoryEntry> = leaderboardStore.loadHistory()

    private var lastFrameNanos = System.nanoTime()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(36, 42, 52)
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val innerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 76, 90)
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
    }
    private val signalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        isFakeBoldText = true
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onDetachedFromWindow() {
        sensorManager?.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        pxPerMeter = min(w, h) / 12f
        world = GameWorld(
            widthMeters = w / pxPerMeter,
            heightMeters = h / pxPerMeter,
            seed = 20260731
        ) { entry ->
            leaderboardStore.append(entry)
            history = leaderboardStore.loadHistory()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(14, 17, 22))

        val game = world ?: return
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, 1f / 20f)
        lastFrameNanos = now

        game.step(dt, gravityX, gravityY)
        val state = game.renderState()

        drawLoop(canvas, state.outerLoop, trackPaint)
        drawLoop(canvas, state.innerLoop, innerTrackPaint)

        val hazardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(93, 102, 120)
            style = Paint.Style.FILL
        }
        for ((center, radius) in state.rocks) {
            canvas.drawCircle(toPxX(center.x), toPxY(center.y), radius * pxPerMeter, hazardPaint)
        }
        hazardPaint.color = Color.rgb(114, 130, 157)
        for ((center, radius) in state.bumps) {
            canvas.drawCircle(toPxX(center.x), toPxY(center.y), radius * pxPerMeter, hazardPaint)
        }

        if (state.horseshoe.size >= 2) {
            val horseshoePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(166, 122, 90)
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            val path = Path()
            val first = state.horseshoe.first()
            path.moveTo(toPxX(first.x), toPxY(first.y))
            state.horseshoe.drop(1).forEach { path.lineTo(toPxX(it.x), toPxY(it.y)) }
            canvas.drawPath(path, horseshoePaint)
        }

        state.balls.forEach {
            ballPaint.color = it.color
            canvas.drawCircle(toPxX(it.position.x), toPxY(it.position.y), it.radius * pxPerMeter, ballPaint)
            textPaint.textSize = 24f
            canvas.drawText("${it.id + 1}", toPxX(it.position.x) - 8f, toPxY(it.position.y) + 7f, textPaint)
        }

        state.signals.forEach {
            val alpha = (255f * (it.ttlSeconds / 1f).coerceIn(0f, 1f)).toInt()
            signalPaint.alpha = alpha
            canvas.drawText(it.text, toPxX(it.position.x), toPxY(it.position.y), signalPaint)
        }

        drawHud(canvas, state)

        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val game = world ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                game.applyProdImpulse(event.x / pxPerMeter, event.y / pxPerMeter)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val gx = -event.values[0]
        val gy = event.values[1]
        gravityX = gx * 0.38f
        gravityY = gy * 0.38f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun drawLoop(canvas: Canvas, points: List<org.jbox2d.common.Vec2>, paint: Paint) {
        if (points.size < 2) return
        val path = Path()
        path.moveTo(toPxX(points[0].x), toPxY(points[0].y))
        for (i in 1 until points.size) {
            path.lineTo(toPxX(points[i].x), toPxY(points[i].y))
        }
        canvas.drawPath(path, paint)
    }

    private fun drawHud(canvas: Canvas, state: WorldRenderState) {
        val leaderboardWidth = width * 0.56f
        canvas.drawRoundRect(14f, 14f, 14f + leaderboardWidth, 260f, 20f, 20f, overlayPaint)

        textPaint.textSize = 28f
        canvas.drawText("Tipsy Race • rotate + prod", 26f, 50f, textPaint)
        textPaint.textSize = 22f
        canvas.drawText("Leader: Ball ${state.leader + 1}", 26f, 84f, textPaint)
        canvas.drawText("Finish: first 3 balls to 3 laps", 26f, 114f, textPaint)

        if (history.isNotEmpty()) {
            canvas.drawText("Leaderboard", 26f, 146f, textPaint)
            history.takeLast(4).reversed().forEachIndexed { idx, entry ->
                val stamp = DateFormat.format("MM-dd kk:mm", entry.epochMillis)
                val podium = entry.podiumBallIds.joinToString("-") { "${it + 1}" }
                canvas.drawText("${idx + 1}. [$podium]  $stamp", 26f, 174f + idx * 24f, textPaint)
            }
        }

        if (state.raceOverCountdown > 0f) {
            val msg = "Race complete! Reset in ${"%.1f".format(state.raceOverCountdown)}s"
            textPaint.textSize = 34f
            canvas.drawText(msg, width * 0.09f, height * 0.5f, textPaint)
        }
    }

    private fun toPxX(worldX: Float): Float = worldX * pxPerMeter
    private fun toPxY(worldY: Float): Float = worldY * pxPerMeter
}
