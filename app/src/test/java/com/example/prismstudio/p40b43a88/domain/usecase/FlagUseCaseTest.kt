package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Resolve's flags on source media, picked through its clips. */
class FlagUseCaseTest {

    private val flags = FlagUseCase()

    // A and A2 both play "a"; B plays "b"; T is a title; S is a's sound on A1.
    private val doc = projectOf(
        timeline = timelineOf(
            clipOf("B", 120, 60, assetId = "b"),
            clipOf("A", 0, 60, assetId = "a", linkId = "L"),
            clipOf("S", 0, 60, track = Timeline.A1, assetId = "a", linkId = "L"),
            clipOf("A2", 60, 60, assetId = "a"),
            titleClipOf("T", 0, 60),
        ),
        media = listOf(assetOf("a"), assetOf("b"), assetOf("unused")),
    )

    private fun rejection(block: () -> Unit): String? = try {
        block()
        fail("expected a refusal")
        null
    } catch (e: EditRejected) {
        e.message
    }

    private fun flagsOf(d: com.example.prismstudio.p40b43a88.domain.model.ProjectDocument, id: String) = d.media.first { it.id == id }.flags

    @Test
    fun `a clip's flags are its media's - each media once, in timeline order, titles have none`() {
        assertEquals(listOf("a", "b"), flags.mediaOf(doc, listOf("B", "A2", "S", "A", "T")))
        assertEquals("所选片段没有素材，无法标记旗标", rejection { flags.mediaOf(doc, listOf("T")) })
        assertEquals("请先在时间线上选择片段", rejection { flags.mediaOf(doc, emptyList()) })
    }

    @Test
    fun `a flag goes on in palette order, several at once, and a second toggle takes it off`() {
        val red = flags.toggle(doc, listOf("a"), FlagColor.RED)
        assertEquals("已为素材加上红色旗标", red.message)
        val both = flags.toggle(red.doc, listOf("a"), FlagColor.BLUE).doc
        assertEquals(listOf(FlagColor.BLUE, FlagColor.RED), flagsOf(both, "a"))
        assertEquals("other media keep theirs", emptyList<FlagColor>(), flagsOf(both, "b"))
        val off = flags.toggle(both, listOf("a"), FlagColor.RED)
        assertEquals("已移除红色旗标", off.message)
        assertEquals(listOf(FlagColor.BLUE), flagsOf(off.doc, "a"))
    }

    @Test
    fun `on several media a toggle adds to all unless every one has it, and clear takes every flag off`() {
        val one = flags.toggle(doc, listOf("a"), FlagColor.GREEN).doc
        val all = flags.toggle(one, listOf("a", "b"), FlagColor.GREEN)
        assertEquals("已为 2 个素材加上绿色旗标", all.message)
        assertEquals(listOf(FlagColor.GREEN), flagsOf(all.doc, "b"))
        assertEquals(listOf(FlagColor.GREEN), flagsOf(all.doc, "a"))
        val none = flags.toggle(all.doc, listOf("a", "b"), FlagColor.GREEN)
        assertEquals("已移除 2 个素材的绿色旗标", none.message)
        val marked = flags.toggle(flags.toggle(doc, listOf("a", "b"), FlagColor.YELLOW).doc, listOf("a"), FlagColor.PINK).doc
        val cleared = flags.clear(marked, listOf("a", "b"))
        assertEquals("已清除 2 个素材的旗标", cleared.message)
        assertEquals(emptyList<FlagColor>(), flagsOf(cleared.doc, "a"))
        assertEquals("所选素材没有旗标", rejection { flags.clear(cleared.doc, listOf("a")) })
        assertEquals("请先选择素材", rejection { flags.toggle(doc, listOf("gone"), FlagColor.RED) })
        assertEquals("the timeline is untouched", doc.timeline, cleared.doc.timeline)
    }
}
