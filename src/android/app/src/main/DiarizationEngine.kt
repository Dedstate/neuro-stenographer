interface DiarizzationEngine {
    fun diarize(samples: FloatArray) : List<SpeakerSegment>
}