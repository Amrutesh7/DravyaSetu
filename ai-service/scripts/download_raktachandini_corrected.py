import os
import hashlib
import requests
import pandas as pd
from PIL import Image
from io import BytesIO
from datetime import datetime

BASE_DIR = r"D:\DravyaSetu\datasets\v2_expansion"

INPUT = os.path.join(
    BASE_DIR,
    "metadata",
    "raktachandini_inat_download_shortlist.csv"
)

OUT_DIR = os.path.join(
    BASE_DIR,
    "raw",
    "iNaturalist_Raktachandini_Corrected"
)

OUT_MANIFEST = os.path.join(
    BASE_DIR,
    "metadata",
    "raktachandini_corrected_download_manifest.csv"
)

os.makedirs(OUT_DIR, exist_ok=True)

df = pd.read_csv(INPUT)

session = requests.Session()
session.headers.update({
    "User-Agent": "DravyaSetu-V2-Dataset/1.0"
})

records = []
failures = []

print("=" * 60)
print("Raktachandini corrected download")
print("=" * 60)
print(f"Candidates: {len(df)}")

for i, row in df.iterrows():

    photo_id = str(int(row["photo_id"]))
    url = row["photo_url"]

    filename = f"Raktachandini_inat_{photo_id}.jpg"
    output_path = os.path.join(OUT_DIR, filename)

    print(f"[{i + 1}/{len(df)}] Photo {photo_id}")

    try:

        response = session.get(url, timeout=60)
        response.raise_for_status()

        data = response.content

        if len(data) < 1000:
            raise ValueError(f"Image too small: {len(data)} bytes")

        image = Image.open(BytesIO(data))
        image.verify()

        image = Image.open(BytesIO(data))
        width, height = image.size

        sha256 = hashlib.sha256(data).hexdigest()

        with open(output_path, "wb") as f:
            f.write(data)

        records.append({
            "class_name": "Raktachandini",
            "taxon_id": 442817,
            "observation_id": row["observation_id"],
            "photo_id": photo_id,
            "source": "iNaturalist",
            "source_url": url,
            "creator": row.get("creator"),
            "license": row.get("license_code"),
            "quality_grade": row.get("quality_grade"),
            "filename": filename,
            "local_path": output_path,
            "sha256": sha256,
            "width": width,
            "height": height,
            "download_date": datetime.now().isoformat(),
            "status": "downloaded"
        })

        print(f"  OK - {width}x{height} - {len(data)} bytes")

    except Exception as e:

        print(f"  FAILED - {e}")

        failures.append({
            "observation_id": row["observation_id"],
            "photo_id": photo_id,
            "source_url": url,
            "error": str(e)
        })

pd.DataFrame(records).to_csv(
    OUT_MANIFEST,
    index=False
)

print()
print("=" * 60)
print("DOWNLOAD COMPLETE")
print("=" * 60)
print(f"Successful: {len(records)}")
print(f"Failed:     {len(failures)}")
print(f"Manifest:   {OUT_MANIFEST}")
print(f"Images:     {OUT_DIR}")