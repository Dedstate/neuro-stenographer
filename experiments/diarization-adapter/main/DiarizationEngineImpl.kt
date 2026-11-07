import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.*

class DiarizationEngineImpl(
    private val assetManager: AssetManager
) : DiarizationEngine {

    private val sd: OfflineSpeakerDiarization by lazy {
        val segmentationModel = "pyannote-segmentation-3-0.onnx"
        val embeddingModel = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    model = segmentationModel,
                    windowShiftRatio = 0.1f
                )
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = embeddingModel
            ),
            clustering = FastClusteringConfig(threshold = 0.5f)
        )
        OfflineSpeakerDiarization(config = config, assetManager = assetManager)
    }

    override fun diarize(samples: FloatArray): List<SpeakerSegment> {
        val rawSegments = sd.process(samples)
        return rawSegments.map { seg ->
            SpeakerSegment(
                speakerId = "speaker_${seg.speaker}",
                start = (seg.start * 1000).toLong(),
                end = (seg.end * 1000).toLong()
            )
        }
    }

    fun release() {
        sd.release()
    }
}
