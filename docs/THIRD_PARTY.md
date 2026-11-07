# Компоненты и происхождение весов

- Sherpa-ONNX 2.0.1, Apache-2.0: https://github.com/k2-fsa/sherpa-onnx/tree/af2d8d81dbfa61c4cea4c568da78f3982edca005 . Kotlin API скопирован без модификаций; собственный конвейер находится в пакете `ru.neurostenographer`.
- GigaAM v2 CTC, MIT: https://github.com/salute-developers/GigaAM . INT8 ONNX-конверсия из релиза Sherpa, точный архив и хэш закреплены.
- pyannote segmentation 3.0, MIT: https://huggingface.co/pyannote/segmentation-3.0 . ONNX из официального Sherpa baseline APK, уже находившегося в ветке команды.
- 3D-Speaker ERes2Net, Apache-2.0: https://github.com/modelscope/3D-Speaker . ONNX embedding из того же baseline APK.
- RUPunct_small, MIT по model card: https://huggingface.co/RUPunct/RUPunct_small . INT8 экспорт: https://huggingface.co/ekhodzitsky/rupunct-small-onnx/tree/c4a35be7e4a413cdfb08427db75b621b571569c9 . Сохранён model card экспортёра.
- ONNX Runtime Android 1.28.0, MIT: https://github.com/microsoft/onnxruntime . AAR поставляет **единственный** `libonnxruntime.so` и совпадающий Java JNI bridge; Sherpa JNI использует его C API. В upstream Sherpa Android архиве лежит ORT 1.28.2; этот второй runtime намеренно не копируется. Обе версии используют C API 28; совместимость не заменяет тест на устройстве. Поэтому работоспособность конкретного APK проверяется `NativeSmokeTest` на Android.

Лицензии и model card включены в `android/app/src/main/assets/licenses/`. Точные бинарные источники/хэши находятся в `scripts/models.lock.json`, хэши извлечённых моделей — в assets/models/manifest.json.
