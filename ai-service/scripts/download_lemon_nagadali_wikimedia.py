from pathlib import Path
import pandas as pd
import requests
import time

SHORTLIST = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\lemon_nagadali_wikimedia_shortlist.csv"
)

STAGING = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\raw\Wikimedia_Lemon_Nagadali"
)

MANIFEST = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\lemon_nagadali_wikimedia_download_manifest.csv"
)

STAGING.mkdir(parents=True, exist_ok=True)

df = pd.read_csv(SHORTLIST)

session = requests.Session()
session.headers.update({
    "User-Agent": "DravyaSetu-DatasetDownloader/1.0"
})

records = []

for i, row in df.iterrows():
    class_name = row["class_name"]
    source_url = row["source_url"]

    try:
        # Extract Wikimedia file title
        file_part = source_url.split("/wiki/File:", 1)[1]
        file_title = "File:" + requests.utils.unquote(file_part)

        # Get direct image URL from Wikimedia API
        api_url = "https://commons.wikimedia.org/w/api.php"

        params = {
            "action": "query",
            "format": "json",
            "prop": "imageinfo",
            "iiprop": "url",
            "titles": file_title
        }

        response = session.get(api_url, params=params, timeout=30)
        response.raise_for_status()

        data = response.json()
        pages = data["query"]["pages"]

        page = next(iter(pages.values()))
        image_url = page["imageinfo"][0]["url"]

        # Keep original extension where possible
        filename = Path(requests.utils.unquote(image_url.split("?")[0])).name

        if not filename:
            filename = f"commons_{i+1}.jpg"

        dest_dir = STAGING / class_name
        dest_dir.mkdir(parents=True, exist_ok=True)

        dest = dest_dir / filename

        image_response = session.get(image_url, timeout=60)
        image_response.raise_for_status()

        dest.write_bytes(image_response.content)

        records.append({
            "class_name": class_name,
            "source_url": source_url,
            "image_url": image_url,
            "local_path": str(dest),
            "status": "DOWNLOADED",
            "error": ""
        })

        print(f"{i + 1}/{len(df)} DOWNLOADED: {class_name} -> {filename}")

    except Exception as e:
        records.append({
            "class_name": class_name,
            "source_url": source_url,
            "image_url": "",
            "local_path": "",
            "status": "FAILED",
            "error": str(e)
        })

        print(f"{i + 1}/{len(df)} FAILED: {class_name} -> {e}")

    time.sleep(0.2)

pd.DataFrame(records).to_csv(MANIFEST, index=False)

print("\nDONE")
print("Total candidates:", len(df))
print("Downloaded:", sum(r["status"] == "DOWNLOADED" for r in records))
print("Failed:", sum(r["status"] == "FAILED" for r in records))
print("Staging:", STAGING)
print("Manifest:", MANIFEST)