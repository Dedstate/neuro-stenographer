import unittest
from evaluate import validate, wer

class EvaluationTest(unittest.TestCase):
    def test_empty(self):
        self.assertEqual(wer([],[])['wer'],0)
        self.assertIsNone(wer([],['word'])['wer'])
    def test_insert_delete_replace(self):
        self.assertEqual(wer(['a','b','c'],['a','x','c','d'])['word_errors'],2)
        self.assertEqual(wer(['a','b'],[])['word_errors'],2)
    def test_exact_schema(self):
        validate({'transcript':[]})
        with self.assertRaises(ValueError): validate({'transcript':[], 'extra':1})
    def test_boolean_is_not_milliseconds(self):
        with self.assertRaises(ValueError):
            validate({'transcript':[dict(speaker_id='s',start_ms=True,end_ms=20,text='t')]})
    def test_overlap_allowed(self):
        validate({'transcript':[dict(speaker_id='a',start_ms=0,end_ms=20,text='t'),dict(speaker_id='b',start_ms=10,end_ms=30,text='t')]})

if __name__ == '__main__':unittest.main()
