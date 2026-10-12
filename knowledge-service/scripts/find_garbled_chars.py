"""
Read-only scan: lists every non-ASCII character in knowledge-base/cleaned/*.txt
with its count and sample contexts, so garbled Sanskrit font characters
(e.g. "Vis£cik¡", "Graha¸¢") can be identified and mapped correctly.

Run from knowledge-service/:
  python scripts/find_garbled_chars.py

Prints the 40 most frequent characters and writes the full list to
knowledge-base/garbled_chars_report.txt (open it in VS Code to read it
correctly). Nothing is modified.
"""
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = Path(__file__).resolve().parent.parent
CLEANED = BASE / "knowledge-base" / "cleaned"
REPORT = BASE / "knowledge-base" / "garbled_chars_report.txt"
SKIP = set("\u2018\u2019\u201c\u201d\u2013\u2014\u2022\u2026\u00b0\u00a0")

counts = Counter()
samples = defaultdict(list)
files = defaultdict(Counter)

for path in sorted(CLEANED.glob("*.txt")):
    text = path.read_text(encoding="utf-8", errors="replace")
    for m in re.finditer(r"[^\x00-\x7f]", text):
        ch = m.group()
        if ch in SKIP:
            continue
        counts[ch] += 1
        files[ch][path.name] += 1
        if len(samples[ch]) < 3:
            ctx = text[max(0, m.start() - 12): m.end() + 12]
            samples[ch].append(" ".join(ctx.split()))


def line(ch):
    top = ", ".join(f"{f} ({n})" for f, n in files[ch].most_common(2))
    return (
        f"{ch}  U+{ord(ch):04X}  {unicodedata.name(ch, '?')[:30]:<30} "
        f"{counts[ch]:>6}  files: {top}\n      e.g. " + " | ".join(samples[ch])
    )


ordered = [c for c, _ in counts.most_common()]
REPORT.write_text("\n".join(line(c) for c in ordered), encoding="utf-8")

print(f"Distinct non-ASCII characters: {len(ordered)}  (full list: {REPORT})\n")
for c in ordered[:40]:
    print(line(c))