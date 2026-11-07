#!/usr/bin/env python3
"""Check model integrity and JNI coverage in an already built APK."""
import hashlib,json,sys,zipfile,os,subprocess,shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'android/app/src/main/assets/models/manifest.json').read_text())
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name,expected in manifest.items():
        with apk.open('assets/models/'+name) as f:
            actual=hashlib.file_digest(f,'sha256').hexdigest()
        assert actual==expected, name
    for abi in ['arm64-v8a','x86_64']:
        for name in ['libsherpa-onnx-jni.so','libonnxruntime.so','libonnxruntime4j_jni.so']:
            assert 'lib/'+abi+'/'+name in apk.namelist(), (abi,name)
    assert not any(n.endswith('.gguf') for n in apk.namelist())
aapt=shutil.which('aapt')
if not aapt:
    sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    candidates=sorted(Path(sdk).glob('build-tools/*/aapt')) if sdk else []
    if candidates:aapt=str(candidates[-1])
if not aapt:
    raise SystemExit('Set ANDROID_HOME to verify merged APK permissions with aapt')
permissions=subprocess.check_output([aapt,'dump','permissions',sys.argv[1]],text=True)
assert 'android.permission.INTERNET' not in permissions, permissions
print('APK model hashes, both ABIs and absence of INTERNET permission verified')
