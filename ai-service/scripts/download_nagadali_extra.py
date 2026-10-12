import pandas as pd
import requests
from pathlib import Path
import time

SHORTLIST = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\nagadali_extra_40_shortlist.csv"
)

STAGING = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\raw\iNaturalist_Nagadali_Extra"
)

MANIFEST = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\nagadali_extra_download_manifest.csv"
)

STAGING.mkdir(parents=True, exist_ok=True)

df = pd.read_csv(SHORTLIST)

session = requests.Session()
session.headers.update({
    "User-Agent": "DravyaSetu/1.0 research dataset downloader"
})

results = []

for i, row in df.iterrows():
    observation_id = int(row["observation_id"])
    photo_id = int(row["photo_id"])
    url = row["photo_url"]

    out = STAGING / f"Nagadali_{observation_id}_{photo_id}.jpg"

    try:
        r = session.get(url, timeout=30)
        r.raise_for_status()

        out.write_bytes(r.content)

        results.append({
            "class_name": "Nagadali",
            "observation_id": observation_id,
            "photo_id": photo_id,
            "photo_url": url,
            "local_path": str(out),
            "status": "DOWNLOADED",
            "error": ""
        })

        print(f"{i+1}/40 DOWNLOADED: {out.name}")

    except Exception as e:
        results.append({
            "class_name": "Nagadali",
            "observation_id": observation_id,
            "photo_id": photo_id,
            "photo_url": url,
            "local_path": "",
            "status": "FAILED",
            "error": str(e)
        })

        print(f"{i+1}/40 FAILED: {url} -> {e}")

    time.sleep(0.5)

pd.DataFrame(results).to_csv(MANIFEST, index=False)

print("\nDONE")
print("Total candidates:", len(df))
print("Downloaded:", sum(r["status"] == "DOWNLOADED" for r in results))
print("Failed:", sum(r["status"] == "FAILED" for r in results))
print("Staging:", STAGING)
print("Manifest:", MANIFEST)