package com.neurostenographer.llm

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale

class QwenPunctuation(
    context: Context,
    private val modelPath: String
) {
    private val engine = AiChat.getInferenceEngine(
        context.applicationContext
    )

    private val mutex = Mutex()
    private var modelReady = false

    private val systemPrompt = """
        Ты корректор русских стенограмм.
        Восстанавливай только пунктуацию и заглавные буквы.
        Сохраняй все исходные слова в исходном порядке.
        Не добавляй, не удаляй и не заменяй слова.
        Верни только исправленный текст без пояснений.

        Пример:
        Вход: привет коллеги сегодня мы начинаем совещание завтра обсудим результаты
        Выход: Привет, коллеги. Сегодня мы начинаем совещание. Завтра обсудим результаты.
    """.trimIndent()

    private suspend fun ensureReady() {
        if (modelReady) return

        val state = withTimeout(30_000) {
            engine.state.first {
                it is InferenceEngine.State.Initialized ||
                it is InferenceEngine.State.Error ||
                it is InferenceEngine.State.ModelReady
            }
        }

        when (state) {
            is InferenceEngine.State.Error ->
                throw state.exception

            is InferenceEngine.State.Initialized -> {
                engine.loadModel(modelPath)
                engine.setSystemPrompt(systemPrompt)
                modelReady = true
            }

            else ->
                error("LLM engine is already occupied")
        }
    }

    suspend fun punctuate(text: String): String {
        if (text.isBlank()) return text

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    ensureReady()

                    val result = engine.sendUserPrompt(
                        message = text,
                        predictLength = 256
                    ).toList().joinToString("").trim()

                    if (sameWords(text, result)) {
                        result
                    } else {
                        text
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    text
                }
            }
        }
    }

    private fun sameWords(
        original: String,
        generated: String
    ): Boolean {
        val wordRegex = Regex("""[\p{L}\p{N}]+""")

        fun words(value: String): List<String> =
            wordRegex.findAll(value)
                .map { it.value.lowercase(Locale.ROOT) }
                .toList()

        return generated.isNotBlank() &&
            words(original) == words(generated)
    }
}
