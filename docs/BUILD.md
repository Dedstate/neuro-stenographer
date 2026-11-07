# Сборка и установка

## Требования

| Компонент | Версия |
|---|---|
| JDK | 17 или 21; для локальной сборки рекомендуем 21 |
| Python | 3.11+ |
| Android SDK Platform | 35 |
| SDK Build-Tools | 34.0.0 и 35.0.0 |
| Platform-Tools | adb |
| binutils | readelf |

Системный Gradle не нужен. NDK для обычной сборки не требуется: JNI-мост уже включён в исходники. Пересборка самого моста описана в `scripts/rebuild_ort_java_bridge.py`.

## Java на Linux/Fedora

Проверьте `java -version`. Gradle 8.9 не запускается на Java 25. Если JDK 21 доступен в репозиториях дистрибутива, можно использовать его. Иначе установите Temurin 21 в домашний каталог:

```bash
mkdir -p "$HOME/Downloads" "$HOME/.local/share/jdks/temurin-21"
curl --fail --location --retry 3 \
  "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
  --output "$HOME/Downloads/temurin-21-linux-x64.tar.gz"
tar -xzf "$HOME/Downloads/temurin-21-linux-x64.tar.gz" \
  -C "$HOME/.local/share/jdks/temurin-21" --strip-components=1
export JAVA_HOME="$HOME/.local/share/jdks/temurin-21"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Команды приведены для Bash. В Fish можно сначала открыть `bash`. При ошибке загрузки архива не переходите к распаковке.

## Android SDK

В Android Studio установите через SDK Manager: Platform 35, Build-Tools 34.0.0/35.0.0, Platform-Tools и Command-Line Tools. Откройте в IDE папку `android/`, выберите JDK 17/21 в Gradle JDK.

Для терминала укажите реальный путь к SDK:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Если Command-Line Tools уже установлены:

```bash
sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-35" \
  "build-tools;34.0.0" "build-tools;35.0.0"
```

Проверьте наличие `$ANDROID_HOME/platforms/android-35/android.jar`. Можно указать `sdk.dir=/абсолютный/путь/к/SDK` в `android/local.properties`. Этот локальный файл не добавляется в Git. Переменная ANDROID_HOME нужна также скрипту проверки APK.

## Модели, тесты и APK

Все следующие команды выполняются из корня репозитория:

```bash
python3 scripts/prepare_models.py
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/check_apk.py android/app/build/outputs/apk/debug/app-debug.apk
```

Первый запуск скачивает зависимости и веса. При подготовке моделей проверяются SHA-256; корректные скачанные архивы используются повторно. Веса основной сборки загружаются отдельно и включаются в готовый APK. Исторические assets прототипов сохранены в experiments/.

Корневой wrapper перенаправляет команды в `android/`. Равнозначный вызов: `./android/gradlew -p android :app:assembleDebug`.

Результаты:

| Артефакт | Путь |
|---|---|
| APK | `android/app/build/outputs/apk/debug/app-debug.apk` |
| JVM tests | `android/app/build/reports/tests/testDebugUnitTest/index.html` |
| Lint | `android/app/build/reports/lint-results-debug.html` |
| Android tests | `android/app/build/reports/androidTests/` |

## Телефон

Включите отладку по USB и подтвердите подключение на телефоне:

```bash
adb devices
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` означает разные подписи установленного и нового APK. Сначала экспортируйте нужные результаты, удалите предыдущую установку, затем установите новую. Удаление стирает приватные данные приложения.

```bash
adb uninstall ru.neurostenographer
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

## Тест нативного конвейера

Оставьте подключённым одно устройство и выполните:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Тест использует тот же callback диаризации, что и приложение, затем запускает ASR и пунктуацию. Это проверка совместимости и реального инференса, а не оценка WER/DER.

## Типичные проблемы

| Сообщение | Что проверить |
|---|---|
| `25.0...` при старте Gradle | JAVA_HOME должен указывать на JDK 17/21 |
| `SDK location not found` | ANDROID_HOME или sdk.dir |
| `Missing ... run prepare_models.py` | Подготовить модели до сборки |
| `cannot locate symbol OrtGetApiBase` | Не заменять комплектный ORT отдельным Maven runtime |
| `invoke(IIJ)Ljava/lang/Integer;` | Использовать именованный DiarizationProgressCallback из текущей версии |
| `readelf` не найден | Установить binutils |

При падении сохраните `adb logcat -b all -d -v threadtime > crash-log.txt` и `adb shell dumpsys activity exit-info ru.neurostenographer > exit-info.txt`. Перед публикацией проверьте логи на личные данные и содержимое других приложений.
