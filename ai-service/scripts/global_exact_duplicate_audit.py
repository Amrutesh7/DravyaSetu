import csv
import os
from collections import defaultdict


# ============================================================
# CONFIGURATION
# ============================================================

BASE = r"D:\DravyaSetu\datasets\v2_expansion"
META = os.path.join(BASE, "metadata")

INPUT = os.path.join(
    META,
    "global_candidate_manifest.csv"
)

AUDIT = os.path.join(
    META,
    "global_exact_duplicate_audit.csv"
)


# ============================================================
# PATH RESOLUTION
# ============================================================

def resolve_path(path):
    """
    Resolve a manifest source_path safely.

    Handles:
    1. Absolute paths
    2. Paths relative to the dataset BASE
    3. Paths containing Windows separators
    """

    if not path:
        return ""

    path = path.strip()

    # Already absolute
    if os.path.isabs(path):
        return os.path.normpath(path)

    # Normalize Windows separators
    path = path.replace("/", os.sep)
    path = path.replace("\\", os.sep)

    # First interpretation:
    # relative to dataset BASE
    candidate = os.path.normpath(
        os.path.join(BASE, path)
    )

    if os.path.exists(candidate):
        return candidate

    # Second interpretation:
    # relative to metadata directory
    candidate = os.path.normpath(
        os.path.join(META, path)
    )

    if os.path.exists(candidate):
        return candidate

    # Third interpretation:
    # relative to repository root
    repo_root = os.path.dirname(
        os.path.dirname(BASE)
    )

    candidate = os.path.normpath(
        os.path.join(repo_root, path)
    )

    if os.path.exists(candidate):
        return candidate

    # Return the primary interpretation even if missing
    return os.path.normpath(
        os.path.join(BASE, path)
    )


# ============================================================
# LOAD MANIFEST
# ============================================================

if not os.path.exists(INPUT):
    raise FileNotFoundError(
        f"Global candidate manifest not found:\n{INPUT}"
    )


with open(
    INPUT,
    "r",
    encoding="utf-8-sig",
    newline=""
) as f:

    rows = list(
        csv.DictReader(f)
    )


if not rows:
    raise RuntimeError(
        "Global candidate manifest is empty."
    )


print("=" * 70)
print("GLOBAL EXACT DUPLICATE AUDIT")
print("=" * 70)

print(
    f"Manifest                    : {INPUT}"
)

print(
    f"Input records               : {len(rows)}"
)

print()


# ============================================================
# VALIDATE REQUIRED COLUMNS
# ============================================================

required_columns = [
    "source_path",
    "sha256",
    "class_name",
    "source",
    "source_url",
    "observation_id",
    "photo_id"
]

missing_columns = [
    c
    for c in required_columns
    if c not in rows[0]
]

if missing_columns:
    raise RuntimeError(
        "Manifest is missing required columns: "
        + ", ".join(missing_columns)
    )


# ============================================================
# EXACT DUPLICATE ANALYSIS
# ============================================================

sha_groups = defaultdict(list)

missing_files = []
missing_sha = []

resolved_paths = {}

for i, row in enumerate(rows):

    raw_path = row.get(
        "source_path",
        ""
    ).strip()

    sha = row.get(
        "sha256",
        ""
    ).strip().lower()

    # --------------------------------------------------------
    # Resolve filesystem path
    # --------------------------------------------------------

    resolved = resolve_path(
        raw_path
    )

    resolved_paths[i] = resolved

    # --------------------------------------------------------
    # Check file existence
    # --------------------------------------------------------

    if not resolved or not os.path.isfile(resolved):
        missing_files.append(i)

    # --------------------------------------------------------
    # Check SHA
    # --------------------------------------------------------

    if not sha:
        missing_sha.append(i)
    else:
        sha_groups[sha].append(i)


# ============================================================
# DUPLICATE GROUPS
# ============================================================

duplicate_groups = {
    sha: indices
    for sha, indices in sha_groups.items()
    if len(indices) > 1
}


# ============================================================
# BUILD AUDIT ROWS
# ============================================================

audit_rows = []

for sha, indices in duplicate_groups.items():

    classes = sorted({
        rows[i].get(
            "class_name",
            ""
        )
        for i in indices
    })

    sources = sorted({
        rows[i].get(
            "source",
            ""
        )
        for i in indices
    })

    for i in indices:

        row = rows[i]

        audit_rows.append({

            "sha256":
                sha,

            "class_name":
                row.get(
                    "class_name",
                    ""
                ),

            "source":
                row.get(
                    "source",
                    ""
                ),

            "source_path":
                row.get(
                    "source_path",
                    ""
                ),

            "resolved_path":
                resolved_paths[i],

            "source_url":
                row.get(
                    "source_url",
                    ""
                ),

            "observation_id":
                row.get(
                    "observation_id",
                    ""
                ),

            "photo_id":
                row.get(
                    "photo_id",
                    ""
                ),

            "duplicate_group_size":
                len(indices),

            "duplicate_classes":
                "|".join(classes),

            "duplicate_sources":
                "|".join(sources),

            "cross_class":
                len(classes) > 1,

            "cross_source":
                len(sources) > 1
        })


# ============================================================
# WRITE AUDIT CSV
# ============================================================

fields = [
    "sha256",
    "class_name",
    "source",
    "source_path",
    "resolved_path",
    "source_url",
    "observation_id",
    "photo_id",
    "duplicate_group_size",
    "duplicate_classes",
    "duplicate_sources",
    "cross_class",
    "cross_source"
]


with open(
    AUDIT,
    "w",
    encoding="utf-8",
    newline=""
) as f:

    writer = csv.DictWriter(
        f,
        fieldnames=fields
    )

    writer.writeheader()

    writer.writerows(
        audit_rows
    )


# ============================================================
# STATISTICS
# ============================================================

duplicate_records = sum(
    len(indices) - 1
    for indices in duplicate_groups.values()
)


cross_class_groups = sum(
    1
    for indices in duplicate_groups.values()
    if len({
        rows[i].get(
            "class_name",
            ""
        )
        for i in indices
    }) > 1
)


cross_source_groups = sum(
    1
    for indices in duplicate_groups.values()
    if len({
        rows[i].get(
            "source",
            ""
        )
        for i in indices
    }) > 1
)


unique_sha_count = len(
    sha_groups
)


# ============================================================
# SOURCE STATISTICS
# ============================================================

source_counts = defaultdict(int)

source_missing_files = defaultdict(int)

for i, row in enumerate(rows):

    source = row.get(
        "source",
        "UNKNOWN"
    )

    source_counts[source] += 1

    if i in missing_files:
        source_missing_files[source] += 1


# ============================================================
# CLASS STATISTICS
# ============================================================

class_counts = defaultdict(int)

class_missing_files = defaultdict(int)

for i, row in enumerate(rows):

    class_name = row.get(
        "class_name",
        "UNKNOWN"
    )

    class_counts[class_name] += 1

    if i in missing_files:
        class_missing_files[class_name] += 1


# ============================================================
# PRINT RESULTS
# ============================================================

print(
    f"Input records              : {len(rows)}"
)

print(
    f"Unique SHA-256             : {unique_sha_count}"
)

print(
    f"Duplicate SHA groups       : {len(duplicate_groups)}"
)

print(
    f"Duplicate records beyond 1 : {duplicate_records}"
)

print(
    f"Cross-class duplicate groups: {cross_class_groups}"
)

print(
    f"Cross-source duplicate groups: {cross_source_groups}"
)

print(
    f"Missing files              : {len(missing_files)}"
)

print(
    f"Missing SHA-256            : {len(missing_sha)}"
)

print()


# ============================================================
# SOURCE FILE CHECK
# ============================================================

print("-" * 70)
print("FILES BY SOURCE")
print("-" * 70)

for source in sorted(source_counts):

    total = source_counts[source]

    missing = source_missing_files.get(
        source,
        0
    )

    present = total - missing

    print(
        f"{source:<25} "
        f"Total: {total:<6} "
        f"Present: {present:<6} "
        f"Missing: {missing}"
    )


# ============================================================
# CLASS FILE CHECK
# ============================================================

print()
print("-" * 70)
print("CLASSES WITH MISSING FILES")
print("-" * 70)

classes_with_missing = [
    (
        cls,
        count
    )
    for cls, count
    in class_missing_files.items()
    if count > 0
]

if not classes_with_missing:

    print(
        "None"
    )

else:

    for cls, count in sorted(
        classes_with_missing
    ):

        print(
            f"{cls:<25} Missing: {count}"
        )


# ============================================================
# FINAL STATUS
# ============================================================

print()
print("-" * 70)
print("FINAL EXACT-DUPLICATE STATUS")
print("-" * 70)

if (
    len(missing_files) == 0
    and len(missing_sha) == 0
    and len(duplicate_groups) == 0
    and cross_class_groups == 0
):

    print(
        "PASS: Exact duplicate audit passed."
    )

else:

    print(
        "FAIL: Exact duplicate audit requires attention."
    )

print()

print(
    "Audit saved to:"
)

print(
    AUDIT
)

print("=" * 70)