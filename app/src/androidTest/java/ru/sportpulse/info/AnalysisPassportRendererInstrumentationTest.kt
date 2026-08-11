package ru.sportpulse.info

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisPassportRendererInstrumentationTest {
    @Test
    fun maximumEventTextFitsPassportWithoutHiddenCharacters() {
        val context = InstrumentationRegistry.getInstrumentation()
            .targetContext
        val event = DemoCatalog.events.first().copy(
            id = "qa_maximum_passport_text",
            match = "Ж".repeat(
                SportEventContentPolicy.MAX_MATCH_LENGTH
            ),
            tournament = "Ш".repeat(
                SportEventContentPolicy.MAX_TOURNAMENT_LENGTH
            ),
            region = "Щ".repeat(
                SportEventContentPolicy.MAX_REGION_LENGTH
            )
        )
        val renderer = AnalysisPassportRenderer(context)
        val audit = renderer.eventTextAudit(event)

        assertTrue(audit.toString(), audit.fits)
        assertEquals(event.match.length, audit.match.visibleCharacters)
        assertEquals(
            event.tournament.length + event.region.length + 3,
            audit.metadata.visibleCharacters
        )
        assertTrue(audit.match.lineCount <= 3)
        assertTrue(audit.metadata.lineCount <= 3)

        val visualEvent = event.copy(
            match = "Международная академия спортивных технологий против объединённой молодёжной команды центра подготовки",
            tournament = "Международная лига развития спортивной аналитики и молодёжных академий",
            region = "Россия, СНГ и Центральная Азия"
        )
        assertTrue(renderer.eventTextAudit(visualEvent).fits)
        val bitmap = renderer.render(
            AnalysisPassportFactory.create(
                event = visualEvent,
                assessment = visualEvent.seedAssessment,
                decision = null,
                generatedAt = 1_786_080_000_000L
            )
        )
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(1350, bitmap.height)
            assertTrue(bitmap.getPixel(0, 0) != bitmap.getPixel(0, 100))
            FileOutputStream(
                context.filesDir.resolve("qa_long_event_passport.png")
            ).use { output ->
                assertTrue(
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                )
            }
        } finally {
            bitmap.recycle()
        }
    }
}
