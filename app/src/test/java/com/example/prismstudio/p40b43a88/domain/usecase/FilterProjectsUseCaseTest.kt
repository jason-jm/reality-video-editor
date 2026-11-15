package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.ProjectSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterProjectsUseCaseTest {

    private val filter = FilterProjectsUseCase()

    private fun summary(id: String, name: String, modified: Long) =
        ProjectSummary(id, name, ProjectFormat(1920, 1080, 30), 0, 0, modified, PosterColors.NEUTRAL)

    private val wedding = summary("p1", "Wedding Film", modified = 300)
    private val trip = summary("p2", "Summer Trip", modified = 900)
    private val vlog = summary("p3", "trip vlog", modified = 100)

    @Test
    fun `projects are listed most recently modified first`() {
        assertEquals(listOf(trip, wedding, vlog), filter(listOf(wedding, trip, vlog), ""))
    }

    @Test
    fun `search matches names case insensitively after trimming and keeps the order`() {
        assertEquals(listOf(trip, vlog), filter(listOf(vlog, wedding, trip), "  TRIP "))
        assertEquals(emptyList<ProjectSummary>(), filter(listOf(vlog, wedding, trip), "concert"))
    }
}
