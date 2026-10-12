from pathlib import Path
import pandas as pd
import shutil
from datetime import datetime

# ---------------------------------------------------------
# PATHS
# ---------------------------------------------------------

BASE = Path(r"D:\DravyaSetu")

INPUT_CSV = (
    BASE
    / "datasets"
    / "v2_expansion"
    / "metadata"
    / "ganike_nagadali_distance0_conflicts.csv"
)

QUARANTINE_DIR = (
    BASE
    / "datasets"
    / "v2_expansion"
    / "duplicates"
    / "cross_class_phash"
    / "ganike_nagadali_distance0"
)

AUDIT_CSV = (
    BASE
    / "datasets"
    / "v2_expansion"
    / "metadata"
    / "ganike_nagadali_distance0_quarantine_audit.csv"
)

# ---------------------------------------------------------
# LOAD PAIRS
# ---------------------------------------------------------

df = pd.read_csv(INPUT_CSV)

# ---------------------------------------------------------
# COLLECT UNIQUE FILES
# ---------------------------------------------------------

files = []

for _, row in df.iterrows():
    files.append(("Ganike", Path(row["path_a"])))
    files.append(("Nagadali", Path(row["path_b"])))

unique = {}
    
for original_class, path in files:
    key = str(path.resolve())
    unique[key] = (original_class, path)

print("Distance-0 pairs:", len(df))
print("Unique files to quarantine:", len(unique))
print()

# ---------------------------------------------------------
# CREATE QUARANTINE DIRECTORIES
# ---------------------------------------------------------

for class_name in ["Ganike", "Nagadali"]:
    (QUARANTINE_DIR / class_name).mkdir(
        parents=True,
        exist_ok=True
    )

# ---------------------------------------------------------
# MOVE FILES
# ---------------------------------------------------------

records = []

timestamp = datetime.now().isoformat(timespec="seconds")

for _, (original_class, source) in unique.items():

    destination = (
        QUARANTINE_DIR
        / original_class
        / source.name
    )

    record = {
        "timestamp": timestamp,
        "original_class": original_class,
        "original_path": str(source),
        "quarantine_path": str(destination),
        "decision": "QUARANTINE",
        "reason": (
            "Cross-class pHash distance 0 with visual confirmation "
            "of repeated/duplicate photographic scene between "
            "Ganike and Nagadali."
        ),
        "status": ""
    }

    if not source.exists():
        record["status"] = "SOURCE_MISSING"
        records.append(record)
        print("MISSING:", source)
        continue

    if destination.exists():
        record["status"] = "ALREADY_QUARANTINED"
        records.append(record)
        print("ALREADY EXISTS:", destination)
        continue

    shutil.move(str(source), str(destination))

    record["status"] = "MOVED"

    records.append(record)

    print("MOVED:")
    print(" ", source)
    print(" ->", destination)

# ---------------------------------------------------------
# SAVE AUDIT
# ---------------------------------------------------------

audit = pd.DataFrame(records)

audit.to_csv(
    AUDIT_CSV,
    index=False
)

# ---------------------------------------------------------
# SUMMARY
# ---------------------------------------------------------

print()
print("=" * 70)
print("QUARANTINE COMPLETE")
print("=" * 70)

print("Total unique files:", len(unique))
print("Moved:", (audit["status"] == "MOVED").sum())
print("Already quarantined:", (audit["status"] == "ALREADY_QUARANTINED").sum())
print("Missing:", (audit["status"] == "SOURCE_MISSING").sum())

print()
print("Quarantine directory:")
print(QUARANTINE_DIR)

print()
print("Audit report:")
print(AUDIT_CSV)