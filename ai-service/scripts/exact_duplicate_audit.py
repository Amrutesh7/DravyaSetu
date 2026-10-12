from pathlib import Path
import pandas as pd
from collections import Counter

BASE = Path(r"D:\DravyaSetu\datasets\v2_expansion")
META = BASE / "metadata"

INPUT = META / "unified_candidate_manifest.csv"

OUTPUT = META / "exact_duplicate_audit.csv"

df = pd.read_csv(INPUT)

print("=" * 80)
print("DRAVYASETU V2 - EXACT SHA256 DUPLICATE AUDIT")
print("=" * 80)

print("Input records:", len(df))

# Only records with hashes can participate immediately.
hashed = df[df["sha256"].fillna("").astype(str).str.len() == 64].copy()

print("Records with SHA-256:", len(hashed))
print("Records without SHA-256:", len(df) - len(hashed))

# ------------------------------------------------------------
# Duplicate groups
# ------------------------------------------------------------

groups = (
    hashed
    .groupby("sha256")
    .size()
    .reset_index(name="count")
)

duplicate_hashes = groups[groups["count"] > 1]

print()
print("Unique SHA-256:", groups["sha256"].nunique())
print("Duplicate SHA groups:", len(duplicate_hashes))
print(
    "Duplicate records beyond first:",
    int((duplicate_hashes["count"] - 1).sum())
)

# ------------------------------------------------------------
# Detailed duplicate audit
# ------------------------------------------------------------

duplicate_records = hashed[
    hashed["sha256"].isin(duplicate_hashes["sha256"])
].copy()

if not duplicate_records.empty:

    duplicate_records["duplicate_count"] = (
        duplicate_records
        .groupby("sha256")["sha256"]
        .transform("size")
    )

    duplicate_records["cross_class_duplicate"] = (
        duplicate_records
        .groupby("sha256")["class_name"]
        .transform("nunique")
        > 1
    )

    duplicate_records["cross_source_duplicate"] = (
        duplicate_records
        .groupby("sha256")["source"]
        .transform("nunique")
        > 1
    )

    duplicate_records = duplicate_records.sort_values(
        ["sha256", "class_name", "source"]
    )

    duplicate_records.to_csv(
        OUTPUT,
        index=False,
        encoding="utf-8"
    )

else:

    pd.DataFrame(
        columns=list(df.columns)
        + [
            "duplicate_count",
            "cross_class_duplicate",
            "cross_source_duplicate"
        ]
    ).to_csv(
        OUTPUT,
        index=False,
        encoding="utf-8"
    )

# ------------------------------------------------------------
# Cross-class duplicates
# ------------------------------------------------------------

cross_class = (
    duplicate_records[
        duplicate_records["cross_class_duplicate"] == True
    ]
    if not duplicate_records.empty
    else pd.DataFrame()
)

print()
print("Cross-class duplicate records:", len(cross_class))

if not cross_class.empty:

    print()
    print("Cross-class duplicate hashes:")

    for sha, group in cross_class.groupby("sha256"):

        classes = sorted(group["class_name"].dropna().unique())

        sources = sorted(group["source"].dropna().unique())

        print(
            f"{sha} | "
            f"classes={classes} | "
            f"sources={sources}"
        )

# ------------------------------------------------------------
# Cross-source duplicates
# ------------------------------------------------------------

cross_source = (
    duplicate_records[
        duplicate_records["cross_source_duplicate"] == True
    ]
    if not duplicate_records.empty
    else pd.DataFrame()
)

print()
print("Cross-source duplicate records:", len(cross_source))

# ------------------------------------------------------------
# Class impact
# ------------------------------------------------------------

print()
print("=" * 80)
print("CLASS COUNTS")
print("=" * 80)

class_counts = (
    df["class_name"]
    .value_counts()
    .sort_index()
)

print(class_counts.to_string())

print()
print("=" * 80)
print("AUDIT COMPLETE")
print("=" * 80)

print("Report:")
print(OUTPUT)
