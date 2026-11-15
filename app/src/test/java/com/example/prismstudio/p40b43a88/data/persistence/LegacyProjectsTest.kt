package com.example.prismstudio.p40b43a88.data.persistence

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release round R4: projects saved by earlier versions (from iteration 65 on, kept in test resources) still open, and
 * saving one again loses nothing: decode → encode → decode gives the same document. With PRISM_LEGACY_DIR set, every
 * project file in that folder is checked too (R4 ran it over the 267 projects the development history left behind).
 */
class LegacyProjectsTest {

    private fun files(): List<File> {
        val bundled = File(javaClass.getResource("/legacy-projects")!!.toURI()).listFiles().orEmpty().filter { it.extension == "json" }
        val extra = System.getenv("PRISM_LEGACY_DIR")?.let { File(it).listFiles().orEmpty().filter { f -> f.extension == "json" } }.orEmpty()
        return (bundled + extra).sortedBy { it.name }
    }

    @Test
    fun `projects saved by earlier versions open, and saving them again loses nothing`() {
        val files = files()
        assertTrue("the bundled projects are there", files.size >= 7)
        val failures = mutableListOf<String>()
        for (file in files) {
            try {
                val text = file.readText()
                val raw = org.json.JSONObject(text)
                // Only project files (a design spec or a job list in the extra folder is not one).
                if (!raw.has("format") || !raw.has("timeline")) continue
                val doc = ProjectJsonCodec.decode(text)
                val again = ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc))
                if (again != doc) failures += "${file.name}: changed when saved again"
                // Every clip and medium the file lists is in the project.
                val clips = raw.getJSONObject("timeline").optJSONArray("clips")?.length() ?: 0
                if (doc.timeline.clips.size != clips) failures += "${file.name}: ${clips} clips in the file, ${doc.timeline.clips.size} opened"
                val media = raw.optJSONArray("media")?.length() ?: 0
                if (doc.media.size != media) failures += "${file.name}: ${media} media in the file, ${doc.media.size} opened"
            } catch (e: Exception) {
                failures += "${file.name}: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
        assertEquals("${files.size} projects checked", emptyList<String>(), failures)
    }
}
