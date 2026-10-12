from pathlib import Path
import pandas as pd

BASE = Path(r"D:\DravyaSetu\datasets\v2_expansion")
META = BASE / "metadata"

print("=" * 80)
print("DRAVYASETU V2 - SOURCE INVENTORY")
print("=" * 80)

files = [
    "inat_observation_candidates.csv",
    "downloaded_manifest.csv",
    "raktachandini_legacy_candidates.csv",
]

for name in files:
    path = META / name

    print()
    print("-" * 80)
    print(name)

    if not path.exists():
        print("NOT FOUND")
        continue

    try:
        df = pd.read_csv(path)
    except Exception as e:
        print("READ ERROR:", e)
        continue

    print("Rows:", len(df))
    print("Columns:", list(df.columns))

    if "class_name" in df.columns:
        print()
        print("Class counts:")
        print(
            df["class_name"]
            .value_counts()
            .sort_index()
            .to_string()
        )

print()
print("=" * 80)
print("LEGACY / EXISTING POOL")
print("=" * 80)

POOL = Path(r"D:\DravyaSetu\datasets\clean_candidate_pool")

if POOL.exists():

    records = []

    for path in POOL.rglob("*"):

        if not path.is_file():
            continue

        if path.suffix.lower() not in {
            ".jpg",
            ".jpeg",
            ".png",
            ".bmp",
            ".webp"
        }:
            continue

        rel = path.relative_to(POOL)
        parts = rel.parts

        class_name = parts[0] if parts else "UNKNOWN"

        records.append({
            "path": str(path),
            "class_name": class_name
        })

    legacy = pd.DataFrame(records)

    print("Image files:", len(legacy))

    if not legacy.empty:

        print()
        print("Class counts:")

        print(
            legacy["class_name"]
            .value_counts()
            .sort_index()
            .to_string()
        )

else:

    print("clean_candidate_pool NOT FOUND")

print()
print("=" * 80)
print("TARGET")
print("=" * 80)

classes = [
    "Aloe_Vera",
    "Amla",
    "Amruta_Balli",
    "Arali",
    "Ashoka",
    "Ashwagandha",
    "Betel",
    "Betel_Nut",
    "Brahmi",
    "Castor",
    "Curry_Leaf",
    "Doddapatre",
    "Ekka",
    "Ganike",
    "Henna",
    "Hibiscus",
    "Honge",
    "Insulin",
    "Jasmine",
    "Lemon",
    "Lemon_grass",
    "Mango",
    "Mint",
    "Nagadali",
    "Neem",
    "Nithyapushpa",
    "Nooni",
    "Pappaya",
    "Pepper",
    "Pomegranate",
    "Raktachandini",
    "Rose",
    "Tulasi",
    "Wood_sorel",
]

targets = {
    cls: 295 if i < 4 else 294
    for i, cls in enumerate(classes)
}

print("Classes:", len(classes))
print("Target images:", sum(targets.values()))

print()

for cls in classes:
    print(f"{cls:20s} {targets[cls]}")

print()
print("=" * 80)
print("INVENTORY COMPLETE")
print("=" * 80)
