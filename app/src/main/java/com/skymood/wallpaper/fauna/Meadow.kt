package com.skymood.wallpaper.fauna

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.skymood.domain.SceneSpec
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A strip of ground where flowers grow. Flowers are scattered from [top] (the ground line, by x
 * in pixels) down [band] pixels, nearer ones drawn larger.
 */
class FlowerBed(val x0: Float, val x1: Float, val top: GroundY, val band: Float, val count: Int)

/**
 * A snag or post standing in the water that a kingfisher fishes from. All values are pixels:
 * [x] and [baseY] where it enters the water, [height] above that, [waterY] the surface the bird
 * dives into, [scale] how big to draw the bird.
 */
class Perch(val x: Float, val baseY: Float, val height: Float, val waterY: Float, val scale: Float)

/** A fire or lamp that moths gather round after dark: its centre [x], [y] and how far they circle, all pixels. */
class Light(val x: Float, val y: Float, val radius: Float)

/**
 * What a landscape offers the meadow: places for flowers, a fishing perch near water (dragonflies patrol
 * there too, and gnats gather), and lights for moths.
 */
class MeadowSpec(val beds: List<FlowerBed>, val perch: Perch? = null, val lights: List<Light> = emptyList())

/**
 * Flowers, honey bees, butterflies and a kingfisher, chosen by the weather and the time of day.
 *
 * Flowers sway more in wind, open with the day and close at night and in heavy rain; none grow in
 * snow. Bees and butterflies only come out in calm, fair, warm weather: bees hover from bloom to
 * bloom and butterflies drift, settle on a flower and take off again. The kingfisher sits on its
 * perch, then flies out, hovers, plunges for a fish and returns, except in drizzle (it sits
 * hunched and waits) and at night, in fog, in wind and in storms (it stays away).
 *
 * Sprites are baked bitmaps and all state lives in pre-sized arrays, so drawing does not allocate.
 */
internal class Meadow {
    private var w = 0f
    private var h = 0f
    private var unit = 1f
    private var parallaxPx = 0f
    private var time = 0f
    private var windKmh = 0f
    private var tint = Color.WHITE
    private var lastSpec: MeadowSpec? = null
    private var lastKey = -1
    private val rnd = Random(System.nanoTime())
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- flowers, sorted back to front
    private var flowerCount = 0
    private val flX = FloatArray(MAX_FLOWERS); private val flY = FloatArray(MAX_FLOWERS)
    private val flSize = FloatArray(MAX_FLOWERS); private val flPh = FloatArray(MAX_FLOWERS)
    private val flSp = FloatArray(MAX_FLOWERS); private val flKind = IntArray(MAX_FLOWERS)
    private var openNow = 1f
    private var openTarget = 1f
    private var windy = false
    private val flowerBitmaps = arrayOfNulls<Bitmap>(MeadowSprites.FLOWER_KINDS * MeadowSprites.LEVELS)
    private var regX0 = 0f; private var regX1 = 0f; private var regTop = 0f; private var regBottom = 0f

    // ---- honey bees
    private var beeCount = 0
    private val beeX = FloatArray(MAX_BEE); private val beeY = FloatArray(MAX_BEE)
    private val beeVx = FloatArray(MAX_BEE); private val beeVy = FloatArray(MAX_BEE)
    private val beeDir = FloatArray(MAX_BEE); private val beeTimer = FloatArray(MAX_BEE)
    private val beeT = IntArray(MAX_BEE); private val beeAt = BooleanArray(MAX_BEE)

    // ---- butterflies
    private var butCount = 0
    private val bfX = FloatArray(MAX_BUT); private val bfY = FloatArray(MAX_BUT)
    private val bfHead = FloatArray(MAX_BUT); private val bfPh = FloatArray(MAX_BUT)
    private val bfVx = FloatArray(MAX_BUT); private val bfTimer = FloatArray(MAX_BUT)
    private val bfT = IntArray(MAX_BUT); private val bfState = IntArray(MAX_BUT)
    private val bfKind = IntArray(MAX_BUT)
    private val bfGlide = BooleanArray(MAX_BUT)

    // ---- dragonflies patrolling the water: they hover, then dart to a new spot
    private var dragonCount = 0
    private val dfX = FloatArray(MAX_DRAGON); private val dfY = FloatArray(MAX_DRAGON)
    private val dfVx = FloatArray(MAX_DRAGON); private val dfVy = FloatArray(MAX_DRAGON)
    private val dfTx = FloatArray(MAX_DRAGON); private val dfTy = FloatArray(MAX_DRAGON)
    private val dfT = FloatArray(MAX_DRAGON); private val dfDir = FloatArray(MAX_DRAGON)
    private val dfDart = BooleanArray(MAX_DRAGON); private val dfKind = IntArray(MAX_DRAGON)
    private var pX0 = 0f; private var pX1 = 0f; private var pY0 = 0f; private var pY1 = 0f

    // ---- a swarm of gnats at dusk and dawn
    private var gnatsOn = false
    private var swX = 0f; private var swY = 0f
    private val gnA = FloatArray(MAX_GNAT); private val gnB = FloatArray(MAX_GNAT); private val gnP = FloatArray(MAX_GNAT)

    // ---- moths circling a light
    private var lights: List<Light> = emptyList()
    private var mothCount = 0
    private val mtLight = IntArray(MAX_MOTH); private val mtTh = FloatArray(MAX_MOTH)
    private val mtW = FloatArray(MAX_MOTH); private val mtR = FloatArray(MAX_MOTH)
    private val mtRt = FloatArray(MAX_MOTH); private val mtT = FloatArray(MAX_MOTH)
    private val mtX = FloatArray(MAX_MOTH); private val mtY = FloatArray(MAX_MOTH)
    private val mtDir = FloatArray(MAX_MOTH)

    // ---- kingfisher
    private var perch: Perch? = null
    private var kingOn = false
    private var kingDives = false
    private var kState = K_PERCHED
    private var kT = 0f; private var kDur = 0f
    private var kx = 0f; private var ky = 0f; private var kdir = 1f
    private var kvx = 0f; private var kvy = 0f
    private var kHx = 0f; private var kHy = 0f; private var kSx = 0f
    private var kFish = false
    private var kSplash = -1f
    private val kingBitmaps = arrayOfNulls<Bitmap>(5)

    fun resize(width: Int, height: Int) {
        if (width.toFloat() == w && height.toFloat() == h) return
        w = width.toFloat(); h = height.toFloat(); unit = w / 1080f
        for (i in flowerBitmaps.indices) { flowerBitmaps[i]?.recycle(); flowerBitmaps[i] = null }
        for (i in kingBitmaps.indices) { kingBitmaps[i]?.recycle(); kingBitmaps[i] = null }
        lastKey = -1
        lastSpec = null
    }

    fun setParallax(offset: Float) {
        parallaxPx = -offset * w * 0.04f
    }

    fun configure(spec: SceneSpec, mead: MeadowSpec?, tintColor: Int) {
        if (w <= 0f) return
        tint = tintColor
        spritePaint.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.MULTIPLY)
        windKmh = spec.windKmh
        val warm = spec.temperatureC == null || spec.temperatureC >= MIN_INSECT_C
        val key = (((spec.landscape.ordinal * 8 + spec.timeOfDay.ordinal) * 16 + spec.type.ordinal) * 2 +
            (if (spec.wildlife) 1 else 0)) * 2 + (if (warm) 1 else 0)
        if (key == lastKey && mead === lastSpec) return
        lastKey = key
        lastSpec = mead
        populate(spec, mead, warm)
    }

    private fun populate(spec: SceneSpec, mead: MeadowSpec?, warm: Boolean) {
        flowerCount = 0; beeCount = 0; butCount = 0
        dragonCount = 0; gnatsOn = false; mothCount = 0; lights = emptyList()
        kingOn = false; perch = null
        if (!spec.wildlife || mead == null) return
        val wx = spec.type
        val tod = spec.timeOfDay
        val night = tod == TimeOfDay.NIGHT
        val fair = wx == SceneType.CLEAR || wx == SceneType.PARTLY_CLOUDY || wx == SceneType.HAZE
        val calm = spec.windKmh < CALM_KMH && wx != SceneType.WINDY

        // Flowers open with the sun and fold up at night and in heavy rain.
        openTarget = when {
            night -> 0f
            wx == SceneType.HEAVY_RAIN || wx == SceneType.STORM -> 0f
            tod == TimeOfDay.DAWN || tod == TimeOfDay.DUSK -> 0.55f
            wx == SceneType.DRIZZLE -> 0.4f
            wx == SceneType.OVERCAST || wx == SceneType.FOG -> 0.75f
            else -> 1f
        }
        openNow = openTarget
        windy = wx == SceneType.WINDY || wx == SceneType.STORM
        if (wx != SceneType.SNOW) placeFlowers(mead)

        if (flowerCount > 0 && fair && warm && calm) {
            when (tod) {
                TimeOfDay.DAY -> { beeCount = 3; butCount = 3 }
                TimeOfDay.DAWN -> { beeCount = 1; butCount = 1 }
                TimeOfDay.DUSK -> butCount = 1
                TimeOfDay.NIGHT -> {}
            }
            for (i in 0 until beeCount) startBee(i)
            for (i in 0 until butCount) startButterfly(i)
        }

        val dry = fair || wx == SceneType.OVERCAST
        val pc = mead.perch
        // Dragonflies hunt over the water by day in warm, calm weather.
        if (pc != null && warm && dry && calm && !night) {
            dragonCount = if (tod == TimeOfDay.DAY) 2 else 1
            pX0 = (pc.x - 0.35f * w).coerceAtLeast(0.04f * w); pX1 = (pc.x + 0.35f * w).coerceAtMost(0.96f * w)
            pY0 = pc.waterY - 0.1f * h; pY1 = pc.waterY - 0.02f * h
            for (i in 0 until dragonCount) startDragon(i)
        }
        // Gnats dance in a loose cloud at dusk and dawn, over the water or the flowers.
        if (warm && dry && calm && (tod == TimeOfDay.DAWN || tod == TimeOfDay.DUSK) && (flowerCount > 0 || pc != null)) {
            gnatsOn = true
            if (pc != null) {
                swX = pc.x + (if (pc.x > w * 0.5f) -1f else 1f) * 0.18f * w; swY = pc.waterY - 0.06f * h
            } else {
                swX = (regX0 + regX1) / 2f; swY = regTop + (regBottom - regTop) * 0.15f
            }
            for (i in 0 until MAX_GNAT) { gnA[i] = 2.5f + rnd.nextFloat() * 3f; gnB[i] = 3f + rnd.nextFloat() * 3f; gnP[i] = rnd.nextFloat() * 6.28f }
        }
        // Moths are drawn to fires and lamps once it gets dark.
        if ((night || tod == TimeOfDay.DUSK) && warm && dry && calm && mead.lights.isNotEmpty()) {
            lights = mead.lights
            mothCount = min(MAX_MOTH, 2 * lights.size)
            for (i in 0 until mothCount) startMoth(i)
        }
        val kingWeather = fair || wx == SceneType.OVERCAST || wx == SceneType.DRIZZLE
        if (pc != null && !night && kingWeather && calm) {
            perch = pc
            kingOn = true
            kingDives = wx != SceneType.DRIZZLE
            kState = K_PERCHED
            kT = 1f + rnd.nextFloat() * 3f
            kSplash = -1f
            kx = pc.x; ky = pc.baseY - pc.height
            kdir = if (pc.x > w * 0.5f) -1f else 1f
        }
    }

    private fun placeFlowers(mead: MeadowSpec) {
        var n = 0
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (bed in mead.beds) {
            for (i in 0 until bed.count) {
                if (n >= MAX_FLOWERS) break
                val x = (bed.x0 + rnd.nextFloat() * (bed.x1 - bed.x0)) * w
                val top = bed.top.at(x)
                val depth = rnd.nextFloat()
                val y = top + depth * bed.band
                flX[n] = x; flY[n] = y
                flSize[n] = h * (0.04f + 0.026f * depth) * (0.85f + rnd.nextFloat() * 0.3f)
                flPh[n] = rnd.nextFloat() * 6.28f
                flSp[n] = 1.1f + rnd.nextFloat() * 0.9f
                flKind[n] = rnd.nextInt(MeadowSprites.FLOWER_KINDS)
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y - flSize[n]); maxY = max(maxY, y)
                n++
            }
        }
        flowerCount = n
        // Insertion sort by y so nearer flowers overlap farther ones.
        for (i in 1 until n) {
            var j = i
            while (j > 0 && flY[j - 1] > flY[j]) { swapFlowers(j - 1, j); j-- }
        }
        if (n > 0) {
            regX0 = minX; regX1 = maxX
            regTop = minY - h * 0.1f; regBottom = maxY - h * 0.01f
        }
    }

    private fun swapFlowers(a: Int, b: Int) {
        var t = flX[a]; flX[a] = flX[b]; flX[b] = t
        t = flY[a]; flY[a] = flY[b]; flY[b] = t
        t = flSize[a]; flSize[a] = flSize[b]; flSize[b] = t
        t = flPh[a]; flPh[a] = flPh[b]; flPh[b] = t
        t = flSp[a]; flSp[a] = flSp[b]; flSp[b] = t
        val k = flKind[a]; flKind[a] = flKind[b]; flKind[b] = k
    }

    private fun swayDeg(i: Int): Float {
        val wind = windKmh.coerceAtMost(60f)
        val amp = 2.2f + wind * 0.2f + (if (windy) 3f else 0f)
        return sin(time * flSp[i] + flPh[i]) * amp + wind * 0.08f
    }

    private fun headX(i: Int) = flX[i] + sin(Math.toRadians(swayDeg(i).toDouble())).toFloat() * flSize[i] * 0.73f
    private fun headY(i: Int) = flY[i] - cos(Math.toRadians(swayDeg(i).toDouble())).toFloat() * flSize[i] * 0.73f

    private fun startBee(i: Int) {
        val t = rnd.nextInt(flowerCount)
        beeT[i] = t
        beeX[i] = headX(t) + (rnd.nextFloat() - 0.5f) * w * 0.3f
        beeY[i] = headY(t) - h * 0.04f
        beeVx[i] = 0f; beeVy[i] = 0f; beeDir[i] = 1f
        beeAt[i] = false; beeTimer[i] = 0f
    }

    private fun startButterfly(i: Int) {
        bfX[i] = regX0 + rnd.nextFloat() * (regX1 - regX0)
        bfY[i] = regTop + rnd.nextFloat() * (regBottom - regTop)
        bfHead[i] = rnd.nextFloat() * 6.28f
        bfPh[i] = rnd.nextFloat() * 6.28f
        bfState[i] = 0
        bfTimer[i] = 3f + rnd.nextFloat() * 6f
        bfKind[i] = rnd.nextInt(BUTTERFLY_COLORS.size)
        bfVx[i] = 0f
        bfGlide[i] = false
    }

    private fun startDragon(i: Int) {
        dfX[i] = pX0 + rnd.nextFloat() * (pX1 - pX0); dfY[i] = pY0 + rnd.nextFloat() * (pY1 - pY0)
        dfVx[i] = 0f; dfVy[i] = 0f
        dfDart[i] = false; dfT[i] = 0.3f + rnd.nextFloat() * 1.5f
        dfDir[i] = if (rnd.nextBoolean()) 1f else -1f
        dfKind[i] = rnd.nextInt(DRAGON_COLORS.size)
    }

    private fun startMoth(i: Int) {
        val l = lights[i % lights.size]
        mtLight[i] = i % lights.size
        mtTh[i] = rnd.nextFloat() * 6.28f
        mtW[i] = (if (rnd.nextBoolean()) 1f else -1f) * (2.5f + rnd.nextFloat() * 3f)
        mtR[i] = l.radius * (0.4f + rnd.nextFloat() * 0.8f); mtRt[i] = mtR[i]
        mtT[i] = 0.4f + rnd.nextFloat() * 1.2f
        mtX[i] = l.x + cos(mtTh[i]) * mtR[i]; mtY[i] = l.y + sin(mtTh[i]) * mtR[i] * 0.75f
        mtDir[i] = 1f
    }

    // ---------------------------------------------------------------- update

    fun update(dt: Float) {
        if (w <= 0f) return
        time += dt
        openNow += (openTarget - openNow).coerceIn(-dt * 0.3f, dt * 0.3f)
        if (flowerCount > 0) {
            for (i in 0 until beeCount) updateBee(i, dt)
            for (i in 0 until butCount) updateButterfly(i, dt)
        }
        for (i in 0 until dragonCount) updateDragon(i, dt)
        for (i in 0 until mothCount) updateMoth(i, dt)
        if (kingOn) updateKingfisher(dt)
        if (kSplash >= 0f) { kSplash += dt; if (kSplash > 0.9f) kSplash = -1f }
    }

    private fun updateBee(i: Int, dt: Float) {
        val t = beeT[i]
        val hx = headX(t); val hy = headY(t) - 6f * unit
        var tx = hx; var ty = hy
        if (beeAt[i]) {
            tx += cos(time * 3.1f + i * 1.7f) * 5f * unit
            ty += sin(time * 4.3f + i) * 3f * unit
        }
        val dx = tx - beeX[i]; val dy = ty - beeY[i]
        val dist = sqrt(dx * dx + dy * dy)
        val sp = min(dist * 5f, 110f * unit)
        val dvx = if (dist > 0.01f) dx / dist * sp else 0f
        var dvy = if (dist > 0.01f) dy / dist * sp else 0f
        if (!beeAt[i]) dvy += sin(time * 9f + i * 2f) * 18f * unit
        val k = min(1f, 6f * dt)
        beeVx[i] += (dvx - beeVx[i]) * k
        beeVy[i] += (dvy - beeVy[i]) * k
        beeX[i] += beeVx[i] * dt
        beeY[i] += beeVy[i] * dt
        if (beeAt[i]) {
            beeDir[i] = if (hx >= beeX[i]) 1f else -1f
            beeTimer[i] -= dt
            if (beeTimer[i] <= 0f) {
                beeAt[i] = false
                var next = rnd.nextInt(flowerCount)
                if (next == t && flowerCount > 1) next = (next + 1) % flowerCount
                beeT[i] = next
            }
        } else {
            if (abs(beeVx[i]) > 8f * unit) beeDir[i] = if (beeVx[i] > 0f) 1f else -1f
            if (dist < 9f * unit) { beeAt[i] = true; beeTimer[i] = 1.6f + rnd.nextFloat() * 2.4f }
        }
    }

    private fun updateButterfly(i: Int, dt: Float) {
        when (bfState[i]) {
            0 -> {
                // Flap-flap-glide: now and then the wings are held open in a V and the butterfly sinks a little.
                val gliding = sin(time * 1.9f + i * 2.3f) > 0.55f
                bfGlide[i] = gliding
                if (!gliding) bfPh[i] += dt * 2f * PI.toFloat() * 5.5f
                bfHead[i] += (sin(time * 1.1f + i * 2.3f) * 1.8f + sin(time * 2.7f + i) * 1.1f) * dt
                val sp = (38f + 14f * sin(time * 3f + i)) * unit
                val vx = cos(bfHead[i]) * sp
                val vy = sin(bfHead[i]) * sp * 0.6f - (if (gliding) -8f * unit else sin(bfPh[i]) * 14f * unit)
                bfX[i] += vx * dt; bfY[i] += vy * dt
                bfVx[i] = vx
                if (bfX[i] < regX0 || bfX[i] > regX1 || bfY[i] < regTop || bfY[i] > regBottom) {
                    val desired = atan2((regTop + regBottom) / 2f - bfY[i], (regX0 + regX1) / 2f - bfX[i])
                    bfHead[i] += angleDiff(bfHead[i], desired) * min(1f, 2.5f * dt)
                }
                bfTimer[i] -= dt
                if (bfTimer[i] <= 0f) { bfState[i] = 1; bfT[i] = rnd.nextInt(flowerCount); bfTimer[i] = 7f }
            }
            1 -> {
                bfGlide[i] = false
                bfPh[i] += dt * 2f * PI.toFloat() * 6f
                val dx = headX(bfT[i]) - bfX[i]; val dy = headY(bfT[i]) - 3f * unit - bfY[i]
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                val sp = min(50f * unit, dist * 3f + 12f * unit)
                bfVx[i] = dx / dist * sp
                bfX[i] += bfVx[i] * dt
                bfY[i] += dy / dist * sp * dt - sin(bfPh[i]) * 8f * unit * dt
                bfHead[i] = atan2(dy, dx)
                bfTimer[i] -= dt
                if (dist < 6f * unit) { bfState[i] = 2; bfTimer[i] = 2.5f + rnd.nextFloat() * 3f }
                else if (bfTimer[i] <= 0f) { bfState[i] = 0; bfTimer[i] = 4f + rnd.nextFloat() * 4f }
            }
            else -> {
                bfGlide[i] = false
                bfPh[i] += dt * 2.2f
                bfX[i] = headX(bfT[i]); bfY[i] = headY(bfT[i]) - 3f * unit
                bfVx[i] = 0f
                bfTimer[i] -= dt
                if (bfTimer[i] <= 0f) { bfState[i] = 0; bfTimer[i] = 5f + rnd.nextFloat() * 7f; bfHead[i] = -1.2f }
            }
        }
    }

    /** A dragonfly hovers on the spot, then darts to a new place over the water and stops dead. */
    private fun updateDragon(i: Int, dt: Float) {
        dfT[i] -= dt
        if (dfDart[i]) {
            val dx = dfTx[i] - dfX[i]; val dy = dfTy[i] - dfY[i]
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val sp = min(270f * unit, dist * 7f + 30f * unit)
            val k = min(1f, 9f * dt)
            dfVx[i] += (dx / dist * sp - dfVx[i]) * k
            dfVy[i] += (dy / dist * sp - dfVy[i]) * k
            dfX[i] += dfVx[i] * dt; dfY[i] += dfVy[i] * dt
            if (abs(dfVx[i]) > 12f * unit) dfDir[i] = if (dfVx[i] > 0f) 1f else -1f
            if (dist < 7f * unit) { dfDart[i] = false; dfT[i] = 0.5f + rnd.nextFloat() * 1.7f }
        } else {
            val k = min(1f, 8f * dt)
            dfVx[i] -= dfVx[i] * k; dfVy[i] -= dfVy[i] * k
            dfX[i] += dfVx[i] * dt + sin(time * 17f + i * 2f) * 14f * unit * dt
            dfY[i] += dfVy[i] * dt + sin(time * 13f + i) * 12f * unit * dt
            if (dfT[i] <= 0f) {
                // Choose somewhere at least a fifth of the screen away, so each dart is worth the trip.
                var tx = pX0 + rnd.nextFloat() * (pX1 - pX0)
                if (abs(tx - dfX[i]) < 0.2f * w) tx = if (dfX[i] < (pX0 + pX1) / 2f) pX1 - rnd.nextFloat() * 0.1f * w else pX0 + rnd.nextFloat() * 0.1f * w
                dfTx[i] = tx; dfTy[i] = pY0 + rnd.nextFloat() * (pY1 - pY0)
                dfDart[i] = true
            }
        }
    }

    /** A moth circles its light in loops that wander in and out, reversing now and then. */
    private fun updateMoth(i: Int, dt: Float) {
        val l = lights[mtLight[i]]
        mtT[i] -= dt
        if (mtT[i] <= 0f) {
            mtT[i] = 0.3f + rnd.nextFloat() * 1.3f
            mtW[i] = (if (rnd.nextBoolean()) 1f else -1f) * (2f + rnd.nextFloat() * 4.5f)
            mtRt[i] = l.radius * (0.25f + rnd.nextFloat() * 1.15f)
        }
        mtR[i] += (mtRt[i] - mtR[i]) * min(1f, 3f * dt)
        mtTh[i] += mtW[i] * dt
        val nx = l.x + cos(mtTh[i]) * mtR[i]
        val ny = l.y + sin(mtTh[i]) * mtR[i] * 0.75f + sin(time * 11f + i * 2f) * 3f * unit
        if (abs(nx - mtX[i]) > 0.01f) mtDir[i] = if (nx > mtX[i]) 1f else -1f
        mtX[i] = nx; mtY[i] = ny
    }

    private fun angleDiff(from: Float, to: Float): Float {
        var d = to - from
        while (d > PI) d -= 2f * PI.toFloat()
        while (d < -PI) d += 2f * PI.toFloat()
        return d
    }

    private fun smooth(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun updateKingfisher(dt: Float) {
        val pc = perch ?: return
        val px = pc.x; val py = pc.baseY - pc.height
        val ox = kx; val oy = ky
        when (kState) {
            K_PERCHED -> {
                kx = px; ky = py; kvx = 0f; kvy = 0f
                kT -= dt
                if (kT <= 0f && kingDives) {
                    val side = if (px > w * 0.5f) -1f else 1f
                    kSx = px + side * w * (0.12f + 0.12f * rnd.nextFloat())
                    kHx = kSx
                    kHy = pc.waterY - h * 0.055f * pc.scale
                    kdir = side
                    kState = K_OUT; kT = 0f; kDur = 0.9f
                } else if (kT <= 0f) kT = 2f + rnd.nextFloat() * 3f
            }
            K_OUT -> {
                kT += dt
                val u = kT / kDur
                val e = smooth(u)
                kx = px + (kHx - px) * e
                ky = py + (kHy - py) * e - sin(PI.toFloat() * u.coerceIn(0f, 1f)) * h * 0.012f
                if (u >= 1f) { kState = K_HOVER; kT = 0f; kDur = 0.7f + rnd.nextFloat() * 0.7f }
            }
            K_HOVER -> {
                kT += dt
                kx = kHx + sin(time * 9f) * 1.5f * unit
                ky = kHy + sin(time * 6f) * 2f * unit
                if (kT >= kDur) { kState = K_PLUNGE; kT = 0f; kDur = 0.22f }
            }
            K_PLUNGE -> {
                kT += dt
                val u = (kT / kDur).coerceIn(0f, 1f)
                kx = kHx; ky = kHy + (pc.waterY - kHy) * u * u
                if (kT >= kDur) {
                    kState = K_UNDER; kT = 0f; kDur = 0.45f
                    kSplash = 0f
                    kFish = rnd.nextFloat() < 0.6f
                }
            }
            K_UNDER -> {
                kT += dt
                kx = kSx; ky = pc.waterY
                if (kT >= kDur) {
                    kState = K_BACK; kT = 0f; kDur = 1.1f
                    kdir = if (px >= kSx) 1f else -1f
                }
            }
            K_BACK -> {
                kT += dt
                val u = (kT / kDur).coerceIn(0f, 1f)
                val e = smooth(u)
                kx = kSx + (px - kSx) * e
                ky = pc.waterY + (py - pc.waterY) * e - sin(PI.toFloat() * u) * h * 0.035f * pc.scale
                if (u >= 1f) { kState = K_PERCHED; kT = 3f + rnd.nextFloat() * 6f; kFish = false; kdir = -kdir }
            }
        }
        if (dt > 0f) { kvx = (kx - ox) / dt; kvy = (ky - oy) / dt }
    }

    // ---------------------------------------------------------------- drawing

    private fun mul(color: Int, t: Int): Int = Color.argb(
        Color.alpha(color),
        Color.red(color) * Color.red(t) / 255,
        Color.green(color) * Color.green(t) / 255,
        Color.blue(color) * Color.blue(t) / 255,
    )

    private fun flowerBitmap(kind: Int, level: Int): Bitmap? {
        val idx = kind * MeadowSprites.LEVELS + level
        flowerBitmaps[idx]?.let { return it }
        val bmp = MeadowSprites.bakeFlower(kind, level) ?: return null
        flowerBitmaps[idx] = bmp
        return bmp
    }

    private fun kingBitmap(frame: Int): Bitmap? {
        kingBitmaps[frame]?.let { return it }
        val bmp = MeadowSprites.bakeKingfisher(frame) ?: return null
        kingBitmaps[frame] = bmp
        return bmp
    }

    /** Flowers and the fishing perch, drawn before the animals walk in front of them. */
    fun drawFlowers(canvas: Canvas) {
        if (w <= 0f) return
        if (flowerCount > 0) {
            val level = (openNow * 2f + 0.5f).toInt().coerceIn(0, 2)
            val shift = parallaxPx * 0.95f
            for (i in 0 until flowerCount) {
                val bmp = flowerBitmap(flKind[i], level) ?: continue
                val k = flSize[i] / (MeadowSprites.FLOWER_H * MeadowSprites.FLOWER_SC)
                canvas.save()
                canvas.translate(flX[i] + shift, flY[i])
                canvas.rotate(swayDeg(i))
                canvas.scale(k, k)
                canvas.drawBitmap(bmp, -MeadowSprites.FLOWER_BASE_X * MeadowSprites.FLOWER_SC, -MeadowSprites.FLOWER_H * MeadowSprites.FLOWER_SC, spritePaint)
                canvas.restore()
            }
        }
        val pc = perch
        if (kingOn && pc != null) drawPerch(canvas, pc)
    }

    private fun drawPerch(canvas: Canvas, pc: Perch) {
        val shift = parallaxPx * 0.95f
        val s = pc.scale
        val x = pc.x + shift
        val top = pc.baseY - pc.height
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        // Ripple where the post enters the water.
        paint.strokeWidth = 1.6f * unit
        paint.color = mul(0x66FFFFFF, tint)
        canvas.drawOval(x - 11f * unit * s, pc.baseY - 2f * unit * s, x + 11f * unit * s, pc.baseY + 2.5f * unit * s, paint)
        paint.strokeWidth = 5f * unit * s
        paint.color = mul(0xFF4A3524.toInt(), tint)
        canvas.drawLine(x - 3f * unit * s, pc.baseY, x, top, paint)
        paint.strokeWidth = 2.6f * unit * s
        canvas.drawLine(x - 1.5f * unit * s, top + pc.height * 0.42f, x + 15f * unit * s, top + pc.height * 0.3f, paint)
        paint.style = Paint.Style.FILL
    }

    /** Bees, butterflies and the kingfisher, drawn on top of everything on the ground. */
    fun drawFlyers(canvas: Canvas) {
        if (w <= 0f) return
        val shift = parallaxPx * 0.9f
        if (gnatsOn) drawGnats(canvas, shift)
        for (i in 0 until beeCount) drawBee(canvas, i, shift)
        for (i in 0 until butCount) drawButterfly(canvas, i, shift)
        for (i in 0 until dragonCount) drawDragon(canvas, i, shift)
        for (i in 0 until mothCount) drawMoth(canvas, i, shift)
        if (kingOn) drawKingfisher(canvas)
    }

    /** A cloud of tiny dark specks, each on its own looping path, the cloud drifting slowly. */
    private fun drawGnats(canvas: Canvas, shift: Float) {
        val cx = swX + sin(time * 0.18f) * 0.04f * w + shift
        val cy = swY + cos(time * 0.14f) * 0.015f * h
        val r = 0.045f * w
        paint.style = Paint.Style.FILL
        paint.color = mul(0xD0202428.toInt(), tint)
        for (i in 0 until MAX_GNAT) {
            val x = cx + sin(time * gnA[i] + gnP[i]) * r * (0.65f + 0.35f * sin(time * 0.8f + gnP[i] * 3f))
            val y = cy + sin(time * gnB[i] + gnP[i] * 1.7f) * r * 0.55f
            canvas.drawCircle(x, y, 1.3f * unit, paint)
        }
    }

    /** A slim jewelled body with four glassy wings that blur when it moves and shimmer when it hovers. */
    private fun drawDragon(canvas: Canvas, i: Int, shift: Float) {
        val u = unit * 2f
        val col = DRAGON_COLORS[dfKind[i]]
        val wf = 0.45f + 0.55f * abs(sin(time * 55f + i * 1.7f))
        canvas.save()
        canvas.translate(dfX[i] + shift, dfY[i])
        canvas.scale(dfDir[i], 1f)
        // Dart with the head a little down; hover with the tail a little down.
        canvas.rotate(if (dfDart[i]) 6f else -4f)
        paint.style = Paint.Style.FILL
        // Far wings first, then the body, then the near wings.
        paint.color = mul(0x66D6E8F8, tint)
        for (p in 0..1) {
            canvas.save(); canvas.translate((3.5f - p * 3f) * u, -0.9f * u); canvas.rotate(-62f - p * 22f)
            canvas.drawOval(0f, -1.3f * u * wf - 0.3f * u, 10.5f * u, 0.2f * u, paint)
            canvas.restore()
        }
        paint.color = mul(col[0], tint)
        canvas.drawOval(-17.5f * u, -0.85f * u, 2f * u, 0.85f * u, paint)
        paint.color = mul(col[1], tint)
        canvas.drawOval(-12f * u, -0.95f * u, -10f * u, 0.95f * u, paint)
        canvas.drawOval(-7f * u, -0.95f * u, -5f * u, 0.95f * u, paint)
        canvas.drawOval(1f * u, -2.2f * u, 6.4f * u, 2.2f * u, paint)
        paint.color = mul(col[2], tint)
        canvas.drawCircle(7.2f * u, -0.4f * u, 2.1f * u, paint)
        paint.color = mul(0xFF14181C.toInt(), tint)
        canvas.drawCircle(8.2f * u, -0.9f * u, 0.7f * u, paint)
        paint.color = mul(0x77DCEBFA, tint)
        for (p in 0..1) {
            canvas.save(); canvas.translate((4.2f - p * 3.4f) * u, -1.5f * u); canvas.rotate(-48f - p * 26f)
            canvas.drawOval(0f, -1.4f * u * wf - 0.3f * u, 11f * u, 0.2f * u, paint)
            canvas.restore()
        }
        canvas.restore()
    }

    /** A pale moth whose wings sweep up and down quickly as it loops round its light. */
    private fun drawMoth(canvas: Canvas, i: Int, shift: Float) {
        val u = unit * 1.7f
        val ang = -15f + 80f * abs(sin(time * 26f + i * 1.3f))
        canvas.save()
        canvas.translate(mtX[i] + shift, mtY[i])
        canvas.scale(mtDir[i], 1f)
        paint.style = Paint.Style.FILL
        paint.color = mul(0xCCC9BFA8.toInt(), tint)
        canvas.save(); canvas.translate(0.8f * u, -0.9f * u); canvas.rotate(ang * 0.8f)
        canvas.drawOval(-7f * u, -2.3f * u, 0.5f * u, 0.4f * u, paint)
        canvas.restore()
        paint.color = mul(0xFF5A4C3A.toInt(), tint)
        canvas.drawOval(-3.2f * u, -1.1f * u, 3.2f * u, 1.1f * u, paint)
        canvas.drawCircle(3.6f * u, -0.2f * u, 1.1f * u, paint)
        paint.color = mul(0xFFE6DDC8.toInt(), tint)
        canvas.save(); canvas.translate(0.8f * u, -0.9f * u); canvas.rotate(ang)
        canvas.drawOval(-7.5f * u, -2.6f * u, 0.5f * u, 0.4f * u, paint)
        canvas.restore()
        canvas.restore()
    }

    private fun drawBee(canvas: Canvas, i: Int, shift: Float) {
        val u = unit * 2.0f
        canvas.save()
        canvas.translate(beeX[i] + shift, beeY[i])
        canvas.scale(beeDir[i], 1f)
        // Wings buzz too fast to follow; they simply flicker in size.
        val wf = 0.35f + 0.65f * abs(sin(time * 70f + i * 1.9f))
        paint.style = Paint.Style.FILL
        paint.color = mul(0x99EEF4FF.toInt(), tint)
        canvas.drawOval(-4.5f * u, -3.5f * u - 7f * u * wf, 1.5f * u, -2.5f * u, paint)
        canvas.drawOval(-1.5f * u, -3.8f * u - 6f * u * wf, 3.5f * u, -2.8f * u, paint)
        // Striped body and dark head.
        paint.color = mul(0xFFF2B922.toInt(), tint)
        canvas.drawOval(-5.5f * u, -3f * u, 5.5f * u, 3f * u, paint)
        paint.color = mul(0xFF2A2218.toInt(), tint)
        canvas.drawOval(-2.4f * u, -2.9f * u, -0.8f * u, 2.9f * u, paint)
        canvas.drawOval(1.0f * u, -2.9f * u, 2.6f * u, 2.9f * u, paint)
        canvas.drawCircle(6f * u, 0.3f * u, 2.3f * u, paint)
        canvas.drawOval(-7f * u, -0.9f * u, -5f * u, 0.9f * u, paint)
        canvas.restore()
    }

    private fun drawButterfly(canvas: Canvas, i: Int, shift: Float) {
        val u = unit * 2.2f
        val colors = BUTTERFLY_COLORS[bfKind[i]]
        val flap = if (bfState[i] == 2) 0.3f + 0.7f * (0.5f + 0.5f * sin(bfPh[i]))
        else if (bfGlide[i]) 0.7f
        else 0.2f + 0.8f * abs(cos(bfPh[i]))
        val tilt = (bfVx[i] / (50f * unit)).coerceIn(-1f, 1f) * 16f
        canvas.save()
        canvas.translate(bfX[i] + shift, bfY[i])
        canvas.rotate(tilt)
        paint.style = Paint.Style.FILL
        for (side in 0..1) {
            canvas.save()
            canvas.scale(if (side == 0) flap else -flap, 1f)
            // Upper wing: a dark edge with the bright wing inside, then a spot.
            canvas.save(); canvas.translate(6.5f * u, -4.5f * u); canvas.rotate(-22f)
            paint.color = mul(colors[1], tint); canvas.drawOval(-7f * u, -5.4f * u, 7f * u, 5.4f * u, paint)
            paint.color = mul(colors[0], tint); canvas.drawOval(-6f * u, -4.4f * u, 5.6f * u, 4.2f * u, paint)
            paint.color = mul(colors[2], tint); canvas.drawCircle(1.5f * u, -1f * u, 1.3f * u, paint)
            canvas.restore()
            // Lower wing.
            canvas.save(); canvas.translate(5f * u, 4.5f * u); canvas.rotate(26f)
            paint.color = mul(colors[1], tint); canvas.drawOval(-4.6f * u, -3.6f * u, 4.6f * u, 3.6f * u, paint)
            paint.color = mul(colors[0], tint); canvas.drawOval(-3.8f * u, -2.9f * u, 3.8f * u, 2.9f * u, paint)
            canvas.restore()
            canvas.restore()
        }
        // Body and antennae.
        paint.color = mul(0xFF2A2018.toInt(), tint)
        canvas.drawOval(-1.3f * u, -6.5f * u, 1.3f * u, 6.5f * u, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.9f * unit
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(0f, -6f * u, -3f * u, -10.5f * u, paint)
        canvas.drawLine(0f, -6f * u, 3f * u, -10.5f * u, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun drawKingfisher(canvas: Canvas) {
        val pc = perch ?: return
        if (kState == K_UNDER) { drawSplash(canvas, pc); return }
        val frame = when (kState) {
            K_PERCHED -> MeadowSprites.KF_PERCHED
            K_PLUNGE -> MeadowSprites.KF_DIVE
            else -> {
                val rate = if (kState == K_HOVER) 22f else 14f
                when (((time * rate).toInt()) and 3) { 0 -> 1; 1 -> 2; 2 -> 3; else -> 2 }
            }
        }
        val bmp = kingBitmap(frame)
        if (bmp != null) {
            val shift = parallaxPx * 0.95f
            val k = h * 0.036f * pc.scale / (MeadowSprites.KF_W * MeadowSprites.KF_SC)
            val ax = MeadowSprites.kingfisherAnchorX(frame) * MeadowSprites.KF_SC
            val ay = MeadowSprites.kingfisherAnchorY(frame) * MeadowSprites.KF_SC
            canvas.save()
            if (kState == K_PLUNGE) canvas.clipRect(-w, -h, w * 2f, pc.waterY + 2f * unit)
            // Perched, the bird bobs its head a little; in flight it banks into climbs and drops.
            var bob = 0f
            if (kState == K_PERCHED) bob = max(0f, sin(time * 1.7f + 1.3f)) * 0.8f * unit
            canvas.translate(kx + shift, ky + bob)
            if (kState != K_PERCHED && kState != K_PLUNGE) {
                val tilt = Math.toDegrees(atan2(kvy.toDouble(), abs(kvx).toDouble().coerceAtLeast(1.0))).toFloat().coerceIn(-30f, 30f)
                canvas.rotate(tilt * kdir)
            }
            canvas.scale(kdir * k, k)
            canvas.drawBitmap(bmp, -ax, -ay, spritePaint)
            if (kState == K_BACK && kFish) {
                paint.style = Paint.Style.FILL
                paint.color = mul(0xFFD8E2EA.toInt(), tint)
                canvas.save()
                canvas.translate(MeadowSprites.KF_SC * 42f, MeadowSprites.KF_SC * 4f)
                canvas.rotate(14f)
                canvas.drawOval(-7f * MeadowSprites.KF_SC, -2.4f * MeadowSprites.KF_SC, 7f * MeadowSprites.KF_SC, 2.4f * MeadowSprites.KF_SC, paint)
                canvas.restore()
            }
            canvas.restore()
        }
        drawSplash(canvas, pc)
    }

    private fun drawSplash(canvas: Canvas, pc: Perch) {
        if (kSplash < 0f) return
        val shift = parallaxPx * 0.95f
        val t = kSplash / 0.9f
        val fade = 1f - t
        val s = pc.scale
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * unit
        paint.color = Color.argb((190 * fade).toInt().coerceIn(0, 255), 255, 255, 255)
        val rx = (6f + 24f * t) * unit * s
        canvas.drawOval(kSx + shift - rx, pc.waterY - rx * 0.22f, kSx + shift + rx, pc.waterY + rx * 0.22f, paint)
        val r2 = rx * 0.6f
        canvas.drawOval(kSx + shift - r2, pc.waterY - r2 * 0.22f, kSx + shift + r2, pc.waterY + r2 * 0.22f, paint)
        // Droplets thrown up and falling back.
        paint.style = Paint.Style.FILL
        if (t < 0.6f) {
            val tt = t / 0.6f
            val rise = sin(PI.toFloat() * tt)
            for (d in 0 until 5) {
                val dx = (d - 2f) * 5f * unit * s * (0.6f + tt)
                val dy = -(8f + (if (d % 2 == 0) 6f else 0f)) * unit * s * rise
                paint.color = Color.argb((220 * (1f - tt)).toInt().coerceIn(0, 255), 255, 255, 255)
                canvas.drawCircle(kSx + shift + dx, pc.waterY + dy, 2f * unit * s, paint)
            }
        }
    }

    private companion object {
        const val MAX_FLOWERS = 48
        const val MAX_BEE = 4
        const val MAX_BUT = 4
        const val MAX_DRAGON = 3
        const val MAX_GNAT = 18
        const val MAX_MOTH = 4
        const val MIN_INSECT_C = 12.0
        const val CALM_KMH = 25f

        const val K_PERCHED = 0
        const val K_OUT = 1
        const val K_HOVER = 2
        const val K_PLUNGE = 3
        const val K_UNDER = 4
        const val K_BACK = 5

        /** Abdomen, thorax and eye colour of each dragonfly: a blue darter, an emerald and a red darter. */
        val DRAGON_COLORS = arrayOf(
            intArrayOf(0xFF2E86C1.toInt(), 0xFF1B4F72.toInt(), 0xFF5DADE2.toInt()),
            intArrayOf(0xFF2FAE74.toInt(), 0xFF145A32.toInt(), 0xFF58D68D.toInt()),
            intArrayOf(0xFFC0392B.toInt(), 0xFF7B241C.toInt(), 0xFFE67E22.toInt()),
        )

        /** Wing colour, edge colour and spot colour of each butterfly. */
        val BUTTERFLY_COLORS = arrayOf(
            intArrayOf(0xFFE8822A.toInt(), 0xFF2A1A10.toInt(), 0xFFFFFFFF.toInt()), // monarch
            intArrayOf(0xFFF2DA3A.toInt(), 0xFFB89A18.toInt(), 0xFFE8822A.toInt()), // brimstone
            intArrayOf(0xFF4AA3E8.toInt(), 0xFF1C3A6A.toInt(), 0xFFFFFFFF.toInt()), // blue
            intArrayOf(0xFFF4F0F4.toInt(), 0xFF8A8590.toInt(), 0xFF2A2A30.toInt()), // cabbage white
        )
    }
}
