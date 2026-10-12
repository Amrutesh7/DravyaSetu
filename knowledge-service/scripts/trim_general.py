import json
from pathlib import Path

p = Path("knowledge-base/chunks/GENERAL.jsonl")
rows = [json.loads(l) for l in p.read_text(encoding="utf-8").splitlines() if l.strip()]

keep = []
for r in rows:
    i = r["section_chunk_index"]
    if 7 <= i <= 25:                       # Introduction only
        if i == 25:                        # chunk 25 runs into the first monograph
            r["text"] = r["text"].split("Dr Xiaorui Zhang")[0].strip()
        r["section"] = "WHO Monographs Vol.1 - Introduction"
        keep.append(r)

p.with_suffix(".jsonl.bak").write_text(p.read_text(encoding="utf-8"), encoding="utf-8")
p.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in keep) + "\n", encoding="utf-8")
print(f"kept {len(keep)} of {len(rows)} chunks")