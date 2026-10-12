from pathlib import Path
import pandas as pd

source_csv = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\ganike_nagadali_conflicts.csv"
)

output_csv = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\ganike_nagadali_distance4_active_conflicts.csv"
)

clean_root = Path(
    r"D:\DravyaSetu\datasets\clean_candidate_pool"
)

df = pd.read_csv(source_csv)

distance4 = df[df["distance"] == 4].copy()

active_rows = []

for _, row in distance4.iterrows():

    ganike_path = Path(row["ganike_path"])
    nagadali_path = Path(row["nagadali_path"])

    ganike_active = ganike_path.exists()
    nagadali_active = nagadali_path.exists()

    if ganike_active and nagadali_active:
        active_rows.append({
            "ganike_path": str(ganike_path),
            "nagadali_path": str(nagadali_path),
            "distance": row["distance"]
        })

active_df = pd.DataFrame(active_rows)

active_df.to_csv(output_csv, index=False)

print()
print("DISTANCE-4 ACTIVE CONFLICTS")
print("=" * 60)
print("Original distance-4 pairs:", len(distance4))
print("Still-active distance-4 pairs:", len(active_df))

print()

if len(active_df) > 0:
    print(active_df.to_string(index=False))
else:
    print("NONE")

print()
print("Report:", output_csv)