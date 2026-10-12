import os
import time
import hashlib
import requests
import pandas as pd
from PIL import Image
from io import BytesIO
from datetime import datetime

BASE_DIR = r"D:\DravyaSetu\datasets\v2_expansion"
MANIFEST = os.path.join(BASE_DIR, "metadata", "inat_observation_candidates.csv")
RAW_DIR = os.path.join(BASE_DIR, "raw", "iNaturalist")
OUT_MANIFEST = os.path.join(BASE_DIR, "metadata", "inat_download_manifest.csv")
FAIL_LOG = os.path.join(BASE_DIR, "logs", "inat_download_failures.csv")

os.makedirs(RAW_DIR, exist_ok=True)
os.makedirs(os.path.dirname(OUT_MANIFEST), exist_ok=True)
os.makedirs(os.path.dirname(FAIL_LOG), exist_ok=True)

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
    photo_url = str(row["photo_url"])

    class_dir = os.path.join(RAW_DIR, class_name)
    os.makedirs(class_dir, exist_ok=True)

    ext = ".jpg"
    if ".jpeg" in photo_url.lower():
        ext = ".jpeg"
    elif ".png" in photo_url.lower():
        ext = ".png"

    filename = f"{class_name}_inat_{photo_id}{ext}"
    output_path = os.path.join(class_dir, filename)

    print(f"[{i+1}/{total}] {class_name} | photo {photo_id}")

    try:

        # Resume support
        if os.path.exists(output_path) and os.path.getsize(output_path) > 0:
            with open(output_path, "rb") as f:
                data = f.read()

            image = Image.open(BytesIO(data))
            image.verify()

            sha256 = hashlib.sha256(data).hexdigest()

            image = Image.open(BytesIO(data))
            width, height = image.size

            records.append({
                "class_name": class_name,
                "photo_id": photo_id,
                "observation_id": row.get("observation_id"),
                "source": "iNaturalist",
                "source_url": photo_url,
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

            print("  SKIP - already downloaded")
            continue

        response = session.get(
            photo_url,
            timeout=60
        )

        response.raise_for_status()

        content_type = response.headers.get("Content-Type", "")

        if not content_type.lower().startswith("image/"):
            raise ValueError(f"Invalid Content-Type: {content_type}")

        data = response.content

        if len(data) < 1000:
            raise ValueError(f"Image too small: {len(data)} bytes")

        # Verify actual image
        image = Image.open(BytesIO(data))
        image.verify()

        image = Image.open(BytesIO(data))
        width, height = image.size

        sha256 = hashlib.sha256(data).hexdigest()

        with open(output_path, "wb") as f:
            f.write(data)

        records.append({
            "class_name": class_name,
            "photo_id": photo_id,
            "observation_id": row.get("observation_id"),
            "source": "iNaturalist",
            "source_url": photo_url,
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

        print(f"  OK - {width}x{height} - {len(data)} bytes")

    except Exception as e:

        print(f"  FAILED - {e}")

        failures.append({
            "class_name": class_name,
            "photo_id": photo_id,
            "observation_id": row.get("observation_id"),
            "source_url": photo_url,
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        })

    # Save progress periodically
    if (i + 1) % 100 == 0:

        pd.DataFrame(records).to_csv(
            OUT_MANIFEST,
            index=False
        )

        pd.DataFrame(failures).to_csv(
            FAIL_LOG,
            index=False
        )

        print(f"\n--- CHECKPOINT {i+1}/{total} ---")
        print(f"Downloaded: {len(records)}")
        print(f"Failed: {len(failures)}\n")

    time.sleep(0.15)

# Final save
pd.DataFrame(records).to_csv(
    OUT_MANIFEST,
    index=False
)

pd.DataFrame(failures).to_csv(
    FAIL_LOG,
    index=False
)

print("\n======================================")
print("iNaturalist download complete")
print("======================================")
print(f"Candidates: {total}")
print(f"Downloaded: {len(records)}")
print(f"Failed: {len(failures)}")
print(f"Manifest: {OUT_MANIFEST}")
print(f"Failures: {FAIL_LOG}")
