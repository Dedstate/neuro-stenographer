
# GigaAM v2 — Android ASR

Локальное распознавание русской речи на Android с помощью
GigaAM v2 CTC INT8 и sherpa-onnx.

## Полученный результат

- Устройство: Google Pixel 7 Pro (arm64-v8a)
- ОС: Android 17 Beta
- Движок: sherpa-onnx
- Нативные библиотеки: v2.0.1
- Модель: GigaAM v2 CTC INT8
- Работа без интернета
- Ввод в тестовом приложении через микрофон

## Файлы модели

Разместить в Android-проекте:

app/src/main/assets/
└── sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19/
    ├── model.int8.onnx
    └── tokens.txt

Модель доступна в репозитории моделей sherpa-onnx.

## Kotlin

Файл GigaAmConfig.kt возвращает OfflineModelConfig
для модели NeMo CTC.

Для интеграции использовать результат gigaAmModelConfig()
при создании OfflineRecognizer.

Требуются совместимые Kotlin-обёртки sherpa-onnx
и нативные библиотеки для arm64-v8a:

- libsherpa-onnx-jni.so
- libsherpa-onnx-c-api.so
- libsherpa-onnx-cxx-api.so
- libonnxruntime.so

Их нужно включить в app/src/main/jniLibs/arm64-v8a/.

## Текущее состояние

Работает:
- Распознавание русской речи на устройстве
- Полностью офлайн-обработка микрофонного ввода

В данном модуле не реализовано:
- Импорт аудиофайлов
- Диаризация
- Экспорт JSON
- Постобработка локальной LLM
