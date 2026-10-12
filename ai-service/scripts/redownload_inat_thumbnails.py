import pandas as pd
import requests
import hashlib
import time
from pathlib import Path
from PIL import Image
from io import BytesIO

MANIFEST = Path(r"D:\DravyaSetu\datasets\v2_expansion\metadata\inat_original_manifest.csv")
FAILURES = Path(r"D:\DravyaSetu\datasets\v2_expansion\logs\inat_thumbnail_redownload_failures.csv")

df = pd.read_csv(MANIFEST)

bad = df[(df["width"] == 75) & (df["height"] == 75)].copy()

print("=" * 50)
print("iNaturalist targeted thumbnail replacement")
print("=" * 50)
print(f"Total manifest rows: {len(df)}")
print(f"75x75 files to replace: {len(bad)}")
print()

session = requests.Session()
session.headers.update({
    "User-Agent": "DravyaSetu-Dataset/2.0"
})

failures = []
success = 0

for i, (idx, row) in enumerate(bad.iterrows(), 1):
    photo_id = str(row["photo_id"])
    url = str(row["source_url"])
    local_path = Path(str(row["local_path"]))

    print(f"[{i}/{len(bad)}] {row['class_name']} | photo {photo_id}")

    try:
        response = session.get(url, timeout=30)

        if response.status_code != 200:
            raise RuntimeError(f"HTTP {response.status_code}")

        content = response.content

        image = Image.open(BytesIO(content))
        image.load()

        width, height = image.size

        if width < 384 or height < 384:
            raise RuntimeError(
                f"Still low resolution: {width}x{height}"
            )

        local_path.parent.mkdir(parents=True, exist_ok=True)
        local_path.write_bytes(content)

        sha256 = hashlib.sha256(content).hexdigest()

        df.at[idx, "sha256"] = sha256
        df.at[idx, "width"] = width
        df.at[idx, "height"] = height
        df.at[idx, "status"] = "downloaded_original"

        success += 1

        print(f"  OK - {width}x{height}")

    except Exception as e:
        failures.append({
            "photo_id": photo_id,
            "class_name": row["class_name"],
            "source_url": url,
            "local_path": str(local_path),
            "reason": str(e)
        })

        print(f"  FAILED - {e}")

    if i % 100 == 0:
        df.to_csv(MANIFEST, index=False)
        pd.DataFrame(failures).to_csv(FAILURES, index=False)

        print()
        print(f"--- CHECKPOINT {i}/{len(bad)} ---")
        print(f"Successful: {success}")
        print(f"Failed: {len(failures)}")
        print()

    time.sleep(0.05)

df.to_csv(MANIFEST, index=False)
pd.DataFrame(failures).to_csv(FAILURES, index=False)

print()
print("=" * 50)
print("Targeted redownload complete")
print("=" * 50)
print(f"Attempted: {len(bad)}")
print(f"Successful: {success}")
print(f"Failed: {len(failures)}")
print(f"Manifest: {MANIFEST}")
print(f"Failures: {FAILURES}")
