import csv
import os
import shutil
from datetime import datetime


BASE = r"D:\DravyaSetu\datasets\v2_expansion"
META = os.path.join(BASE, "metadata")

GLOBAL_MANIFEST = os.path.join(
    META,
    "global_candidate_manifest.csv"
)

WIKIMEDIA_MANIFEST = os.path.join(
    META,
    "downloaded_manifest.csv"
)

BACKUP_MANIFEST = GLOBAL_MANIFEST + ".backup"

TEMP_MANIFEST = GLOBAL_MANIFEST + ".tmp"


# ============================================================
# CHECK INPUT FILES
# ============================================================

if not os.path.isfile(GLOBAL_MANIFEST):
    raise FileNotFoundError(
        f"Global manifest not found:\n{GLOBAL_MANIFEST}"
    )

if not os.path.isfile(WIKIMEDIA_MANIFEST):
    raise FileNotFoundError(
        f"Wikimedia manifest not found:\n{WIKIMEDIA_MANIFEST}"
    )


# ============================================================
# LOAD GLOBAL MANIFEST
# ============================================================

with open(
    GLOBAL_MANIFEST,
    "r",
    encoding="utf-8-sig",
    newline=""
) as f:

    global_rows = list(
        csv.DictReader(f)
    )


if not global_rows:
    raise RuntimeError(
        "Global manifest is empty."
    )


global_fields = list(
    global_rows[0].keys()
)


# ============================================================
# LOAD WIKIMEDIA MANIFEST
# ============================================================

with open(
    WIKIMEDIA_MANIFEST,
    "r",
    encoding="utf-8-sig",
    newline=""
) as f:

    wikimedia_rows = list(
        csv.DictReader(f)
    )


if not wikimedia_rows:
    raise RuntimeError(
        "Wikimedia manifest is empty."
    )


wikimedia_fields = list(
    wikimedia_rows[0].keys()
)


# ============================================================
# VALIDATE REQUIRED COLUMNS
# ============================================================

required_global = [
    "source",
    "source_path",
    "source_url"
]

required_wikimedia = [
    "source_url",
    "filename"
]

for column in required_global:

    if column not in global_fields:

        raise RuntimeError(
            f"Global manifest missing column: {column}"
        )


for column in required_wikimedia:

    if column not in wikimedia_fields:

        raise RuntimeError(
            f"Wikimedia manifest missing column: {column}"
        )


# ============================================================
# BUILD WIKIMEDIA LOOKUP
# ============================================================

wikimedia_lookup = {}

duplicate_wikimedia_urls = []

for row in wikimedia_rows:

    url = row.get(
        "source_url",
        ""
    ).strip()

    filename = row.get(
        "filename",
        ""
    ).strip()

    if not url:
        continue

    if url in wikimedia_lookup:

        duplicate_wikimedia_urls.append(
            url
        )

    else:

        wikimedia_lookup[url] = filename


# ============================================================
# BACKUP GLOBAL MANIFEST
# ============================================================

if os.path.exists(BACKUP_MANIFEST):

    timestamp = datetime.now().strftime(
        "%Y%m%d_%H%M%S"
    )

    BACKUP_MANIFEST = (
        GLOBAL_MANIFEST
        + f".backup_{timestamp}"
    )


shutil.copy2(
    GLOBAL_MANIFEST,
    BACKUP_MANIFEST
)


print("=" * 70)
print("WIKIMEDIA SOURCE-PATH REPAIR")
print("=" * 70)

print(
    f"Global manifest       : {GLOBAL_MANIFEST}"
)

print(
    f"Wikimedia manifest    : {WIKIMEDIA_MANIFEST}"
)

print(
    f"Backup created        : {BACKUP_MANIFEST}"
)

print()


# ============================================================
# REPAIR PATHS
# ============================================================

wikimedia_total = 0
wikimedia_repaired = 0
wikimedia_missing_filename = 0
wikimedia_missing_file = 0
wikimedia_unmatched_url = 0
wikimedia_already_correct = 0

unmatched_urls = []
missing_files = []

for row in global_rows:

    if row.get(
        "source",
        ""
    ) != "Wikimedia_Commons":

        continue

    wikimedia_total += 1

    source_url = row.get(
        "source_url",
        ""
    ).strip()

    if not source_url:

        wikimedia_unmatched_url += 1

        unmatched_urls.append(
            source_url
        )

        continue

    filename = wikimedia_lookup.get(
        source_url,
        ""
    ).strip()

    if not filename:

        wikimedia_missing_filename += 1

        unmatched_urls.append(
            source_url
        )

        continue

    # --------------------------------------------------------
    # Normalize path
    # --------------------------------------------------------

    filename = filename.replace(
        "/",
        os.sep
    )

    filename = filename.replace(
        "\\",
        os.sep
    )

    # --------------------------------------------------------
    # Verify file exists
    # --------------------------------------------------------

    absolute_path = os.path.normpath(
        os.path.join(
            BASE,
            filename
        )
    )

    if not os.path.isfile(
        absolute_path
    ):

        wikimedia_missing_file += 1

        missing_files.append(
            absolute_path
        )

        continue

    # --------------------------------------------------------
    # Repair source_path
    # --------------------------------------------------------

    current_path = row.get(
        "source_path",
        ""
    ).strip()

    normalized_current = (
        current_path
        .replace("/", os.sep)
        .replace("\\", os.sep)
    )

    if normalized_current == filename:

        wikimedia_already_correct += 1

    else:

        row["source_path"] = filename

        wikimedia_repaired += 1


# ============================================================
# WRITE REPAIRED MANIFEST
# ============================================================

with open(
    TEMP_MANIFEST,
    "w",
    encoding="utf-8-sig",
    newline=""
) as f:

    writer = csv.DictWriter(
        f,
        fieldnames=global_fields
    )

    writer.writeheader()

    writer.writerows(
        global_rows
    )


os.replace(
    TEMP_MANIFEST,
    GLOBAL_MANIFEST
)


# ============================================================
# RESULTS
# ============================================================

print(
    f"Wikimedia records       : {wikimedia_total}"
)

print(
    f"Paths repaired          : {wikimedia_repaired}"
)

print(
    f"Already correct         : {wikimedia_already_correct}"
)

print(
    f"Missing filename        : {wikimedia_missing_filename}"
)

print(
    f"Missing physical files  : {wikimedia_missing_file}"
)

print(
    f"Unmatched source URLs   : {wikimedia_unmatched_url}"
)

print(
    f"Duplicate Wikimedia URLs: {len(duplicate_wikimedia_urls)}"
)

print()


# ============================================================
# VALIDATION
# ============================================================

print("-" * 70)
print("VALIDATION")
print("-" * 70)

remaining_empty = 0
remaining_missing = 0

for row in global_rows:

    if row.get(
        "source",
        ""
    ) != "Wikimedia_Commons":

        continue

    path = row.get(
        "source_path",
        ""
    ).strip()

    if not path:

        remaining_empty += 1

        continue

    path = path.replace(
        "/",
        os.sep
    ).replace(
        "\\",
        os.sep
    )

    absolute_path = os.path.normpath(
        os.path.join(
            BASE,
            path
        )
    )

    if not os.path.isfile(
        absolute_path
    ):

        remaining_missing += 1


print(
    f"Empty Wikimedia paths : {remaining_empty}"
)

print(
    f"Missing Wikimedia files: {remaining_missing}"
)


# ============================================================
# FINAL STATUS
# ============================================================

print()

if (
    wikimedia_total == 1273
    and remaining_empty == 0
    and remaining_missing == 0
    and wikimedia_unmatched_url == 0
):

    print(
        "PASS: Wikimedia source paths repaired successfully."
    )

else:

    print(
        "WARNING: Wikimedia path repair needs attention."
    )


print()

print(
    f"Updated manifest:"
)

print(
    GLOBAL_MANIFEST
)

print("=" * 70)