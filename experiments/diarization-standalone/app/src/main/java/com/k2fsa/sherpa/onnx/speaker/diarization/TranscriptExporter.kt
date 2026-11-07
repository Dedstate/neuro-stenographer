package com.k2fsa.sherpa.onnx.speaker.diarization

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class SpeakerSegment(val speakerId: String, val startTms: Long, val endMs: Long)
data class TranscriptLine(val speakerId: String, val startTms: Long, val endMs: Long, val text: String)

object TranscriptExporter {
    fun generateJson(transcript: List<TranscriptLine>): String {
        val root = JSONObject()
        val transcriptArray = JSONArray()
        for (line in transcript) {
            val obj = JSONObject()
            obj.put("speaker_id", line.speakerId)
            obj.put("start_ms", line.startTms)
            obj.put("end_ms", line.endMs)
            obj.put("text", line.text)
            transcriptArray.put(obj)
        }
        root.put("transcript", transcriptArray)
        return root.toString(4)
    }

    fun createExportIntent(fileName: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
    }
}