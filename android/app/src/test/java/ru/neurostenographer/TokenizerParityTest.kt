package ru.neurostenographer

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class TokenizerParityTest {
    @Test fun pinnedWordpieceMatchesHuggingFaceGoldenIds() {
        val vocab=JSONObject(File("src/main/assets/models/tokenizer.json").readText()).getJSONObject("model").getJSONObject("vocab")
        val tokenizer=WordPiece(vocab.keys().asSequence().associateWith { vocab.getInt(it) })
        val cases=JSONArray(javaClass.getResource("/wordpiece-golden.json")!!.readText())
        for (i in 0 until cases.length()) {
            val row=cases.getJSONObject(i);val ids=row.getJSONArray("ids")
            assertEquals(row.getString("word"),(0 until ids.length()).map { ids.getLong(it) },tokenizer.encode(row.getString("word")))
        }
    }
}
