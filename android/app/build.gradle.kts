import java.security.MessageDigest

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ru.neurostenographer"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.neurostenographer"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
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
    implementation(files("libs/onnxruntime-java-1.28.0-sherpa-1.28.2.aar"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.json:json:20240303")
}
val verifyModels by tasks.registering {
    doLast {
        val bridge = file("libs/onnxruntime-java-1.28.0-sherpa-1.28.2.aar")
        check(bridge.isFile) { "Missing checked-in ORT Java bridge AAR" }
        val bridgeHash = MessageDigest.getInstance("SHA-256").digest(bridge.readBytes()).joinToString("") { "%02x".format(it) }
        check(bridgeHash == "3ae5d0ead23994f847d42700937f00c48cbab5db185fcb50357f3beabb001643") { "ORT Java bridge hash mismatch" }
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
            for (name in listOf("libsherpa-onnx-jni.so", "libonnxruntime.so")) {
                check(file("src/main/jniLibs/$abi/$name").isFile) { "Missing $abi/$name; run prepare_models.py" }
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyModels) }
