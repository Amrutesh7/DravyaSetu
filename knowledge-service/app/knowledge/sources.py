"""Source registry + citation builder for DravyaSetu RAG answers.

The registry file  knowledge-base/sources_registry.json  maps each source file
(stem, without .txt) to a trusted, human-readable record:
    {"source_id": "SRC-003", "title": "...", "reference": "...", "verified": true}

Generate / refresh the skeleton with:
    python -m scripts.make_sources_registry
then fill in title + reference for every entry and set "verified": true.
"""
import json
import re
from functools import lru_cache
from pathlib import Path

BASE = Path(__file__).resolve().parents[2]          # knowledge-service/
REGISTRY_PATH = BASE / "knowledge-base" / "sources_registry.json"

_CITE_RE = re.compile(r"\[(\d+(?:\s*,\s*\d+)*)\]")


def stem_of(source_file: str) -> str:
    return Path(source_file).stem if source_file.lower().endswith(".txt") else source_file


@lru_cache(maxsize=1)
def load_registry() -> dict:
    if REGISTRY_PATH.exists():
        return json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    return {}


def get_source(source_file: str) -> dict:
    """Trusted record for a source file. Falls back safely if not registered."""
    key = stem_of(source_file)
    rec = load_registry().get(key)
    if rec and rec.get("title"):
        return {
            "source_id": rec["source_id"],
            "title": rec["title"],
            "reference": rec.get("reference") or "Reference pending verification",
        }
    slug = re.sub(r"[^A-Za-z0-9]+", "-", key).strip("-").upper()[:24] or "UNKNOWN"
    return {
        "source_id": f"SRC-UNREG-{slug}",
        "title": key.replace("_", " ").strip(),
        "reference": "Reference pending verification",
    }


def build_cited_sources(text: str, hits: list[dict]) -> tuple[str, list[dict]]:
    """Rewrite [n] chunk citations in `text` into per-SOURCE numbers and return
    (new_text, sources). Only cited sources are returned, each once.
    sources = [{"ref": 1, "source_id": ..., "title": ..., "reference": ...}]
    If the model cited nothing valid, falls back to the sources of all hits."""
    order: dict[str, int] = {}
    meta: dict[str, dict] = {}

    def register(hit: dict) -> int:
        s = get_source(hit["source_file"])
        sid = s["source_id"]
        if sid not in order:
            order[sid] = len(order) + 1
            meta[sid] = s
        return order[sid]

    def repl(m: re.Match) -> str:
        shown: list[int] = []
        for n in (int(x) for x in re.findall(r"\d+", m.group(1))):
            if 1 <= n <= len(hits):
                d = register(hits[n - 1])
                if d not in shown:
                    shown.append(d)
        return "[" + ", ".join(map(str, shown)) + "]" if shown else ""

    new_text = _CITE_RE.sub(repl, text)
    if not order:                       # nothing valid cited -> list all retrieved sources
        for h in hits:
            register(h)
    sources = [{"ref": n, **meta[sid]} for sid, n in sorted(order.items(), key=lambda x: x[1])]
    return new_text, sources