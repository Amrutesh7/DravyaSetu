"""
Text fixes applied by build_chunks.py (originals in cleaned/ are never changed).

clean_common(text)  -> all sources: ligatures, zero-width/en spaces, box lines,
                       and drops corrupted lines (Devanagari / replacement chars).
fix_sanskrit(text)  -> ONLY the two combined Ayurvedic Pharmacopoeia sources.
                       Their PDF font stores IAST letters as Latin-1 symbols,
                       e.g. "Vis£cik¡" -> "Visūcikā", "KUâÙHA" -> "KUṢṬHA".
The mapping below was derived from word evidence in your own files.
"""
import re
import unicodedata
from collections import Counter

STATS = {"corrupt_lines_dropped": 0}

# ---- all sources ------------------------------------------------------------
LIGATURES = {"\ufb00": "ff", "\ufb01": "fi", "\ufb02": "fl", "\ufb03": "ffi", "\ufb04": "ffl"}
BAD_LINE = re.compile(r"[\u0900-\u097f\ufffd]")  # Devanagari or replacement char


def clean_common(text: str) -> str:
    for k, v in LIGATURES.items():
        text = text.replace(k, v)
    text = text.replace("\u200b", "").replace("\u2002", " ").replace("\u2502", " ")
    kept = []
    for line in text.split("\n"):
        if BAD_LINE.search(line):
            STATS["corrupt_lines_dropped"] += 1
        else:
            kept.append(line)
    return "\n".join(kept)


# ---- Ayurvedic Pharmacopoeia of India (combined sources only) ---------------
# Direct, context-free replacements (lowercase Sanskrit letters)
LOWER = {"¡": "ā", "¢": "ī", "£": "ū", "¤": "ṛ", "¸": "ṇ", "¶": "ṭ", "À": "ṣ"}
# Direct replacements used for UPPERCASE letters in headings
UPPER = {"Ë": "Ā", "Ì": "Ī", "Í": "Ū", "Î": "Ṛ", "Û": "Ṇ", "Ú": "Ḍ", "Ù": "Ṭ", "â": "Ṣ"}
TABLE = str.maketrans({**LOWER, **UPPER})

LETTER_BEFORE = r"(?<=[^\W\d_])"
LETTER_AFTER = r"(?=[^\W\d_])"
UP = "A-ZĀĪŪṚṆḌṬṢŚṄḤ"


def fix_sanskrit(text: str) -> str:
    text = text.translate(TABLE)
    # context-dependent characters (only inside / between letters)
    text = re.sub(LETTER_BEFORE + "·" + LETTER_AFTER, "ḍ", text)
    text = re.sub(LETTER_BEFORE + "´" + LETTER_AFTER, "ṅ", text)
    text = re.sub(LETTER_BEFORE + "¿" + LETTER_AFTER, "ś", text)
    text = re.sub(LETTER_BEFORE + "Å", "ḥ", text)
    text = re.sub(LETTER_BEFORE + "Æ" + LETTER_AFTER, "ṃ", text)
    text = re.sub(f"(?<=[{UP}])×(?=[{UP}])", "Ṅ", text)   # HI×GU -> HIṄGU
    text = re.sub(f"(?<=[{UP}])é", "Ḥ", text)              # PUDÌNËé -> PUDĪNĀḤ
    text = re.sub(r"(?<![a-z])á", "Ś", text)                # á¢ta -> Śīta (keeps "sábila")
    return text


# ---- reporting --------------------------------------------------------------
KNOWN_OK = set("āīūṛṇṭḍṣśḥṅṃĀĪŪṚṆḌṬṢŚṄḤ") | set("µαβ‘’“”–—•…°©±")


def leftover_chars(texts, top=15):
    """Non-ASCII characters still present after fixing (to spot unmapped ones)."""
    c = Counter()
    for t in texts:
        for ch in t:
            if ord(ch) > 127 and ch not in KNOWN_OK:
                c[ch] += 1
    return [(ch, f"U+{ord(ch):04X}", unicodedata.name(ch, "?"), n) for ch, n in c.most_common(top)]