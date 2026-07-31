import re

content = open("app/src/main/java/com/hdclark/tipsy/GameWorld.kt", "r").read()

# 1. Update textList
old_text = 'val text = if ((signalCounter++ % 2) == 0) "oof!" else "ow!"'
new_text = '''val textList = listOf("oof!", "ow!", "ouch!", "zounds!", "bang!", "ting!", "wut!", "dang!", "zoinks!", "toasty!")
                val text = textList[signalCounter++ % textList.size]'''
content = content.replace(old_text, new_text)

# 2. Update trackHalfWidths
old_widths = '''private val trackHalfWidths = FloatArray(centerLine.size) { i ->
        0.8f + if (i % 4 == 0) 0.18f else if (i % 5 == 0) -0.16f else 0f
    }'''
new_widths = '''private val trackHalfWidths = FloatArray(centerLine.size) { i ->
        1.1f + if (i % 4 == 0) 0.18f else if (i % 5 == 0) -0.16f else 0f
    }'''
content = content.replace(old_widths, new_widths)

# 3. Update offsetLoop width max
old_offset_loop = 'val width = max(0.55f, halfWidths[i])'
new_offset_loop = 'val width = max(0.9f, halfWidths[i])'
content = content.replace(old_offset_loop, new_offset_loop)

# 4. Update buildFeatures
old_features = '''private fun buildFeatures() {
        addRockAt(0.17f, 0.32f, 0.36f)
        addRockAt(0.66f, 0.28f, 0.34f)
        addRockAt(0.40f, 0.76f, 0.28f)

        repeat(6) { i ->
            val offset = if (i % 2 == 0) 0.55f else -0.55f
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
        val chuteLeft = chuteA.add(chuteNormal.mul(0.64f))
        val chuteRight = chuteA.sub(chuteNormal.mul(0.64f))
        addStaticCircle(chuteLeft, 0.24f)
        addStaticCircle(chuteRight, 0.24f)

        val hsCenter = pointAtDistance(trackLength * 0.31f)
        val hsTan = tangentAtDistance(trackLength * 0.31f)
        val hsNorm = Vec2(-hsTan.y, hsTan.x)
        val p1 = hsCenter.add(hsNorm.mul(0.7f)).sub(hsTan.mul(0.8f))
        val p2 = hsCenter.add(hsNorm.mul(0.7f)).add(hsTan.mul(0.8f))
        val p3 = hsCenter.sub(hsNorm.mul(0.7f)).add(hsTan.mul(0.8f))
        val p4 = hsCenter.sub(hsNorm.mul(0.7f)).sub(hsTan.mul(0.8f))
        addStaticEdge(p1, p2)
        addStaticEdge(p2, p3)
        addStaticEdge(p3, p4)
        horseshoe.addAll(listOf(p1, p2, p3, p4))
    }'''

new_features = '''private fun buildFeatures() {
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
    }'''
content = content.replace(old_features, new_features)

# 5. Update buildCenterLine
old_centerline = '''private fun buildCenterLine(width: Float, height: Float): List<Vec2> {
        val points = listOf(
            Vec2(0.10f, 0.24f), Vec2(0.26f, 0.12f), Vec2(0.47f, 0.20f), Vec2(0.66f, 0.11f),
            Vec2(0.86f, 0.21f), Vec2(0.89f, 0.40f), Vec2(0.78f, 0.52f), Vec2(0.93f, 0.74f),
            Vec2(0.74f, 0.88f), Vec2(0.56f, 0.76f), Vec2(0.38f, 0.90f), Vec2(0.22f, 0.78f),
            Vec2(0.09f, 0.64f), Vec2(0.18f, 0.50f), Vec2(0.06f, 0.34f)
        )
        val scaled = points.map { Vec2(it.x * width, it.y * height) }
        return scaled + scaled.first().clone()
    }'''

new_centerline = '''private fun buildCenterLine(width: Float, height: Float): List<Vec2> {
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
    }'''

content = content.replace(old_centerline, new_centerline)

open("app/src/main/java/com/hdclark/tipsy/GameWorld.kt", "w").write(content)
