package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.ClipMetadata
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.FlagRule
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.SmartBin
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.UsageRule
import com.example.prismstudio.p40b43a88.domain.usecase.SmartBinsUseCase.Rules
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.UiFixtures.clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's Smart Bins: saved searches over the media pool by kind, flag, usage and name. */
class SmartBinsUseCaseTest {

    private val use = SmartBinsUseCase(SequentialIds())

    /** v1 is on the open timeline (picture + its linked sound) and on 预告 twice; night is flagged red; the rest unused. */
    private val doc = UiFixtures.document(
        media = listOf(
            UiFixtures.video("v1"),
            UiFixtures.video("night", name = "Night Run.mp4").copy(flags = listOf(FlagColor.RED, FlagColor.BLUE)),
            UiFixtures.audio("au").copy(flags = listOf(FlagColor.GREEN)),
            UiFixtures.image("im"),
        ),
        clips = listOf(
            clip("c1", 0, 60, assetId = "v1", linkId = "L1"),
            clip("c1a", 0, 60, trackId = Timeline.A1, assetId = "v1", linkId = "L1"),
            clip("t1", 60, 30, kind = com.example.prismstudio.p40b43a88.domain.model.ClipKind.TITLE),
        ),
    ).copy(
        timelines = listOf(
            StoredTimeline(
                "tl-2", "预告", ProjectFormat(1920, 1080, 30),
                Timeline(Timeline.defaultTracks(), listOf(clip("p1", 0, 30, assetId = "v1"), clip("p2", 30, 30, assetId = "v1"), clip("p3", 60, 30, assetId = "au")), emptyList()),
                MixerState.forTracks(Timeline.defaultTracks()),
            ),
        ),
    )

    private fun ids(rules: Rules) = use.media(doc, rules).map { it.id }

    @Test
    fun `usage counts clips over every timeline, a linked picture and sound once`() {
        assertEquals(mapOf("v1" to 3, "au" to 1), use.usage(doc))
        assertEquals("3 次 · 时间线 1、预告 ×2", use.usageLabel(doc, "v1"))
        assertEquals("1 次 · 预告", use.usageLabel(doc, "au"))
        assertEquals("未使用", use.usageLabel(doc, "im"))
    }

    @Test
    fun `every rule must hold`() {
        assertEquals(listOf("v1", "night"), ids(Rules("x", kind = MediaKind.VIDEO)))
        assertEquals(listOf("night", "au"), ids(Rules("x", flag = FlagRule.FLAGGED)))
        assertEquals(listOf("v1", "im"), ids(Rules("x", flag = FlagRule.UNFLAGGED)))
        assertEquals(listOf("night"), ids(Rules("x", flag = FlagRule.BLUE)))
        assertEquals(listOf("v1", "au"), ids(Rules("x", usage = UsageRule.USED)))
        assertEquals(listOf("night", "im"), ids(Rules("x", usage = UsageRule.UNUSED)))
        assertEquals(listOf("night"), ids(Rules("x", nameContains = " night ")))
        assertEquals(listOf("night"), ids(Rules("x", kind = MediaKind.VIDEO, usage = UsageRule.UNUSED)))
        assertEquals(emptyList<String>(), ids(Rules("x", kind = MediaKind.IMAGE, flag = FlagRule.FLAGGED)))
        assertEquals("视频 · 红色旗标 · 未使用 · 名称含“夜”", use.describe(Rules("x", MediaKind.VIDEO, FlagRule.RED, UsageRule.UNUSED, " 夜 ")))
        assertEquals("全部素材", use.describe(Rules("x")))
    }

    @Test
    fun `smart bins are made, changed and deleted as the project's`() {
        val made = use.create(doc, Rules("  未用镜头 ", kind = MediaKind.VIDEO, usage = UsageRule.UNUSED))
        assertEquals(SmartBin(made.binId!!, "未用镜头", MediaKind.VIDEO, usage = UsageRule.UNUSED), made.doc.smartBins.single())
        assertEquals("已新建智能媒体夹“未用镜头”（1 个素材）", made.message)
        assertEquals("智能媒体夹 1", use.suggestedName(made.doc))
        val changed = use.update(made.doc, made.binId!!, Rules("未用镜头", usage = UsageRule.UNUSED))
        assertEquals("已更新智能媒体夹“未用镜头”（2 个素材）", changed.message)
        assertEquals("条件没有变化", assertThrows(EditRejected::class.java) { use.update(changed.doc, made.binId!!, Rules("未用镜头", usage = UsageRule.UNUSED)) }.message)
        val gone = use.delete(changed.doc, made.binId!!)
        assertEquals("已删除智能媒体夹“未用镜头”，素材仍在媒体池中", gone.message)
        assertEquals(emptyList<SmartBin>(), gone.doc.smartBins)
        assertEquals(doc.media, gone.doc.media)
    }

    @Test
    fun `a smart bin needs a new short name and at least one rule`() {
        val made = use.create(doc, Rules("红旗", flag = FlagRule.RED)).doc
        assertEquals("名称不能为空", use.problem(made, null, Rules(" ", flag = FlagRule.RED)))
        assertEquals("已有同名的智能媒体夹", use.problem(made, null, Rules("红旗", usage = UsageRule.USED)))
        assertNull(use.problem(made, made.smartBins.single().id, Rules("红旗", usage = UsageRule.USED)))
        assertEquals("名称最多 20 个字", use.problem(made, null, Rules("长".repeat(21), flag = FlagRule.RED)))
        assertEquals("请至少设置一个条件", use.problem(made, null, Rules("全部", nameContains = "  ")))
        assertEquals("请至少设置一个条件", assertThrows(EditRejected::class.java) { use.create(made, Rules("全部")) }.message)
        assertEquals("智能媒体夹 4", use.suggestedName(use.create(made, Rules("智能媒体夹 3", flag = FlagRule.ANY, usage = UsageRule.USED)).doc))
    }

    @Test
    fun `keyword and Good Take rules read the clip metadata`() {
        val tagged = doc.copy(media = doc.media.map {
            when (it.id) {
                "v1" -> it.copy(metadata = ClipMetadata(keywords = listOf("Night Run", "城市"), goodTake = true))
                "im" -> it.copy(metadata = ClipMetadata(keywords = listOf("海报")))
                else -> it
            }
        })
        assertEquals(listOf("v1"), use.media(tagged, Rules("x", keyword = " night ")).map { it.id })
        assertEquals(listOf("im"), use.media(tagged, Rules("x", keyword = "海报")).map { it.id })
        assertEquals(listOf("v1"), use.media(tagged, Rules("x", goodTake = true)).map { it.id })
        assertEquals(emptyList<String>(), use.media(tagged, Rules("x", kind = MediaKind.IMAGE, goodTake = true)).map { it.id })
        assertEquals("关键词“夜” · 好条", use.describe(Rules("x", keyword = "夜", goodTake = true)))
        assertNull(use.problem(tagged, null, Rules("好条", goodTake = true)))
        assertEquals("关键词条件最多 20 个字", use.problem(tagged, null, Rules("x", keyword = "长".repeat(21))))
        val bin = SmartBin("s3", "好条", keyword = "夜", goodTake = true)
        assertEquals(listOf(bin), ProjectJsonCodec.decode(ProjectJsonCodec.encode(tagged.copy(smartBins = listOf(bin)))).smartBins)
    }

    @Test
    fun `smart bins survive the project file, only set rules written`() {
        val bins = listOf(SmartBin("s1", "红旗视频", MediaKind.VIDEO, FlagRule.RED), SmartBin("s2", "未用", usage = UsageRule.UNUSED, nameContains = "夜"))
        val text = ProjectJsonCodec.encode(doc.copy(smartBins = bins))
        assertEquals(bins, ProjectJsonCodec.decode(text).smartBins)
        val json = org.json.JSONObject(text).getJSONArray("smartBins")
        assertEquals(setOf("id", "name", "kind", "flag"), json.getJSONObject(0).keys().asSequence().toSet())
        assertEquals(emptyList<SmartBin>(), ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)).smartBins)
    }
}
