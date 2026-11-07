package ru.neurostenographer

import kotlin.math.*

/** Streaming windowed-sinc down/up-sampler. Bounded input storage, anti-alias filter. */
class Resampler(private val sourceRate: Int, private val check: () -> Unit = {}) {
    companion object { const val RATE = 16000; const val MAX_SECONDS = 600 }
    private val radius = 32
    private val ring = FloatArray(256)
    private var inputCount = 0L
    private var outputCount = 0
    private var output = FloatArray(RATE * 10)
    init { require(sourceRate in 8000..192000) { "Неподдерживаемая частота: $sourceRate" } }
    fun add(value: Float) {
        require(inputCount < sourceRate.toLong() * MAX_SECONDS) { "Лимит записи: $MAX_SECONDS секунд" }
        ring[(inputCount % ring.size).toInt()] = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
        inputCount++
        if (sourceRate == RATE) append(ring[((inputCount - 1) % ring.size).toInt()])
        else while (outputCount.toDouble() * sourceRate / RATE + radius < inputCount) emit()
    }
    private fun append(value: Float) {
        if (outputCount % RATE == 0) check()
        if (outputCount == output.size) output = output.copyOf(minOf(RATE * MAX_SECONDS, output.size * 2))
        output[outputCount++] = value
    }
    private fun emit() {
        val position = outputCount.toDouble() * sourceRate / RATE
        val center = floor(position).toLong()
        val cutoff = min(1.0, RATE.toDouble() / sourceRate) * 0.94
        var total = 0.0
        var weight = 0.0
        for (i in center - radius + 1..center + radius) {
            val distance = i - position
            val x = Math.PI * cutoff * distance
            val sinc = if (abs(x) < 1e-10) 1.0 else sin(x) / x
            val w = cutoff * sinc * (0.5 + 0.5 * cos(Math.PI * distance / radius))
            if (i >= 0 && i < inputCount) {
                total += ring[(i % ring.size).toInt()] * w; weight += w
            }
        }
        append(if (abs(weight) > 1e-12) (total / weight).toFloat() else 0f)
    }
    fun finish(): FloatArray {
        val expected = (inputCount * RATE / sourceRate).toInt()
        if (sourceRate != RATE) while (outputCount < expected) emit()
        return output.copyOf(outputCount)
    }
}
