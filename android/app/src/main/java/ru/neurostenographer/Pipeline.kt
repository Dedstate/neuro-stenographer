package ru.neurostenographer

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

class Pipeline(private val context: Context, private val cancelled: AtomicBoolean, private val progress: (String) -> Unit) {
    private fun check() { if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException() }
    data class Result(val segments: List<TranscriptSegment>, val elapsedMs: Long, val durationMs: Long, val warnings: List<String>)

    internal fun prepareModels(): File {
        val root = File(context.filesDir, "models-v1").apply { mkdirs() }
        val manifest = JSONObject(context.assets.open("models/manifest.json").bufferedReader().use { it.readText() })
        for (name in manifest.keys()) {
            check()
            val expected = manifest.getString(name)
            val target = File(root, name)
            val marker = File(root, "$name.sha256")
            if (target.isFile && marker.isFile && marker.readText() == expected) continue
            progress("Подготовка модели: $name")
            val temp = File(root, "$name.part")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                context.assets.open("models/$name").use { input -> temp.outputStream().use { out ->
                    val buffer = ByteArray(65536)
                    while (true) { check(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer,0,n); out.write(buffer,0,n) }
                    out.fd.sync()
                } }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                require(hash == expected) { "Повреждён файл модели: $name" }
                check(temp.renameTo(target)) { "Не удалось сохранить модель: $name" }
                marker.writeText(expected)
            } finally { temp.delete() }
        }
        return root
    }
    fun run(uri: Uri): Result {
        android.system.Os.setenv("ORT_DISABLE_TELEMETRY", "1", true)
        ai.onnxruntime.OrtEnvironment.getEnvironment().setTelemetry(false)
        val start = SystemClock.elapsedRealtime()
        val root = prepareModels()
        check(); progress("Декодирование аудио • максимум 10 минут")
        val samples = AudioDecoder.decode(context, uri, ::check)
        require(samples.isNotEmpty()) { "Аудиофайл пуст" }
        val duration = samples.size / 16000f
        val warnings = mutableListOf<String>()
        progress("Загрузка моделей диаризации")
        val diarization = OfflineSpeakerDiarization(config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(File(root,"segmentation.onnx").path), numThreads = 2),
            embedding = SpeakerEmbeddingExtractorConfig(model = File(root,"embedding.onnx").path, numThreads = 2),
            clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f)
        ))
        val turns = try {
            require(diarization.sampleRate() == 16000)
            progress("Определение говорящих")
            val callback = DiarizationProgressCallback { done, total ->
                progress("Диаризация: $done / $total")
            }
            Transcript.normalize(diarization.processWithCallback(samples, callback)
                .map { Turn(it.speaker, it.start, it.end) }, duration)
        } finally { diarization.release() }
        check()
        if (turns.indices.any { i -> turns.take(i).any { it.end > turns[i].start && it.speaker != turns[i].speaker } }) {
            warnings.add("Есть одновременная речь: разделение голосов не выполняется, текст перекрывающихся интервалов может повторяться.")
        }
        val segments = mutableListOf<TranscriptSegment>()
        if (turns.isNotEmpty()) {
            progress("Загрузка распознавания речи")
            val asr = OfflineRecognizer(config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(nemo = OfflineNemoEncDecCtcModelConfig(File(root,"asr.int8.onnx").path),
                    tokens = File(root,"tokens.txt").path, numThreads = 2)
            ))
            try {
                turns.forEachIndexed { index, turn ->
                    for (range in Transcript.split(turn, samples)) {
                        check(); progress("Распознавание: реплика ${index + 1} / ${turns.size}")
                        val stream = asr.createStream()
                        val text = try {
                            stream.acceptWaveform(samples.copyOfRange(range.first, range.last + 1), 16000)
                            asr.decode(stream); asr.getResult(stream).text.trim()
                        } finally { stream.release() }
                        if (text.isNotBlank()) segments.add(TranscriptSegment("speaker_${turn.speaker}",
                            (range.first * 1000.0 / 16000).roundToLong(), ((range.last + 1) * 1000.0 / 16000).roundToLong(), text))
                    }
                }
            } finally { asr.release() }
        }
        check(); progress("Восстановление пунктуации • RUPunct")
        val punctuated = if (segments.isEmpty()) segments else {
            try {
                Punctuation(root).use { punct -> segments.mapIndexed { i, segment ->
                    check(); progress("Пунктуация: ${i + 1} / ${segments.size}")
                    segment.copy(text = punct.apply(segment.text))
                } }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.e("NeuroStenographer", "Punctuation failed", e)
                warnings.add("Пунктуация недоступна: ${e.javaClass.simpleName}. Сохранён исходный текст ASR.")
                segments
            }
        }
        check()
        if (segments.isEmpty()) warnings.add("Речь не распознана. Проверьте исходную запись.")
        return Result(punctuated.sortedBy { it.startMs }, SystemClock.elapsedRealtime() - start, (duration * 1000).roundToLong(), warnings)
    }
}
