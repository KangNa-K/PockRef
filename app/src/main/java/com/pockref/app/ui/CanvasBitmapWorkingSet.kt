package com.pockref.app.ui

/**
 * Android equivalent of iOS VisibleImageLODWorkingSet.
 *
 * The cache is bounded by decoded pixel cost and entry count. The caller
 * protects the current viewport and interaction targets from eviction.
 */
internal class CanvasBitmapWorkingSet(
    private val maximumEntryCount: Int = DEFAULT_MAXIMUM_ENTRY_COUNT,
    private val maximumCostBytes: Long = defaultMaximumCostBytes(),
) {
    internal data class BitmapInfo(
        val level: Int,
        val width: Int,
        val height: Int,
    ) {
        val costBytes: Long
            get() = width.toLong().coerceAtLeast(1) *
                height.toLong().coerceAtLeast(1) * BYTES_PER_PIXEL
    }

    private data class Entry(val info: BitmapInfo, val access: Long)

    private val entries = mutableMapOf<String, Entry>()
    private var accessCounter = 0L

    @Synchronized
    fun update(
        cached: Map<String, BitmapInfo>,
        protectedIds: Set<String>,
    ): Set<String> {
        entries.keys.retainAll(cached.keys)
        cached.forEach { (id, info) ->
            entries[id] = Entry(info, entries[id]?.access ?: nextAccess())
        }
        protectedIds.forEach { id ->
            entries[id]?.let { entries[id] = it.copy(access = nextAccess()) }
        }

        val evicted = linkedSetOf<String>()
        while (entries.size > maximumEntryCount || totalCost() > maximumCostBytes) {
            val victim = entries.entries
                .asSequence()
                .filter { it.key !in protectedIds }
                .minByOrNull { it.value.access }
                ?.key ?: break
            entries.remove(victim)
            evicted += victim
        }
        return evicted
    }

    private fun totalCost(): Long = entries.values.sumOf { it.info.costBytes }

    private fun nextAccess(): Long = ++accessCounter

    companion object {
        const val DEFAULT_MAXIMUM_ENTRY_COUNT = 512
        private const val BYTES_PER_PIXEL = 4L
        private const val MINIMUM_COST_BYTES = 32L * 1024 * 1024
        private const val MAXIMUM_COST_BYTES = 96L * 1024 * 1024

        fun defaultMaximumCostBytes(maxHeapBytes: Long = Runtime.getRuntime().maxMemory()): Long =
            (maxHeapBytes / 16).coerceIn(MINIMUM_COST_BYTES, MAXIMUM_COST_BYTES)
    }
}
