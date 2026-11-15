package com.skymood.wallpaper.landscape

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.sin

/**
 * Bends the trees painted into one layer bitmap. The bitmap is drawn through a mesh; the
 * vertices around every tree swing further the higher they are above that tree's foot, with a
 * quicker flutter on top, so trunks barely move, limbs bend and leaves ripple. Vertices that
 * belong to no tree never move, so ground, water and buildings stay put. All the geometry is
 * worked out once, when the layer is painted; a frame only moves the vertices near trees.
 */
internal class WindMesh(
    bitmapW: Int, bitmapH: Int, marks: List<TreeMark>, originX: Float, originY: Float,
) {
    val cols: Int
    val rows: Int
    /** Vertex positions in bitmap pixels; the grid is the undistorted layer. */
    val verts: FloatArray

    private val restX: FloatArray
    private val restY: FloatArray
    private val treeSize = FloatArray(marks.size)
    private val treePhase = FloatArray(marks.size)
    private val swing = FloatArray(marks.size)
    private val activeVertex: IntArray
    private val activeTree: IntArray
    private val activeSide: FloatArray   // horizontal weight, fading out beyond the crown
    private val activeHeight: FloatArray // 0 at the foot, 1 at the top of the tree

    init {
        val cell = max(10f, sqrt(bitmapW.toFloat() * bitmapH / 9000f))
        cols = ceil(bitmapW / cell).toInt().coerceAtLeast(1)
        rows = ceil(bitmapH / cell).toInt().coerceAtLeast(1)
        val cw = bitmapW.toFloat() / cols
        val ch = bitmapH.toFloat() / rows
        val count = (cols + 1) * (rows + 1)
        restX = FloatArray(count); restY = FloatArray(count)
        verts = FloatArray(count * 2)
        for (r in 0..rows) for (c in 0..cols) {
            val i = r * (cols + 1) + c
            restX[i] = c * cw; restY[i] = r * ch
            verts[i * 2] = restX[i]; verts[i * 2 + 1] = restY[i]
        }

        val best = FloatArray(count)
        val bestTree = IntArray(count) { -1 }
        val bestSide = FloatArray(count)
        val bestHeight = FloatArray(count)
        for ((n, m) in marks.withIndex()) {
            treeSize[n] = m.height * m.stiffness
            treePhase[n] = m.x * 0.006f
            val lx = m.x + originX
            val ly = m.baseY - originY
            val reach = m.halfWidth * 1.4f
            val c0 = ((lx - reach) / cw).toInt().coerceIn(0, cols)
            val c1 = ceil((lx + reach) / cw).toInt().coerceIn(0, cols)
            val r0 = ((ly - m.height * 1.05f) / ch).toInt().coerceIn(0, rows)
            val r1 = ceil(ly / ch).toInt().coerceIn(0, rows)
            for (r in r0..r1) {
                val up = ly - restY[r * (cols + 1)]
                if (up <= 0f) continue
                val f = (up / m.height).coerceAtMost(1f)
                for (c in c0..c1) {
                    val i = r * (cols + 1) + c
                    val away = kotlin.math.abs(restX[i] - lx)
                    // Full strength across the crown, fading to nothing a little beyond it.
                    val side = 1f - ((away - m.halfWidth) / (reach - m.halfWidth)).coerceIn(0f, 1f)
                    val strength = side * f * f
                    if (strength > best[i]) {
                        best[i] = strength; bestTree[i] = n; bestSide[i] = side; bestHeight[i] = f
                    }
                }
            }
        }
        var active = 0
        for (i in 0 until count) if (bestTree[i] >= 0) active++
        activeVertex = IntArray(active); activeTree = IntArray(active)
        activeSide = FloatArray(active); activeHeight = FloatArray(active)
        var k = 0
        for (i in 0 until count) if (bestTree[i] >= 0) {
            activeVertex[k] = i; activeTree[k] = bestTree[i]
            activeSide[k] = bestSide[i]; activeHeight[k] = bestHeight[i]
            k++
        }
    }

    val hasTrees: Boolean get() = activeVertex.isNotEmpty()

    /** Moves the vertices for time [t] (seconds); stronger and quicker as [windKmh] rises. */
    fun update(t: Float, windKmh: Float) {
        val amp = (0.006f + windKmh * 0.0016f).coerceAtMost(0.06f)
        val freq = 0.7f + windKmh / 25f
        for (n in swing.indices) swing[n] = (sin(t * freq + treePhase[n]) * 0.65f + 0.35f) * amp * treeSize[n]
        for (k in activeVertex.indices) {
            val v = activeVertex[k]
            val f2 = activeHeight[k] * activeHeight[k]
            val tree = activeTree[k]
            val flutter = sin(t * freq * 2.6f + restX[v] * 0.045f + restY[v] * 0.03f)
            val dx = activeSide[k] * f2 * (swing[tree] + 0.22f * amp * treeSize[tree] * flutter)
            val dy = activeSide[k] * f2 * 0.1f * amp * treeSize[tree] * sin(t * freq * 2.1f + restX[v] * 0.05f)
            verts[v * 2] = restX[v] + dx
            verts[v * 2 + 1] = restY[v] + dy
        }
    }
}
