package com.k2fsa.sherpa.onnx

/**
 * GigaAM v2 Russian CTC INT8.
 *
 * Required Android assets:
 * sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19/
 *   model.int8.onnx
 *   tokens.txt
 */
fun gigaAmModelConfig(): OfflineModelConfig {
    val modelDir =
        "sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19"

    return OfflineModelConfig(
        nemo = OfflineNemoEncDecCtcModelConfig(
            model = "$modelDir/model.int8.onnx"
        ),
        tokens = "$modelDir/tokens.txt"
    )
}
