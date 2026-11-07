#!/usr/bin/env python3
"""Build-time downloads only. Verify pinned SHA-256 before extracting selected files."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def prepare():
    cache = Path(os.environ.get('NEURO_MODEL_CACHE', ROOT / '.model-cache'))
    cache.mkdir(parents=True, exist_ok=True)
    # Remove an older duplicated ORT: the AAR supplies the sole runtime.
    for stale in (ROOT / 'android/app/src/main/jniLibs').glob('*/libonnxruntime.so'):
        stale.unlink()
    lock = json.loads((ROOT / 'scripts/models.lock.json').read_text())
    for item in lock['downloads']:
        archive = cache / item['name']
        if not archive.exists() or sha(archive) != item['sha256']:
            temp = archive.with_suffix('.part')
            try:
                print('Downloading', item['name'], flush=True)
                with urllib.request.urlopen(item['url'], timeout=120) as src, temp.open('wb') as dst:
                    shutil.copyfileobj(src, dst)
                if sha(temp) != item['sha256']:
                    raise ValueError('SHA-256 mismatch: ' + item['name'])
                temp.replace(archive)
            finally:
                temp.unlink(missing_ok=True)
        # Explicit members only: never extract archive paths wholesale.
        if item['kind'] == 'tar':
            with tarfile.open(archive) as source:
                for member, destination in item['files'].items():
                    with source.extractfile(member) as stream:
                        write(stream, ROOT / destination)
        elif item['kind'] == 'zip':
            with zipfile.ZipFile(archive) as source:
                for member, destination in item['files'].items():
                    with source.open(member) as stream:
                        write(stream, ROOT / destination)
        else:
            with archive.open('rb') as stream:
                write(stream, ROOT / item['destination'])
    manifest_path = ROOT / 'android/app/src/main/assets/models/manifest.json'
    for name, expected in json.loads(manifest_path.read_text()).items():
        if sha(manifest_path.parent / name) != expected:
            raise ValueError('Model mismatch: ' + name)
    print('Models and native libraries verified. Ready to build offline APK.')

def write(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    temp = destination.with_name(destination.name + '.part')
    try:
        with temp.open('wb') as out:
            shutil.copyfileobj(source, out)
        temp.replace(destination)
    finally:
        temp.unlink(missing_ok=True)

if __name__ == '__main__':
    prepare()
