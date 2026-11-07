package ru.neurostenographer

/**
 * Sherpa 2.0.1 JNI looks up invoke(IIJ)Ljava/lang/Integer; on the concrete object.
 * Kotlin 2 invokedynamic lambdas only expose the erased invoke(Object,Object,Object).
 * A named Function3 implementation preserves the typed method required by that JNI.
 */
internal class DiarizationProgressCallback(
    private val onProgress: (Int, Int) -> Unit
) : Function3<Int, Int, Long, Int> {
    override fun invoke(processed: Int, total: Int, arg: Long): Int {
        onProgress(processed, total)
        // The native implementation ignores this value. Cancellation is checked
        // after the whole native call, never promised at callback boundaries.
        return 0
    }
}
