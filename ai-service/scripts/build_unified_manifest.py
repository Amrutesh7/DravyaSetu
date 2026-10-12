from pathlib import Path
import pandas as pd
import hashlib

BASE = Path(r"D:\DravyaSetu\datasets\v2_expansion")
META = BASE / "metadata"
POOL = Path(r"D:\DravyaSetu\datasets\clean_candidate_pool")

OUTPUT = META / "unified_candidate_manifest.csv"

records = []


def sha256_file(path):
    h = hashlib.sha256()

    try:
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1024 * 1024), b""):
                h.update(chunk)

        return h.hexdigest()

    except Exception:
        return ""


# ============================================================
# 1. EXISTING ARYA + WARCODER POOL
# ============================================================

print("=" * 80)
print("ADDING EXISTING ARYA + WARCODER POOL")
print("=" * 80)

if POOL.exists():

    count = 0

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

        relative = path.relative_to(POOL)
        parts = relative.parts

        class_name = parts[0] if parts else "UNKNOWN"

        records.append({
            "candidate_id": f"LEGACY_{count:06d}",
            "class_name": class_name,
            "source": "Legacy_Pool",
            "source_path": str(path),
            "source_url": "",
            "observation_id": "",
            "photo_id": "",
            "creator": "",
            "license": "",
            "quality_grade": "",
            "evidence_type": "",
            "sha256": sha256_file(path),
            "width": "",
            "height": "",
            "status": "CANDIDATE"
        })

        count += 1

    print("Added:", count)


# ============================================================
# 2. iNATURALIST
# ============================================================

print()
print("=" * 80)
print("ADDING INATURALIST")
print("=" * 80)

inat_file = META / "inat_observation_candidates.csv"

if inat_file.exists():

    df = pd.read_csv(inat_file)

    print("Records:", len(df))

    for i, row in df.iterrows():

        records.append({
            "candidate_id": f"INAT_{i:06d}",
            "class_name": row.get("class_name", ""),
            "source": "iNaturalist",
            "source_path": "",
            "source_url": row.get("photo_url", ""),
            "observation_id": row.get("observation_id", ""),
            "photo_id": row.get("photo_id", ""),
            "creator": row.get("creator", ""),
            "license": row.get("license_code", ""),
            "quality_grade": row.get("quality_grade", ""),
            "evidence_type": row.get("evidence_hint", ""),
            "sha256": "",
            "width": "",
            "height": "",
            "status": "CANDIDATE"
        })

else:

    print("iNaturalist manifest NOT FOUND")


# ============================================================
# 3. WIKIMEDIA COMMONS
# ============================================================

print()
print("=" * 80)
print("ADDING WIKIMEDIA COMMONS")
print("=" * 80)

commons_file = META / "downloaded_manifest.csv"

if commons_file.exists():

    df = pd.read_csv(commons_file)

    print("Records:", len(df))

    for i, row in df.iterrows():

        records.append({
            "candidate_id": f"COMMONS_{i:06d}",
            "class_name": row.get("class_name", ""),
            "source": "Wikimedia Commons",
            "source_path": row.get("filename", ""),
            "source_url": row.get("source_url", ""),
            "observation_id": row.get("observation_id", ""),
            "photo_id": row.get("photo_id", ""),
            "creator": row.get("creator", ""),
            "license": row.get("license", ""),
            "quality_grade": row.get("quality_grade", ""),
            "evidence_type": row.get("evidence_type", ""),
            "sha256": row.get("sha256", ""),
            "width": row.get("width", ""),
            "height": row.get("height", ""),
            "status": row.get("status", "CANDIDATE")
        })

else:

    print("Commons manifest NOT FOUND")


# ============================================================
# 4. RAKTACHANDINI LEGACY CANDIDATES
# ============================================================

print()
print("=" * 80)
print("ADDING RAKTACHANDINI LEGACY CANDIDATES")
print("=" * 80)

rakt_file = META / "raktachandini_legacy_candidates.csv"

if rakt_file.exists():

    df = pd.read_csv(rakt_file)

    print("Records:", len(df))

    for i, row in df.iterrows():

        records.append({
            "candidate_id": f"RAKTA_{i:06d}",
            "class_name": "Raktachandini",
            "source": "iNaturalist_Legacy",
            "source_path": row.get("filename", ""),
            "source_url": "",
            "observation_id": row.get("observation_id", ""),
            "photo_id": "",
            "creator": "",
            "license": row.get("license", ""),
            "quality_grade": "",
            "evidence_type": "",
            "sha256": row.get("sha256", ""),
            "width": "",
            "height": "",
            "status": "CANDIDATE"
        })

else:

    print("Raktachandini manifest NOT FOUND")


# ============================================================
# CREATE DATAFRAME
# ============================================================

df = pd.DataFrame(records)

print()
print("=" * 80)
print("UNIFIED INVENTORY")
print("=" * 80)

print("Total candidate records:", len(df))

print()
print("Source counts:")
print(df["source"].value_counts().to_string())

print()
print("Class counts:")
print(
    df["class_name"]
    .value_counts()
    .sort_index()
    .to_string()
)


# ============================================================
# SAVE
# ============================================================

df.to_csv(
    OUTPUT,
    index=False,
    encoding="utf-8"
)

print()
print("=" * 80)
print("SAVED")
print("=" * 80)

print(OUTPUT)
print("Rows:", len(df))
print("Columns:", len(df.columns))
