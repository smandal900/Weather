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
 * Birds, bats, fireflies and animals that fit the weather, time of day and landscape, plus the
 * flowers, bees, butterflies and kingfisher of the [Meadow].
 *
 * Birds are feathered silhouettes that flap, glide and bank into their turns; four-legged
 * animals have a real walk cycle (knees and hocks bend, feet lift and slide back), speed up and
 * slow down smoothly, stop to graze with their heads down and turn around at the edge of their
 * range. Horses, wild cattle and jackals move as a group, the rest copying their leader.
 * Heavy rain and storms keep only ducks and frogs, snow keeps hardy animals (and turns the fox
 * white), and the night belongs to crabs, frogs, foxes, jackals, bats and fireflies.
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
    /**
     * Small birds fly in loose flocks: [fgrp] is the bird a member follows (itself for the leader), [foffx]
     * how far behind it keeps, [foffy] how far above or below. A gull's [fdiv] is how far through a dive it
     * is (-1 for none); a leader's is the time to its next change of height.
     */
    private val fgrp = IntArray(MAX_FLY)
    private val foffx = FloatArray(MAX_FLY); private val foffy = FloatArray(MAX_FLY)
    private val fdiv = FloatArray(MAX_FLY)
    private var wind = 0f
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
    /** Coat variant, size relative to its species, and a small offset in depth, per animal. */
    private val gvar = IntArray(MAX_GROUND); private val gsize = FloatArray(MAX_GROUND)
    private val gyoff = FloatArray(MAX_GROUND)
    /** Herds and packs: the animal each one follows, where it keeps itself beside it, and a pending reaction. */
    private val glead = IntArray(MAX_GROUND); private val goff = FloatArray(MAX_GROUND)
    private val gpend = BooleanArray(MAX_GROUND)
    /**
     * Reactions: whether it is calm, watching or running ([gstate]), how long that lasts, which way the
     * thing it watches lies (0 for an idle glance), whether watching ends in flight, a pause before it
     * reacts again, the time to its next glance up from grazing, and the hops a hare has left.
     */
    private val gstate = IntArray(MAX_GROUND); private val gst = FloatArray(MAX_GROUND)
    private val gthr = FloatArray(MAX_GROUND); private val gflee = BooleanArray(MAX_GROUND)
    private val gcool = FloatArray(MAX_GROUND); private val gvig = FloatArray(MAX_GROUND)
    private val ghops = IntArray(MAX_GROUND)
    /** Where each hen's three ducklings are (x), indexed hen * 3 + duckling. */
    private val gdlx = FloatArray(MAX_GROUND * 3)
    private var scanT = 0f
    private val wakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAFFFFFF.toInt(); style = Paint.Style.STROKE }
    private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
    private var groundCount = 0
    private val sprites = HashMap<Int, Bitmap>()
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rnd = Random(System.nanoTime())
    private val meadow = Meadow()

    fun resize(width: Int, height: Int) {
        if (width.toFloat() == w && height.toFloat() == h) return
        w = width.toFloat(); h = height.toFloat(); unit = w / 1080f
        sprites.values.forEach { it.recycle() }
        sprites.clear()
        for (kind in birdFrames) for (i in kind.indices) { kind[i]?.recycle(); kind[i] = null }
        lastKey = -1
        meadow.resize(width, height)
    }

    fun setParallax(offset: Float) {
        parallaxPx = -offset * w * 0.04f
        meadow.setParallax(offset)
    }

    /** Re-evaluates what lives here; cheap when nothing relevant changed. */
    fun configure(spec: SceneSpec, groundSlots: List<GroundSlot>, tintColor: Int, meadowSpec: MeadowSpec? = null) {
        if (w <= 0f) return
        wind = spec.windKmh
        meadow.configure(spec, meadowSpec, tintColor)
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

        if (!night && !sea && ls != LandscapeType.SNOWY_PEAKS) target[0] = if (fair) 9 else if (dull) 5 else if (windy) 3 else 0
        if (!night && sea) target[1] = if (fair) 5 else if (dull) 4 else if (windy) 7 else if (wx == SceneType.DRIZZLE) 2 else 0
        // No big soaring eagles: the small birds, gulls and geese are the only daytime flyers.
        gooseDay = twilight && dry && ls != LandscapeType.WATERFALL
        if (night && dry && !sea && ls != LandscapeType.SNOWY_PEAKS) target[4] = 5 else if (tod == TimeOfDay.DUSK && dry && !sea) target[4] = 3
        val glowLand = ls == LandscapeType.FOREST || ls == LandscapeType.GRASS_FIELD || ls == LandscapeType.HILL ||
            ls == LandscapeType.LAKE || ls == LandscapeType.RIVER_VALLEY || ls == LandscapeType.COUNTRYSIDE_VILLAGE ||
            ls == LandscapeType.WATERFALL || ls == LandscapeType.MANGROVE_BACKWATERS || ls == LandscapeType.CAMPING
        if ((night || tod == TimeOfDay.DUSK) && (fair || dull) && glowLand) fireflies = if (night) 26 else 10

        var n = 0
        // Small birds go about in loose flocks of two to four.
        var made = 0
        while (made < target[0] && n < MAX_FLY) {
            val size = minOf(target[0] - made, 2 + rnd.nextInt(3))
            val leader = n
            spawn(n++, 0, true); made++
            for (k in 1 until size) { if (n >= MAX_FLY) break; attach(n++, leader, k); made++ }
        }
        for (kind in 1..4) {
            if (kind == 3) continue
            for (i in 0 until target[kind]) { if (n >= MAX_FLY) break; spawn(n++, kind, true) }
        }
        gooseDelay = 2f + rnd.nextFloat() * 4f
        for (i in 0 until fireflies) {
            ffx[i] = rnd.nextFloat() * w; ffy[i] = h * (0.64f + 0.3f * rnd.nextFloat())
            // Every third one flashes in step with the others, as fireflies in a group tend to.
            ffph[i] = if (i % 3 == 0) 0.7f + rnd.nextFloat() * 0.5f else rnd.nextFloat() * 6.28f
            ffsp[i] = if (i % 3 == 0) 1f else 0.6f + rnd.nextFloat() * 1.2f
        }

        for ((si, slot) in slots.withIndex()) {
            val sp = slot.species
            if (!allowed(sp, wx, tod)) continue
            val herd = AnimalSprites.isHerd(sp)
            val span = slot.x1 - slot.x0
            val herdCx = slot.x0 + span * (0.3f + 0.4f * rnd.nextFloat())
            val herdDir = if (rnd.nextBoolean()) 1f else -1f
            val herdWalk = rnd.nextFloat() < 0.5f
            // Members of a herd stand side by side, about two thirds of a body length apart, with the
            // group as a whole centred on the herd's chosen spot.
            val gap = sp.heightFraction * sp.aspect * h * slot.scale * 0.7f
            var leader = -1
            val n = headCount(sp, slot.count, wx, tod)
            var meanOff = 0f
            for (i in 0 until n) meanOff += (if (i % 2 == 1) 1f else -1f) * ((i + 1) / 2) * gap / n
            var leaderX = 0f
            for (i in 0 until n) {
                if (groundCount >= MAX_GROUND) break
                val g = groundCount++
                gSlot[g] = si
                gvar[g] = variantFor(sp, i, ls, wx)
                gsize[g] = sizeFor(sp, gvar[g])
                gyoff[g] = 0f
                gpend[g] = false
                gstate[g] = ST_NORMAL; gst[g] = 0f; gthr[g] = 0f; gflee[g] = false
                // A short grace period, so a scene does not open with every animal already bolting.
                gcool[g] = 3f + rnd.nextFloat() * 4f; gvig[g] = 3f + rnd.nextFloat() * 8f; ghops[g] = 0
                gph[g] = rnd.nextFloat() * 4f
                gv[g] = 0f
                gu[g] = -1f
                if (herd) {
                    if (i == 0) { leader = g; leaderX = herdCx * w - meanOff }
                    glead[g] = leader
                    // The rest alternate either side of the leader.
                    goff[g] = (if (i % 2 == 1) 1f else -1f) * ((i + 1) / 2) * gap
                    gx[g] = (leaderX + goff[g]).coerceIn(slot.x0 * w - 0.25f * w, slot.x1 * w + 0.25f * w)
                    gdir[g] = herdDir
                    gwalk[g] = herdWalk
                    ggraze[g] = !herdWalk && rnd.nextFloat() < 0.75f
                    gtimer[g] = 1f + rnd.nextFloat() * 5f
                    gyoff[g] = (rnd.nextFloat() - 0.5f) * h * 0.016f
                } else {
                    glead[g] = g
                    goff[g] = 0f
                    gx[g] = (slot.x0 + rnd.nextFloat() * span) * w
                    gdir[g] = if (rnd.nextBoolean()) 1f else -1f
                    gwalk[g] = rnd.nextFloat() < 0.4f
                    ggraze[g] = !gwalk[g] && rnd.nextFloat() < 0.75f
                    gtimer[g] = 1f + rnd.nextFloat() * 5f
                }
                if (sp == Species.DUCK && gvar[g] == 1) {
                    val spacing = sp.heightFraction * sp.aspect * h * slot.scale * 0.42f
                    for (k in 0 until 3) gdlx[g * 3 + k] = gx[g] - gdir[g] * (k + 1) * spacing
                }
            }
        }
    }

    /**
     * Which animals are out for this weather and time of day. Frogs come out in rain and after dark,
     * jackals and foxes keep to the twilight and the night (foxes also roam on grey days and in
     * snow), while horses and wild cattle graze by day and stand about in snow and drizzle.
     */
    private fun allowed(sp: Species, wx: SceneType, tod: TimeOfDay): Boolean {
        val night = tod == TimeOfDay.NIGHT
        val twilight = tod == TimeOfDay.DAWN || tod == TimeOfDay.DUSK
        val wet = wx == SceneType.DRIZZLE || wx == SceneType.HEAVY_RAIN || wx == SceneType.STORM
        val foul = wx == SceneType.HEAVY_RAIN || wx == SceneType.STORM
        val bright = wx == SceneType.CLEAR || wx == SceneType.PARTLY_CLOUDY || wx == SceneType.HAZE || wx == SceneType.WINDY
        return when (sp) {
            Species.FROG -> wx != SceneType.SNOW && (wet || night || twilight || wx == SceneType.FOG)
            Species.JACKAL -> (night || twilight) && !wet && wx != SceneType.SNOW
            Species.FOX -> !foul && !(tod == TimeOfDay.DAY && bright)
            Species.HORSE, Species.WILD_COW -> !night && !foul
            // Gulls and pigeons roost after dark and shelter from the worst weather.
            Species.GULL -> !night && !foul
            else -> when {
                wx == SceneType.HEAVY_RAIN || wx == SceneType.STORM -> sp == Species.DUCK
                wx == SceneType.DRIZZLE -> sp == Species.DUCK || sp == Species.COW || sp == Species.SHEEP
                wx == SceneType.SNOW -> sp == Species.GOAT || sp == Species.HARE || sp == Species.DEER
                night -> sp == Species.CRAB
                else -> true
            }
        }
    }

    /** How many of a slot's animals show: herds thin out in bad weather, packs and frogs vary with the hour. */
    private fun headCount(sp: Species, base: Int, wx: SceneType, tod: TimeOfDay): Int = when (sp) {
        Species.HORSE, Species.WILD_COW ->
            if (wx == SceneType.SNOW || wx == SceneType.DRIZZLE) (base - 1).coerceAtLeast(minOf(2, base)) else base
        Species.JACKAL -> if (tod == TimeOfDay.NIGHT) base else (base - 1).coerceAtLeast(1)
        Species.FROG ->
            if (wx == SceneType.DRIZZLE || wx == SceneType.HEAVY_RAIN || wx == SceneType.STORM) base else (base * 2 / 3).coerceAtLeast(1)
        else -> base
    }

    /** A horse herd is a bay, a chestnut mare, her foal, a grey and a black; a fox turns white in the snow. */
    private fun variantFor(sp: Species, i: Int, ls: LandscapeType, wx: SceneType): Int = when (sp) {
        Species.HORSE -> HORSE_MIX[i % HORSE_MIX.size]
        Species.FOX -> if (ls == LandscapeType.SNOWY_PEAKS || ls == LandscapeType.FROZEN_LAKE || wx == SceneType.SNOW) 1 else 0
        // Drakes and hens alternate; every hen leads a brood.
        Species.DUCK -> if (i % 2 == 1) 1 else 0
        // Pigeons in town and at the temple, gulls by the sea.
        Species.GULL ->
            if (ls == LandscapeType.CITY_SKYLINE || ls == LandscapeType.TEMPLE_HILLTOP || ls == LandscapeType.COUNTRYSIDE_VILLAGE) 1 else 0
        else -> 0
    }

    private fun sizeFor(sp: Species, variant: Int): Float = when {
        sp == Species.GULL && variant == 1 -> 0.72f
        sp == Species.HORSE && variant == AnimalSprites.FOAL -> 0.7f
        sp == Species.HORSE || sp == Species.WILD_COW -> 0.94f + rnd.nextFloat() * 0.12f
        else -> 1f
    }

    private fun spawn(i: Int, kind: Int, anywhere: Boolean) {
        fon[i] = true
        fkind[i] = kind
        fph[i] = rnd.nextFloat() * 6.28f
        fglide[i] = false
        ftimer[i] = 0.3f + rnd.nextFloat()
        fvy[i] = 0f
        fgrp[i] = i; fdiv[i] = -1f
        when (kind) {
            0 -> {
                // A flock's leader, flying either way across the screen; its members attach to it.
                val dir = if (rnd.nextBoolean()) 1f else -1f
                fsize[i] = (20f + rnd.nextFloat() * 8f) * unit
                fvx[i] = dir * (26f + rnd.nextFloat() * 22f) * unit
                val margin = FLOCK_REACH * w + 30f * unit
                fx[i] = if (anywhere) rnd.nextFloat() * w else if (dir > 0f) -margin else w + margin
                fa1[i] = h * (0.1f + rnd.nextFloat() * 0.34f)
                fa2[i] = fa1[i]
                fdiv[i] = 4f + rnd.nextFloat() * 6f
            }
            1 -> {
                fsize[i] = (30f + rnd.nextFloat() * 8f) * unit
                fvx[i] = (28f + rnd.nextFloat() * 30f) * unit * (if (rnd.nextBoolean()) 1f else -1f)
                fx[i] = if (anywhere) rnd.nextFloat() * w else if (fvx[i] > 0f) -30f * unit else w + 30f * unit
                fa1[i] = h * (0.33f + rnd.nextFloat() * 0.22f)
                // Seconds until this gull next dives for a fish.
                fa2[i] = 6f + rnd.nextFloat() * 24f
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
                fa2[i] = fa1[i]
            }
        }
        if (kind != 2) fy[i] = fa1[i]
    }

    /** Makes [i] the [k]th member of the flock led by [leader], trailing it on one side or the other. */
    private fun attach(i: Int, leader: Int, k: Int) {
        fon[i] = true; fkind[i] = 0; fgrp[i] = leader
        fsize[i] = fsize[leader] * (0.9f + rnd.nextFloat() * 0.2f)
        foffx[i] = (0.015f * k + rnd.nextFloat() * 0.03f) * w
        foffy[i] = (if (k % 2 == 0) 1f else -1f) * (0.008f + 0.012f * k + rnd.nextFloat() * 0.01f) * h
        fph[i] = rnd.nextFloat() * 6.28f
        fglide[i] = false; ftimer[i] = 1f; fdiv[i] = -1f; fvy[i] = 0f
        snapToLeader(i)
    }

    private fun snapToLeader(i: Int) {
        val l = fgrp[i]
        fvx[i] = fvx[l]
        fx[i] = fx[l] - (if (fvx[l] >= 0f) 1f else -1f) * foffx[i]
        fa1[i] = fa1[l]
        fy[i] = fa1[l] + foffy[i]
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
                    val flapRate = 16f * (1f + wind.coerceAtMost(50f) / 60f)
                    val lead = fgrp[i]
                    val wave = sin(time * 0.45f + lead * 1.9f) * h * 0.022f
                    if (lead == i) {
                        // The leader cruises, drifts to a new height now and then, and glides between bursts.
                        fx[i] += fvx[i] * (1f + wind.coerceAtMost(50f) / 90f) * dt
                        flapOrGlide(i, dt, flapRate, 0.45f, 1.1f, 0.2f, 0.6f)
                        fdiv[i] -= dt
                        if (fdiv[i] <= 0f) { fa2[i] = h * (0.1f + rnd.nextFloat() * 0.34f); fdiv[i] = 5f + rnd.nextFloat() * 8f }
                        fa1[i] += (fa2[i] - fa1[i]) * (0.25f * dt).coerceAtMost(1f)
                        fy[i] = fa1[i] + wave + sin(time * 1.3f + i) * h * 0.008f
                        val reach = FLOCK_REACH * w + 30f * unit
                        if ((fvx[i] > 0f && fx[i] > w + reach) || (fvx[i] < 0f && fx[i] < -reach)) {
                            spawn(i, 0, false)
                            for (j in 0 until MAX_FLY) if (fon[j] && fkind[j] == 0 && j != i && fgrp[j] == i) snapToLeader(j)
                        }
                    } else {
                        // Members hold station behind the leader, easing after it, and glide when it does.
                        if (!fon[lead] || fkind[lead] != 0) fgrp[i] = i
                        else {
                            fvx[i] = fvx[lead]
                            fglide[i] = fglide[lead]
                            val tx = fx[lead] - (if (fvx[lead] >= 0f) 1f else -1f) * foffx[i]
                            fx[i] += (tx - fx[i]) * (2.5f * dt).coerceAtMost(1f)
                            if (!fglide[i]) fph[i] += dt * flapRate * (0.94f + 0.12f * sin(i * 1.7f))
                            fy[i] = fa1[lead] + foffy[i] + wave + sin(time * 1.3f + i) * h * 0.008f
                        }
                    }
                }
                1 -> {
                    fx[i] += fvx[i] * (1f + wind.coerceAtMost(50f) / 90f) * dt
                    flapOrGlide(i, dt, 6f, 1f, 2.2f, 2f, 5f)
                    var plunge = 0f
                    if (fdiv[i] >= 0f) {
                        // Stoops on a fish: wings held out, down in an arc, then climbs away.
                        fdiv[i] += dt
                        val u = fdiv[i] / GULL_DIVE_SECONDS
                        if (u >= 1f) { fdiv[i] = -1f; fa2[i] = 14f + rnd.nextFloat() * 30f }
                        else { fglide[i] = u in 0.12f..0.72f; plunge = Math.pow(sin(PI * u), 1.6).toFloat() * h * 0.1f }
                    } else {
                        fa2[i] -= dt
                        if (fa2[i] <= 0f && wind < 35f) fdiv[i] = 0f
                    }
                    fy[i] = fa1[i] + sin(time * 0.6f + i * 2f) * h * 0.022f + plunge
                    if (fx[i] > w + 40f * unit || fx[i] < -40f * unit) spawn(i, 1, false)
                }
                2 -> {
                    fx[i] += fy[i] * dt * 0.28f
                }
                3 -> {
                    fx[i] += fvx[i] * dt
                    fph[i] += dt * 7f
                    if ((fvx[i] > 0f && fx[i] > w + 40f * unit) || (fvx[i] < 0f && fx[i] < -40f * unit)) fon[i] = false
                }
                4 -> {
                    // A bat jinks about: it picks a new height and now and then a new heading, and swoops to it.
                    fph[i] += dt * 20f
                    ftimer[i] -= dt
                    if (ftimer[i] <= 0f) {
                        ftimer[i] = 0.5f + rnd.nextFloat() * 1.6f
                        fa2[i] = h * (0.15f + rnd.nextFloat() * 0.42f)
                        if (rnd.nextFloat() < 0.35f) fvx[i] = -fvx[i]
                    }
                    fa1[i] += ((fa2[i] - fa1[i]) * 2.2f).coerceIn(-90f * unit, 90f * unit) * dt
                    fx[i] += fvx[i] * (0.45f + 0.55f * abs(sin(time * 1.3f + i))) * dt
                    fy[i] = fa1[i] + sin(time * 9f + i) * h * 0.004f
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
        meadow.update(dt)
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
        // The skein crosses the sky in either direction, the leader at the point of the V.
        val dir = if (rnd.nextBoolean()) 1f else -1f
        val speed = (36f + rnd.nextFloat() * 10f) * unit * dir
        var placed = 0
        for (i in 0 until MAX_FLY) {
            if (placed >= 7) break
            if (fon[i]) continue
            val k = (placed + 1) / 2
            val side = if (placed % 2 == 0) 1f else -1f
            fon[i] = true; fkind[i] = 3; fglide[i] = false
            fsize[i] = (28f + rnd.nextFloat() * 5f) * unit
            fvx[i] = speed
            fx[i] = if (dir > 0f) -30f * unit - k * w * 0.035f else w + 30f * unit + k * w * 0.035f
            fy[i] = baseY + side * k * h * 0.016f
            fph[i] = placed * 0.5f + rnd.nextFloat() * 0.4f
            fvy[i] = 0f
            placed++
        }
    }

    private fun updateGround(dt: Float) {
        scanThreats(dt)
        for (g in 0 until groundCount) {
            val slot = slots.getOrNull(gSlot[g]) ?: continue
            val x0 = slot.x0 * w; val x1 = slot.x1 * w
            gtimer[g] -= dt
            when (slot.species) {
                Species.DOLPHIN -> {
                    if (gu[g] < -0.3f) {
                        if (gtimer[g] <= 0f) {
                            gu[g] = -0.3f
                            gdir[g] = if (rnd.nextBoolean()) 1f else -1f
                            gstart[g] = x0 + (x1 - x0) * (0.2f + 0.6f * rnd.nextFloat())
                        }
                    } else {
                        gu[g] += dt / 1.6f
                        if (gu[g] >= 1.3f) { gu[g] = -0.5f; gtimer[g] = 2.0f + rnd.nextFloat() * 4.0f }
                    }
                }
                Species.DUCK -> {
                    gph[g] += dt
                    // Paddle for a while, then stop to dabble with the tail in the air or to float and preen.
                    if (gtimer[g] <= 0f) {
                        val pick = rnd.nextFloat()
                        gwalk[g] = pick < 0.55f
                        ggraze[g] = !gwalk[g] && pick < 0.8f
                        gtimer[g] = if (gwalk[g]) 4f + rnd.nextFloat() * 5f else if (ggraze[g]) 2.5f + rnd.nextFloat() * 2.5f else 2f + rnd.nextFloat() * 3f
                        if (gwalk[g] && rnd.nextFloat() < 0.3f) gdir[g] = -gdir[g]
                    }
                    val paddle = if (gwalk[g]) 9f * unit * (0.85f + 0.3f * sin(g * 1.7f)) else 0f
                    gv[g] += (paddle - gv[g]).coerceIn(-14f * unit * dt, 14f * unit * dt)
                    gx[g] += gdir[g] * gv[g] * dt
                    if (gx[g] < x0 && gdir[g] < 0f) gdir[g] = 1f else if (gx[g] > x1 && gdir[g] > 0f) gdir[g] = -1f
                    if (gvar[g] == 1) {
                        // A hen's ducklings paddle after her in a line, easing across behind her when she turns.
                        val spacing = slot.species.heightFraction * slot.species.aspect * h * slot.scale * 0.42f
                        for (k in 0 until 3) {
                            val ix = g * 3 + k
                            val tx = gx[g] - gdir[g] * (k + 1) * spacing
                            gdlx[ix] += (tx - gdlx[ix]) * (2f * dt).coerceAtMost(1f)
                        }
                    }
                }
                Species.CRAB -> {
                    if (gtimer[g] <= 0f) {
                        gwalk[g] = !gwalk[g]
                        gtimer[g] = if (gwalk[g]) 0.4f + rnd.nextFloat() else 0.6f + rnd.nextFloat() * 2f
                        // Standing still, a crab sometimes waves a claw.
                        ggraze[g] = !gwalk[g] && rnd.nextFloat() < 0.45f
                        if (gwalk[g] && rnd.nextFloat() < 0.4f) gdir[g] = -gdir[g]
                    }
                    if (gwalk[g]) { gx[g] += gdir[g] * 40f * unit * dt; gph[g] += dt * 9f }
                    if (gx[g] < x0) gdir[g] = 1f else if (gx[g] > x1) gdir[g] = -1f
                }
                Species.FROG -> {
                    if (gu[g] >= 0f) {
                        // Mid-hop: cover about two body lengths in half a second.
                        gu[g] += dt / HOP_SECONDS
                        gx[g] += gdir[g] * slot.species.heightFraction * h * slot.scale * 2.4f / HOP_SECONDS * dt
                        if (gu[g] >= 1f) { gu[g] = -1f; gtimer[g] = 1.2f + rnd.nextFloat() * 4f }
                    } else if (gtimer[g] <= 0f) {
                        if (rnd.nextFloat() < 0.45f) {
                            gu[g] = 0f; gwalk[g] = false
                            val span = x1 - x0
                            gdir[g] = if (gx[g] < x0 + span * 0.15f) 1f else if (gx[g] > x1 - span * 0.15f) -1f else if (rnd.nextBoolean()) 1f else -1f
                        } else {
                            // Start or stop calling, with the throat sac pulsing while it does.
                            gwalk[g] = !gwalk[g]
                            gtimer[g] = if (gwalk[g]) 1.5f + rnd.nextFloat() * 3f else 1f + rnd.nextFloat() * 3f
                        }
                    }
                    gx[g] = gx[g].coerceIn(x0, x1)
                }
                Species.HARE -> updateHare(g, slot, dt, x0, x1)
                Species.GULL -> updateGroundBird(g, slot, dt, x0, x1)
                else -> walkQuadruped(g, slot, dt, x0, x1)
            }
        }
        for (i in 0 until fireflies) {
            ffx[i] += sin(time * 0.7f + ffph[i]) * 10f * unit * dt
            ffy[i] += cos(time * 0.5f + ffph[i] * 1.7f) * 8f * unit * dt
        }
    }

    /** Walk, stop and graze, with smooth speed changes and a turn at each end of the range. */
    private fun walkQuadruped(g: Int, slot: GroundSlot, dt: Float, x0In: Float, x1In: Float) {
        val sp = slot.species
        val lead = glead[g]
        val follower = AnimalSprites.isHerd(sp) && lead != g && lead in 0 until groundCount
        // Followers may drift a little beyond the range their leader keeps to, and a runner beyond its own.
        val reach = (if (follower) 0.25f * w else 0f) + (if (gstate[g] == ST_FLEE) 0.08f * w else 0f)
        val x0 = x0In - reach
        val x1 = x1In + reach
        if (gstate[g] != ST_NORMAL) {
            // Watching or running: the usual walk, stop and graze routine is suspended.
            gst[g] -= dt
            if (gst[g] <= 0f) { if (gstate[g] == ST_ALERT) endAlert(g) else endFlight(g) }
        } else if (follower) {
            // Copy the leader's mood and heading after a short, personal reaction time.
            val differs = gwalk[g] != gwalk[lead] || gdir[g] != gdir[lead] || ggraze[g] != ggraze[lead]
            if (differs && !gpend[g]) { gpend[g] = true; gtimer[g] = 0.3f + rnd.nextFloat() * 1.4f }
            else if (gpend[g] && gtimer[g] <= 0f) {
                gpend[g] = false
                gwalk[g] = gwalk[lead]; gdir[g] = gdir[lead]; ggraze[g] = ggraze[lead]
            }
        } else if (gtimer[g] <= 0f) {
            gwalk[g] = !gwalk[g]
            if (gwalk[g]) { gtimer[g] = 3f + rnd.nextFloat() * 5f; ggraze[g] = false }
            else { gtimer[g] = 2.5f + rnd.nextFloat() * 6f; ggraze[g] = rnd.nextFloat() < 0.75f }
        }
        // A grazer now and then lifts its head and looks around.
        if (gstate[g] == ST_NORMAL && ggraze[g] && gv[g] < 0.5f) {
            gvig[g] -= dt
            if (gvig[g] <= 0f) {
                gstate[g] = ST_ALERT; gthr[g] = 0f; gflee[g] = false
                gst[g] = 1f + rnd.nextFloat() * 1.6f
                gvig[g] = 4f + rnd.nextFloat() * 9f
            }
        }
        // Speed is the adult's, so a foal keeps up by taking quicker steps.
        val heightPx = sp.heightFraction * h * slot.scale
        var vmax = heightPx * when (sp) {
            Species.SHEEP -> 0.2f
            Species.DEER, Species.HORSE -> 0.42f
            Species.FOX -> 0.55f
            Species.JACKAL -> 0.6f
            Species.WILD_COW -> 0.24f
            else -> 0.3f
        }
        if (follower) {
            // Hurry when behind the place beside the leader, dawdle when ahead of it.
            val behind = (gx[lead] + goff[g] - gx[g]) * gdir[g]
            vmax *= 1f + (behind / (0.25f * w)).coerceIn(-0.4f, 0.4f)
        }
        val running = gstate[g] == ST_FLEE
        val target = when {
            // A bolting animal covers nearly two of its own heights a second, and gallops to do it.
            running -> heightPx * 1.9f
            gstate[g] == ST_ALERT -> 0f
            gwalk[g] -> vmax
            else -> 0f
        }
        val accel = if (target > gv[g]) heightPx * (if (running) 1.8f else 0.5f) else heightPx * 1.2f
        gv[g] += (target - gv[g]).coerceIn(-accel * dt, accel * dt)
        gx[g] += gdir[g] * gv[g] * dt
        if (gv[g] > 0.5f) {
            // Stride length is about a fifth of the animal's height; a cycle covers one stance at 60%.
            val stride = heightPx * gsize[g] * 0.2f * (if (running) 1.9f else 1f)
            gph[g] += (0.6f * gv[g] / stride) * dt
        }
        if ((gx[g] < x0 && gdir[g] < 0f) || (gx[g] > x1 && gdir[g] > 0f)) {
            if (running) {
                // Nowhere left to run: stop and look back.
                endFlight(g)
            } else {
                gdir[g] = -gdir[g]
                gwalk[g] = false; gtimer[g] = 1f + rnd.nextFloat() * 2f; ggraze[g] = rnd.nextBoolean()
                gpend[g] = false
            }
        }
    }

    // ---------------------------------------------------------------- reactions

    /** A gull or pigeon: walks a few steps, pecks at the ground, or stands about, turning round now and then. */
    private fun updateGroundBird(g: Int, slot: GroundSlot, dt: Float, x0: Float, x1: Float) {
        if (gtimer[g] <= 0f) {
            val pick = rnd.nextFloat()
            gwalk[g] = pick < 0.4f
            ggraze[g] = !gwalk[g] && pick < 0.7f
            gtimer[g] = if (gwalk[g]) 1.5f + rnd.nextFloat() * 3f else if (ggraze[g]) 1.2f + rnd.nextFloat() * 2f else 2f + rnd.nextFloat() * 4f
            if (rnd.nextFloat() < 0.35f) gdir[g] = -gdir[g]
        }
        val v = if (gwalk[g]) 15f * unit * slot.scale else 0f
        gv[g] += (v - gv[g]).coerceIn(-60f * unit * dt, 60f * unit * dt)
        gx[g] += gdir[g] * gv[g] * dt
        if (gv[g] > 0.5f) gph[g] += dt * 4f
        if (gx[g] < x0 && gdir[g] < 0f) gdir[g] = 1f else if (gx[g] > x1 && gdir[g] > 0f) gdir[g] = -1f
    }

    /** Animals that hunt (or look like they might), and so are watched by the rest. */
    private fun isPredator(sp: Species) = sp == Species.FOX || sp == Species.JACKAL

    /** Species that run from a predator rather than only watching it. */
    private fun canFlee(sp: Species) = sp == Species.HARE || sp == Species.SHEEP || sp == Species.GOAT || sp == Species.DEER

    private fun reacts(sp: Species) = isPredator(sp) || canFlee(sp) ||
        sp == Species.HORSE || sp == Species.COW || sp == Species.WILD_COW

    /**
     * How close (as a fraction of the screen width) [other] has to come before [sp] notices it, or 0 if
     * it pays no attention. Hares, sheep, goats and deer watch then run; horses and cattle only watch;
     * a fox or jackal stops and watches the small animals it could hunt.
     */
    private fun reactionRange(sp: Species, other: Species): Float = when {
        isPredator(other) -> when (sp) {
            Species.HARE -> 0.34f
            Species.SHEEP, Species.GOAT -> if (other == Species.JACKAL) 0.26f else 0.15f
            Species.DEER -> if (other == Species.JACKAL) 0.26f else 0f
            Species.HORSE, Species.COW, Species.WILD_COW -> if (other == Species.JACKAL) 0.2f else 0f
            else -> 0f
        }
        sp == Species.JACKAL && canFlee(other) -> 0.3f
        sp == Species.FOX && other == Species.HARE -> 0.3f
        else -> 0f
    }

    /** About five times a second, lets each animal notice nearby predators or prey, and keeps runners running. */
    private fun scanThreats(dt: Float) {
        for (g in 0 until groundCount) if (gcool[g] > 0f) gcool[g] -= dt
        scanT -= dt
        if (scanT > 0f) return
        scanT = 0.2f
        for (g in 0 until groundCount) {
            val sp = slots.getOrNull(gSlot[g])?.species ?: continue
            if (!reacts(sp)) continue
            val fleeing = gstate[g] == ST_FLEE
            if (gstate[g] == ST_ALERT) continue
            // Having just reacted, an animal ignores a distant threat, but not one that walks right up to it.
            val range0 = if (fleeing) 1.7f else if (gcool[g] > 0f) 0.4f else 1f
            var best = -1; var bestD = Float.MAX_VALUE
            for (o in 0 until groundCount) {
                if (o == g) continue
                val osp = slots.getOrNull(gSlot[o])?.species ?: continue
                val range = reactionRange(sp, osp) * w * range0
                if (range <= 0f) continue
                val d = abs(gx[o] - gx[g])
                if (d < range && d < bestD) { best = o; bestD = d }
            }
            val side = if (best >= 0 && gx[best] >= gx[g]) 1f else -1f
            if (fleeing) {
                if (best < 0) endFlight(g) else { gthr[g] = side; gdir[g] = -side }
            } else if (best >= 0) {
                // It stops, turns to face what it has seen, and (if it is prey) gets ready to bolt.
                gstate[g] = ST_ALERT; gthr[g] = side; gdir[g] = side
                gflee[g] = canFlee(sp)
                gst[g] = if (gflee[g]) 0.5f + rnd.nextFloat() * 0.6f else 1.8f + rnd.nextFloat() * 2f
                gwalk[g] = false; ggraze[g] = false; gpend[g] = false; ghops[g] = 0
            }
        }
    }

    /** Watching is over: bolt if it was prey that saw a predator, otherwise carry on as before. */
    private fun endAlert(g: Int) {
        if (gthr[g] == 0f) { gstate[g] = ST_NORMAL; return }
        if (gflee[g]) {
            gstate[g] = ST_FLEE; gst[g] = 5f + rnd.nextFloat() * 3f
            gdir[g] = -gthr[g]; gwalk[g] = true; ggraze[g] = false
            ghops[g] = 99; gtimer[g] = 0f
        } else {
            gstate[g] = ST_NORMAL; gcool[g] = 14f + rnd.nextFloat() * 6f
            gwalk[g] = false; gtimer[g] = 0.5f + rnd.nextFloat()
            // A fox or jackal that has looked its fill usually wanders off the other way, not onto its prey.
            val sp = slots.getOrNull(gSlot[g])?.species
            if (sp != null && isPredator(sp) && rnd.nextFloat() < 0.75f) {
                gdir[g] = -gthr[g]; gwalk[g] = true; ggraze[g] = false; gtimer[g] = 4f + rnd.nextFloat() * 4f
            }
        }
    }

    /** A runner has got clear (or is cornered): it stops and looks back at where it ran from. */
    private fun endFlight(g: Int) {
        gstate[g] = ST_ALERT; gflee[g] = false
        gst[g] = 2.5f + rnd.nextFloat() * 2f
        gdir[g] = if (gthr[g] != 0f) gthr[g] else -gdir[g]
        gwalk[g] = false; ggraze[g] = false; ghops[g] = 0
    }

    /**
     * A hare sits up and watches, nibbles with its head down, or bounds off in a few hops, crouching to
     * spring and landing in a crouch. When running it takes long, fast leaps.
     */
    private fun updateHare(g: Int, slot: GroundSlot, dt: Float, x0In: Float, x1In: Float) {
        val body = slot.species.heightFraction * h * slot.scale
        val fleeing = gstate[g] == ST_FLEE
        if (gstate[g] != ST_NORMAL && gu[g] < 0f) {
            gst[g] -= dt
            if (gst[g] <= 0f) { if (gstate[g] == ST_ALERT) endAlert(g) else endFlight(g) }
        }
        val reach = if (gstate[g] == ST_FLEE) 0.08f * w else 0f
        val x0 = x0In - reach
        val x1 = x1In + reach
        if (gu[g] >= 0f) {
            val dur = if (fleeing) 0.3f else 0.4f
            val dist = body * (if (fleeing) 2.7f else 2.1f)
            gu[g] += dt / dur
            gx[g] += gdir[g] * dist / dur * dt
            if (gu[g] >= 1f) {
                gu[g] = -1f
                ghops[g]--
                gtimer[g] = if (fleeing) 0.03f else 0.16f
            }
        } else if (gstate[g] != ST_ALERT && gtimer[g] <= 0f) {
            if (ghops[g] > 0) {
                val hopDist = body * (if (fleeing) 2.7f else 2.1f)
                val heading = gx[g] + gdir[g] * hopDist
                if (heading < x0 || heading > x1) {
                    if (fleeing) { endFlight(g); return }
                    gdir[g] = -gdir[g]
                }
                gu[g] = 0f
            } else {
                val pick = rnd.nextFloat()
                when {
                    pick < 0.4f -> { ggraze[g] = true; gtimer[g] = 2f + rnd.nextFloat() * 3.5f }
                    pick < 0.65f -> { ggraze[g] = false; gtimer[g] = 1.5f + rnd.nextFloat() * 3f }
                    else -> {
                        ggraze[g] = false
                        ghops[g] = 2 + rnd.nextInt(3)
                        val mid = (x0 + x1) / 2f
                        gdir[g] = if (rnd.nextFloat() < 0.7f) (if (gx[g] < mid) 1f else -1f) else (if (rnd.nextBoolean()) 1f else -1f)
                        gtimer[g] = 0.25f
                    }
                }
            }
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
        meadow.drawFlowers(canvas)
        for (g in 0 until groundCount) {
            val slot = slots.getOrNull(gSlot[g]) ?: continue
            val sp = slot.species
            val k = slot.scale / BAKE_SCALE
            if (sp == Species.DOLPHIN) {
                val leaderU = gu[g]
                if (leaderU < -0.3f) continue
                val bmp = sprite(sp, 0) ?: continue

                val podOffsets = POD_OFFSETS
                val podScales = POD_SCALES
                val podYOffsets = POD_Y_OFFSETS

                for (p in 0 until 2) {
                    val u = leaderU + podOffsets[p]
                    val dir = gdir[g]
                    val span = w * 0.42f
                    val curScale = k * podScales[p]

                    // Surface Swimming / Fin Breaching Phase
                    if ((u in -0.3f..0f) || (u in 1.0f..1.3f)) {
                        val swimU = if (u < 0f) u else (u - 1.0f)
                        val x = gstart[g] + dir * (swimU - 0.5f) * span
                        val surfaceY = slot.y.at(x) + podYOffsets[p] * h
                        val finY = surfaceY + 12f * unit

                        canvas.save()
                        canvas.clipRect(-w * 0.08f, 0f, w * 1.08f, surfaceY + 2f * unit)
                        canvas.translate(x + parallaxPx * 0.5f, finY)
                        if (dir < 0f) {
                            canvas.scale(-curScale, curScale)
                        } else {
                            canvas.scale(curScale, curScale)
                        }
                        canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, spritePaint)
                        canvas.restore()

                        wakePaint.strokeWidth = 1.8f * unit
                        canvas.drawLine(
                            x - dir * 15f * unit, surfaceY,
                            x - dir * 35f * unit, surfaceY + sin(time * 5f) * 2f * unit,
                            wakePaint
                        )
                        continue
                    }

                    if (u !in 0f..1f) continue

                    // Leaping Arc Phase
                    val x = gstart[g] + dir * (u - 0.5f) * span
                    val surfaceY = slot.y.at(x) + podYOffsets[p] * h
                    val jumpHeight = h * 0.075f * slot.scale * podScales[p]
                    val y = surfaceY - sin(u * PI.toFloat()) * jumpHeight

                    val jumpFrame = when {
                        u < 0.28f -> 0
                        u < 0.72f -> 1
                        else -> 2
                    }
                    val frameBmp = sprite(sp, jumpFrame) ?: bmp

                    // Smooth trajectory pitch (always keeps dolphin upright)
                    val climbSlope = cos(u * PI.toFloat())
                    val maxPitchDeg = 40f

                    canvas.save()
                    canvas.clipRect(-w * 0.08f, 0f, w * 1.08f, surfaceY + 6f * unit)
                    canvas.translate(x + parallaxPx * 0.5f, y)

                    if (dir > 0f) {
                        val pitch = -maxPitchDeg * climbSlope
                        canvas.rotate(pitch)
                        canvas.scale(curScale, curScale)
                    } else {
                        val pitch = maxPitchDeg * climbSlope
                        canvas.rotate(pitch)
                        canvas.scale(-curScale, curScale)
                    }

                    canvas.drawBitmap(frameBmp, -frameBmp.width / 2f, -frameBmp.height / 2f, spritePaint)
                    canvas.restore()

                    val emergeX = gstart[g] - dir * 0.5f * span
                    val entryX = gstart[g] + dir * 0.5f * span

                    if (u in 0.01f..0.22f) {
                        val pulse = sin((u / 0.22f) * PI.toFloat())
                        foamPaint.alpha = (210 * pulse).toInt().coerceIn(0, 255)
                        canvas.drawOval(
                            emergeX - 22f * unit * pulse, surfaceY - 3f * unit,
                            emergeX + 22f * unit * pulse, surfaceY + 5f * unit,
                            foamPaint
                        )
                        repeat(5) { idx ->
                            val dropX = emergeX + (idx - 2.0f) * 6f * unit
                            val dropY = surfaceY - (8f + idx * 3f) * unit * pulse
                            canvas.drawCircle(dropX, dropY, 2.8f * unit * pulse, foamPaint)
                        }
                    }

                    if (u in 0.78f..0.99f) {
                        val pulse = sin(((u - 0.78f) / 0.21f) * PI.toFloat())
                        foamPaint.alpha = (210 * pulse).toInt().coerceIn(0, 255)
                        canvas.drawOval(
                            entryX - 24f * unit * pulse, surfaceY - 3f * unit,
                            entryX + 24f * unit * pulse, surfaceY + 5f * unit,
                            foamPaint
                        )
                        repeat(6) { idx ->
                            val dropX = entryX + (idx - 2.5f) * 6f * unit
                            val dropY = surfaceY - (10f + idx * 2.5f) * unit * pulse
                            canvas.drawCircle(dropX, dropY, 3.0f * unit * pulse, foamPaint)
                        }
                    }
                }
                continue
            }
            val frame = when {
                AnimalSprites.isQuadruped(sp) -> when {
                    gv[g] > 0.5f -> {
                        val step = (((gph[g] % 1f) + 1f) % 1f * Quadruped.WALK_FRAMES).toInt().coerceIn(0, Quadruped.WALK_FRAMES - 1)
                        if (gstate[g] == ST_FLEE) Quadruped.GALLOP_FRAME + step else step
                    }
                    gstate[g] == ST_ALERT -> Quadruped.ALERT_FRAME
                    ggraze[g] -> Quadruped.GRAZE_FRAME
                    // Standing: now and then the ear flicks and the tail swings.
                    ((time + g * 1.7f) % 7f) < 0.4f && ((time * 14f).toInt() and 1) == 0 -> Quadruped.TWITCH_FRAME
                    else -> Quadruped.STAND_FRAME
                }
                // Airborne, crouched between hops, nibbling, or sitting up to look.
                sp == Species.HARE -> when {
                    gu[g] >= 0f -> 2
                    ghops[g] > 0 && gtimer[g] > 0f -> 3
                    ggraze[g] -> 1
                    else -> 0
                }
                // Swimming, dabbling tail up, or floating with the bill tucked into the wing.
                sp == Species.DUCK -> when {
                    ggraze[g] && gv[g] < 0.5f -> 1
                    !gwalk[g] && gv[g] < 0.5f && ((time + g * 3.1f) % 12f) < 2.2f -> 2
                    else -> 0
                }
                sp == Species.CRAB -> when {
                    gwalk[g] -> if (((gph[g] * 2f).toInt() and 1) == 1) 1 else 0
                    ggraze[g] -> 2 + ((time * 2.6f).toInt() and 1)
                    else -> 0
                }
                // Standing, mid-step, or bobbing down to peck.
                sp == Species.GULL -> when {
                    gv[g] > 0.5f -> if (((gph[g] * 2f).toInt() and 1) == 1) 1 else 0
                    ggraze[g] -> if (((time * 3.2f + g).toInt() and 1) == 0) 2 else 0
                    else -> 0
                }
                // Sitting, calling with the throat sac pulsing, or in the air.
                sp == Species.FROG -> if (gu[g] >= 0f) 2 else if (gwalk[g] && sin(time * 13f + g * 1.7f) > 0f) 1 else 0
                else -> if (gwalk[g] && ((gph[g] * 2f).toInt() and 1) == 1) 1 else 0
            }
            val bmp = sprite(sp, frame, gvar[g]) ?: continue
            val size = k * gsize[g]
            var y = slot.y.at(gx[g]) + gyoff[g]
            if (sp == Species.DUCK) y += sin(gph[g] * 2.2f) * h * 0.0012f
            if (sp == Species.FROG && gu[g] >= 0f) y -= sin(gu[g] * PI.toFloat()) * sp.heightFraction * h * slot.scale * 1.3f
            var pitch = 0f
            if (sp == Species.HARE && gu[g] >= 0f) {
                // A leap is a smooth arc, the nose up on take-off and down on landing.
                val arc = if (gstate[g] == ST_FLEE) 0.7f else 0.95f
                y -= sin(gu[g] * PI.toFloat()) * sp.heightFraction * h * slot.scale * arc
                pitch = -cos(gu[g] * PI.toFloat()) * 16f * gdir[g]
            }
            if (sp == Species.DUCK) drawDuckRipples(canvas, g, slot, y)
            canvas.save()
            canvas.translate(gx[g] + parallaxPx * 0.8f, y)
            if (pitch != 0f) canvas.rotate(pitch)
            canvas.scale(gdir[g] * size, size)
            canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height.toFloat(), spritePaint)
            canvas.restore()
            if (sp == Species.DUCK && gvar[g] == 1) drawDucklings(canvas, g, slot, y)
        }
        for (i in 0 until fireflies) {
            // A firefly shows a brief flash every few seconds, then goes dark while it drifts on.
            val frac = (time * 0.33f * ffsp[i] + ffph[i] / 6.2832f) % 1f
            if (frac > 0.2f) continue
            val a = sin(PI.toFloat() * frac / 0.2f)
            glowPaint.color = 0xFFE8F27A.toInt()
            glowPaint.alpha = (60 * a).toInt()
            canvas.drawCircle(ffx[i] + parallaxPx * 0.8f, ffy[i], 7f * unit, glowPaint)
            glowPaint.alpha = (255 * a).toInt()
            canvas.drawCircle(ffx[i] + parallaxPx * 0.8f, ffy[i], 2f * unit, glowPaint)
        }
        meadow.drawFlyers(canvas)
        FaunaDebug.sheet?.let { drawSheet(canvas, it) }
    }

    /** A V of ripples behind a paddling duck, and rings spreading where a dabbling one's bill goes under. */
    private fun drawDuckRipples(canvas: Canvas, g: Int, slot: GroundSlot, y: Float) {
        val dw = slot.species.heightFraction * slot.species.aspect * h * slot.scale
        val x = gx[g] + parallaxPx * 0.8f
        val dir = gdir[g]
        wakePaint.strokeWidth = 1.3f * unit
        if (gv[g] > 1f) {
            wakePaint.alpha = (110 * (gv[g] / (6f * unit)).coerceIn(0f, 1f)).toInt()
            val sx = x - dir * dw * 0.4f
            for (s in -1..1 step 2) canvas.drawLine(sx, y - unit, sx - dir * dw * 0.75f, y + s * 3.2f * unit * slot.scale, wakePaint)
        }
        if (ggraze[g] && gv[g] < 0.5f) {
            val bx = x + dir * dw * 0.38f
            for (k in 0..1) {
                val t = (time * 0.9f + g * 0.37f + k * 0.5f) % 1f
                wakePaint.alpha = (140 * (1f - t)).toInt()
                val rx = (4f + 16f * t) * unit * slot.scale
                canvas.drawOval(bx - rx, y - rx * 0.18f, bx + rx, y + rx * 0.18f, wakePaint)
            }
        }
        wakePaint.alpha = 0xAA
    }

    /** The hen's three ducklings, small and yellow, paddling in her wake. */
    private fun drawDucklings(canvas: Canvas, g: Int, slot: GroundSlot, y: Float) {
        val bmp = sprite(slot.species, 0, 2) ?: return
        val size = slot.scale / BAKE_SCALE * 0.42f
        for (k in 0 until 3) {
            val bob = sin(time * 3.2f + k * 1.4f + g) * h * 0.0009f
            canvas.save()
            canvas.translate(gdlx[g * 3 + k] + parallaxPx * 0.8f, y + bob)
            canvas.scale(gdir[g] * size, size)
            canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height.toFloat(), spritePaint)
            canvas.restore()
        }
    }

    /** Debug aid, see [FaunaDebug]: every frame of one species in a grid on a dark panel. */
    private fun drawSheet(canvas: Canvas, spec: String) {
        val parts = spec.split(':')
        val panel = Paint().apply { color = 0xE0101820.toInt() }
        canvas.drawRect(0f, h * 0.22f, w, h * 0.78f, panel)
        if (parts[0] == "BIRD") {
            val cell = w / (BirdSprites.GLIDE_FRAME + 1)
            for (kind in 0..4) for (f in 0..BirdSprites.GLIDE_FRAME) {
                val bmp = birdFrame(kind, f) ?: continue
                val k = cell / bmp.width * 1.1f
                canvas.save()
                canvas.translate(f * cell, h * 0.24f + kind * cell * 0.95f)
                canvas.scale(k, k)
                canvas.drawBitmap(bmp, 0f, 0f, spritePaint)
                canvas.restore()
            }
            return
        }
        val sp = Species.values().firstOrNull { it.name == parts[0] } ?: return
        val variant = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val n = when {
            AnimalSprites.isQuadruped(sp) -> Quadruped.GALLOP_FRAME + Quadruped.WALK_FRAMES
            sp == Species.HARE -> 4
            sp == Species.FROG || sp == Species.DOLPHIN || sp == Species.DUCK || sp == Species.GULL -> 3
            sp == Species.CRAB -> 4
            else -> 1
        }
        val first = sprite(sp, 0, variant) ?: return
        val cols = (w / (first.width * 1.04f)).toInt().coerceAtLeast(1)
        val cw = first.width * 1.04f
        val ch = first.height * 1.15f
        val rowLine = Paint().apply { color = 0x33FFFFFF }
        for (f in 0 until n) {
            val bmp = sprite(sp, f, variant) ?: continue
            val x = (f % cols) * cw
            val y = h * 0.24f + (f / cols) * ch
            canvas.drawLine(x, y + first.height, x + cw, y + first.height, rowLine)
            canvas.drawBitmap(bmp, x, y, spritePaint)
        }
    }

    private fun sprite(sp: Species, frame: Int, variant: Int = 0): Bitmap? {
        val key = (sp.ordinal * 8 + variant) * 32 + frame
        sprites[key]?.let { return it }
        val px = (sp.heightFraction * h * BAKE_SCALE).toInt()
        val bmp = AnimalSprites.bake(sp, frame, px, variant) ?: return null
        sprites[key] = bmp
        return bmp
    }

    private companion object {
        const val MAX_FLY = 24
        const val MAX_GROUND = 20
        const val MAX_FF = 30
        const val BAKE_SCALE = 1.5f
        const val HOP_SECONDS = 0.55f
        /** How far, as a fraction of the screen width, a flock's members can be from its leader. */
        const val FLOCK_REACH = 0.14f
        const val GULL_DIVE_SECONDS = 3.0f
        const val ST_NORMAL = 0
        const val ST_ALERT = 1
        const val ST_FLEE = 2
        val POD_OFFSETS = floatArrayOf(0f, -0.22f)
        val POD_SCALES = floatArrayOf(1.0f, 0.8f)
        /** Depth offset of each pod member, as a fraction of the screen height. */
        val POD_Y_OFFSETS = floatArrayOf(0f, 0.010f)
        /** Coats of a horse herd in the order they join it: bay, chestnut, foal, grey, black. */
        val HORSE_MIX = intArrayOf(0, 1, AnimalSprites.FOAL, 2, 3)
    }
}
