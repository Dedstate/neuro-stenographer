#!/usr/bin/env python3
"""Optional maintainer rebuild; normal APK builds use the checked-in bridge AAR.
Sources: official ONNX Runtime v1.28.0; link target: Sherpa 2.0.1 Android ORT 1.28.2.
Requires JDK 17+, Android NDK 27.2.12479018 (Linux x86_64), and unpacked source archives.
"""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ort-source', type=Path, required=True)
    parser.add_argument('--ort-aar', type=Path, required=True, help='Official onnxruntime-android-1.28.0.aar')
    parser.add_argument('--sherpa-dir', type=Path, required=True, help='Directory containing jniLibs/ from Sherpa v2.0.1')
    parser.add_argument('--ndk', type=Path, required=True)
    args = parser.parse_args()
    src = args.ort_source.resolve()
    if (src / 'VERSION_NUMBER').read_text().strip() != '1.28.0':
        raise ValueError('Expected ONNX Runtime source v1.28.0')
    # Official Maven artifact, also recorded in the build provenance document.
    if hashlib.file_digest(args.ort_aar.open('rb'), 'sha256').hexdigest() != 'f351a0638696f54b35184290dbc001d66daae17281ad0b548d2c70347d53b8a9':
        raise ValueError('Unexpected official ORT AAR; verify its provenance before rebuilding')
    destination = ROOT / 'android/app/libs/onnxruntime-java-1.28.0-sherpa-1.28.2.aar'
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='ort-java-bridge-') as tmp:
        build = Path(tmp)
        java_sources = sorted((src / 'java/src/main/java').rglob('*.java')) + sorted((src / 'java/src/main/jvm').rglob('*.java'))
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-h', str(build/'headers'), '-d', str(build/'classes'), *map(str, java_sources)], check=True)
        (build/'headers/onnxruntime_config.h').write_text('#pragma once\n')
        with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as aar:
            with zipfile.ZipFile(args.ort_aar) as original:
                for name in ['classes.jar', 'AndroidManifest.xml']:
                    aar.writestr(name, original.read(name))
            for abi, triple in [('arm64-v8a', 'aarch64-linux-android'), ('x86_64', 'x86_64-linux-android')]:
                output = build / abi / 'libonnxruntime4j_jni.so'
                output.parent.mkdir()
                compiler = args.ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin' / (triple+'26-clang')
                includes = [src/'include', src/'include/onnxruntime/core/session', src/'orttraining/orttraining/training_api/include', build/'headers', src/'java/src/main/native']
                command = [str(compiler), '-shared', '-fPIC', '-O2', '-DORT_VERSION="1.28.2"', '-Wl,-z,defs', '-Wl,-z,max-page-size=16384', '-Wl,-soname,libonnxruntime4j_jni.so', *['-I'+str(p) for p in includes], *map(str, sorted((src/'java/src/main/native').glob('*.c'))), '-L'+str(args.sherpa_dir.resolve()/'jniLibs'/abi), '-lonnxruntime', '-ldl', '-lm', '-o', str(output)]
                subprocess.run(command, check=True)
                aar.write(output, 'jni/'+abi+'/libonnxruntime4j_jni.so')
    print(destination)

if __name__ == '__main__':
    main()
