package com.skymood.wallpaper.fauna

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.skymood.domain.LandscapeType
import com.skymood.domain.SceneSpec
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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
 * Birds, bats, fireflies and animals that fit the weather, time of day and landscape.
 *
 * Birds are feathered silhouettes that flap, glide and bank into their turns; four-legged
 * animals have a real walk cycle (knees and hocks bend, feet lift and slide back), speed up and
 * slow down smoothly, stop to graze with their heads down and turn around at the edge of their
 * range. Heavy rain and storms keep only ducks, snow keeps hardy animals, and the night belongs
 * to crabs, bats and fireflies.
 *
 * All sprites are baked bitmaps and all state lives in pre-sized arrays, so drawing does not
 * allocate.
 */
class Wildlife {

    private var w = 0f
    private var h = 0f
    private var unit = 1f
    private var lastKey = -1
    private var parallaxPx = 0f
    private var time = 0f

    // ---- flyers: 0 small bird, 1 gull, 2 eagle, 3 goose, 4 bat
    private val fx = FloatArray(MAX_FLY); private val fy = FloatArray(MAX_FLY)
    private val fvx = FloatArray(MAX_FLY); private val fvy = FloatArray(MAX_FLY)
    private val fph = FloatArray(MAX_FLY); private val fsize = FloatArray(MAX_FLY)
    private val fa1 = FloatArray(MAX_FLY); private val fa2 = FloatArray(MAX_FLY)
    private val ftimer = FloatArray(MAX_FLY)
    private val fkind = IntArray(MAX_FLY); private val fon = BooleanArray(MAX_FLY)
    private val fglide = BooleanArray(MAX_FLY)
    private val target = IntArray(5)
    private var gooseDelay = 4f
    private var gooseDay = false
    private val birdFrames = Array(5) { arrayOfNulls<Bitmap>(BirdSprites.GLIDE_FRAME + 1) }

    // ---- fireflies
    private val ffx = FloatArray(MAX_FF); private val ffy = FloatArray(MAX_FF)
    private val ffph = FloatArray(MAX_FF); private val ffsp = FloatArray(MAX_FF)
    private var fireflies = 0
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- ground animals
    private var slots: List<GroundSlot> = emptyList()
    private val gSlot = IntArray(MAX_GROUND); private val gx = FloatArray(MAX_GROUND)
    private val gdir = FloatArray(MAX_GROUND); private val gtimer = FloatArray(MAX_GROUND)
    private val gwalk = BooleanArray(MAX_GROUND); private val ggraze = BooleanArray(MAX_GROUND)
    private val gph = FloatArray(MAX_GROUND); private val gv = FloatArray(MAX_GROUND)
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
        for (kind in birdFrames) for (i in kind.indices) { kind[i]?.recycle(); kind[i] = null }
        lastKey = -1
    }

    fun setParallax(offset: Float) {
        parallaxPx = -offset * w * 0.04f
    }

    /** Re-evaluates what lives here; cheap when nothing relevant changed. */
    fun configure(spec: SceneSpec, groundSlots: List<GroundSlot>, tintColor: Int) {
        if (w <= 0f) return
        spritePaint.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.MULTIPLY)
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
        val sea = ls == LandscapeType.SEA_BEACH || ls == LandscapeType.OPEN_SEA || ls == LandscapeType.SEA_WITH_BOAT ||
            ls == LandscapeType.LIGHTHOUSE || ls == LandscapeType.TROPICAL_ISLAND ||
            ls == LandscapeType.HARBOR
        val dry = fair || dull || windy

        if (!night && !sea && ls != LandscapeType.SNOWY_PEAKS) target[0] = if (fair) 6 else if (dull) 3 else if (windy) 2 else 0
        if (!night && sea) target[1] = if (fair) 5 else if (dull) 4 else if (windy) 7 else if (wx == SceneType.DRIZZLE) 2 else 0
        // No big soaring eagles: the small birds, gulls and geese are the only daytime flyers.
        gooseDay = twilight && dry && ls != LandscapeType.WATERFALL
        if (night && dry && !sea && ls != LandscapeType.SNOWY_PEAKS) target[4] = 5 else if (tod == TimeOfDay.DUSK && dry && !sea) target[4] = 3
        val glowLand = ls == LandscapeType.FOREST || ls == LandscapeType.GRASS_FIELD || ls == LandscapeType.HILL ||
            ls == LandscapeType.LAKE || ls == LandscapeType.RIVER_VALLEY || ls == LandscapeType.COUNTRYSIDE_VILLAGE ||
            ls == LandscapeType.WATERFALL || ls == LandscapeType.MANGROVE_BACKWATERS || ls == LandscapeType.CAMPING
        if ((night || tod == TimeOfDay.DUSK) && (fair || dull) && glowLand) fireflies = if (night) 26 else 10

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
                gwalk[g] = rnd.nextFloat() < 0.4f
                ggraze[g] = !gwalk[g] && rnd.nextFloat() < 0.75f
                gtimer[g] = 1f + rnd.nextFloat() * 5f
                gph[g] = rnd.nextFloat() * 4f
                gv[g] = 0f
                gu[g] = -1f
            }
        }
    }

    private fun spawn(i: Int, kind: Int, anywhere: Boolean) {
        fon[i] = true
        fkind[i] = kind
        fph[i] = rnd.nextFloat() * 6.28f
        fglide[i] = false
        ftimer[i] = 0.3f + rnd.nextFloat()
        fvy[i] = 0f
        when (kind) {
            0 -> {
                fsize[i] = (20f + rnd.nextFloat() * 8f) * unit
                fvx[i] = (26f + rnd.nextFloat() * 22f) * unit
                fx[i] = if (anywhere) rnd.nextFloat() * w else -20f * unit
                fa1[i] = h * (0.1f + rnd.nextFloat() * 0.34f)
            }
            1 -> {
                fsize[i] = (30f + rnd.nextFloat() * 8f) * unit
                fvx[i] = (28f + rnd.nextFloat() * 30f) * unit * (if (rnd.nextBoolean()) 1f else -1f)
                fx[i] = if (anywhere) rnd.nextFloat() * w else if (fvx[i] > 0f) -30f * unit else w + 30f * unit
                fa1[i] = h * (0.33f + rnd.nextFloat() * 0.22f)
            }
            2 -> {
                fsize[i] = (64f + rnd.nextFloat() * 16f) * unit
                fa1[i] = w * (0.25f + rnd.nextFloat() * 0.5f)
                fa2[i] = h * (0.1f + rnd.nextFloat() * 0.2f)
                fvx[i] = w * (0.12f + rnd.nextFloat() * 0.1f)
                fx[i] = rnd.nextFloat() * 6.28f
                fy[i] = if (rnd.nextBoolean()) 1f else -1f
                fglide[i] = true
            }
            4 -> {
                fsize[i] = (24f + rnd.nextFloat() * 6f) * unit
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
        if (dt <= 0f) return
        time += dt
        for (i in 0 until MAX_FLY) {
            if (!fon[i]) continue
            val oldY = fy[i]
            when (fkind[i]) {
                0 -> {
                    fx[i] += fvx[i] * dt
                    flapOrGlide(i, dt, 16f, 0.45f, 1.1f, 0.2f, 0.6f)
                    fy[i] = fa1[i] + sin(time * 1.3f + i) * h * 0.012f - (if (fglide[i]) 0f else 0f)
                    if (fx[i] > w + 30f * unit) spawn(i, 0, false)
                }
                1 -> {
                    fx[i] += fvx[i] * dt
                    flapOrGlide(i, dt, 6f, 1f, 2.2f, 2f, 5f)
                    fy[i] = fa1[i] + sin(time * 0.6f + i * 2f) * h * 0.022f
                    if (fx[i] > w + 40f * unit || fx[i] < -40f * unit) spawn(i, 1, false)
                }
                2 -> {
                    fx[i] += fy[i] * dt * 0.28f
                }
                3 -> {
                    fx[i] += fvx[i] * dt
                    fph[i] += dt * 7f
                    if (fx[i] > w + 40f * unit) fon[i] = false
                }
                4 -> {
                    fph[i] += dt * 20f
                    val steer = cos(time * 0.8f + i)
                    fvx[i] = (if (steer >= 0f) 1f else -1f) * abs(fvx[i])
                    fx[i] += fvx[i] * abs(steer).coerceAtLeast(0.3f) * dt
                    fa1[i] += sin(time * 1.7f + i * 3f) * h * 0.05f * dt
                    fy[i] = fa1[i]
                    if (fx[i] > w + 30f * unit) fx[i] = -20f * unit
                    if (fx[i] < -30f * unit) fx[i] = w + 20f * unit
                }
            }
            if (fkind[i] != 2) fvy[i] = (fy[i] - oldY) / dt
        }
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

    /** Birds alternate bursts of flapping with glides. */
    private fun flapOrGlide(i: Int, dt: Float, flapRate: Float, flapMin: Float, flapMax: Float, glideMin: Float, glideMax: Float) {
        ftimer[i] -= dt
        if (ftimer[i] <= 0f) {
            fglide[i] = !fglide[i]
            ftimer[i] = if (fglide[i]) glideMin + rnd.nextFloat() * (glideMax - glideMin) else flapMin + rnd.nextFloat() * (flapMax - flapMin)
        }
        if (!fglide[i]) fph[i] += dt * flapRate
    }

    private fun spawnGeese() {
        val baseY = h * (0.1f + rnd.nextFloat() * 0.2f)
        val speed = (36f + rnd.nextFloat() * 10f) * unit
        var placed = 0
        for (i in 0 until MAX_FLY) {
            if (placed >= 7) break
            if (fon[i]) continue
            val k = (placed + 1) / 2
            val side = if (placed % 2 == 0) 1f else -1f
            fon[i] = true; fkind[i] = 3; fglide[i] = false
            fsize[i] = (28f + rnd.nextFloat() * 5f) * unit
            fvx[i] = speed
            fx[i] = -30f * unit - k * w * 0.035f
            fy[i] = baseY + side * k * h * 0.016f
            fph[i] = placed * 0.5f + rnd.nextFloat() * 0.4f
            fvy[i] = 0f
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
                    if (gtimer[g] <= 0f) {
                        gwalk[g] = !gwalk[g]
                        gtimer[g] = if (gwalk[g]) 0.4f + rnd.nextFloat() else 0.6f + rnd.nextFloat() * 2f
                        if (gwalk[g] && rnd.nextFloat() < 0.4f) gdir[g] = -gdir[g]
                    }
                    if (gwalk[g]) { gx[g] += gdir[g] * 40f * unit * dt; gph[g] += dt * 9f }
                    if (gx[g] < x0) gdir[g] = 1f else if (gx[g] > x1) gdir[g] = -1f
                }
                Species.HARE -> {
                    if (gtimer[g] <= 0f) { gwalk[g] = !gwalk[g]; gtimer[g] = if (gwalk[g]) 1f + rnd.nextFloat() * 2f else 2f + rnd.nextFloat() * 5f }
                    if (gwalk[g]) { gx[g] += gdir[g] * 26f * unit * slot.scale * dt; gph[g] += dt * 5f }
                    if (gx[g] < x0) gdir[g] = 1f else if (gx[g] > x1) gdir[g] = -1f
                }
                else -> walkQuadruped(g, slot, dt, x0, x1)
            }
        }
        for (i in 0 until fireflies) {
            ffx[i] += sin(time * 0.7f + ffph[i]) * 10f * unit * dt
            ffy[i] += cos(time * 0.5f + ffph[i] * 1.7f) * 8f * unit * dt
        }
    }

    /** Walk, stop and graze, with smooth speed changes and a turn at each end of the range. */
    private fun walkQuadruped(g: Int, slot: GroundSlot, dt: Float, x0: Float, x1: Float) {
        val sp = slot.species
        if (gtimer[g] <= 0f) {
            gwalk[g] = !gwalk[g]
            if (gwalk[g]) { gtimer[g] = 3f + rnd.nextFloat() * 5f; ggraze[g] = false }
            else { gtimer[g] = 2.5f + rnd.nextFloat() * 6f; ggraze[g] = rnd.nextFloat() < 0.75f }
        }
        val heightPx = sp.heightFraction * h * slot.scale
        val vmax = heightPx * (if (sp == Species.SHEEP) 0.2f else if (sp == Species.DEER || sp == Species.HORSE) 0.42f else 0.3f)
        val target = if (gwalk[g]) vmax else 0f
        val accel = if (target > gv[g]) heightPx * 0.5f else heightPx * 1.2f
        gv[g] += (target - gv[g]).coerceIn(-accel * dt, accel * dt)
        gx[g] += gdir[g] * gv[g] * dt
        if (gv[g] > 0.5f) {
            // Stride length is about a fifth of the animal's height; a cycle covers one stance at 60%.
            val stride = heightPx * 0.2f
            gph[g] += (0.6f * gv[g] / stride) * dt
        }
        if ((gx[g] < x0 && gdir[g] < 0f) || (gx[g] > x1 && gdir[g] > 0f)) {
            gdir[g] = -gdir[g]
            gwalk[g] = false; gtimer[g] = 1f + rnd.nextFloat() * 2f; ggraze[g] = rnd.nextBoolean()
        }
    }

    // ---------------------------------------------------------------- drawing

    /** Birds and bats, drawn between the sky and the landscape. */
    fun drawSky(canvas: Canvas) {
        if (w <= 0f) return
        for (i in 0 until MAX_FLY) {
            if (!fon[i]) continue
            val kind = fkind[i]
            var x = fx[i]; var y = fy[i]
            var vx = fvx[i]; var vy = fvy[i]
            if (kind == 2) {
                val a = fx[i]
                x = fa1[i] + cos(a) * fvx[i]
                y = fa2[i] + sin(a) * fvx[i] * 0.22f
                vx = -sin(a) * fy[i]
                vy = cos(a) * 0.22f * fy[i]
            }
            val frame = if (fglide[i]) BirdSprites.GLIDE_FRAME
            else (((fph[i] / (2f * PI.toFloat())) % 1f + 1f) % 1f * BirdSprites.FLAP_FRAMES).toInt().coerceIn(0, BirdSprites.FLAP_FRAMES - 1)
            val bmp = birdFrame(kind, frame) ?: continue
            val dir = if (vx >= 0f) 1f else -1f
            val tilt = Math.toDegrees(atan2(vy.toDouble(), abs(vx).toDouble().coerceAtLeast(1.0))).toFloat().coerceIn(-28f, 28f)
            val k = fsize[i] / (BirdSprites.WING[kind] * BirdSprites.SC)
            canvas.save()
            canvas.translate(x + parallaxPx * 0.2f, y)
            canvas.rotate(tilt * dir * (if (kind == 2) 0.5f else 1f) + (if (kind == 2) -dir * 9f else 0f))
            canvas.scale(dir * k, k)
            canvas.drawBitmap(bmp, -62f * BirdSprites.SC, -52f * BirdSprites.SC, spritePaint)
            canvas.restore()
        }
    }

    private fun birdFrame(kind: Int, frame: Int): Bitmap? {
        birdFrames[kind][frame]?.let { return it }
        val bmp = BirdSprites.bake(kind, frame) ?: return null
        birdFrames[kind][frame] = bmp
        return bmp
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
            val frame = when {
                AnimalSprites.isQuadruped(sp) ->
                    if (gv[g] < 0.5f && ggraze[g]) Quadruped.GRAZE_FRAME
                    else (((gph[g] % 1f) + 1f) % 1f * Quadruped.WALK_FRAMES).toInt().coerceIn(0, Quadruped.WALK_FRAMES - 1)
                sp == Species.DUCK -> 0
                else -> if (gwalk[g] && ((gph[g] * 2f).toInt() and 1) == 1) 1 else 0
            }
            val bmp = sprite(sp, frame) ?: continue
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
        val key = sp.ordinal * 16 + frame
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
    }
}
