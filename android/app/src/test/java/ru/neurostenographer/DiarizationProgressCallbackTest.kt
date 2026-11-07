package ru.neurostenographer

import org.junit.Assert.*
import org.junit.Test

class DiarizationProgressCallbackTest {
    @Test fun exposesExactMethodLookedUpByNativeSherpa() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val callback = DiarizationProgressCallback { done, total -> calls.add(done to total) }
        // Same lookup as JNI GetMethodID(cls, "invoke", "(IIJ)Ljava/lang/Integer;").
        val method = callback.javaClass.getMethod("invoke",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        assertEquals(Integer::class.java, method.returnType)
        assertEquals(0, method.invoke(callback, 3, 12, 0L))
        assertEquals(listOf(3 to 12), calls)
    }

    @Test fun alsoImplementsErasedFunction3Bridge() {
        var observed = 0 to 0
        val callback: Function3<Int, Int, Long, Int> = DiarizationProgressCallback { a, b -> observed = a to b }
        assertEquals(0, callback.invoke(7, 9, 123L))
        assertEquals(7 to 9, observed)
    }
}
