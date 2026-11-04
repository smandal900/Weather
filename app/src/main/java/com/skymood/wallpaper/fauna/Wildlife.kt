package com.skymood.wallpaper.fauna

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.skymood.domain.LandscapeType
import com.skymood.domain.SceneSpec
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun interface GroundY {
    fun at(x: Float): Float
}

/**
 * Where an animal species may walk (or swim): between [x0] and [x1] (fractions of the screen
 * width) on the surface given by [y], drawn at [scale] to suggest distance.
 */
class GroundSlot(
    val species: Species,
    val x0: Float,
    val x1: Float,
    val y: GroundY,
    val scale: Float,
    val count: Int,
)

/**
 * Birds, bats, fireflies and animals that fit the weather, time of day and landscape: swallows
 * and a V of geese by day and at dusk, gulls over the sea, eagles over mountains, bats and
 * fireflies at night, deer, cows, sheep, goats and ducks on the ground, crabs on the beach and
 * dolphins in the sea. Nothing is shown in storms or heavy rain, and snow keeps only hardy animals.
 *
 * Flyers are batched into line segments, ground animals are baked sprites, and everything lives
 * in pre-sized arrays, so drawing does not allocate.
 */
class Wildlife {

    private var w = 0f
    private var h = 0f
    private var unit = 1f
    private var tint = Color.WHITE
    private var lastKey = -1
    private var parallaxPx = 0f
    private var time = 0f

    // ---- flyers: 0 small bird, 1 gull, 2 eagle, 3 goose, 4 bat
    private val fx = FloatArray(MAX_FLY); private val fy = FloatArray(MAX_FLY)
    private val fvx = FloatArray(MAX_FLY); private val fph = FloatArray(MAX_FLY)
    private val fsize = FloatArray(MAX_FLY); private val fa1 = FloatArray(MAX_FLY); private val fa2 = FloatArray(MAX_FLY)
    private val fkind = IntArray(MAX_FLY); private val fon = BooleanArray(MAX_FLY)
    private val target = IntArray(5)
    private var gooseDelay = 4f
    private var gooseDay = false

    private val lineBuf = Array(5) { FloatArray(MAX_FLY * 16) }
    private val linePaint = Array(5) { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE } }

    // ---- fireflies
    private val ffx = FloatArray(MAX_FF); private val ffy = FloatArray(MAX_FF)
    private val ffph = FloatArray(MAX_FF); private val ffsp = FloatArray(MAX_FF)
    private var fireflies = 0
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- ground animals
    private var slots: List<GroundSlot> = emptyList()
    private val gSlot = IntArray(MAX_GROUND); private val gx = FloatArray(MAX_GROUND)
    private val gdir = FloatArray(MAX_GROUND); private val gtimer = FloatArray(MAX_GROUND)
    private val gwalk = BooleanArray(MAX_GROUND); private val gph = FloatArray(MAX_GROUND)
    private val gu = FloatArray(MAX_GROUND); private val gstart = FloatArray(MAX_GROUND)
    private var groundCount = 0
    private val sprites = HashMap<Int, Bitmap>()
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rnd = Random(System.nanoTime())

    fun resize(width: Int, height: Int) {
        if (width.toFloat() == w && height.toFloat() == h) return
        w = width.toFloat(); h = height.toFloat(); unit = w / 1080f
        sprites.values.forEach { it.recycle() }
        sprites.clear()
        lastKey = -1
        for (i in linePaint.indices) linePaint[i].strokeWidth = STROKE[i] * unit
    }

    fun setParallax(offset: Float) {
        parallaxPx = -offset * w * 0.04f
    }

    /** Re-evaluates what lives here; cheap when nothing relevant changed. */
    fun configure(spec: SceneSpec, groundSlots: List<GroundSlot>, tintColor: Int) {
        if (w <= 0f) return
        tint = tintColor
        spritePaint.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.MULTIPLY)
        val night = spec.timeOfDay == TimeOfDay.NIGHT
        linePaint[0].color = mul(0xFF2B2F38.toInt(), tintColor)
        linePaint[1].color = mul(0xFFF4F4F4.toInt(), tintColor)
        linePaint[2].color = mul(0xFF3A2E24.toInt(), tintColor)
        linePaint[3].color = mul(0xFF2B2F38.toInt(), tintColor)
        linePaint[4].color = if (night) 0xFF0B0D12.toInt() else mul(0xFF1A1A20.toInt(), tintColor)
        val key = ((spec.landscape.ordinal * 8 + spec.timeOfDay.ordinal) * 16 + spec.type.ordinal) * 2 + if (spec.wildlife) 1 else 0
        if (key == lastKey) { slots = groundSlots; return }
        lastKey = key
        slots = groundSlots
        populate(spec)
    }

    private fun populate(spec: SceneSpec) {
        java.util.Arrays.fill(target, 0)
        fireflies = 0
        groundCount = 0
        java.util.Arrays.fill(fon, false)
        if (!spec.wildlife) return
        val ls = spec.landscape
        val wx = spec.type
        val tod = spec.timeOfDay
        val night = tod == TimeOfDay.NIGHT
        val twilight = tod == TimeOfDay.DAWN || tod == TimeOfDay.DUSK
        val fair = wx == SceneType.CLEAR || wx == SceneType.PARTLY_CLOUDY || wx == SceneType.HAZE
        val dull = wx == SceneType.OVERCAST
        val windy = wx == SceneType.WINDY
        val sea = ls == LandscapeType.SEA_BEACH || ls == LandscapeType.OPEN_SEA || ls == LandscapeType.SEA_WITH_BOAT
        val dry = fair || dull || windy

        if (!night && !sea && ls != LandscapeType.SNOWY_PEAKS) target[0] = if (fair) 6 else if (dull) 3 else if (windy) 2 else 0
        if (!night && sea) target[1] = if (fair) 5 else if (dull) 4 else if (windy) 7 else if (wx == SceneType.DRIZZLE) 2 else 0
        val eagleLand = ls == LandscapeType.MOUNTAIN || ls == LandscapeType.SNOWY_PEAKS || ls == LandscapeType.HILL ||
            ls == LandscapeType.FOREST || ls == LandscapeType.WATERFALL || ls == LandscapeType.RIVER_VALLEY
        if (!night && eagleLand && (fair || windy)) target[2] = if (ls == LandscapeType.MOUNTAIN || ls == LandscapeType.SNOWY_PEAKS) 2 else 1
        gooseDay = twilight && dry && ls != LandscapeType.WATERFALL
        if (night && dry && !sea && ls != LandscapeType.SNOWY_PEAKS) target[4] = 5 else if (tod == TimeOfDay.DUSK && dry && !sea) target[4] = 3
        val glowLand = ls == LandscapeType.FOREST || ls == LandscapeType.GRASS_FIELD || ls == LandscapeType.HILL ||
            ls == LandscapeType.LAKE || ls == LandscapeType.RIVER_VALLEY || ls == LandscapeType.COUNTRYSIDE_VILLAGE ||
            ls == LandscapeType.WATERFALL
        if ((night || tod == TimeOfDay.DUSK) && (fair || dull) && glowLand) fireflies = if (night) 26 else 10

        // Spawn flyers to their targets.
        var n = 0
        for (kind in 0..4) {
            if (kind == 3) continue
            for (i in 0 until target[kind]) { if (n >= MAX_FLY) break; spawn(n++, kind, true) }
        }
        gooseDelay = 2f + rnd.nextFloat() * 4f
        for (i in 0 until fireflies) {
            ffx[i] = rnd.nextFloat() * w; ffy[i] = h * (0.64f + 0.3f * rnd.nextFloat())
            ffph[i] = rnd.nextFloat() * 6.28f; ffsp[i] = 0.6f + rnd.nextFloat() * 1.2f
        }

        // Ground animals.
        for ((si, slot) in slots.withIndex()) {
            val sp = slot.species
            val allowed = when {
                wx == SceneType.HEAVY_RAIN || wx == SceneType.STORM -> sp == Species.DUCK
                wx == SceneType.DRIZZLE -> sp == Species.DUCK || sp == Species.COW || sp == Species.SHEEP
                wx == SceneType.SNOW -> sp == Species.GOAT || sp == Species.HARE || sp == Species.DEER
                night -> sp == Species.CRAB
                else -> true
            }
            if (!allowed) continue
            for (i in 0 until slot.count) {
                if (groundCount >= MAX_GROUND) break
                val g = groundCount++
                gSlot[g] = si
                gx[g] = (slot.x0 + rnd.nextFloat() * (slot.x1 - slot.x0)) * w
                gdir[g] = if (rnd.nextBoolean()) 1f else -1f
                gwalk[g] = rnd.nextBoolean()
                gtimer[g] = 1f + rnd.nextFloat() * 5f
                gph[g] = rnd.nextFloat() * 4f
                gu[g] = -1f
            }
        }
    }

    private fun mul(a: Int, b: Int) = Color.rgb(
        Color.red(a) * Color.red(b) / 255, Color.green(a) * Color.green(b) / 255, Color.blue(a) * Color.blue(b) / 255,
    )

    private fun spawn(i: Int, kind: Int, anywhere: Boolean) {
        fon[i] = true
        fkind[i] = kind
        fph[i] = rnd.nextFloat() * 6.28f
        when (kind) {
            0 -> {
                fsize[i] = (11f + rnd.nextFloat() * 5f) * unit
                fvx[i] = (24f + rnd.nextFloat() * 22f) * unit
                fx[i] = if (anywhere) rnd.nextFloat() * w else -20f * unit
                fa1[i] = h * (0.1f + rnd.nextFloat() * 0.34f)
            }
            1 -> {
                fsize[i] = (16f + rnd.nextFloat() * 6f) * unit
                fvx[i] = (28f + rnd.nextFloat() * 30f) * unit * (if (rnd.nextBoolean()) 1f else -1f)
                fx[i] = if (anywhere) rnd.nextFloat() * w else if (fvx[i] > 0f) -30f * unit else w + 30f * unit
                fa1[i] = h * (0.33f + rnd.nextFloat() * 0.22f)
            }
            2 -> {
                fsize[i] = (34f + rnd.nextFloat() * 10f) * unit
                fa1[i] = w * (0.25f + rnd.nextFloat() * 0.5f)
                fa2[i] = h * (0.1f + rnd.nextFloat() * 0.2f)
                fvx[i] = w * (0.12f + rnd.nextFloat() * 0.1f) // orbit radius
                fx[i] = rnd.nextFloat() * 6.28f // angle
                fy[i] = if (rnd.nextBoolean()) 1f else -1f // direction
            }
            4 -> {
                fsize[i] = (12f + rnd.nextFloat() * 4f) * unit
                fvx[i] = (35f + rnd.nextFloat() * 30f) * unit
                fx[i] = if (anywhere) rnd.nextFloat() * w else -20f * unit
                fa1[i] = h * (0.18f + rnd.nextFloat() * 0.35f)
            }
        }
        if (kind != 2) fy[i] = fa1[i]
    }

    fun update(dtMs: Long) {
        if (w <= 0f) return
        val dt = dtMs / 1000f
        time += dt
        for (i in 0 until MAX_FLY) {
            if (!fon[i]) continue
            when (fkind[i]) {
                0 -> {
                    fx[i] += fvx[i] * dt
                    fph[i] += dt * 14f
                    fy[i] = fa1[i] + sin(time * 1.3f + i) * h * 0.012f
                    if (fx[i] > w + 30f * unit) { spawn(i, 0, false) }
                }
                1 -> {
                    fx[i] += fvx[i] * dt
                    fph[i] += dt * 4.5f
                    fy[i] = fa1[i] + sin(time * 0.9f + i * 2f) * h * 0.02f
                    if (fx[i] > w + 40f * unit || fx[i] < -40f * unit) spawn(i, 1, false)
                }
                2 -> {
                    fx[i] += fy[i] * dt * 0.3f // angle grows (fx holds the angle)
                    fph[i] += dt * 1.2f
                }
                3 -> {
                    fx[i] += fvx[i] * dt
                    fph[i] += dt * 7f
                    if (fx[i] > w + 40f * unit) fon[i] = false
                }
                4 -> {
                    fph[i] += dt * 22f
                    val steer = cos(time * 0.8f + i)
                    fx[i] += fvx[i] * steer * dt
                    fa1[i] += sin(time * 1.7f + i * 3f) * h * 0.05f * dt
                    fy[i] = fa1[i]
                    if (fx[i] > w + 30f * unit) fx[i] = -20f * unit
                    if (fx[i] < -30f * unit) fx[i] = w + 20f * unit
                }
            }
        }
        // Geese cross as a V now and then.
        if (gooseDay) {
            var active = false
            for (i in 0 until MAX_FLY) if (fon[i] && fkind[i] == 3) { active = true; break }
            if (!active) {
                gooseDelay -= dt
                if (gooseDelay <= 0f) { spawnGeese(); gooseDelay = 10f + rnd.nextFloat() * 20f }
            }
        }
        updateGround(dt)
    }

    private fun spawnGeese() {
        val baseY = h * (0.1f + rnd.nextFloat() * 0.2f)
        val speed = (36f + rnd.nextFloat() * 10f) * unit
        val ordered = intArrayOf(0, 1, 2, 3, 4, 5, 6)
        var placed = 0
        for (i in 0 until MAX_FLY) {
            if (placed >= ordered.size) break
            if (fon[i]) continue
            val k = (placed + 1) / 2
            val side = if (placed % 2 == 0) 1f else -1f
            fon[i] = true; fkind[i] = 3
            fsize[i] = (14f + rnd.nextFloat() * 4f) * unit
            fvx[i] = speed
            fx[i] = -30f * unit - k * w * 0.035f
            fy[i] = baseY + side * k * h * 0.016f
            fph[i] = rnd.nextFloat() * 6.28f
            placed++
        }
    }

    private fun updateGround(dt: Float) {
        for (g in 0 until groundCount) {
            val slot = slots.getOrNull(gSlot[g]) ?: continue
            val x0 = slot.x0 * w; val x1 = slot.x1 * w
            gtimer[g] -= dt
            when (slot.species) {
                Species.DOLPHIN -> {
                    if (gu[g] < 0f) {
                        if (gtimer[g] <= 0f) {
                            gu[g] = 0f
                            gdir[g] = if (rnd.nextBoolean()) 1f else -1f
                            gstart[g] = x0 + (x1 - x0) * (0.25f + 0.5f * rnd.nextFloat())
                        }
                    } else {
                        gu[g] += dt / 1.8f
                        if (gu[g] >= 1f) { gu[g] = -1f; gtimer[g] = 7f + rnd.nextFloat() * 9f }
                    }
                }
                Species.DUCK -> {
                    gx[g] += gdir[g] * 9f * unit * dt
                    gph[g] += dt
                    if (gx[g] < x0) gdir[g] = 1f else if (gx[g] > x1) gdir[g] = -1f
                }
                Species.CRAB -> {
                    if (gtimer[g] <= 0f) { gwalk[g] = !gwalk[g]; gtimer[g] = if (gwalk[g]) 0.4f + rnd.nextFloat() else 0.6f + rnd.nextFloat() * 2f; if (gwalk[g] && rnd.nextFloat() < 0.4f) gdir[g] = -gdir[g] }
                    if (gwalk[g]) { gx[g] += gdir[g] * 40f * unit * dt; gph[g] += dt * 9f }
                    if (gx[g] < x0) gdir[g] = 1f else if (gx[g] > x1) gdir[g] = -1f
                }
                else -> {
                    if (gtimer[g] <= 0f) { gwalk[g] = !gwalk[g]; gtimer[g] = if (gwalk[g]) 3f + rnd.nextFloat() * 5f else 1.5f + rnd.nextFloat() * 5f }
                    if (gwalk[g]) {
                        val v = (if (slot.species == Species.SHEEP) 8f else if (slot.species == Species.HARE) 22f else 14f) * unit * slot.scale
                        gx[g] += gdir[g] * v * dt
                        gph[g] += dt * (if (slot.species == Species.HARE) 5f else 3.2f)
                    }
                    if (gx[g] < x0) gdir[g] = 1f else if (gx[g] > x1) gdir[g] = -1f
                }
            }
        }
        for (i in 0 until fireflies) {
            ffx[i] += sin(time * 0.7f + ffph[i]) * 10f * unit * dt
            ffy[i] += cos(time * 0.5f + ffph[i] * 1.7f) * 8f * unit * dt
        }
    }

    // ---------------------------------------------------------------- drawing

    /** Birds and bats, drawn between the sky and the landscape. */
    fun drawSky(canvas: Canvas) {
        if (w <= 0f) return
        var n0 = 0; var n1 = 0; var n2 = 0; var n3 = 0; var n4 = 0
        for (i in 0 until MAX_FLY) {
            if (!fon[i]) continue
            when (fkind[i]) {
                0 -> n0 = wings(lineBuf[0], n0, fx[i] + parallaxPx * 0.2f, fy[i], fsize[i], sin(fph[i]))
                1 -> n1 = wings(lineBuf[1], n1, fx[i] + parallaxPx * 0.2f, fy[i], fsize[i], sin(fph[i]) * 0.6f)
                2 -> {
                    val a = fx[i]
                    val cx = fa1[i] + cos(a) * fvx[i]
                    val cy = fa2[i] + sin(a) * fvx[i] * 0.22f
                    n2 = wings(lineBuf[2], n2, cx + parallaxPx * 0.2f, cy, fsize[i], sin(fph[i]) * 0.18f)
                }
                3 -> n3 = wings(lineBuf[3], n3, fx[i] + parallaxPx * 0.2f, fy[i], fsize[i], sin(fph[i]))
                4 -> n4 = wings(lineBuf[4], n4, fx[i] + parallaxPx * 0.2f, fy[i], fsize[i], sin(fph[i]))
            }
        }
        if (n0 > 0) canvas.drawLines(lineBuf[0], 0, n0, linePaint[0])
        if (n1 > 0) canvas.drawLines(lineBuf[1], 0, n1, linePaint[1])
        if (n2 > 0) canvas.drawLines(lineBuf[2], 0, n2, linePaint[2])
        if (n3 > 0) canvas.drawLines(lineBuf[3], 0, n3, linePaint[3])
        if (n4 > 0) canvas.drawLines(lineBuf[4], 0, n4, linePaint[4])
    }

    /** One bird as an M of four line segments. [flap] -1 (wings down) .. 1 (wings up). */
    private fun wings(buf: FloatArray, start: Int, x: Float, y: Float, s: Float, flap: Float): Int {
        var n = start
        val ex = s * 0.5f
        val ey = -s * (0.25f + 0.1f * flap)
        val ty = -flap * s * 0.5f + s * 0.12f
        buf[n++] = x; buf[n++] = y; buf[n++] = x - ex; buf[n++] = y + ey
        buf[n++] = x - ex; buf[n++] = y + ey; buf[n++] = x - s; buf[n++] = y + ty
        buf[n++] = x; buf[n++] = y; buf[n++] = x + ex; buf[n++] = y + ey
        buf[n++] = x + ex; buf[n++] = y + ey; buf[n++] = x + s; buf[n++] = y + ty
        return n
    }

    /** Animals, fireflies and swimmers, drawn on top of the landscape. */
    fun drawGround(canvas: Canvas) {
        if (w <= 0f) return
        for (g in 0 until groundCount) {
            val slot = slots.getOrNull(gSlot[g]) ?: continue
            val sp = slot.species
            val k = slot.scale / BAKE_SCALE
            if (sp == Species.DOLPHIN) {
                val u = gu[g]
                if (u < 0f) continue
                val bmp = sprite(sp, 0) ?: continue
                val span = w * 0.16f
                val x = gstart[g] + gdir[g] * (u - 0.5f) * span
                val surface = slot.y.at(x)
                val y = surface - sin(u * PI.toFloat()) * h * 0.055f * slot.scale
                canvas.save()
                canvas.translate(x + parallaxPx * 0.5f, y)
                canvas.scale(gdir[g] * k, k)
                canvas.rotate(-cos(u * PI.toFloat()) * 38f)
                canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, spritePaint)
                canvas.restore()
                continue
            }
            val walking = gwalk[g] && sp != Species.DUCK
            val frame = if (walking && ((gph[g] * 2f).toInt() and 1) == 1) 1 else 0
            val bmp = sprite(sp, if (sp == Species.DUCK) 0 else frame) ?: continue
            var y = slot.y.at(gx[g])
            if (sp == Species.DUCK) y += sin(gph[g] * 2.2f) * h * 0.0012f
            canvas.save()
            canvas.translate(gx[g] + parallaxPx * 0.8f, y)
            canvas.scale(gdir[g] * k, k)
            canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height.toFloat(), spritePaint)
            canvas.restore()
        }
        for (i in 0 until fireflies) {
            val blink = sin(time * ffsp[i] + ffph[i])
            if (blink <= 0.1f) continue
            val a = blink * blink
            glowPaint.color = 0xFFE8F27A.toInt()
            glowPaint.alpha = (60 * a).toInt()
            canvas.drawCircle(ffx[i] + parallaxPx * 0.8f, ffy[i], 7f * unit, glowPaint)
            glowPaint.alpha = (255 * a).toInt()
            canvas.drawCircle(ffx[i] + parallaxPx * 0.8f, ffy[i], 2f * unit, glowPaint)
        }
    }

    private fun sprite(sp: Species, frame: Int): Bitmap? {
        val key = sp.ordinal * 2 + frame
        sprites[key]?.let { return it }
        val px = (sp.heightFraction * h * BAKE_SCALE).toInt()
        val bmp = AnimalSprites.bake(sp, frame, px) ?: return null
        sprites[key] = bmp
        return bmp
    }

    private companion object {
        const val MAX_FLY = 24
        const val MAX_GROUND = 14
        const val MAX_FF = 30
        const val BAKE_SCALE = 1.5f
        val STROKE = floatArrayOf(2.4f, 3f, 4f, 2.6f, 3f)
    }
}
