import unittest
from unittest.mock import patch
from check_apk import dynamic_symbols

class NativeLinkageTest(unittest.TestCase):
    def test_preserves_required_elf_versions(self):
        output = '''Symbol table '.dynsym' contains 3 entries:
   Num:    Value          Size Type    Bind   Vis      Ndx Name
    23: 0000000000000000     0 FUNC    GLOBAL DEFAULT  UND OrtGetApiBase@VERS_1.28.2 (3)
   217: 00000000005cdd2c    12 FUNC    GLOBAL DEFAULT   14 OrtGetApiBase@@VERS_1.28.0
'''
        with patch('check_apk.shutil.which', return_value='/usr/bin/readelf'), patch('check_apk.subprocess.check_output', return_value=output):
            imports, exports = dynamic_symbols('unused')
        self.assertIn('OrtGetApiBase@VERS_1.28.2', imports)
        self.assertIn('OrtGetApiBase@VERS_1.28.0', exports)
        self.assertFalse(imports <= exports)

if __name__ == '__main__':
    unittest.main()
