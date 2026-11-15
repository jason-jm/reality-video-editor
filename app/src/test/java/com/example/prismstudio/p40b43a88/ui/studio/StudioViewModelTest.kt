package com.example.prismstudio.p40b43a88.ui.studio

import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProjectRepository
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.ui.TestClock
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.navigation.AppDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {

    private val library = StudioUiState(AppDestination.LIBRARY, null)

    @Before
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main.immediate; the unconfined test dispatcher mirrors it.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts in the library when no project is open`() = runTest {
        val session = DefaultEditorSessionRepository(FakeProjectRepository(), backgroundScope, TestClock())

        val model = StudioViewModel(session)

        assertEquals(library, model.state.value)
    }

    @Test
    fun `opening a stored project routes to its workspace`() = runTest {
        val doc = UiFixtures.document(id = "p-7")
        val session = DefaultEditorSessionRepository(FakeProjectRepository(listOf(doc)), backgroundScope, TestClock())
        val model = StudioViewModel(session)

        assertTrue(session.open("p-7"))
        advanceUntilIdle()

        assertEquals(StudioUiState(AppDestination.WORKSPACE, "p-7"), model.state.value)
    }

    @Test
    fun `starting a freshly created project routes to the workspace`() = runTest {
        val session = DefaultEditorSessionRepository(FakeProjectRepository(), backgroundScope, TestClock())
        val model = StudioViewModel(session)

        session.start(UiFixtures.document(id = "p-new"))
        advanceUntilIdle()

        assertEquals(StudioUiState(AppDestination.WORKSPACE, "p-new"), model.state.value)
    }

    @Test
    fun `switching to another project routes to the newly opened one`() = runTest {
        val first = UiFixtures.document(id = "p-1", name = "First")
        val second = UiFixtures.document(id = "p-2", name = "Second")
        val session = DefaultEditorSessionRepository(FakeProjectRepository(listOf(first, second)), backgroundScope, TestClock())
        val model = StudioViewModel(session)

        session.open("p-1")
        session.open("p-2")
        advanceUntilIdle()

        assertEquals(StudioUiState(AppDestination.WORKSPACE, "p-2"), model.state.value)
    }

    @Test
    fun `CloseProject closes the session and returns to the library`() = runTest {
        val doc = UiFixtures.document(id = "p-7")
        val session = DefaultEditorSessionRepository(FakeProjectRepository(listOf(doc)), backgroundScope, TestClock())
        session.open("p-7")
        val model = StudioViewModel(session)

        model.onEvent(StudioEvent.CloseProject)
        advanceUntilIdle()

        assertNull(session.document.value)
        assertEquals(library, model.state.value)
    }

    @Test
    fun `CloseProject flushes pending edits before leaving the workspace`() = runTest {
        val doc = UiFixtures.document(id = "p-7", name = "Before")
        val projects = FakeProjectRepository(listOf(doc))
        val session = DefaultEditorSessionRepository(projects, backgroundScope, TestClock())
        session.open("p-7")
        val model = StudioViewModel(session)
        session.edit("重命名") { it.copy(name = "After") }
        assertTrue("the edit is waiting for the debounced autosave", session.saveState.value.pending)
        val savesBefore = projects.saveCount

        model.onEvent(StudioEvent.CloseProject)
        runCurrent() // Let the cancelled background autosave finish before close writes the pending edit.
        advanceUntilIdle()

        assertEquals(savesBefore + 1, projects.saveCount)
        assertEquals("After", projects.load("p-7")?.name)
        assertEquals(library, model.state.value)
    }

    @Test
    fun `edits to the open project do not change the route`() = runTest {
        val doc = UiFixtures.document(id = "p-7")
        val session = DefaultEditorSessionRepository(FakeProjectRepository(listOf(doc)), backgroundScope, TestClock())
        val model = StudioViewModel(session)
        val routes = mutableListOf<StudioUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect { routes += it } }

        session.open("p-7")
        session.edit("重命名") { it.copy(name = "Renamed") }
        session.edit("添加标记") { it.copy(timeline = it.timeline.copy(markers = listOf(Marker("m-1", 10, "标记 1")))) }
        session.update { it.copy(render = it.render.copy(fileName = "export")) }
        session.undo()
        advanceUntilIdle()

        assertEquals(listOf(library, StudioUiState(AppDestination.WORKSPACE, "p-7")), routes)
    }
}
