package ru.neurostenographer

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {
    private fun wav(bits: Int, encoding: Int, channels: Int, rate: Int, frames: Int, sample: (ByteBuffer) -> Unit): File {
        val size=frames*channels*bits/8
        val b=ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36+size);b.put("WAVEfmt ".toByteArray());b.putInt(16)
        b.putShort(encoding.toShort());b.putShort(channels.toShort());b.putInt(rate);b.putInt(rate*channels*bits/8)
        b.putShort((channels*bits/8).toShort());b.putShort(bits.toShort());b.put("data".toByteArray());b.putInt(size)
        repeat(frames*channels) { sample(b) }
        return File.createTempFile("neuro-test-", ".wav").apply { writeBytes(b.array()); deleteOnExit() }
    }
    @Test fun stereo48kIsDownmixedAndResampled() {
        val f=wav(16,1,2,48000,4800) { it.putShort(16384) }
        try { val a=AudioDecoder.wav(f) {}; assertEquals(1600,a.size); a.forEach { assertEquals(.5f,it,.0001f) } } finally { f.delete() }
    }
    @Test fun supportsSigned24Bit() {
        val f=wav(24,1,1,16000,100) { it.put(0);it.put(0);it.put(0xc0.toByte()) }
        try { val a=AudioDecoder.wav(f) {}; assertEquals(100,a.size); assertEquals(-.5f,a[0],.0001f) } finally { f.delete() }
    }
    @Test fun floatNanIsSanitized() {
        val f=wav(32,3,1,16000,10) { it.putFloat(Float.NaN) }
        try { assertTrue(AudioDecoder.wav(f) {}.all { it==0f }) } finally { f.delete() }
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsTruncatedData() {
        val f=wav(16,1,1,16000,10) { it.putShort(0) }
        try { f.writeBytes(f.readBytes().dropLast(2).toByteArray());AudioDecoder.wav(f) {} } finally { f.delete() }
    }
}
