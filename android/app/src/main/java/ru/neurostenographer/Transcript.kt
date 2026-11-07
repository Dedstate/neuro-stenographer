package ru.neurostenographer

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class Turn(val speaker: Int, val start: Float, val end: Float)
data class TranscriptSegment(val speakerId: String, val startMs: Long, val endMs: Long, val text: String)

object Transcript {
    fun json(segments: List<TranscriptSegment>): String {
        val array = JSONArray()
        segments.forEach {
            require(it.startMs >= 0 && it.endMs > it.startMs && it.speakerId.isNotBlank())
            array.put(JSONObject().put("speaker_id", it.speakerId).put("start_ms", it.startMs)
                .put("end_ms", it.endMs).put("text", it.text))
        }
        return JSONObject().put("transcript", array).toString(2)
    }

    // Keep overlaps: assigning the mixed waveform to either speaker would invent certainty.
    // IDs are normalized by first appearance; no assumption that the recording has two speakers.
    fun normalize(turns: List<Turn>, duration: Float): List<Turn> {
        require(duration.isFinite() && duration >= 0)
        val ids = linkedMapOf<Int, Int>()
        return turns.filter { it.start.isFinite() && it.end.isFinite() && it.end > it.start }
            .sortedWith(compareBy<Turn> { it.start }.thenBy { it.end }.thenBy { it.speaker })
            .mapNotNull { t ->
                val start = t.start.coerceIn(0f, duration)
                val end = t.end.coerceIn(0f, duration)
                if (end <= start) null else Turn(ids.getOrPut(t.speaker) { ids.size + 1 }, start, end)
            }
    }

    // Bound ASR activation memory. Prefer a low-energy cut near the 20-second limit.
    fun split(turn: Turn, samples: FloatArray, rate: Int = 16000): List<IntRange> {
        var start = (turn.start * rate).roundToInt().coerceIn(0, samples.size)
        val end = (turn.end * rate).roundToInt().coerceIn(start, samples.size)
        val result = mutableListOf<IntRange>()
        while (start < end) {
            var cut = minOf(start + 20 * rate, end)
            if (cut < end) {
                val searchEnd = cut
                val searchStart = cut - 2 * rate
                var best = Double.POSITIVE_INFINITY
                var at = searchStart
                while (at + rate / 50 <= searchEnd) {
                    var energy = 0.0
                    for (i in at until at + rate / 50) energy += samples[i].toDouble() * samples[i]
                    if (energy < best) { best = energy; cut = at + rate / 100 }
                    at += rate / 50
                }
            }
            if (cut <= start) cut = minOf(start + 20 * rate, end)
            result.add(start until cut)
            start = cut
        }
        return result
    }
}
