# Работа с проектом

Основной Android-проект находится в `android/`. Прототипы в `experiments/` сохранены для истории и сравнения. Они не входят в основной APK.

## Первый запуск

Следуйте [BUILD.md](docs/BUILD.md). Не добавляйте в Git модели, временное аудио, SDK, ключи подписи и local.properties.

## Изменения

1. Создайте ветку от актуального main: `git switch -c feature/название`.
2. Меняйте один узел конвейера за раз. Для сравнения используйте одни и те же контрольные записи.
3. Проверьте относящиеся к изменению тесты. Перед объединением выполните сборку и `scripts/check_apk.py`.
4. В описании изменения укажите поведение до/после и способ проверки.

## Точки входа

| Файл | Ответственность |
|---|---|
| MainActivity.kt | Импорт, отображение, экспорт, управление заданием |
| Pipeline.kt | Порядок этапов и освобождение моделей |
| AudioDecoder.kt | WAV и MediaCodec |
| Resampler.kt | Mono PCM 16 kHz |
| Transcript.kt | Границы, ASR-нарезка, JSON |
| Punctuation.kt | WordPiece и RUPunct |
| DiarizationProgressCallback.kt | Сигнатура, ожидаемая Sherpa JNI |

Эти файлы находятся в `android/app/src/main/java/ru/neurostenographer/`. Kotlin API Sherpa лежит отдельно, в `com/k2fsa/sherpa/onnx`. Менять его без проверки JNI нельзя.

## Проверка

```bash
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/check_apk.py android/app/build/outputs/apk/debug/app-debug.apk
```

Перед изменением моделей/нативных библиотек также выполните `./gradlew :app:connectedDebugAndroidTest` на устройстве. Факт компиляции instrumentation APK не равен запуску теста.

## Логи и тестовые данные

В issue приложите версию APK, устройство, Android, этап ошибки и минимальный фрагмент лога. Не публикуйте полный системный Logcat без просмотра. Для исходных аудиозаписей необходимо знать происхождение и условия распространения.
