from pathlib import Path
from collections import defaultdict
import pandas as pd
import imagehash
from PIL import Image
from itertools import combinations
from time import time

# ============================================================
# CONFIG
# ============================================================

ROOT = Path(
    r"D:\DravyaSetu\datasets\clean_candidate_pool"
)

OUTPUT_DIR = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata"
)

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

MASTER_OUTPUT = OUTPUT_DIR / "full_cross_class_phash_audit.csv"
SUMMARY_OUTPUT = OUTPUT_DIR / "full_cross_class_phash_summary.csv"
ERROR_OUTPUT = OUTPUT_DIR / "full_cross_class_phash_errors.csv"

MAX_DISTANCE = 6

VALID_EXTENSIONS = {
    ".jpg",
    ".jpeg",
    ".png",
    ".webp",
    ".bmp"
}


# ============================================================
# FIND ALL IMAGES
# ============================================================

print()
print("=" * 75)
print("FULL CROSS-CLASS pHASH AUDIT")
print("=" * 75)

print()
print("Scanning:", ROOT)

images = []

for class_dir in sorted(ROOT.iterdir()):

    if not class_dir.is_dir():
        continue

    class_name = class_dir.name

    for path in class_dir.rglob("*"):

        if path.is_file() and path.suffix.lower() in VALID_EXTENSIONS:

            images.append({
                "class_name": class_name,
                "path": path
            })


print()
print("Images found:", len(images))
print("Classes found:", len(set(x["class_name"] for x in images)))


# ============================================================
# COMPUTE pHASH
# ============================================================

print()
print("Computing pHash...")
print()

hash_records = []
errors = []

start = time()

for i, item in enumerate(images, start=1):

    path = item["path"]

    try:

        with Image.open(path) as img:

            img = img.convert("RGB")

            ph = imagehash.phash(img)

        hash_records.append({
            "class_name": item["class_name"],
            "path": str(path),
            "filename": path.name,
            "phash": str(ph),
            "hash_int": int(str(ph), 16)
        })

    except Exception as e:

        errors.append({
            "class_name": item["class_name"],
            "path": str(path),
            "error": str(e)
        })

    if i % 500 == 0 or i == len(images):

        elapsed = time() - start

        print(
            f"Processed {i}/{len(images)} "
            f"({i / len(images) * 100:.1f}%)"
        )


print()
print("pHash computation complete.")
print("Successful:", len(hash_records))
print("Errors:", len(errors))


# ============================================================
# SAVE ERROR REPORT
# ============================================================

if errors:

    pd.DataFrame(errors).to_csv(
        ERROR_OUTPUT,
        index=False
    )

    print("Error report:", ERROR_OUTPUT)


# ============================================================
# FAST HAMMING SEARCH
#
# 64-bit pHash split into four 16-bit chunks.
#
# If total Hamming distance <= 6,
# at least one of the four chunks must have
# Hamming distance <= 1.
#
# Therefore we only inspect candidates sharing
# one chunk or a one-bit-neighbor chunk.
# ============================================================

print()
print("Building fast pHash buckets...")

records = hash_records

buckets = [
    defaultdict(list),
    defaultdict(list),
    defaultdict(list),
    defaultdict(list)
]

for idx, rec in enumerate(records):

    value = rec["hash_int"]

    for chunk in range(4):

        shift = chunk * 16
        key = (value >> shift) & 0xFFFF

        buckets[chunk][key].append(idx)


# ============================================================
# HELPER: 16-BIT HAMMING DISTANCE
# ============================================================

def hamming16(a, b):
    return (a ^ b).bit_count()


# ============================================================
# FIND CROSS-CLASS CONFLICTS
# ============================================================

print()
print("Searching for cross-class conflicts...")
print("Maximum pHash distance:", MAX_DISTANCE)

pairs = set()

for i, rec in enumerate(records):

    value = rec["hash_int"]

    candidate_indices = set()

    for chunk in range(4):

        shift = chunk * 16

        key = (value >> shift) & 0xFFFF

        # Same chunk
        candidate_indices.update(
            buckets[chunk].get(key, [])
        )

        # One-bit neighbors
        for bit in range(16):

            neighbor = key ^ (1 << bit)

            candidate_indices.update(
                buckets[chunk].get(neighbor, [])
            )

    for j in candidate_indices:

        if j <= i:
            continue

        other = records[j]

        # Only different classes
        if rec["class_name"] == other["class_name"]:
            continue

        distance = (value ^ other["hash_int"]).bit_count()

        if distance <= MAX_DISTANCE:

            # Canonical pair ordering
            pair = tuple(sorted([i, j]))

            if pair in pairs:
                continue

            pairs.add(pair)


print()
print("Cross-class conflict pairs:", len(pairs))


# ============================================================
# BUILD MASTER DATAFRAME
# ============================================================

rows = []

for pair_id, (i, j) in enumerate(sorted(pairs), start=1):

    a = records[i]
    b = records[j]

    distance = (
        a["hash_int"] ^ b["hash_int"]
    ).bit_count()

    rows.append({
        "pair_id": pair_id,

        "class_a": a["class_name"],
        "path_a": a["path"],
        "filename_a": a["filename"],
        "phash_a": a["phash"],

        "class_b": b["class_name"],
        "path_b": b["path"],
        "filename_b": b["filename"],
        "phash_b": b["phash"],

        "distance": distance,

        "decision": "REVIEW",
        "notes": ""
    })


result = pd.DataFrame(rows)


# ============================================================
# SORT BY DISTANCE
# ============================================================

if len(result) > 0:

    result = result.sort_values(
        by=["distance", "class_a", "class_b"]
    ).reset_index(drop=True)

    result["pair_id"] = range(1, len(result) + 1)


# ============================================================
# SAVE MASTER REPORT
# ============================================================

result.to_csv(
    MASTER_OUTPUT,
    index=False
)


# ============================================================
# SUMMARY BY DISTANCE + CLASS PAIR
# ============================================================

if len(result) > 0:

    result["class_pair"] = (
        result["class_a"] + " <-> " + result["class_b"]
    )

    summary = (
        result
        .groupby(["distance", "class_pair"])
        .size()
        .reset_index(name="pair_count")
        .sort_values(
            by=["distance", "pair_count"],
            ascending=[True, False]
        )
    )

else:

    summary = pd.DataFrame(
        columns=[
            "distance",
            "class_pair",
            "pair_count"
        ]
    )


summary.to_csv(
    SUMMARY_OUTPUT,
    index=False
)


# ============================================================
# FINAL REPORT
# ============================================================

print()
print("=" * 75)
print("AUDIT COMPLETE")
print("=" * 75)

print()
print("Images scanned:", len(images))
print("Successful hashes:", len(records))
print("Hash errors:", len(errors))
print("Classes:", len(set(x["class_name"] for x in records)))
print("Cross-class pairs:", len(result))

print()

if len(result) > 0:

    print("DISTANCE COUNTS")
    print("-" * 40)
    print(
        result["distance"]
        .value_counts()
        .sort_index()
        .to_string()
    )

    print()
    print("CLASS-PAIR SUMMARY")
    print("-" * 40)
    print(summary.to_string(index=False))

else:

    print("NO CROSS-CLASS pHASH CONFLICTS FOUND.")


print()
print("MASTER REPORT:")
print(MASTER_OUTPUT)

print()
print("SUMMARY REPORT:")
print(SUMMARY_OUTPUT)

if errors:

    print()
    print("ERROR REPORT:")
    print(ERROR_OUTPUT)

print()
print("No files were moved or deleted.")