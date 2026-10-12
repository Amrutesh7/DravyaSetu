import hashlib
import pandas as pd
from pathlib import Path

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

rows = []

for class_name, path in files:
    sha256 = hashlib.sha256(path.read_bytes()).hexdigest()

    rows.append({
        "class": class_name,
        "filename": path.name,
        "path": str(path),
        "sha256": sha256
    })

df = pd.DataFrame(rows)

out = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\ganike_nagadali_distance2_4file_sha256.csv"
)

df.to_csv(out, index=False)

print()
print("SHA-256 AUDIT")
print("=" * 60)
print(df.to_string(index=False))

print()
print("Cross-class exact SHA-256 matches:")

found = False

for sha, group in df.groupby("sha256"):
    classes = set(group["class"])

    if len(classes) > 1:
        found = True
        print(group[["class", "filename", "sha256"]].to_string(index=False))
        print()

if not found:
    print("NONE")

print()
print("AUDIT COMPLETE")
print("Report:", out)