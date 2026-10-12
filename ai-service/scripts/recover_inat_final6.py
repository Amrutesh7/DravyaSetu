import csv
import os
import hashlib
import requests
from PIL import Image
from io import BytesIO
from datetime import datetime

BASE = r"D:\DravyaSetu\datasets\v2_expansion"
MANIFEST = os.path.join(BASE, "metadata", "inat_original_manifest.csv")
LOG = os.path.join(BASE, "logs", "inat_final_recovery.csv")

TARGET_PHOTOS = {
    "724333441",
    "394097043",
    "295988370",
    "287755417",
    "705528995",
    "626782325",
}

MIN_SIZE = 384

session = requests.Session()
session.headers.update({
    "User-Agent": "DravyaSetu-V2-Dataset/1.0"
})

with open(MANIFEST, "r", encoding="utf-8", newline="") as f:
    rows = list(csv.DictReader(f))

results = []

for row in rows:
    if row["photo_id"] not in TARGET_PHOTOS:
        continue

    photo_id = row["photo_id"]
    path = row["local_path"]
    url = row["source_url"]

    result = {
        "photo_id": photo_id,
        "class_name": row["class_name"],
        "observation_id": row["observation_id"],
        "url": url,
        "old_width": row["width"],
        "old_height": row["height"],
        "new_width": "",
        "new_height": "",
        "status": "",
        "error": "",
        "timestamp": datetime.now().isoformat()
    }

    print("=" * 60)
    print(f"Photo: {photo_id}")
    print(f"Class: {row['class_name']}")
    print(f"URL:   {url}")

    try:
        r = session.get(url, timeout=30)

        if r.status_code != 200:
            raise RuntimeError(f"HTTP {r.status_code}")

        img = Image.open(BytesIO(r.content))
        img.load()

        w, h = img.size

        result["new_width"] = w
        result["new_height"] = h

        print(f"Downloaded: {w}x{h}")

        if w >= MIN_SIZE and h >= MIN_SIZE:
            with open(path, "wb") as out:
                out.write(r.content)

            sha = hashlib.sha256(r.content).hexdigest()

            row["sha256"] = sha
            row["width"] = str(w)
            row["height"] = str(h)
            row["status"] = "downloaded_original"

            result["status"] = "RECOVERED"

            print("RECOVERED")

        else:
            result["status"] = "STILL_LOW_RESOLUTION"
            print("Still below minimum resolution; original file preserved.")

    except Exception as e:
        result["status"] = "FAILED"
        result["error"] = str(e)
        print(f"FAILED: {e}")

    results.append(result)

with open(MANIFEST, "w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=rows[0].keys())
    writer.writeheader()
    writer.writerows(rows)

with open(LOG, "w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=results[0].keys())
    writer.writeheader()
    writer.writerows(results)

print()
print("=" * 60)
print("FINAL RECOVERY COMPLETE")
print("=" * 60)

for r in results:
    print(
        r["photo_id"],
        "|",
        r["class_name"],
        "|",
        r["status"],
        "|",
        r["new_width"],
        "x",
        r["new_height"]
    )

print()
print(f"Log: {LOG}")
