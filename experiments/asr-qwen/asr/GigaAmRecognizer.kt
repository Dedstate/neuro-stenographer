package com.k2fsa.sherpa.onnx

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GigaAmRecognizer(context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
    }

    private val assets = context.applicationContext.assets
    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    suspend fun recognize(samples: FloatArray): String =
        withContext(Dispatchers.IO) {
            if (samples.isEmpty()) {
                return@withContext ""
            }

            mutex.withLock {
                val engine = recognizer ?: OfflineRecognizer(
                    assetManager = assets,
                    config = OfflineRecognizerConfig(
                        featConfig = getFeatureConfig(
                            sampleRate = SAMPLE_RATE,
                            featureDim = 80
                        ),
                        modelConfig = gigaAmModelConfig()
                    )
                ).also {
                    recognizer = it
                }

                val stream = engine.createStream()

                try {
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    engine.decode(stream)
                    engine.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            }
        }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            recognizer?.release()
            recognizer = null
        }
    }
}
