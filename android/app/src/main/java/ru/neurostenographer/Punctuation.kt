package ru.neurostenographer

import ai.onnxruntime.*
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer
import java.util.Locale

// Restricted to the exact, pinned WordPiece tokenizer shipped with RUPunct_small.
// Predictions change case and insert punctuation; original word strings are retained.
class WordPiece(private val vocab: Map<String, Int>) {
    fun encode(word: String): List<Long> {
        if (word.codePointCount(0, word.length) > 100) return listOf(1L)
        val pieces = mutableListOf<Long>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var id: Int? = null
            while (end > start) {
                id = vocab[(if (start > 0) "##" else "") + word.substring(start, end)]
                if (id != null) break
                end = word.offsetByCodePoints(end, -1)
            }
            if (id == null) return listOf(1L)
            pieces.add(id.toLong()); start = end
        }
        return pieces
    }
}

class Punctuation(root: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment().also { it.setTelemetry(false) }
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        env.createSession(File(root, "rupunct.int8.onnx").absolutePath, options)
    }
    private val tokenizer: WordPiece
    private val labels: Map<Int, String>
    init {
        val model = JSONObject(File(root, "tokenizer.json").readText()).getJSONObject("model")
        val vocab = model.getJSONObject("vocab")
        tokenizer = WordPiece(vocab.keys().asSequence().associateWith { vocab.getInt(it) })
        val raw = JSONObject(File(root, "config.json").readText()).getJSONObject("id2label")
        labels = raw.keys().asSequence().associate { it.toInt() to raw.getString(it) }
    }

    fun apply(text: String): String {
        if (text.isBlank()) return text
        // Only bare ASR words are eligible. Preserve existing punctuation, decimals,
        // hyphens, symbols, and unusual scripts instead of corrupting token alignment.
        val words = text.trim().split(Regex("\\s+"))
        if (words.any { !it.matches(Regex("[А-Яа-яЁёA-Za-z0-9]+")) }) return text
        val result = mutableListOf<String>()
        var pos = 0
        while (pos < words.size) {
            val ids = mutableListOf(2L)
            val first = mutableListOf<Int>()
            val chunk = mutableListOf<String>()
            while (pos < words.size) {
                val sub = tokenizer.encode(words[pos])
                if (ids.size + sub.size + 1 > 256) break
                first.add(ids.size); ids.addAll(sub); chunk.add(words[pos++])
            }
            ids.add(3L)
            val shape = longArrayOf(1, ids.size.toLong())
            OnnxTensor.createTensor(env, LongBuffer.wrap(ids.toLongArray()), shape).use { input ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape).use { mask ->
                    OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size)), shape).use { types ->
                        session.run(mapOf("input_ids" to input, "attention_mask" to mask, "token_type_ids" to types)).use { output ->
                            @Suppress("UNCHECKED_CAST")
                            val logits = (output[0].value as Array<Array<FloatArray>>)[0]
                            chunk.forEachIndexed { i, word ->
                                val scores = logits[first[i]]
                                val best = scores.indices.maxBy { scores[it] }
                                result.add(render(word, labels.getValue(best)))
                            }
                        }
                    }
                }
            }
        }
        val candidate = result.joinToString(" ")
        // Case/punctuation must never change the lexical sequence used in WER.
        return if (lexical(candidate) == lexical(text)) candidate else text
    }
    override fun close() { session.close() }
    companion object {
        private val marks = mapOf("PERIOD" to ".", "COMMA" to ",", "QUESTION" to "?", "TIRE" to " —", "VOSKL" to "!", "DVOETOCHIE" to ":", "PERIODCOMMA" to ";", "DEFIS" to "-", "QUESTIONVOSKL" to "?!", "MNOGOTOCHIE" to "...", "O" to "")
        fun lexical(s: String) = Regex("[\\p{L}\\p{N}]+").findAll(s).map { it.value.lowercase(Locale.ROOT) }.toList()
        fun render(word: String, label: String): String {
            val mark = label.substringAfterLast('_')
            val cased = when {
                label.startsWith("UPPER_TOTAL_") -> word.uppercase(Locale.ROOT)
                label.startsWith("UPPER_") -> word.replaceFirstChar { it.uppercaseChar() }
                else -> word.lowercase(Locale.ROOT)
            }
            return cased + marks.getValue(mark)
        }
    }
}
