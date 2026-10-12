import os
import time
import hashlib
import requests
import pandas as pd
from PIL import Image
from io import BytesIO
from datetime import datetime

BASE_DIR = r"D:\DravyaSetu\datasets\v2_expansion"

MANIFEST = os.path.join(
    BASE_DIR,
    "metadata",
    "inat_deduplicated_manifest.csv"
)

OUT_MANIFEST = os.path.join(
    BASE_DIR,
    "metadata",
    "inat_original_manifest.csv"
)

FAIL_LOG = os.path.join(
    BASE_DIR,
    "logs",
    "inat_original_download_failures.csv"
)

RAW_DIR = os.path.join(
    BASE_DIR,
    "raw",
    "iNaturalist"
)

os.makedirs(RAW_DIR, exist_ok=True)

df = pd.read_csv(MANIFEST)

session = requests.Session()
session.headers.update({
    "User-Agent": "DravyaSetu-V2-Dataset/1.0"
})

records = []
failures = []

total = len(df)

for i, row in df.iterrows():

    class_name = str(row["class_name"])
    photo_id = str(row["photo_id"])

    original_url = str(row["source_url"])

    # Upgrade square/medium/etc. URL to original.
    original_url = original_url.replace(
        "/square.",
        "/original."
    ).replace(
        "/small.",
        "/original."
    ).replace(
        "/medium.",
        "/original."
    ).replace(
        "/large.",
        "/original."
    )

    class_dir = os.path.join(
        RAW_DIR,
        class_name
    )

    os.makedirs(class_dir, exist_ok=True)

    # Use JPEG extension for the downloaded media.
    filename = f"{class_name}_inat_{photo_id}.jpg"

    output_path = os.path.join(
        class_dir,
        filename
    )

    print(
        f"[{i+1}/{total}] "
        f"{class_name} | photo {photo_id}"
    )

    try:

        # Resume support
        if os.path.exists(output_path) and os.path.getsize(output_path) > 0:

            with open(output_path, "rb") as f:
                data = f.read()

            image = Image.open(BytesIO(data))
            image.verify()

            image = Image.open(BytesIO(data))

            sha256 = hashlib.sha256(data).hexdigest()

            width, height = image.size

            records.append({
                "class_name": class_name,
                "photo_id": photo_id,
                "observation_id": row.get("observation_id"),
                "source": "iNaturalist",
                "source_url": original_url,
                "creator": row.get("creator"),
                "license": row.get("license_code"),
                "quality_grade": row.get("quality_grade"),
                "download_date": datetime.now().isoformat(),
                "filename": filename,
                "local_path": output_path,
                "sha256": sha256,
                "width": width,
                "height": height,
                "status": "downloaded"
            })

            print(
                f"  SKIP - existing "
                f"{width}x{height}"
            )

            continue

        response = session.get(
            original_url,
            timeout=90
        )

        response.raise_for_status()

        content_type = response.headers.get(
            "Content-Type",
            ""
        )

        if not content_type.lower().startswith("image/"):
            raise ValueError(
                f"Invalid Content-Type: {content_type}"
            )

        data = response.content

        if len(data) < 1000:
            raise ValueError(
                f"Image too small: {len(data)} bytes"
            )

        image = Image.open(
            BytesIO(data)
        )

        image.verify()

        image = Image.open(
            BytesIO(data)
        )

        width, height = image.size

        sha256 = hashlib.sha256(
            data
        ).hexdigest()

        with open(output_path, "wb") as f:
            f.write(data)

        records.append({
            "class_name": class_name,
            "photo_id": photo_id,
            "observation_id": row.get("observation_id"),
            "source": "iNaturalist",
            "source_url": original_url,
            "creator": row.get("creator"),
            "license": row.get("license_code"),
            "quality_grade": row.get("quality_grade"),
            "download_date": datetime.now().isoformat(),
            "filename": filename,
            "local_path": output_path,
            "sha256": sha256,
            "width": width,
            "height": height,
            "status": "downloaded"
        })

        print(
            f"  OK - "
            f"{width}x{height} - "
            f"{len(data)} bytes"
        )

    except Exception as e:

        print(
            f"  FAILED - {e}"
        )

        failures.append({
            "class_name": class_name,
            "photo_id": photo_id,
            "observation_id": row.get("observation_id"),
            "source_url": original_url,
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        })

    if (i + 1) % 100 == 0:

        pd.DataFrame(
            records
        ).to_csv(
            OUT_MANIFEST,
            index=False
        )

        pd.DataFrame(
            failures
        ).to_csv(
            FAIL_LOG,
            index=False
        )

        print()
        print(
            f"--- CHECKPOINT "
            f"{i+1}/{total} ---"
        )
        print(
            f"Downloaded: {len(records)}"
        )
        print(
            f"Failed: {len(failures)}"
        )
        print()

    time.sleep(0.10)

pd.DataFrame(
    records
).to_csv(
    OUT_MANIFEST,
    index=False
)

pd.DataFrame(
    failures
).to_csv(
    FAIL_LOG,
    index=False
)

print()
print("======================================")
print("iNaturalist original download complete")
print("======================================")
print(f"Candidates: {total}")
print(f"Downloaded: {len(records)}")
print(f"Failed: {len(failures)}")
print()
print(f"Manifest: {OUT_MANIFEST}")
print(f"Failures: {FAIL_LOG}")
