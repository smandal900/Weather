package com.skymood.wallpaper.particles

/** A reusable particle. Fields are interpreted by each scene. */
class Particle {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var size = 0f
    var length = 0f
    var alpha = 1f
    var life = 0f
    var maxLife = 0f
    var phase = 0f
    var layer = 0

    fun reset() {
        x = 0f; y = 0f; vx = 0f; vy = 0f; size = 0f; length = 0f
        alpha = 1f; life = 0f; maxLife = 0f; phase = 0f; layer = 0
    }
}

/**
 * Fixed-capacity pool. All particles are allocated up front; active ones live in
 * `[0, activeCount)`, so iterating, obtaining and releasing never allocate.
 *
 * Release while iterating backwards: `for (i in pool.activeCount - 1 downTo 0)`.
 */
class ParticlePool(val capacity: Int) {

    private val items = Array(capacity) { Particle() }

    var activeCount = 0
        private set

    operator fun get(index: Int): Particle = items[index]

    /** Returns a reset particle, or null when the pool is exhausted. */
    fun obtain(): Particle? {
        if (activeCount >= capacity) return null
        val p = items[activeCount++]
        p.reset()
        return p
    }

    fun release(index: Int) {
        val last = activeCount - 1
        if (index < 0 || index > last) return
        if (index != last) {
            val tmp = items[index]
            items[index] = items[last]
            items[last] = tmp
        }
        activeCount = last
    }

    fun clear() {
        activeCount = 0
    }
}
