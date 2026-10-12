from pathlib import Path
import shutil
import pandas as pd

files = [
    (
        "Ganike",
        Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Ganike\arya_shah_905a60055555.jpg")
    ),
    (
        "Ganike",
        Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Ganike\arya_shah_9372f2ef737e.jpg")
    ),
    (
        "Nagadali",
        Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Nagadali\arya_shah_3c7ef0876fe1.jpg")
    ),
    (
        "Nagadali",
        Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Nagadali\arya_shah_18ad7f646317.jpg")
    ),
]

quarantine_root = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\duplicates\cross_class_phash\ganike_nagadali_distance2"
)

quarantine_root.mkdir(parents=True, exist_ok=True)

audit_rows = []

for class_name, source in files:

    destination_dir = quarantine_root / class_name
    destination_dir.mkdir(parents=True, exist_ok=True)

    destination = destination_dir / source.name

    if not source.exists():
        status = "MISSING"

    elif destination.exists():
        status = "ALREADY_QUARANTINED"

    else:
        shutil.move(str(source), str(destination))
        status = "MOVED"

    audit_rows.append({
        "class": class_name,
        "filename": source.name,
        "source": str(source),
        "quarantine_path": str(destination),
        "status": status
    })

df = pd.DataFrame(audit_rows)

audit_file = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\ganike_nagadali_distance2_quarantine_audit.csv"
)

df.to_csv(audit_file, index=False)

print()
print("=" * 60)
print("GANike / NAGADALI DISTANCE-2 QUARANTINE")
print("=" * 60)

print(df.to_string(index=False))

print()
print("SUMMARY")
print("-" * 60)
print("Total files:", len(df))
print("Moved:", (df["status"] == "MOVED").sum())
print("Already quarantined:", (df["status"] == "ALREADY_QUARANTINED").sum())
print("Missing:", (df["status"] == "MISSING").sum())

print()
print("QUARANTINE COMPLETE")
print("Quarantine root:", quarantine_root)
print("Audit:", audit_file)