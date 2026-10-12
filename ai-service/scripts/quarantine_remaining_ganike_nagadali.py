from pathlib import Path
import shutil
import pandas as pd

files = [
    Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Nagadali\arya_shah_5f6c42683ee4.jpg"),
    Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Nagadali\arya_shah_8eb11667f0e3.jpg"),
    Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Nagadali\arya_shah_c14578a8878e.jpg"),
]

quarantine_root = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\duplicates\cross_class_phash\ganike_nagadali_distance4_6"
)

quarantine_root.mkdir(parents=True, exist_ok=True)

rows = []

for source in files:

    destination = quarantine_root / "Nagadali" / source.name
    destination.parent.mkdir(parents=True, exist_ok=True)

    if not source.exists():
        status = "MISSING"

    elif destination.exists():
        status = "ALREADY_QUARANTINED"

    else:
        shutil.move(str(source), str(destination))
        status = "MOVED"

    rows.append({
        "class": "Nagadali",
        "filename": source.name,
        "source": str(source),
        "quarantine_path": str(destination),
        "status": status
    })

df = pd.DataFrame(rows)

audit = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\ganike_nagadali_distance4_6_quarantine_audit.csv"
)

df.to_csv(audit, index=False)

print()
print("=" * 70)
print("REMAINING GANike / NAGADALI QUARANTINE")
print("=" * 70)

print(df.to_string(index=False))

print()
print("SUMMARY")
print("-" * 70)
print("Total:", len(df))
print("Moved:", (df["status"] == "MOVED").sum())
print("Already quarantined:", (df["status"] == "ALREADY_QUARANTINED").sum())
print("Missing:", (df["status"] == "MISSING").sum())

print()
print("QUARANTINE COMPLETE")
print("Quarantine:", quarantine_root)
print("Audit:", audit)