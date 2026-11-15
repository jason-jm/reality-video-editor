package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Bytes
import com.example.prismstudio.p40b43a88.domain.model.DeviceMediaItem
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset

/** Import-sheet rules: what can be picked, the selection line, and de-duplication against the pool. */
class ImportMediaUseCase {

    fun toggle(selected: Set<String>, item: DeviceMediaItem, pool: List<MediaAsset>): Set<String> = when {
        isImported(item, pool) -> selected
        item.id in selected -> selected - item.id
        else -> selected + item.id
    }

    fun isImported(item: DeviceMediaItem, pool: List<MediaAsset>): Boolean = pool.any { it.uri == item.uri }

    /** Picked items that are not in the pool yet, in device order. */
    fun pending(items: List<DeviceMediaItem>, selected: Set<String>, pool: List<MediaAsset>): List<DeviceMediaItem> =
        items.filter { it.id in selected && !isImported(it, pool) }

    fun summary(items: List<DeviceMediaItem>, selected: Set<String>): String {
        val picked = items.filter { it.id in selected }
        if (picked.isEmpty()) return "未选择素材"
        return "已选择 ${picked.size} 项 · ${Bytes.label(picked.sumOf { it.sizeBytes })}"
    }
}
