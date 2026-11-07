package ru.neurostenographer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Run on arm64 hardware AND the 4 GB x86_64 emulator; host unit tests do not prove JNI compatibility. */
@RunWith(AndroidJUnit4::class)
class NativeSmokeTest {
    @Test fun allModelsLoadAndInferSequentiallyWithOneOrtRuntime() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=Pipeline(context,AtomicBoolean(false)) {}.prepareModels()
        val sd=OfflineSpeakerDiarization(config=OfflineSpeakerDiarizationConfig(
            segmentation=OfflineSpeakerSegmentationModelConfig(pyannote=OfflineSpeakerSegmentationPyannoteModelConfig(File(root,"segmentation.onnx").path)),
            embedding=SpeakerEmbeddingExtractorConfig(model=File(root,"embedding.onnx").path)))
        try { assertEquals(16000,sd.sampleRate()); sd.process(FloatArray(16000)) } finally { sd.release() }
        val asr=OfflineRecognizer(config=OfflineRecognizerConfig(modelConfig=OfflineModelConfig(
            nemo=OfflineNemoEncDecCtcModelConfig(File(root,"asr.int8.onnx").path),tokens=File(root,"tokens.txt").path)))
        try {
            val stream=asr.createStream()
            try { stream.acceptWaveform(FloatArray(16000),16000);asr.decode(stream);asr.getResult(stream) } finally { stream.release() }
        } finally { asr.release() }
        Punctuation(root).use {
            val input="шестьдесят тысяч тенге сколько будет стоить"
            val output=it.apply(input)
            assertEquals(Punctuation.lexical(input),Punctuation.lexical(output))
            assertNotEquals("Must execute real token classification, not identity fallback",input,output)
        }
    }
}
