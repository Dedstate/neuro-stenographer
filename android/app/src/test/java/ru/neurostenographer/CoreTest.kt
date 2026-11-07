package ru.neurostenographer

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import kotlin.math.*

class CoreTest {
    @Test fun exportHasExactSchemaAndIntegerMilliseconds() {
        val s = "Он сказал: \"да\".\nПроверка\\теста"
        val root = JSONObject(Transcript.json(listOf(TranscriptSegment("speaker_1", 1250, 4380, s))))
        assertEquals(setOf("transcript"), root.keys().asSequence().toSet())
        val row = root.getJSONArray("transcript").getJSONObject(0)
        assertEquals(setOf("speaker_id","start_ms","end_ms","text"), row.keys().asSequence().toSet())
        assertEquals(1250L, row.getLong("start_ms")); assertEquals(s, row.getString("text"))
        assertTrue(row.get("start_ms") is Number)
        assertEquals(0, JSONObject(Transcript.json(emptyList())).getJSONArray("transcript").length())
    }
    @Test fun turnsAreClampedSortedAndRenumberedWithoutDiscardingOverlap() {
        val turns = Transcript.normalize(listOf(Turn(9,2f,6f),Turn(7,-1f,3f),Turn(5,Float.NaN,4f),Turn(9,9f,12f)),10f)
        assertEquals(listOf(Turn(1,0f,3f),Turn(2,2f,6f),Turn(2,9f,10f)),turns)
    }
    @Test fun longTurnsCoverEverySampleExactlyOnce() {
        val samples = FloatArray(16000 * 61) { .3f }
        val ranges = Transcript.split(Turn(1,0f,61f),samples)
        assertTrue(ranges.size >= 4)
        assertEquals(0,ranges.first().first); assertEquals(samples.lastIndex,ranges.last().last)
        ranges.zipWithNext().forEach { (a,b) -> assertEquals(a.last+1,b.first) }
        ranges.forEach { assertTrue(it.last-it.first+1 <= 20*16000) }
    }
    @Test fun resamplerPreservesDurationAndDcAtCommonRates() {
        for (rate in listOf(8000,16000,44100,48000,96000)) {
            val r = Resampler(rate); repeat(rate) { r.add(.25f) }; val out = r.finish()
            assertEquals("rate=$rate",16000,out.size)
            out.forEach { assertEquals(.25f,it,0.0001f) }
        }
    }
    @Test fun downsamplingSuppressesAboveNyquistTone() {
        fun rms(freq: Int): Double {
            val r = Resampler(48000)
            repeat(48000) { r.add(sin(2*Math.PI*freq*it/48000).toFloat()) }
            val out=r.finish().sliceArray(100 until 15900)
            return sqrt(out.sumOf { it.toDouble()*it }/out.size)
        }
        assertTrue(rms(1000) > .65); assertTrue(rms(12000) < .02)
    }
    @Test fun shortAndEmptyAudioDoNotInventSamples() {
        assertEquals(0,Resampler(48000).finish().size)
        val r=Resampler(48000); repeat(30) { r.add(.5f) }; assertEquals(10,r.finish().size)
    }
    @Test fun wordpieceUsesWholeUnknownWordAndContinuationTokens() {
        val t=WordPiece(mapOf("привет" to 10,"кол" to 20,"##леги" to 21))
        assertEquals(listOf(10L),t.encode("привет"))
        assertEquals(listOf(20L,21L),t.encode("коллеги"))
        assertEquals(listOf(1L),t.encode("коллегия"))
        assertEquals(listOf(1L),t.encode("а".repeat(101)))
    }
    @Test fun labelsApplyCasingWithoutRewritingWords() {
        assertEquals("Привет,",Punctuation.render("привет","UPPER_COMMA"))
        assertEquals("РФ.",Punctuation.render("рф","UPPER_TOTAL_PERIOD"))
        assertEquals("да?!",Punctuation.render("да","LOWER_QUESTIONVOSKL"))
        assertEquals(Punctuation.lexical("привет коллеги 123 ёж"),Punctuation.lexical("Привет, коллеги! 123: Ёж."))
        assertNotEquals(Punctuation.lexical("нет"),Punctuation.lexical("да"))
    }
}
