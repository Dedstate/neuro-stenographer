import java.security.MessageDigest

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ru.neurostenographer"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.neurostenographer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += listOf("onnx") }
    packaging { jniLibs.useLegacyPackaging = true }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.json:json:20240303")
}
val verifyModels by tasks.registering {
    doLast {
        val root = file("src/main/assets/models")
        val required = listOf("asr.int8.onnx", "tokens.txt", "segmentation.onnx", "embedding.onnx", "rupunct.int8.onnx", "tokenizer.json", "config.json")
        required.forEach { name -> check(root.resolve(name).isFile && root.resolve(name).length() > 0) { "Missing $name; run python3 scripts/prepare_models.py" } }
        val manifest = groovy.json.JsonSlurper().parse(root.resolve("manifest.json")) as Map<*, *>
        manifest.forEach { (name, expected) ->
            val digest = MessageDigest.getInstance("SHA-256")
            root.resolve(name.toString()).inputStream().use { stream ->
                val buffer = ByteArray(65536)
                while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Model hash mismatch: $name" }
        }
        for (abi in listOf("arm64-v8a", "x86_64")) {
            for (name in listOf("libsherpa-onnx-jni.so")) {
                check(file("src/main/jniLibs/$abi/$name").isFile) { "Missing $abi/$name; run prepare_models.py" }
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyModels) }
