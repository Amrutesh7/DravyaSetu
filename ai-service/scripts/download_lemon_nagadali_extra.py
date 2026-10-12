import os
import time
import requests
import pandas as pd

INPUT = r"D:\DravyaSetu\datasets\v2_expansion\metadata\lemon_nagadali_extra_shortlist.csv"

OUTPUT_DIR = r"D:\DravyaSetu\datasets\v2_expansion\raw\iNaturalist_Lemon_Nagadali_Extra"

MANIFEST = r"D:\DravyaSetu\datasets\v2_expansion\metadata\lemon_nagadali_extra_download_manifest.csv"

os.makedirs(OUTPUT_DIR, exist_ok=True)

df = pd.read_csv(INPUT)

records = []

for _, row in df.iterrows():

    class_name = row["class_name"]
    observation_id = row["observation_id"]

    # Find the photo URL column
    url = row.get("photo_url")

    if pd.isna(url) or not str(url).startswith("http"):
        print("NO URL:", class_name, observation_id)
        continue

    url = str(url)

    ext = ".jpg"

    filename = f"{class_name}_{observation_id}{ext}"
    output = os.path.join(OUTPUT_DIR, filename)

    try:
        r = requests.get(url, timeout=30)
        r.raise_for_status()

        with open(output, "wb") as f:
            f.write(r.content)

        print("DOWNLOADED:", filename)

        records.append({
            "class_name": class_name,
            "observation_id": observation_id,
            "photo_url": url,
            "local_path": output,
            "status": "DOWNLOADED"
        })

    except Exception as e:

        print("FAILED:", class_name, observation_id, e)

        records.append({
            "class_name": class_name,
            "observation_id": observation_id,
            "photo_url": url,
            "local_path": output,
            "status": "FAILED"
        })

    time.sleep(0.5)

pd.DataFrame(records).to_csv(MANIFEST, index=False)

print()
print("DOWNLOAD COMPLETE")
print("Total records:", len(records))
print("Successful:", sum(r["status"] == "DOWNLOADED" for r in records))
print("Failed:", sum(r["status"] == "FAILED" for r in records))
print("Manifest:", MANIFEST)