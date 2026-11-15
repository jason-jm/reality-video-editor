package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.model.MediaBin
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterMediaUseCaseTest {

    private val filter = FilterMediaUseCase()
    private val beach = assetOf("beach", name = "Beach.MOV", durationMs = 65_000)
    private val ambience = assetOf("amb", kind = MediaKind.AUDIO, name = "beach-ambience.wav", durationMs = 70_000)
    private val poster = assetOf("poster", kind = MediaKind.IMAGE, name = "Poster.png", durationMs = 5_000)
    private val city = assetOf("city", name = "City.mp4", durationMs = 12_000)
    private val pool = listOf(beach, ambience, poster, city)

    @Test
    fun `filters by bin and by a trimmed case insensitive name search`() {
        assertEquals(listOf(beach, ambience), filter(pool, MediaBin.ALL, "  beach "))
        assertEquals(listOf(beach), filter(pool, MediaBin.VIDEO, "BEACH"))
        assertEquals(listOf(poster), filter(pool, MediaBin.IMAGE, ""))
        assertEquals(listOf(beach, city), filter(pool, MediaBin.VIDEO, "   "))
        assertEquals(emptyList<Any>(), filter(pool, MediaBin.AUDIO, "city"))
    }

    @Test
    fun `summary counts the items and their total duration`() {
        assertEquals("2 个素材 · 共 02:15", filter.summary(listOf(beach, ambience)))
        assertEquals("0 个素材 · 共 00:00", filter.summary(emptyList()))
    }
}
