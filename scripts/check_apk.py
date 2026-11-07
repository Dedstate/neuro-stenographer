#!/usr/bin/env python3
"""Verify models, Android permissions and actual versioned ORT symbols in the APK."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def dynamic_symbols(path):
    tool = shutil.which('readelf') or shutil.which('llvm-readelf')
    if not tool:
        raise RuntimeError('Install binutils (readelf) to validate native linkage')
    output = subprocess.check_output([tool, '--dyn-syms', '--wide', str(path)], text=True)
    imports, exports = set(), set()
    for line in output.splitlines():
        parts = line.split()
        if len(parts) < 8 or not parts[0].endswith(':'):
            continue
        name = parts[7].replace('@@', '@')
        (imports if parts[6] == 'UND' else exports).add(name)
    return imports, exports

def verify_native_versions(apk):
    with tempfile.TemporaryDirectory(prefix='neuro-link-check-') as scratch:
        for abi in ['arm64-v8a', 'x86_64']:
            paths = {}
            for name in ['libsherpa-onnx-jni.so', 'libonnxruntime.so', 'libonnxruntime4j_jni.so']:
                destination = Path(scratch) / (abi + '-' + name)
                destination.write_bytes(apk.read('lib/' + abi + '/' + name))
                paths[name] = destination
            _, exports = dynamic_symbols(paths['libonnxruntime.so'])
            for consumer in ['libsherpa-onnx-jni.so', 'libonnxruntime4j_jni.so']:
                imports, _ = dynamic_symbols(paths[consumer])
                required = {s for s in imports if s.startswith('Ort') and '@VERS_' in s}
                if not any(s.startswith('OrtGetApiBase@') for s in required):
                    raise ValueError(f'{abi}/{consumer}: missing versioned OrtGetApiBase requirement')
                missing = required - exports
                if missing:
                    raise ValueError(f'{abi}/{consumer}: runtime does not export {sorted(missing)}')
            print(f'{abi}: Sherpa and Java JNI symbols match the packaged runtime')

def check(path):
    manifest = json.loads((ROOT / 'android/app/src/main/assets/models/manifest.json').read_text())
    with zipfile.ZipFile(path) as apk:
        for name, expected in manifest.items():
            with apk.open('assets/models/' + name) as stream:
                actual = hashlib.file_digest(stream, 'sha256').hexdigest()
            if actual != expected:
                raise ValueError('Model hash mismatch: ' + name)
        if any(n.endswith('.gguf') for n in apk.namelist()):
            raise ValueError('Unexpected GGUF model')
        verify_native_versions(apk)
    aapt = shutil.which('aapt')
    if not aapt:
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        candidates = sorted(Path(sdk).glob('build-tools/*/aapt')) if sdk else []
        if candidates:
            aapt = str(candidates[-1])
    if not aapt:
        raise RuntimeError('Set ANDROID_HOME to verify merged APK permissions with aapt')
    permissions = subprocess.check_output([aapt, 'dump', 'permissions', str(path)], text=True)
    if 'android.permission.INTERNET' in permissions:
        raise ValueError('APK requests INTERNET')
    print('APK model hashes, native linkage, both ABIs and absence of INTERNET verified')

if __name__ == '__main__':
    check(sys.argv[1])
