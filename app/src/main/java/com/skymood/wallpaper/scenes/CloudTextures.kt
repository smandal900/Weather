package com.skymood.wallpaper.scenes

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Real cloud photographs (CC0 / public domain, see assets/clouds/LICENSES.txt) turned into
 * drifting cloud banks. Each photo is decoded once on a background thread and converted to a
 * grey-scale cloud with its own transparency, so the sky behind shows through and the colour of
 * the light (day, dusk, night, storm) can be applied when drawing:
 *
 * - SKY photos have clouds against blue sky: transparency is how little blue each pixel has.
 * - VEIL photos are dark cloud masses against a brighter horizon: darker pixels are cloud.
 *
 * The edges are feathered, so overlapping banks blend into one continuous sky.
 */
object CloudTextures {

    enum class Group { CUMULUS, OVERCAST, RAIN, STORM }

    private enum class Mode { SKY, VEIL }

    private class Def(val asset: String, val mode: Mode)

    private val defs = mapOf(
        Group.CUMULUS to listOf(
            Def("cu_1", Mode.SKY), Def("cu_2", Mode.SKY), Def("cu_3", Mode.SKY), Def("cu_4", Mode.SKY), Def("cu_5", Mode.SKY),
        ),
        Group.OVERCAST to listOf(Def("ov_1", Mode.VEIL), Def("st_2", Mode.SKY)),
        Group.RAIN to listOf(Def("ov_1", Mode.VEIL), Def("st_1", Mode.VEIL)),
        Group.STORM to listOf(Def("st_1", Mode.VEIL), Def("st_3", Mode.VEIL), Def("st_2", Mode.SKY)),
    )

    @Volatile private var loaded: Map<Group, List<Bitmap>> = emptyMap()
    private var started = false

    /** Starts decoding in the background; until it finishes the scenes use their drawn clouds. */
    @Synchronized
    fun init(assets: AssetManager) {
        if (started) return
        started = true
        Executors.newSingleThreadExecutor { r -> Thread(r, "SkyMoodClouds").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
            .execute {
                val cache = HashMap<String, Bitmap?>()
                val result = HashMap<Group, List<Bitmap>>()
                for ((group, list) in defs) {
                    result[group] = list.mapNotNull { d -> cache.getOrPut(d.asset + d.mode) { decode(assets, d) } }
                }
                loaded = result
            }
    }

    /** The cloud banks for [group], or an empty list while they are still loading. */
    fun get(group: Group): List<Bitmap> = loaded[group].orEmpty()

    private fun decode(assets: AssetManager, d: Def): Bitmap? = try {
        val src = assets.open("clouds/${d.asset}.jpg").use { BitmapFactory.decodeStream(it) } ?: return null
        val out = convert(src, d.mode)
        src.recycle()
        out
    } catch (e: Exception) {
        null
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun convert(src: Bitmap, mode: Mode): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        // Luminance range of the picture, used by the veil mode.
        val hist = IntArray(256)
        for (c in px) {
            val l = ((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
            hist[l]++
        }
        fun percentile(p: Float): Float {
            var sum = 0
            val goal = (px.size * p).toInt()
            for (i in 0 until 256) { sum += hist[i]; if (sum >= goal) return i / 255f }
            return 1f
        }
        val p05 = percentile(0.05f)
        val p25 = percentile(0.25f)
        val p90 = percentile(0.9f).coerceAtLeast(p25 + 0.1f)

        val out = IntArray(w * h)
        val fadeX = w * 0.2f
        val fadeY = h * 0.3f
        for (y in 0 until h) {
            val fy = smooth(0f, fadeY, min(y.toFloat(), (h - 1 - y).toFloat()))
            for (x in 0 until w) {
                val c = px[y * w + x]
                val r = (c shr 16 and 0xFF) / 255f
                val g = (c shr 8 and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val alpha: Float
                val grey: Float
                if (mode == Mode.SKY) {
                    alpha = 1f - smooth(0.06f, 0.3f, b - r)
                    grey = ((lum - 0.1f) / 0.65f).coerceIn(0f, 1f)
                } else {
                    alpha = 1f - smooth(p25, p90, lum)
                    grey = (0.4f + 0.6f * ((lum - p05) / (p90 - p05))).coerceIn(0f, 1f)
                }
                val fx = smooth(0f, fadeX, min(x.toFloat(), (w - 1 - x).toFloat()))
                val a = (alpha * fx * fy * 255f).toInt().coerceIn(0, 255)
                val v = (grey * 255f).toInt().coerceIn(0, 255)
                out[y * w + x] = (a shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }
}
