#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: fix_acc_media_metadata_insertion.py <capacitor-build-dir>')

root = Path(sys.argv[1])
main_files = list((root / 'android/app/src/main/java').rglob('MainActivity.java'))
if not main_files:
    raise SystemExit('MainActivity.java not found')
main = main_files[0]
s = main.read_text(encoding='utf-8')

marker = '\n\n        @JavascriptInterface\n        public void downloadYoutubeAdvanced'
if marker not in s:
    raise SystemExit('advanced bridge marker not found')
# Close the legacy downloadYoutube method before the new bridge annotation.
s = s.replace(marker, '\n        }' + marker, 1)

# Remove the legacy method-closing brace that was left after the inserted advanced method.
tail = '        }\n    }\n}\n'
pos = s.rfind(tail)
if pos < 0:
    raise SystemExit('closing brace target not found')
s = s[:pos] + '    }\n}\n' + s[pos + len(tail):]

main.write_text(s, encoding='utf-8')
print('Fixed ACC Media advanced native bridge placement')
