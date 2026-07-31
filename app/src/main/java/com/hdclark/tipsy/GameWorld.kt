package com.hdclark.tipsy

import kotlin.math.max
import kotlin.random.Random
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.collision.WorldManifold
import org.jbox2d.collision.shapes.ChainShape
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.EdgeShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.contacts.Contact

data class BallRenderState(
    val id: Int,
    val color: Int,
    val radius: Float,
    val position: Vec2,
    val laps: Int
)

data class FloatingSignal(
    val text: String,
    val position: Vec2,
    val ttlSeconds: Float
)

data class RaceHistoryEntry(
    val epochMillis: Long,
    val podiumBallIds: List<Int>
)

data class WorldRenderState(
    val outerLoop: List<Vec2>,
    val innerLoop: List<Vec2>,
    val balls: List<BallRenderState>,
    val rocks: List<Pair<Vec2, Float>>,
    val bumps: List<Pair<Vec2, Float>>,
    val horseshoe: List<Vec2>,
    val signals: List<FloatingSignal>,
    val leader: Int,
    val podium: List<Int>,
    val raceOverCountdown: Float
)

class RaceState(
    private val trackLength: Float,
    private val targetLaps: Int = 3,
    private val podiumSize: Int = 3,
    private val ballCount: Int = 10
) {
    val lapCounts = IntArray(ballCount)
    private val lastProgress = FloatArray(ballCount)
    private val initialized = BooleanArray(ballCount)
    val podium = mutableListOf<Int>()
    private var leaderId = 0

    data class Update(
        val leaderChangedTo: Int?,
        val raceFinishedNow: Boolean
    )

    fun currentLeader(): Int = leaderId

    fun reset() {
        lapCounts.fill(0)
        lastProgress.fill(0f)
        initialized.fill(false)
        podium.clear()
        leaderId = 0
    }

    fun update(progresses: FloatArray): Update {
        for (i in 0 until ballCount) {
            val progress = progresses[i]
            if (!initialized[i]) {
                initialized[i] = true
                lastProgress[i] = progress
                continue
            }

            val prev = lastProgress[i]
            val forwardDelta = wrapDistance(prev, progress)
            val backwardDelta = wrapDistance(progress, prev)
            if (prev > trackLength * 0.82f && progress < trackLength * 0.18f && forwardDelta < backwardDelta) {
                lapCounts[i] += 1
                if (lapCounts[i] >= targetLaps && !podium.contains(i)) {
                    podium.add(i)
                }
            }

            lastProgress[i] = progress
        }

        val currentLeader = (0 until ballCount).maxBy {
            lapCounts[it] * trackLength + lastProgress[it]
        }
        val leaderChanged = if (currentLeader != leaderId) {
            leaderId = currentLeader
            currentLeader
        } else {
            null
        }

        return Update(
            leaderChangedTo = leaderChanged,
            raceFinishedNow = podium.size >= podiumSize
        )
    }

    private fun wrapDistance(from: Float, to: Float): Float {
        val raw = to - from
        return if (raw >= 0f) raw else raw + trackLength
    }
}

class GameWorld(
    widthMeters: Float,
    heightMeters: Float,
    private val seed: Int = 1337,
    private val onRaceFinished: (RaceHistoryEntry) -> Unit = {}
) {
    private data class BallEntity(
        val id: Int,
        val color: Int,
        val radius: Float,
        val body: Body
    )

    private val world = World(Vec2(0f, 0f))
    private val balls = mutableListOf<BallEntity>()

    private val centerLine = buildCenterLine(widthMeters, heightMeters)
    private val trackHalfWidths = FloatArray(centerLine.size) { i ->
        1.1f + if (i % 4 == 0) 0.18f else if (i % 5 == 0) -0.16f else 0f
    }
    private val outerLoop = offsetLoop(centerLine, trackHalfWidths, +1f)
    private val innerLoop = offsetLoop(centerLine, trackHalfWidths, -1f)
    private val cumulativeCenterLengths = cumulativeLengths(centerLine)
    private val trackLength = cumulativeCenterLengths.last()

    private val rocks = mutableListOf<Pair<Vec2, Float>>()
    private val bumps = mutableListOf<Pair<Vec2, Float>>()
    private val horseshoe = mutableListOf<Vec2>()

    private val activeSignals = mutableListOf<FloatingSignal>()
    private val raceState = RaceState(trackLength)
    private var raceOverCountdown = 0f
    private var raceCompleted = false
    private var signalCounter = 0

    private val colorPalette = listOf(
        0xFFEF5350.toInt(), 0xFFAB47BC.toInt(), 0xFF5C6BC0.toInt(), 0xFF29B6F6.toInt(),
        0xFF26A69A.toInt(), 0xFF9CCC65.toInt(), 0xFFFFCA28.toInt(), 0xFFFF7043.toInt(),
        0xFF8D6E63.toInt(), 0xFFEC407A.toInt()
    )

    init {
        buildTrackBoundaries()
        buildFeatures()
        spawnBalls()
        world.setContactListener(object : ContactListener {
            override fun beginContact(contact: Contact) = Unit
            override fun endContact(contact: Contact) = Unit
            override fun preSolve(contact: Contact, oldManifold: Manifold) = Unit

            override fun postSolve(contact: Contact, impulse: ContactImpulse) {
                val hardImpulse = impulse.normalImpulses.maxOrNull() ?: 0f
                if (hardImpulse < 1.8f) return
                val aId = contact.fixtureA.body.userData as? Int
                val bId = contact.fixtureB.body.userData as? Int
                if (aId == null && bId == null) return
                val manifold = WorldManifold()
                contact.getWorldManifold(manifold)
                val impactPoint = manifold.points[0]
                val textList = listOf("oof!", "ow!", "ouch!", "zounds!", "bang!", "ting!", "wut!", "dang!", "zoinks!", "toasty!")
                val text = textList[signalCounter++ % textList.size]
                activeSignals.add(FloatingSignal(text, impactPoint.clone(), 0.9f))
            }
        })
    }

    fun step(dtSeconds: Float, gravityX: Float, gravityY: Float) {
        world.gravity.set(gravityX, gravityY)
        val safeDt = dtSeconds.coerceIn(1f / 240f, 1f / 24f)
        val subStep = safeDt / 3f
        repeat(3) {
            world.step(subStep, 8, 3)
        }

        updateSignals(safeDt)

        val progresses = FloatArray(balls.size)
        for ((i, ball) in balls.withIndex()) {
            progresses[i] = projectDistanceOnCenter(ball.body.position)
        }

        if (!raceCompleted) {
            val update = raceState.update(progresses)
            update.leaderChangedTo?.let { leader ->
                val leaderPos = balls.firstOrNull { it.id == leader }?.body?.position ?: Vec2()
                activeSignals.add(FloatingSignal("1st!", leaderPos.clone(), 1.0f))
            }
            if (update.raceFinishedNow) {
                raceCompleted = true
                raceOverCountdown = 5f
                onRaceFinished(
                    RaceHistoryEntry(
                        epochMillis = System.currentTimeMillis(),
                        podiumBallIds = raceState.podium.toList()
                    )
                )
            }
        } else {
            raceOverCountdown -= safeDt
            if (raceOverCountdown <= 0f) {
                resetRace()
            }
        }
    }

    fun applyProdImpulse(worldX: Float, worldY: Float) {
        val touch = Vec2(worldX, worldY)
        for (ball in balls) {
            val delta = ball.body.position.sub(touch)
            val distance = max(0.001f, delta.length())
            if (distance < 1.8f) {
                val impulse = delta.mul(0.6f / distance)
                ball.body.applyLinearImpulse(impulse, ball.body.worldCenter)
            }
        }
    }

    fun renderState(): WorldRenderState {
        return WorldRenderState(
            outerLoop = outerLoop,
            innerLoop = innerLoop,
            balls = balls.map {
                BallRenderState(
                    id = it.id,
                    color = it.color,
                    radius = it.radius,
                    position = it.body.position.clone(),
                    laps = raceState.lapCounts[it.id]
                )
            },
            rocks = rocks,
            bumps = bumps,
            horseshoe = horseshoe,
            signals = activeSignals.toList(),
            leader = raceState.currentLeader(),
            podium = raceState.podium.toList(),
            raceOverCountdown = raceOverCountdown
        )
    }

    fun snapshotBallSpecs(): List<Triple<Int, Float, Vec2>> {
        return balls.map { Triple(it.color, it.radius, it.body.position.clone()) }
    }

    private fun updateSignals(dtSeconds: Float) {
        val iter = activeSignals.listIterator()
        while (iter.hasNext()) {
            val signal = iter.next()
            val nextTtl = signal.ttlSeconds - dtSeconds
            if (nextTtl <= 0f) {
                iter.remove()
            } else {
                iter.set(signal.copy(position = signal.position.add(Vec2(0f, -0.2f * dtSeconds)), ttlSeconds = nextTtl))
            }
        }
    }

    private fun resetRace() {
        raceCompleted = false
        raceOverCountdown = 0f
        raceState.reset()
        activeSignals.clear()
        spawnBalls(resetOnly = true)
    }

    private fun spawnBalls(resetOnly: Boolean = false) {
        if (resetOnly) {
            balls.forEach { world.destroyBody(it.body) }
            balls.clear()
        }
        val random = Random(seed)

        repeat(10) { i ->
            val radius = 0.25f + (i * 0.015f) + random.nextFloat() * 0.01f
            val startDistance = i * (trackLength / 10f)
            val start = pointAtDistance(startDistance)

            val bodyDef = BodyDef().apply {
                type = BodyType.DYNAMIC
                position.set(start)
                linearDamping = 0.08f
                angularDamping = 0.08f
                bullet = true
            }
            val body = world.createBody(bodyDef)

            val shape = CircleShape().apply { m_radius = radius }
            val fixtureDef = FixtureDef().apply {
                this.shape = shape
                density = 1.0f
                friction = 0.02f
                restitution = 0.92f
            }
            body.createFixture(fixtureDef)
            body.userData = i

            balls.add(
                BallEntity(
                    id = i,
                    color = colorPalette[i % colorPalette.size],
                    radius = radius,
                    body = body
                )
            )
        }
    }

    private fun buildTrackBoundaries() {
        addLoopBoundary(outerLoop)
        addLoopBoundary(innerLoop)
    }

    private fun addLoopBoundary(points: List<Vec2>) {
        val body = world.createBody(BodyDef().apply { type = BodyType.STATIC })
        val shape = ChainShape()
        val first = points.first()
        val last = points.last()
        val isClosedWithDuplicate = points.size > 2 &&
            kotlin.math.abs(first.x - last.x) < 0.0001f &&
            kotlin.math.abs(first.y - last.y) < 0.0001f
        val loopVertices = if (isClosedWithDuplicate) {
            points.dropLast(1)
        } else {
            points
        }
        val vertices = Array(loopVertices.size) { i -> loopVertices[i] }
        shape.createLoop(vertices, vertices.size)
        body.createFixture(
            FixtureDef().apply {
                this.shape = shape
                friction = 0.08f
                restitution = 0.75f
            }
        )
    }

    private fun buildFeatures() {
        val random = Random(seed)

        repeat(3) {
            val dist = random.nextFloat() * trackLength
            val center = pointAtDistance(dist)
            val tangent = tangentAtDistance(dist)
            val normal = Vec2(-tangent.y, tangent.x)
            val side = if (random.nextBoolean()) 1f else -1f
            val offset = 1.15f + random.nextFloat() * 0.4f
            val pos = center.add(normal.mul(side * offset))
            val radius = 0.28f + random.nextFloat() * 0.1f
            addStaticCircle(pos, radius)
            rocks.add(pos to radius)
        }

        repeat(6) { i ->
            val offset = if (i % 2 == 0) 0.65f else -0.65f
            val point = pointAtDistance(trackLength * (0.56f + i * 0.02f))
            val tangent = tangentAtDistance(trackLength * (0.56f + i * 0.02f))
            val normal = Vec2(-tangent.y, tangent.x)
            val bumpPos = point.add(normal.mul(offset))
            addStaticCircle(bumpPos, 0.18f)
            bumps.add(bumpPos to 0.18f)
        }

        val chuteA = pointAtDistance(trackLength * 0.82f)
        val chuteTangent = tangentAtDistance(trackLength * 0.82f)
        val chuteNormal = Vec2(-chuteTangent.y, chuteTangent.x)
        val chuteLeft = chuteA.add(chuteNormal.mul(0.85f))
        val chuteRight = chuteA.sub(chuteNormal.mul(0.85f))
        addStaticCircle(chuteLeft, 0.24f)
        addStaticCircle(chuteRight, 0.24f)

        val hsCenter = pointAtDistance(trackLength * 0.31f)
        val hsTan = tangentAtDistance(trackLength * 0.31f)
        val hsNorm = Vec2(-hsTan.y, hsTan.x)
        val side = if (random.nextBoolean()) 1.0f else -1.0f
        val p1 = hsCenter.add(hsNorm.mul(side * 1.0f)).sub(hsTan.mul(0.6f))
        val p2 = hsCenter.add(hsNorm.mul(side * 1.0f)).add(hsTan.mul(0.6f))
        val p3 = hsCenter.add(hsNorm.mul(side * 0.1f)).add(hsTan.mul(0.6f))
        val p4 = hsCenter.add(hsNorm.mul(side * 0.1f)).sub(hsTan.mul(0.6f))
        addStaticEdge(p1, p2)
        addStaticEdge(p2, p3)
        addStaticEdge(p3, p4)
        horseshoe.addAll(listOf(p1, p2, p3, p4))
    }

    private fun addRockAt(xFactor: Float, yFactor: Float, radius: Float) {
        val pos = Vec2(
            lerp(centerLine.minOf { it.x }, centerLine.maxOf { it.x }, xFactor),
            lerp(centerLine.minOf { it.y }, centerLine.maxOf { it.y }, yFactor)
        )
        addStaticCircle(pos, radius)
        rocks.add(pos to radius)
    }

    private fun addStaticCircle(center: Vec2, radius: Float) {
        val body = world.createBody(BodyDef().apply {
            type = BodyType.STATIC
            position.set(center)
        })
        val shape = CircleShape().apply { m_radius = radius }
        body.createFixture(
            FixtureDef().apply {
                this.shape = shape
                friction = 0.2f
                restitution = 0.35f
            }
        )
    }

    private fun addStaticEdge(a: Vec2, b: Vec2) {
        val body = world.createBody(BodyDef().apply { type = BodyType.STATIC })
        val shape = EdgeShape()
        shape.set(a, b)
        body.createFixture(
            FixtureDef().apply {
                this.shape = shape
                friction = 0.15f
                restitution = 0.4f
            }
        )
    }

    private fun pointAtDistance(distance: Float): Vec2 {
        val wrapped = ((distance % trackLength) + trackLength) % trackLength
        val lengths = cumulativeCenterLengths
        for (i in 0 until centerLine.lastIndex) {
            val segStart = lengths[i]
            val segEnd = lengths[i + 1]
            if (wrapped <= segEnd) {
                val t = if (segEnd == segStart) 0f else (wrapped - segStart) / (segEnd - segStart)
                return lerp(centerLine[i], centerLine[i + 1], t)
            }
        }
        return centerLine.last().clone()
    }

    private fun tangentAtDistance(distance: Float): Vec2 {
        val wrapped = ((distance % trackLength) + trackLength) % trackLength
        for (i in 0 until centerLine.lastIndex) {
            val segStart = cumulativeCenterLengths[i]
            val segEnd = cumulativeCenterLengths[i + 1]
            if (wrapped <= segEnd) {
                val seg = centerLine[i + 1].sub(centerLine[i])
                val len = max(0.0001f, seg.length())
                return seg.mul(1f / len)
            }
        }
        return Vec2(1f, 0f)
    }

    private fun projectDistanceOnCenter(point: Vec2): Float {
        var bestDistSq = Float.MAX_VALUE
        var bestDistance = 0f
        for (i in 0 until centerLine.lastIndex) {
            val a = centerLine[i]
            val b = centerLine[i + 1]
            val ab = b.sub(a)
            val abLenSq = max(0.0001f, ab.lengthSquared())
            val ap = point.sub(a)
            val t = (ap.x * ab.x + ap.y * ab.y) / abLenSq
            val clamped = t.coerceIn(0f, 1f)
            val projection = a.add(ab.mul(clamped))
            val dx = point.x - projection.x
            val dy = point.y - projection.y
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestDistance = cumulativeCenterLengths[i] + ab.length() * clamped
            }
        }
        return bestDistance
    }

    private fun buildCenterLine(width: Float, height: Float): List<Vec2> {
        val random = Random(seed)
        val numPoints = 15
        val points = mutableListOf<Vec2>()
        for (i in 0 until numPoints) {
            val angle = 2.0 * Math.PI * i / numPoints
            val radius = 0.28 + random.nextDouble() * 0.08
            val x = 0.5 + Math.cos(angle) * radius
            val y = 0.5 + Math.sin(angle) * radius
            points.add(Vec2((x * width).toFloat(), (y * height).toFloat()))
        }
        return points + points.first().clone()
    }

    private fun offsetLoop(base: List<Vec2>, halfWidths: FloatArray, side: Float): List<Vec2> {
        val offset = mutableListOf<Vec2>()
        for (i in 0 until base.lastIndex) {
            val prev = base[(i - 1 + base.lastIndex) % base.lastIndex]
            val current = base[i]
            val next = base[(i + 1) % base.lastIndex]

            val tangent = next.sub(prev)
            val tangentLen = max(0.0001f, tangent.length())
            val normal = Vec2(-tangent.y / tangentLen, tangent.x / tangentLen)
            val width = max(0.9f, halfWidths[i])
            offset.add(current.add(normal.mul(width * side)))
        }
        return offset + offset.first().clone()
    }

    private fun cumulativeLengths(points: List<Vec2>): FloatArray {
        val result = FloatArray(points.size)
        var total = 0f
        result[0] = 0f
        for (i in 0 until points.lastIndex) {
            total += points[i + 1].sub(points[i]).length()
            result[i + 1] = total
        }
        return result
    }

    private fun lerp(a: Vec2, b: Vec2, t: Float): Vec2 {
        return Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
