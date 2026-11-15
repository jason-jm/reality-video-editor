package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.MediaAsset

/** What the media pool is ordered by (Resolve's media pool sort / list view columns). */
enum class MediaSort(val label: String) {
    /** The pool's own order: as imported. */
    IMPORTED("导入顺序"),
    NAME("名称"),
    KIND("类型"),
    DURATION("时长"),
    RESOLUTION("分辨率"),
    /** Scene, then shot, then take — media without a slate after those with one. */
    SLATE("场记"),
    USAGE("使用次数"),
}

/**
 * Orders the media pool (Resolve's sort by column): by one [MediaSort], ascending or not. Names and slates compare
 * naturally — digit runs as numbers, so 2 comes before 10 and 3 before 3A —, ties keep the import order, and media
 * without a slate stay last whichever way round.
 */
class SortMediaUseCase {

    data class Order(val by: MediaSort = MediaSort.IMPORTED, val ascending: Boolean = true) {
        /** `pool-sort-chip`: "按名称 ↑" (by import order, ↑ is oldest first). */
        val label: String get() = "按${by.label} ${if (ascending) "↑" else "↓"}"
    }

    operator fun invoke(items: List<MediaAsset>, order: Order, usage: Map<String, Int> = emptyMap()): List<MediaAsset> {
        if (order.by == MediaSort.IMPORTED) return if (order.ascending) items else items.reversed()
        val indexed = items.withIndex().toList()
        val key: Comparator<IndexedValue<MediaAsset>> = when (order.by) {
            MediaSort.NAME -> Comparator { a, b -> natural(a.value.name, b.value.name) }
            MediaSort.KIND -> compareBy { it.value.kind.ordinal }
            MediaSort.DURATION -> compareBy { it.value.durationMs }
            MediaSort.RESOLUTION -> compareBy { it.value.width.toLong() * it.value.height }
            MediaSort.USAGE -> compareBy { usage[it.value.id] ?: 0 }
            MediaSort.SLATE -> Comparator { a, b -> slate(a.value, b.value) }
            MediaSort.IMPORTED -> compareBy { it.index }
        }
        val directed = if (order.ascending) key else key.reversed()
        // Media without a slate stay after those with one, either way round.
        val unslatedLast: Comparator<IndexedValue<MediaAsset>> =
            if (order.by == MediaSort.SLATE) compareBy { !it.value.metadata.hasSlate } else Comparator { _, _ -> 0 }
        return indexed.sortedWith(unslatedLast.then(directed).thenBy { it.index }).map { it.value }
    }

    private fun slate(a: MediaAsset, b: MediaAsset): Int {
        val x = a.metadata
        val y = b.metadata
        return natural(x.scene, y.scene).takeIf { it != 0 }
            ?: natural(x.shot, y.shot).takeIf { it != 0 }
            ?: natural(x.take, y.take)
    }

    companion object {
        private val CHUNK = Regex("\\d+|\\D+")

        /** Natural order, case ignored: digit runs compare as numbers ("2" < "10", "3" < "3A"); empty first. */
        fun natural(a: String, b: String): Int {
            val xs = CHUNK.findAll(a.lowercase()).map { it.value }.toList()
            val ys = CHUNK.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(xs.size, ys.size)) {
                val x = xs[i]
                val y = ys[i]
                val c = if (x[0].isDigit() && y[0].isDigit()) {
                    x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
                } else {
                    x.compareTo(y)
                }
                if (c != 0) return c
            }
            return xs.size.compareTo(ys.size)
        }
    }
}
