package ru.neurostenographer

import android.content.Context
import android.media.*
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder

object AudioDecoder {
    fun decode(context: Context, uri: Uri, check: () -> Unit): FloatArray {
        val file = File.createTempFile("input-", ".audio", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Не удалось открыть аудио" }
                file.outputStream().use { out ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        check(); val n = input.read(buffer); if (n < 0) break
                        total += n
                        require(total <= 512L * 1024 * 1024) { "Файл больше 512 МБ" }
                        out.write(buffer, 0, n)
                    }
                }
            }
            val header = ByteArray(minOf(12L, file.length()).toInt())
            RandomAccessFile(file, "r").use { it.readFully(header) }
            return if (header.size == 12 && String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") wav(file, check) else compressed(file, check)
        } finally { file.delete() }
    }

    internal fun wav(file: File, check: () -> Unit): FloatArray = RandomAccessFile(file, "r").use { f ->
        fun u16() = java.lang.Short.reverseBytes(f.readShort()).toInt() and 65535
        fun u32() = java.lang.Integer.reverseBytes(f.readInt()).toLong() and 0xffffffffL
        f.seek(12)
        var channels = 0; var rate = 0; var bits = 0; var encoding = 0; var align = 0
        var dataStart = -1L; var dataSize = 0L
        while (f.filePointer + 8 <= f.length()) {
            val id = ByteArray(4); f.readFully(id); val size = u32(); val start = f.filePointer
            require(start + size <= f.length()) { "Обрезанный WAV" }
            when (String(id)) {
                "fmt " -> {
                    require(size >= 16)
                    encoding = u16(); channels = u16(); rate = u32().toInt(); u32(); align = u16(); bits = u16()
                }
                "data" -> { dataStart = start; dataSize = size }
            }
            f.seek(start + size + size % 2)
        }
        require(dataStart >= 0 && channels in 1..8 && rate in 8000..192000) { "Некорректный WAV" }
        require((encoding == 1 && bits in listOf(16,24,32)) || (encoding == 3 && bits == 32)) { "WAV: поддерживаются PCM16/24/32 и Float32" }
        require(align == channels * bits / 8 && dataSize % align == 0L) { "Некорректный размер PCM-кадра" }
        require(dataSize / align <= rate.toLong() * Resampler.MAX_SECONDS) { "Лимит записи: 10 минут" }
        val resampler = Resampler(rate, check)
        f.seek(dataStart)
        val storage = ByteArray(4096 * align)
        var remaining = dataSize
        while (remaining > 0) {
            check()
            val n = minOf(storage.size.toLong(), remaining).toInt()
            f.readFully(storage, 0, n)
            val buffer = java.nio.ByteBuffer.wrap(storage, 0, n).order(ByteOrder.LITTLE_ENDIAN)
            repeat(n / align) {
                var mono = 0f
                repeat(channels) {
                    mono += when {
                        encoding == 3 -> buffer.float
                        bits == 16 -> buffer.short / 32768f
                        bits == 24 -> {
                            val x = (buffer.get().toInt() and 255) or ((buffer.get().toInt() and 255) shl 8) or (buffer.get().toInt() shl 16)
                            x / 8388608f
                        }
                        else -> buffer.int / 2147483648f
                    }
                }
                resampler.add(mono / channels)
            }
            remaining -= n
        }
        resampler.finish()
    }

    private fun compressed(file: File, check: () -> Unit): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("Аудиодорожка не найдена")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            var inputDone = false; var outputDone = false
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var resampler: Resampler? = null
            val info = MediaCodec.BufferInfo()
            var lastOutput = System.nanoTime()
            while (!outputDone) {
                check()
                require(System.nanoTime() - lastOutput < 30_000_000_000L) { "Декодер аудио не отвечает" }
                if (!inputDone) {
                    val i = decoder.dequeueInputBuffer(10000)
                    if (i >= 0) {
                        val buffer = decoder.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buffer, 0)
                        if (n < 0) { decoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { decoder.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                when (val index = decoder.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        val newRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        require(resampler == null || newRate == rate) { "Смена частоты внутри файла не поддерживается" }
                        rate = newRate; channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        require(channels in 1..8)
                        encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    }
                    else -> if (index >= 0) {
                        try {
                            if (info.size > 0) {
                                lastOutput = System.nanoTime()
                                val sink = resampler ?: Resampler(rate, check).also { resampler = it }
                                require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) { "Неподдерживаемый PCM от декодера" }
                                val buffer = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                                require(buffer.remaining() % (bytes * channels) == 0)
                                while (buffer.remaining() >= bytes * channels) {
                                    var mono = 0f
                                    repeat(channels) { mono += if (bytes == 4) buffer.float else buffer.short / 32768f }
                                    sink.add(mono / channels)
                                }
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally { decoder.releaseOutputBuffer(index, false) }
                    }
                }
            }
            return resampler?.finish() ?: FloatArray(0)
        } finally { codec?.release(); extractor.release() }
    }
}
