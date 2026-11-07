#!/usr/bin/env python3
"""Validate contest JSON and report text-only WER; does NOT measure diarization."""
import argparse
import json
import re
from pathlib import Path

def validate(data):
    if not isinstance(data, dict) or set(data) != {'transcript'} or not isinstance(data['transcript'], list):
        raise ValueError('Expected exactly {"transcript": [...]}')
    previous = -1
    for row in data['transcript']:
        if not isinstance(row, dict) or set(row) != {'speaker_id', 'start_ms', 'end_ms', 'text'}:
            raise ValueError('Wrong segment schema')
        if type(row['start_ms']) is not int or type(row['end_ms']) is not int:
            raise ValueError('Milliseconds must be integers, not floats or booleans')
        if row['start_ms'] < 0 or row['end_ms'] <= row['start_ms'] or row['start_ms'] < previous:
            raise ValueError('Invalid timestamps/order')
        if not isinstance(row['speaker_id'], str) or not row['speaker_id'].strip() or not isinstance(row['text'], str):
            raise ValueError('Invalid speaker_id/text')
        previous = row['start_ms']
    return data

def words(data):
    return re.findall(r'[^\W_]+', ' '.join(s['text'] for s in data['transcript']).lower(), re.UNICODE)

def wer(reference, hypothesis):
    # Linear memory Levenshtein. No transliteration, ё/e substitution or number rewriting.
    row = list(range(len(hypothesis) + 1))
    for i, a in enumerate(reference, 1):
        current = [i]
        for j, b in enumerate(hypothesis, 1):
            current.append(min(current[-1] + 1, row[j] + 1, row[j - 1] + (a != b)))
        row = current
    return {'word_errors': row[-1], 'reference_words': len(reference),
            'wer': row[-1] / len(reference) if reference else (0 if not hypothesis else None)}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('hypothesis', type=Path)
    parser.add_argument('--reference', type=Path)
    args = parser.parse_args()
    hyp = validate(json.loads(args.hypothesis.read_text()))
    print('JSON valid:',len(hyp['transcript']),'segments')
    if args.reference:
        ref=validate(json.loads(args.reference.read_text()))
        print(json.dumps(wer(words(ref), words(hyp)),ensure_ascii=False,indent=2))
