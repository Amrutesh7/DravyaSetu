from pathlib import Path
import pandas as pd
from PIL import Image
import imagehash
from collections import defaultdict

CLEAN_ROOT = Path(r"D:\DravyaSetu\datasets\clean_candidate_pool")
INAT_MANIFEST = Path(r"D:\DravyaSetu\datasets\v2_expansion\metadata\inat_accepted_manifest.csv")
OUT = Path(r"D:\DravyaSetu\datasets\v2_expansion\metadata\cross_source_phash_audit.csv")
SUMMARY = Path(r"D:\DravyaSetu\datasets\v2_expansion\metadata\cross_source_phash_summary.csv")

MAX_DISTANCE = 6

print("=" * 70)
print("CROSS-SOURCE pHASH AUDIT")
print("=" * 70)

inat = pd.read_csv(INAT_MANIFEST)

clean_files = [
    p for p in CLEAN_ROOT.rglob("*")
    if p.is_file() and p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}
]

print(f"Clean pool images: {len(clean_files)}")
print(f"Accepted iNaturalist: {len(inat)}")
print()
print("Computing pHash values...")

clean_hashes = []
errors = []

for i, path in enumerate(clean_files, 1):
    try:
        with Image.open(path) as img:
            h = imagehash.phash(img)
        class_name = path.parent.name
        clean_hashes.append((class_name, str(path), str(h)))
    except Exception as e:
        errors.append((str(path), str(e)))

    if i % 500 == 0 or i == len(clean_files):
        print(f"Clean pool: {i}/{len(clean_files)}")

print()
print("Computing iNaturalist pHash values...")

inat_hashes = []

for i, row in enumerate(inat.itertuples(index=False), 1):
    path = Path(row.local_path)

    if not path.exists():
        # Try relative path under v2_expansion
        path = Path(r"D:\DravyaSetu\datasets\v2_expansion") / str(row.local_path)

    if not path.exists():
        errors.append((str(path), "File not found"))
        continue

    try:
        with Image.open(path) as img:
            h = imagehash.phash(img)
        inat_hashes.append((row.class_name, str(path), str(h), row.filename))
    except Exception as e:
        errors.append((str(path), str(e)))

    if i % 500 == 0 or i == len(inat):
        print(f"iNaturalist: {i}/{len(inat)}")

print()
print("Building pHash index...")

# Convert clean hashes to integer form
clean_index = []

for class_name, path, h in clean_hashes:
    clean_index.append((class_name, path, int(h, 16)))

print("Searching cross-source conflicts...")
print(f"Maximum pHash distance: {MAX_DISTANCE}")

results = []

for idx, (inat_class, inat_path, inat_h, inat_filename) in enumerate(inat_hashes, 1):
    h2 = int(inat_h, 16)

    for clean_class, clean_path, h1 in clean_index:
        if clean_class == inat_class:
            continue

        distance = (h1 ^ h2).bit_count()

        if distance <= MAX_DISTANCE:
            results.append({
                "inat_class": inat_class,
                "inat_path": inat_path,
                "inat_filename": inat_filename,
                "clean_class": clean_class,
                "clean_path": clean_path,
                "distance": distance,
                "inat_phash": inat_h,
                "clean_phash": format(h1, "016x"),
            })

print()
print("=" * 70)
print("AUDIT COMPLETE")
print("=" * 70)

df = pd.DataFrame(results)

if len(df):
    df = df.sort_values(["distance", "inat_class", "clean_class"])

    summary = (
        df.groupby(["distance", "inat_class", "clean_class"])
        .size()
        .reset_index(name="pair_count")
    )

    df.to_csv(OUT, index=False)
    summary.to_csv(SUMMARY, index=False)

    print(f"Cross-source conflict pairs: {len(df)}")
    print()
    print("DISTANCE COUNTS")
    print(df["distance"].value_counts().sort_index())
    print()
    print("CLASS-PAIR SUMMARY")
    print(summary.to_string(index=False))
else:
    pd.DataFrame(columns=[
        "inat_class",
        "inat_path",
        "inat_filename",
        "clean_class",
        "clean_path",
        "distance",
        "inat_phash",
        "clean_phash"
    ]).to_csv(OUT, index=False)

    pd.DataFrame(columns=[
        "distance",
        "inat_class",
        "clean_class",
        "pair_count"
    ]).to_csv(SUMMARY, index=False)

    print("Cross-source conflict pairs: 0")

print()
print(f"Hash errors: {len(errors)}")

if errors:
    error_file = OUT.parent / "cross_source_phash_errors.csv"
    pd.DataFrame(errors, columns=["path", "error"]).to_csv(error_file, index=False)
    print(f"Error report: {error_file}")

print()
print("REPORT:")
print(OUT)
print()
print("SUMMARY:")
print(SUMMARY)
