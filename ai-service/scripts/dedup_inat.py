import os
import pandas as pd

BASE_DIR = r"D:\DravyaSetu\datasets\v2_expansion"
INPUT = os.path.join(BASE_DIR, "metadata", "inat_download_manifest.csv")
OUTPUT = os.path.join(BASE_DIR, "metadata", "inat_deduplicated_manifest.csv")
DUPLICATES = os.path.join(BASE_DIR, "metadata", "inat_exact_duplicates.csv")

df = pd.read_csv(INPUT)

# Keep the first occurrence of each actual file hash.
df = df.sort_values(["sha256", "photo_id"])

duplicate_mask = df.duplicated("sha256", keep="first")

duplicates = df[duplicate_mask].copy()
unique_df = df[~duplicate_mask].copy()

duplicates["duplicate_of_sha256"] = duplicates["sha256"]

unique_df.to_csv(OUTPUT, index=False)
duplicates.to_csv(DUPLICATES, index=False)

print("======================================")
print("iNaturalist exact deduplication")
print("======================================")
print(f"Input records:       {len(df)}")
print(f"Unique photographs:  {len(unique_df)}")
print(f"Duplicate records:   {len(duplicates)}")
print(f"Duplicate groups:    {df['sha256'].duplicated(keep=False).sum() - len(duplicates)}")
print()
print(f"Clean manifest:      {OUTPUT}")
print(f"Duplicate manifest:  {DUPLICATES}")
print()
print("Class counts after deduplication:")
print(unique_df["class_name"].value_counts().sort_index().to_string())
