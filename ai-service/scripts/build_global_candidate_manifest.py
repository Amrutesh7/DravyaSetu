import csv
import os
from collections import Counter

BASE = r"D:\DravyaSetu\datasets\v2_expansion"
META = os.path.join(BASE, "metadata")

UNIFIED_OLD = os.path.join(META, "unified_candidate_manifest.csv")
INAT_ACCEPTED = os.path.join(META, "inat_accepted_manifest.csv")
WIKI = os.path.join(META, "downloaded_manifest.csv")
RAKTA = os.path.join(META, "raktachandini_legacy_candidates.csv")

OUTPUT = os.path.join(META, "global_candidate_manifest.csv")


def read_csv(path):
    if not os.path.exists(path):
        print("WARNING - file not found:", path)
        return []

    with open(path, "r", encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def first(row, *keys):
    for key in keys:
        value = row.get(key, "")
        if value not in ("", None):
            return value
    return ""


records = []


# =========================================================
# 1. LEGACY POOL
# =========================================================

old = read_csv(UNIFIED_OLD)

legacy = [
    r for r in old
    if r.get("source") == "Legacy_Pool"
]

for r in legacy:
    records.append({
        "class_name": r.get("class_name", ""),
        "source": "Legacy_Pool",
        "source_path": r.get("source_path", ""),
        "source_url": r.get("source_url", ""),
        "observation_id": r.get("observation_id", ""),
        "photo_id": r.get("photo_id", ""),
        "creator": r.get("creator", ""),
        "license": r.get("license", ""),
        "quality_grade": r.get("quality_grade", ""),
        "evidence_type": r.get("evidence_type", ""),
        "sha256": r.get("sha256", ""),
        "width": r.get("width", ""),
        "height": r.get("height", ""),
        "status": "CANDIDATE"
    })

print("Legacy Pool:", len(legacy))


# =========================================================
# 2. ACCEPTED INATURALIST
# =========================================================

inat = read_csv(INAT_ACCEPTED)

for r in inat:
    records.append({
        "class_name": r.get("class_name", ""),
        "source": "iNaturalist",
        "source_path": r.get("local_path", ""),
        "source_url": r.get("source_url", ""),
        "observation_id": r.get("observation_id", ""),
        "photo_id": r.get("photo_id", ""),
        "creator": r.get("creator", ""),
        "license": r.get("license", ""),
        "quality_grade": r.get("quality_grade", ""),
        "evidence_type": r.get("evidence_type", ""),
        "sha256": r.get("sha256", ""),
        "width": r.get("width", ""),
        "height": r.get("height", ""),
        "status": "CANDIDATE"
    })

print("Accepted iNaturalist:", len(inat))


# =========================================================
# 3. WIKIMEDIA COMMONS
# =========================================================

wiki = read_csv(WIKI)

for r in wiki:
    records.append({
        "class_name": first(
            r, "class_name", "class"
        ),
        "source": "Wikimedia_Commons",
        "source_path": first(
            r, "local_path", "path", "source_path"
        ),
        "source_url": first(
            r, "source_url", "url", "page_url"
        ),
        "observation_id": "",
        "photo_id": first(
            r, "photo_id", "image_id", "file_id"
        ),
        "creator": first(
            r, "creator", "author"
        ),
        "license": first(
            r, "license", "license_name"
        ),
        "quality_grade": "",
        "evidence_type": first(
            r, "evidence_type", "plant_part"
        ),
        "sha256": r.get("sha256", ""),
        "width": r.get("width", ""),
        "height": r.get("height", ""),
        "status": "CANDIDATE"
    })

print("Wikimedia Commons:", len(wiki))


# =========================================================
# 4. RAKTACHANDINI LEGACY
# =========================================================

r_candidates = read_csv(RAKTA)

r_added = 0

for r in r_candidates:

    width = first(r, "width")
    height = first(r, "height")

    try:
        good_dimensions = (
            int(width) >= 384
            and int(height) >= 384
        )
    except (ValueError, TypeError):
        good_dimensions = False

    status_text = str(
        first(r, "acceptable", "quality_status", "status")
    ).lower()

    rejected = (
        "reject" in status_text
        or "fail" in status_text
    )

    if rejected or not good_dimensions:
        continue

    records.append({
        "class_name": "Raktachandini",
        "source": "iNaturalist_Legacy",
        "source_path": first(
            r, "local_path", "source_path"
        ),
        "source_url": first(
            r, "source_url", "photo_url"
        ),
        "observation_id": first(
            r, "observation_id"
        ),
        "photo_id": first(
            r, "photo_id"
        ),
        "creator": first(
            r, "creator"
        ),
        "license": first(
            r, "license", "license_code"
        ),
        "quality_grade": first(
            r, "quality_grade"
        ),
        "evidence_type": first(
            r, "evidence_type", "plant_part"
        ),
        "sha256": first(
            r, "sha256"
        ),
        "width": width,
        "height": height,
        "status": "CANDIDATE"
    })

    r_added += 1

print("Raktachandini accepted:", r_added)


# =========================================================
# 5. WRITE GLOBAL MANIFEST
# =========================================================

fields = [
    "class_name",
    "source",
    "source_path",
    "source_url",
    "observation_id",
    "photo_id",
    "creator",
    "license",
    "quality_grade",
    "evidence_type",
    "sha256",
    "width",
    "height",
    "status"
]

with open(
    OUTPUT,
    "w",
    encoding="utf-8",
    newline=""
) as f:

    writer = csv.DictWriter(
        f,
        fieldnames=fields
    )

    writer.writeheader()
    writer.writerows(records)


# =========================================================
# 6. SUMMARY
# =========================================================

source_counts = Counter(
    r["source"]
    for r in records
)

class_counts = Counter(
    r["class_name"]
    for r in records
)


print()
print("=" * 60)
print("GLOBAL CANDIDATE MANIFEST")
print("=" * 60)

print("Total candidates:", len(records))

print()
print("By source:")

for source, count in sorted(
    source_counts.items()
):
    print(f"  {source}: {count}")

print()
print("By class:")

for class_name, count in sorted(
    class_counts.items()
):
    print(f"  {class_name}: {count}")

print()
print("Classes represented:", len(class_counts))

print()
print("Saved:")
print(OUTPUT)

print("=" * 60)
